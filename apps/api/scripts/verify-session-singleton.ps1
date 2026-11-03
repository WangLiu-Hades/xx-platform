#requires -Version 5.1
<#
    verify-session-singleton.ps1 - A02 regression: one account, one session.

    The review found that login only revoked the SID the client presented. A second
    browser (or a second machine) logs in without any SID, so the first session keeps
    working - the same person can hold several live sessions even though the contract
    says "single account, single session". The fix locks auth.app_user FOR UPDATE,
    re-checks the account status under the lock, revokes *every* live session of that
    account and only then inserts the new one.

    What this script proves, against a real API and a real PostgreSQL:

      1. two logins in a row (neither presents a SID): the first SID is dead, the
         second works, and the account has exactly one live session row;
      2. two logins fired concurrently: both may succeed, but exactly one live session
         survives - which is what the row lock is for. Without the lock both inserts
         see no rows to revoke and both sessions stay live;
      3. a normal authenticated request moves idle_expires_at later, but never past
         absolute_expires_at;
      4. health probes carrying the SID cookie do not renew: polling must not keep a
         session alive forever (a load balancer hits /health every few seconds);
      5. if the transaction fails after the revocations (a trigger makes the insert
         throw), the previous session is still alive - the revocations rolled back
         with it instead of logging the user out on a server error.

    Requires: API running with the db profile, psql on PATH, DB credentials, seed password.

        .\scripts\verify-session-singleton.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    The script revokes the account's live sessions in step 1, so it is safe to re-run.
    It does not delete audit rows: sessions are only ever revoked, never removed.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the file has
    a UTF-8 BOM, so non-ASCII literals silently never match.
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string]$DB_URL  = 'jdbc:postgresql://127.0.0.1:5432/xxplatform',
    [string]$DB_USER = 'xxplatform_owner',
    [string]$DB_PASSWORD = $env:DB_PASSWORD,
    [string]$SeedLogin = 'demo.admin',
    [string]$SeedPassword = $env:XX_AUTH_DEVSEED_PASSWORD
)

$ErrorActionPreference = 'Stop'
$script:passed = 0
$script:failed = 0

function Show-Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }

function Report($ok, $name, $detail) {
    if ($ok) {
        $script:passed++
        Write-Host "  [PASS] $name" -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host "  [FAIL] $name" -ForegroundColor Red
    }
    if ($detail) { Write-Host "         $detail" -ForegroundColor DarkGray }
}

function ConvertTo-JsonSafe($text) {
    try { return ($text | ConvertFrom-Json) } catch { return $null }
}

function Invoke-Curl([string[]]$curlArgs) {
    $raw = & curl.exe -s -i @curlArgs 2>&1
    $text = ($raw | Out-String)
    $code = 0
    if ($text -match 'HTTP/\d(?:\.\d)?\s+(\d{3})') { $code = [int]$Matches[1] }
    $parts = $text -split "`r?`n`r?`n", 2
    $headers = if ($parts.Count -ge 1) { $parts[0] } else { '' }
    $body = if ($parts.Count -ge 2) { $parts[1] } else { '' }
    return [pscustomobject]@{ Code = $code; Headers = $headers; Body = $body; Raw = $text }
}

function Get-CookieValue($headers, [string]$name) {
    foreach ($c in [regex]::Matches($headers, '(?im)^\s*Set-Cookie\s*:\s*(.+?)\s*$')) {
        $m = [regex]::Match($c.Groups[1].Value, "^$([regex]::Escape($name))=([^;]*)")
        if ($m.Success) { return $m.Groups[1].Value }
    }
    return $null
}

# Login is gated by the synchronizer token: the CSRFID cookie and the matching
# X-CSRF-Token header must both be present (see verify-login.ps1 section 1).
function New-CsrfPair {
    $csrf = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
    $json = ConvertTo-JsonSafe $csrf.Body
    $token = $null
    if ($json) { $token = $json.data.token }
    return [pscustomobject]@{ Token = $token; Cookie = (Get-CookieValue $csrf.Headers 'CSRFID') }
}

function New-LoginBodyFile {
    $file = [System.IO.Path]::Combine($env:TEMP, ('a02-' + [guid]::NewGuid().ToString('N') + '.json'))
    $json = @{ loginName = $SeedLogin; password = $SeedPassword } | ConvertTo-Json -Compress
    [System.IO.File]::WriteAllText($file, $json, (New-Object System.Text.UTF8Encoding($false)))
    return $file
}

