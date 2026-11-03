#requires -Version 5.1
<#
    verify-session-renew.ps1 - POST /auth/session/renew acceptance checks (DEV-01, W2 task 4)

    Proves the three things the contract keeps separate:
      1. explicit renewal only moves idleExpiresAt - absoluteExpiresAt never moves;
      2. neither SID nor the CSRF pair is rotated (a renewed session keeps working
         with the very same Cookie + token it came in with);
      3. an expired session cannot be renewed: idle timeout and absolute timeout
         both answer 401 AUTH_SESSION_EXPIRED, not 200 with a past deadline.

    Requires the API running WITH the db profile AND the dev seed account enabled:

        $env:SPRING_PROFILES_ACTIVE      = 'db'
        $env:SPRING_DATASOURCE_URL       = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
        $env:SPRING_DATASOURCE_USERNAME  = 'xxplatform_owner'
        $env:SPRING_DATASOURCE_PASSWORD  = '<password>'
        $env:XX_AUTH_DEVSEED_ENABLED     = 'true'          # xx.auth.dev-seed.enabled
        $env:XX_AUTH_DEVSEED_PASSWORD    = '<password>'    # never put this in yml
        .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"

    Then: .\scripts\verify-session-renew.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    The timeout cases need to move a session deadline into the past/near future, which
    only the database can do; they are skipped when psql is absent or cannot connect.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the file has
    a UTF-8 BOM, so non-ASCII literals silently never match.

    Uses curl.exe rather than Invoke-WebRequest: on this host Invoke-WebRequest fails with
    'Win32 internal error "Access is denied" 0x5 occurred while reading the console output
    buffer', which has nothing to do with the API.
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

# curl.exe: -i so we get status line + headers + body in one blob.
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

function Get-Json($response) {
    try { return $response.Body | ConvertFrom-Json } catch { return $null }
}

function Get-EnvelopeCode($response) {
    $json = Get-Json $response
    if ($null -eq $json) { return $null }
    return $json.code
}