function New-Login([pscustomobject]$csrf) {
    $bodyFile = New-LoginBodyFile
    try {
        return Invoke-Curl @('--max-time', '20', '-X', 'POST', '-H', 'Content-Type: application/json',
                             '-H', "X-CSRF-Token: $($csrf.Token)", '-b', "CSRFID=$($csrf.Cookie)",
                             '--data-binary', "@$bodyFile", "$BaseUrl/auth/login")
    } finally {
        Remove-Item $bodyFile -ErrorAction SilentlyContinue
    }
}

function Get-MeStatus([string]$sid) {
    if (-not $sid) { return 0 }
    $r = Invoke-Curl @('--max-time', '10', '-b', "SID=$sid", "$BaseUrl/me")
    return $r.Code
}

# The two concurrent logins run in-process runspaces (no Start-Process and no pipes, so
# they also work under the file sandbox). Each runspace performs its own curl call.
function Start-ParallelLogin([pscustomobject]$csrf) {
    $bodyFile = New-LoginBodyFile
    $script = @'
param($base, $token, $cookie, $dataFile)
$raw = & curl.exe -s -i --max-time 20 -X POST -H 'Content-Type: application/json' -H "X-CSRF-Token: $token" -b "CSRFID=$cookie" --data-binary "@$dataFile" "$base/auth/login" 2>&1
($raw | Out-String)
'@
    $ps = [powershell]::Create()
    [void]$ps.AddScript($script).AddArgument($BaseUrl).AddArgument($csrf.Token).
        AddArgument($csrf.Cookie).AddArgument($bodyFile)
    $handle = $ps.BeginInvoke()
    return [pscustomobject]@{ Shell = $ps; Handle = $handle; BodyFile = $bodyFile }
}

function Complete-ParallelLogin([pscustomobject]$login) {
    try {
        $out = $login.Shell.EndInvoke($login.Handle)
        $text = ($out | Out-String)
    } finally {
        $login.Shell.Dispose()
        Remove-Item $login.BodyFile -ErrorAction SilentlyContinue
    }
    $code = 0
    if ($text -match 'HTTP/\d(?:\.\d)?\s+(\d{3})') { $code = [int]$Matches[1] }
    $parts = $text -split "`r?`n`r?`n", 2
    $headers = if ($parts.Count -ge 1) { $parts[0] } else { '' }
    return [pscustomobject]@{ Code = $code; Headers = $headers }
}

$psql = Get-Command psql.exe -ErrorAction SilentlyContinue
if (-not $psql) { $psql = Get-Command psql -ErrorAction SilentlyContinue }
if (-not $psql -or -not $DB_PASSWORD) {
    Write-Host "  [SKIP] psql.exe or DB_PASSWORD missing; session rows cannot be inspected" -ForegroundColor Yellow
    exit 0
}
if (-not $SeedPassword) {
    Write-Host "  [SKIP] SeedPassword (XX_AUTH_DEVSEED_PASSWORD) missing" -ForegroundColor Yellow
    exit 0
}

if ($DB_URL -notmatch '^jdbc:postgresql://([^:/]+):(\d+)/(.+)$') {
    Write-Host "  [FAIL] DB_URL is not a jdbc:postgresql URL: $DB_URL" -ForegroundColor Red
    exit 2
}
$dbHost = $Matches[1]
$dbPort = $Matches[2]
$dbName = $Matches[3]

function Invoke-Sql([string]$sql) {
    $env:PGPASSWORD = $DB_PASSWORD
    # psql writes NOTICE lines to stderr; with $ErrorActionPreference='Stop' the redirection below
    # would turn the first NOTICE into a terminating error. Keep this function on 'Continue' and
    # judge success by the exit code only.
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $out = & $psql.Source -h $dbHost -p $dbPort -U $DB_USER -d $dbName -v ON_ERROR_STOP=1 -tA -c $sql 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($code -ne 0) { throw "psql failed: $($out -join ' ')" }
    return ($out | Out-String).Trim()
}