function Get-HeaderValue($response, [string]$name) {
    $m = [regex]::Match($response.Headers, "(?im)^\s*$([regex]::Escape($name))\s*:\s*(.+?)\s*$")
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function Get-SetCookies($response) {
    return [regex]::Matches($response.Headers, '(?im)^\s*Set-Cookie\s*:\s*(.+?)\s*$') |
        ForEach-Object { $_.Groups[1].Value }
}

function Get-CookieValue($response, [string]$name) {
    foreach ($c in Get-SetCookies $response) {
        $m = [regex]::Match($c, "^$([regex]::Escape($name))=([^;]*)")
        if ($m.Success) { return $m.Groups[1].Value }
    }
    return $null
}

# A fresh synchronizer pair (anonymous GET /auth/csrf).
function Get-CsrfPair {
    $r = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
    $json = Get-Json $r
    return [pscustomobject]@{
        Cookie = (Get-CookieValue $r 'CSRFID')
        Token  = $(if ($json) { $json.data.token } else { $null })
    }
}

$script:loginFile = Join-Path $env:TEMP ("renew-login-" + [guid]::NewGuid().ToString('N') + ".json")

# JSON body goes through a UTF8-no-BOM file: PowerShell 5.1 mangles inline JSON
# quoting when handing args to curl.exe.
function Write-LoginBody {
    $json = @{ loginName = $SeedLogin; password = $SeedPassword } | ConvertTo-Json -Compress
    [System.IO.File]::WriteAllText($script:loginFile, $json, (New-Object System.Text.UTF8Encoding($false)))
}

function Post-Login($csrf) {
    Write-LoginBody
    return Invoke-Curl @(
        '--max-time', '15', '-X', 'POST',
        '-H', 'Content-Type: application/json',
        '-H', "X-CSRF-Token: $($csrf.Token)",
        '-b', "CSRFID=$($csrf.Cookie)",
        '--data-binary', "@$($script:loginFile)",
        "$BaseUrl/auth/login")
}

# Log in and return everything the renew cases need.
function New-Session {
    $csrf = Get-CsrfPair
    $login = Post-Login $csrf
    $json = Get-Json $login
    return [pscustomobject]@{
        Response = $login
        Json     = $json
        Sid      = (Get-CookieValue $login 'SID')
        Csrf     = $csrf
    }
}

function Invoke-Renew($sid, $csrf) {
    return Invoke-Curl @(
        '--max-time', '10', '-X', 'POST',
        '-H', "Cookie: SID=$sid; CSRFID=$($csrf.Cookie)",
        '-H', "X-CSRF-Token: $($csrf.Token)",
        "$BaseUrl/auth/session/renew")
}

# ---------------------------------------------------------------- reachability
Show-Step 'Service reachability'
if (-not $SeedPassword) {
    Write-Host '  [FAIL] -SeedPassword / XX_AUTH_DEVSEED_PASSWORD is required (the seed account password)' -ForegroundColor Red
    exit 1
}
$probe = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
if ($probe.Code -eq 0) {
    Write-Host "  [FAIL] cannot reach $BaseUrl/auth/csrf" -ForegroundColor Red
    Write-Host '         Start it with the db profile first (see the header of this script).' -ForegroundColor Yellow
    exit 1
}
Write-Host "  [OK]   reachable, HTTP $($probe.Code)" -ForegroundColor Green

# ------------------------------------------------------- 1) gate and method contract
Show-Step '1) The endpoint is a gated write, not an open door'
$bare = Invoke-Curl @('--max-time', '10', '-X', 'POST', "$BaseUrl/auth/session/renew")
Report ($bare.Code -eq 403) 'POST without a CSRF pair -> 403 from the write gate' "HTTP $($bare.Code)"
Report ((Get-EnvelopeCode $bare) -eq 'AUTH_CSRF_INVALID') 'gate rejection uses AUTH_CSRF_INVALID' "code: $(Get-EnvelopeCode $bare)"

$anonPair = Get-CsrfPair
$noSid = Invoke-Curl @(
    '--max-time', '10', '-X', 'POST',
    '-b', "CSRFID=$($anonPair.Cookie)",
    '-H', "X-CSRF-Token: $($anonPair.Token)",
    "$BaseUrl/auth/session/renew")
Report ($noSid.Code -eq 401) 'valid pair but no SID -> 401 (gate passes, auth rejects)' "HTTP $($noSid.Code)"
Report ((Get-EnvelopeCode $noSid) -eq 'AUTH_REQUIRED') 'no-session case uses AUTH_REQUIRED' "code: $(Get-EnvelopeCode $noSid)"

$get = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/session/renew")
Report ($get.Code -eq 405) 'GET -> 405 (renew is write-only)' "HTTP $($get.Code)"
Report ((Get-EnvelopeCode $get) -eq 'METHOD_NOT_ALLOWED') 'method mismatch uses METHOD_NOT_ALLOWED' "code: $(Get-EnvelopeCode $get)"

# ------------------------------------------------------------- 2) login baseline
Show-Step '2) Baseline session from POST /auth/login'
$session = New-Session
Report ($session.Response.Code -eq 200) 'seed account logs in' "HTTP $($session.Response.Code) body: $($session.Response.Body)"
if (-not $session.Sid -or -not $session.Json) {
    Write-Host '  [FAIL] no SID / no body; the remaining cases need a session' -ForegroundColor Red
    exit 1
}
$userId = $session.Json.data.userId
$idle0 = [datetimeoffset]::Parse($session.Json.data.idleExpiresAt)
$abs0 = [datetimeoffset]::Parse($session.Json.data.absoluteExpiresAt)
Report (([math]::Abs(($idle0 - [datetimeoffset]::UtcNow).TotalMinutes - 15)) -lt 2) 'baseline idleExpiresAt ~= now + 15m' "delta: $([math]::Round(($idle0 - [datetimeoffset]::UtcNow).TotalMinutes,1)) min"

# --------------------------------------------------------------- 3) happy path
Show-Step '3) Renewal moves idleExpiresAt and leaves absoluteExpiresAt alone'
Start-Sleep -Seconds 2   # make sure the truncated-second idle deadline can advance
$renew = Invoke-Renew $session.Sid $session.Csrf
$renewJson = Get-Json $renew
Report ($renew.Code -eq 200) 'renew -> 200' "HTTP $($renew.Code) body: $($renew.Body)"
Report ($renewJson.code -eq '0') 'envelope code is "0"' "code: $($renewJson.code)"
Report ($renewJson.data.userId -eq $userId) 'data.userId is the same user' "userId: $($renewJson.data.userId)"
$idle1 = [datetimeoffset]::Parse($renewJson.data.idleExpiresAt)
$abs1 = [datetimeoffset]::Parse($renewJson.data.absoluteExpiresAt)
Report ($idle1 -gt $idle0) 'idleExpiresAt moved later' "before: $idle0 after: $idle1"
Report ($idle1 -gt [datetimeoffset]::UtcNow) 'returned idleExpiresAt is in the future' "idle: $idle1"
Report (([math]::Abs(($idle1 - [datetimeoffset]::UtcNow).TotalMinutes - 15)) -lt 2) 'idleExpiresAt re-based to now + 15m' "delta: $([math]::Round(($idle1 - [datetimeoffset]::UtcNow).TotalMinutes,1)) min"
Report ($abs1 -eq $abs0) 'absoluteExpiresAt is unchanged (renewal never extends it)' "before: $abs0 after: $abs1"
Report ($renewJson.traceId -eq (Get-HeaderValue $renew 'X-Trace-Id') -and $renewJson.traceId -ne 'unknown-trace') 'traceId matches X-Trace-Id' "header: $(Get-HeaderValue $renew 'X-Trace-Id') body: $($renewJson.traceId)"
Report (@(Get-SetCookies $renew).Count -eq 0) 'renew sends no Set-Cookie (SID/CSRF are not rotated)' "cookies: $(@(Get-SetCookies $renew).Count)"

# The response must be exactly SessionData: 4 envelope keys / 3 data keys.
$topKeys = @($renewJson.PSObject.Properties.Name | Sort-Object)
$dataKeys = @($renewJson.data.PSObject.Properties.Name | Sort-Object)
Report (($topKeys -join ',') -eq 'code,data,message,traceId') 'envelope has exactly code/message/data/traceId' "keys: $($topKeys -join ',')"
Report (($dataKeys -join ',') -eq 'absoluteExpiresAt,idleExpiresAt,userId') 'data has exactly SessionData fields' "keys: $($dataKeys -join ',')"

# ------------------------------------------------- 4) nothing was rotated
Show-Step '4) The renewed session keeps the same SID and the same CSRF pair'
$again = Invoke-Renew $session.Sid $session.Csrf
Report ($again.Code -eq 200) 'second renew with the same SID + CSRF pair -> 200' "HTTP $($again.Code)"
if ($again.Code -ne 200) {
    Write-Host '         (a rotated SID or CSRF pair would show up exactly here)' -ForegroundColor DarkGray
}
$me = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/me")
Report ($me.Code -eq 200) 'GET /me with the same SID still works' "HTTP $($me.Code)"

# ------------------------------------------------------------ 5) absolute cap
Show-Step '5) Renewal is capped at absoluteExpiresAt'
$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
$dbReady = $false
function Query-Scalar([string]$sql) {
    $out = & $psql -h $dbHost -p $dbPort -U $DB_USER -d $dbName -t -A -c $sql 2>&1
    return (($out | Out-String).Trim())
}
if ((Test-Path $psql) -and [bool]$DB_PASSWORD) { $env:PGPASSWORD = $DB_PASSWORD }
if (Test-Path $psql) {
    $m = [regex]::Match($DB_URL, '//([^:/]+):(\d+)/(.+)$')
    $dbHost = $m.Groups[1].Value
    $dbPort = $m.Groups[2].Value
    $dbName = $m.Groups[3].Value
    try { $dbReady = (Query-Scalar 'select 1') -eq '1' } catch { $dbReady = $false }
}
if (-not $dbReady) {
    Write-Host '  [SKIP] psql unavailable; absolute-cap and timeout cases skipped' -ForegroundColor Yellow
} else {
    # Single-session policy means one active row per user, so targeting by user_id is exact.
    # The clamp only triggers when the absolute deadline is closer than one idle TTL, so pull
    # the deadline in to now + 5m (inside the 15m idle window) and pin idle to now + 1m.
    $null = Query-Scalar "UPDATE auth.user_session SET absolute_expires_at = now() + interval '5 minutes', idle_expires_at = now() + interval '1 minute' WHERE user_id = '$userId' AND revoked_at IS NULL"
    $capped = Invoke-Renew $session.Sid $session.Csrf
    $cappedJson = Get-Json $capped
    Report ($capped.Code -eq 200) 'renew near the absolute deadline -> 200' "HTTP $($capped.Code)"
    if ($cappedJson) {
        Report ($cappedJson.data.idleExpiresAt -eq $cappedJson.data.absoluteExpiresAt) `
            'idleExpiresAt is clamped to absoluteExpiresAt' "idle: $($cappedJson.data.idleExpiresAt) abs: $($cappedJson.data.absoluteExpiresAt)"
        $cappedAbsolute = [datetimeoffset]::Parse($cappedJson.data.absoluteExpiresAt)
        Report ($cappedAbsolute -lt [datetimeoffset]::UtcNow.AddMinutes(7)) `
            'clamping never pushes the absolute deadline further out' "absolute: $cappedAbsolute"
    } else {
        Report $false 'capped renewal returned the contract envelope' "body: $($capped.Body)"
    }

    # ---------------------------------------------------- 6) idle timeout
    Write-Host ''
    Show-Step '6) Idle-expired session cannot be renewed'
    $stale = New-Session
    $null = Query-Scalar "UPDATE auth.user_session SET idle_expires_at = now() - interval '1 minute' WHERE user_id = '$userId' AND revoked_at IS NULL"
    $expired = Invoke-Renew $stale.Sid $stale.Csrf
    Report ($expired.Code -eq 401) 'renew with an idle-expired session -> 401' "HTTP $($expired.Code)"
    Report ((Get-EnvelopeCode $expired) -eq 'AUTH_SESSION_EXPIRED') 'idle timeout uses AUTH_SESSION_EXPIRED' "code: $(Get-EnvelopeCode $expired)"

    # ------------------------------------------------- 7) absolute timeout
    Write-Host ''
    Show-Step '7) Absolute-expired session cannot be renewed'
    $doomed = New-Session
    $null = Query-Scalar "UPDATE auth.user_session SET absolute_expires_at = now() + interval '2 seconds', idle_expires_at = now() + interval '2 seconds' WHERE user_id = '$userId' AND revoked_at IS NULL"
    Start-Sleep -Seconds 3
    $late = Invoke-Renew $doomed.Sid $doomed.Csrf
    Report ($late.Code -eq 401) 'renew after the absolute deadline -> 401' "HTTP $($late.Code)"
    Report ((Get-EnvelopeCode $late) -eq 'AUTH_SESSION_EXPIRED') 'absolute timeout uses AUTH_SESSION_EXPIRED' "code: $(Get-EnvelopeCode $late)"
    $lateMe = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($doomed.Sid)", "$BaseUrl/me")
    Report ($lateMe.Code -eq 401) 'authentication agrees: GET /me with that SID is 401 too' "HTTP $($lateMe.Code)"
}

Remove-Item $script:loginFile -ErrorAction SilentlyContinue

# ---------------------------------------------------------------------- result
Write-Host ''
if ($script:failed -eq 0) {
    Write-Host "RESULT: $($script:passed) passed, 0 failed" -ForegroundColor Green
    exit 0
} else {
    Write-Host "RESULT: $($script:passed) passed, $($script:failed) failed" -ForegroundColor Red
    exit 1
}