$loginNameSql = $SeedLogin.Replace("'", "''")
function Get-LiveSessionCount {
    return [int](Invoke-Sql @"
SELECT count(*)
  FROM auth.user_session s
  JOIN auth.app_user u ON u.id = s.user_id
 WHERE u.login_name = '$loginNameSql'
   AND s.revoked_at IS NULL;
"@)
}

try {
    Show-Step '1) two logins in a row, neither presents a SID'
    # Start from a known state: revoke whatever was live for this account.
    Invoke-Sql "UPDATE auth.user_session SET revoked_at = now() WHERE revoked_at IS NULL AND user_id = (SELECT id FROM auth.app_user WHERE login_name = '$loginNameSql');" | Out-Null
    Report ((Get-LiveSessionCount) -eq 0) 'setup: no live session for the test account' ''

    $first = New-Login (New-CsrfPair)
    $sid1 = Get-CookieValue $first.Headers 'SID'
    Report ($first.Code -eq 200 -and $sid1) 'first login succeeds' "HTTP $($first.Code)"
    Report ((Get-LiveSessionCount) -eq 1) 'after the first login exactly one live session exists' ''

    $second = New-Login (New-CsrfPair)
    $sid2 = Get-CookieValue $second.Headers 'SID'
    Report ($second.Code -eq 200 -and $sid2) 'second login succeeds' "HTTP $($second.Code)"
    Report ($sid1 -ne $sid2) 'the two logins issued different SIDs' ''

    $live = Get-LiveSessionCount
    Report ($live -eq 1) 'after the second login the account still has exactly one live session' "live=$live (a pre-A02 build leaves 2)"
    Report ((Get-MeStatus $sid1) -eq 401) 'the first SID is rejected after the second login' "GET /me -> $((Get-MeStatus $sid1))"
    Report ((Get-MeStatus $sid2) -eq 200) 'the second SID still works' "GET /me -> $((Get-MeStatus $sid2))"

    Show-Step '2) two logins fired at the same time'
    Invoke-Sql "UPDATE auth.user_session SET revoked_at = now() WHERE revoked_at IS NULL AND user_id = (SELECT id FROM auth.app_user WHERE login_name = '$loginNameSql');" | Out-Null
    Report ((Get-LiveSessionCount) -eq 0) 'setup: no live session before the concurrent round' ''

    $csrfA = New-CsrfPair
    $csrfB = New-CsrfPair
    $a = Start-ParallelLogin $csrfA
    $b = Start-ParallelLogin $csrfB
    $resultA = Complete-ParallelLogin $a
    $resultB = Complete-ParallelLogin $b
    $sidA = Get-CookieValue $resultA.Headers 'SID'
    $sidB = Get-CookieValue $resultB.Headers 'SID'
    Report ($resultA.Code -eq 200 -or $resultB.Code -eq 200) 'at least one concurrent login succeeded' "HTTP $($resultA.Code) / $($resultB.Code)"

    $live = Get-LiveSessionCount
    Report ($live -eq 1) 'concurrent logins leave exactly one live session (row lock)' "live=$live"

    $okA = (Get-MeStatus $sidA) -eq 200
    $okB = (Get-MeStatus $sidB) -eq 200
    $working = @(($okA, $sidA), ($okB, $sidB)) | Where-Object { $_[0] } | ForEach-Object { $_[1] }
    Report ($working.Count -eq 1) 'exactly one of the two concurrent SIDs survives' "SID A works=$okA, SID B works=$okB"

    function Get-SessionDeadlines {
        $row = Invoke-Sql @"
SELECT s.idle_expires_at::text || '|' || s.absolute_expires_at::text
  FROM auth.user_session s
  JOIN auth.app_user u ON u.id = s.user_id
 WHERE u.login_name = '$loginNameSql'
   AND s.revoked_at IS NULL;
"@
        if (-not $row) { return $null }
        $parts = $row -split '\|'
        return [pscustomobject]@{
            Idle     = [datetimeoffset]::Parse($parts[0])
            Absolute = [datetimeoffset]::Parse($parts[1])
        }
    }

    Show-Step '3) a normal request pushes the idle deadline forward'
    $third = New-Login (New-CsrfPair)
    $sid3 = Get-CookieValue $third.Headers 'SID'
    Report ($third.Code -eq 200 -and $sid3) 'login for the renewal round succeeds' "HTTP $($third.Code)"
    $before = Get-SessionDeadlines
    Start-Sleep -Seconds 2
    $meCode = Get-MeStatus $sid3
    $after = Get-SessionDeadlines
    Report ($meCode -eq 200) 'GET /me with the new SID succeeds' "HTTP $meCode"
    Report ($null -ne $before -and $null -ne $after -and $after.Idle -gt $before.Idle) 'idle_expires_at moved later after GET /me' "before=$($before.Idle.ToString('o')) after=$($after.Idle.ToString('o'))"
    Report ($null -ne $after -and $after.Idle -le $after.Absolute) 'the renewal never passes absolute_expires_at' "idle=$($after.Idle.ToString('o')) absolute=$($after.Absolute.ToString('o'))"

    Show-Step '4) probes do not renew (polling must not keep a session alive forever)'
    $before = Get-SessionDeadlines
    $probeCodes = @()
    for ($i = 0; $i -lt 3; $i++) {
        $probe = Invoke-Curl @('--max-time', '10', '-b', "SID=$sid3", "$BaseUrl/health")
        $probeCodes += $probe.Code
        Start-Sleep -Seconds 1
    }
    $after = Get-SessionDeadlines
    Report (($probeCodes | Where-Object { $_ -ne 200 }).Count -eq 0) 'GET /health stays reachable with a SID cookie' "codes=$($probeCodes -join ',')"
    Report ($null -ne $before -and $null -ne $after -and $after.Idle -eq $before.Idle) 'three probes left idle_expires_at unchanged' "before=$($before.Idle.ToString('o')) after=$($after.Idle.ToString('o'))"

    Show-Step '5) a failure inside the login transaction rolls the revocation back'
    # Inject a failure at the very last write of the transaction (the session insert). A renamed
    # table would not do: the CSRF lookup fails before the transaction starts, so it would prove
    # nothing about rollback.
    Invoke-Sql @'
CREATE OR REPLACE FUNCTION auth.a02_selftest_fail_insert() RETURNS trigger AS $fn$
BEGIN
    RAISE EXCEPTION 'A02 self-test: refusing to insert a session';
END;
$fn$ LANGUAGE plpgsql;
DROP TRIGGER IF EXISTS a02_selftest_fail_insert ON auth.user_session;
CREATE TRIGGER a02_selftest_fail_insert BEFORE INSERT ON auth.user_session
    FOR EACH ROW EXECUTE FUNCTION auth.a02_selftest_fail_insert();
'@ | Out-Null
    try {
        $failedLogin = New-Login (New-CsrfPair)
        Report ($failedLogin.Code -ge 500) 'the login fails while the trigger is installed' "HTTP $($failedLogin.Code)"
        $live = Get-LiveSessionCount
        Report ($live -eq 1) 'the previous session was not revoked (the transaction rolled back)' "live=$live"
        Report ((Get-MeStatus $sid3) -eq 200) 'the previous SID still works after the failed login' "GET /me -> $((Get-MeStatus $sid3))"
    } finally {
        Invoke-Sql @'
DROP TRIGGER IF EXISTS a02_selftest_fail_insert ON auth.user_session;
DROP FUNCTION IF EXISTS auth.a02_selftest_fail_insert();
'@ | Out-Null
    }
    $recovered = New-Login (New-CsrfPair)
    $sid5 = Get-CookieValue $recovered.Headers 'SID'
    Report ($recovered.Code -eq 200 -and $sid5) 'login works again once the trigger is gone' "HTTP $($recovered.Code)"
    Report ((Get-LiveSessionCount) -eq 1) 'the recovered login leaves exactly one live session' ''

    Show-Step '6) cleanup'
    Invoke-Sql "UPDATE auth.user_session SET revoked_at = now() WHERE revoked_at IS NULL AND user_id = (SELECT id FROM auth.app_user WHERE login_name = '$loginNameSql');" | Out-Null
    Report ((Get-LiveSessionCount) -eq 0) 'test account left with no live session' ''
} catch {
    Write-Host "`n[FAIL] script aborted: $($_.Exception.Message)" -ForegroundColor Red
    $script:failed++
}

Write-Host "`n---- summary ----" -ForegroundColor Cyan
Write-Host ("  passed: {0}   failed: {1}" -f $script:passed, $script:failed)
if ($script:failed -gt 0) { exit 1 }
exit 0
