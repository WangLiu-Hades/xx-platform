#requires -Version 5.1
<#
    verify-logout.ps1 - POST /auth/logout acceptance checks (DEV-01, task 11)

    Requires the API running WITH the db profile AND the dev seed account enabled:

        $env:SPRING_PROFILES_ACTIVE      = 'db'
        $env:SPRING_DATASOURCE_URL       = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
        $env:SPRING_DATASOURCE_USERNAME  = 'xxplatform_owner'
        $env:SPRING_DATASOURCE_PASSWORD  = '<password>'
        $env:XX_AUTH_DEVSEED_ENABLED     = 'true'
        $env:XX_AUTH_DEVSEED_PASSWORD    = '<password>'
        .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"

    Then: .\scripts\verify-logout.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    Persistence checks shell out to psql and are skipped when it is absent.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the
    file has a UTF-8 BOM, so non-ASCII literals silently never match.
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

function Get-SetCookies($headers) {
    return [regex]::Matches($headers, '(?im)^\s*Set-Cookie\s*:\s*(.+?)\s*$') | ForEach-Object { $_.Groups[1].Value }
}

function Get-CookieValue($headers, [string]$name) {
    foreach ($c in Get-SetCookies $headers) {
        $m = [regex]::Match($c, "^$([regex]::Escape($name))=([^;]*)")
        if ($m.Success) { return $m.Groups[1].Value }
    }
    return $null
}

function Get-HeaderValue($headers, [string]$name) {
    $m = [regex]::Match($headers, "(?im)^\s*$([regex]::Escape($name))\s*:\s*(.+?)\s*$")
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function ConvertTo-JsonSafe($text) {
    try { return ($text | ConvertFrom-Json) } catch { return $null }
}

function New-BodyFile([string]$json) {
    $path = [System.IO.Path]::Combine($env:TEMP, ('logout-body-' + [guid]::NewGuid().ToString('N') + '.json'))
    [System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
    return $path
}

function New-LoginBody([string]$loginName, [string]$password) {
    return (@{ loginName = $loginName; password = $password } | ConvertTo-Json -Compress)
}

function Get-CsrfPair {
    $r = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
    $j = ConvertTo-JsonSafe $r.Body
    $token = $null
    if ($j) { $token = $j.data.token }
    return [pscustomobject]@{
        Response = $r
        Json     = $j
        Cookie   = (Get-CookieValue $r.Headers 'CSRFID')
        Token    = $token
    }
}

# Login carrying a valid CSRF pair (cookie + header). Returns the login response plus
# the SID and a fresh CSRF pair, mimicking what the browser holds after logging in
# (the frontend re-fetches CSRF right after login, see apps/web/src/stores/platform.ts).
function Login-Seed {
    $csrf = Get-CsrfPair
    $file = New-BodyFile (New-LoginBody $SeedLogin $SeedPassword)
    try {
        $r = Invoke-Curl @('--max-time', '10', '-X', 'POST', '-H', 'Content-Type: application/json',
                           '-H', "X-CSRF-Token: $($csrf.Token)", '-b', "CSRFID=$($csrf.Cookie)",
                           '--data-binary', "@$file", "$BaseUrl/auth/login")
    } finally {
        Remove-Item $file -ErrorAction SilentlyContinue
    }
    $after = Get-CsrfPair
    return [pscustomobject]@{
        Response = $r
        Sid      = (Get-CookieValue $r.Headers 'SID')
        Csrf     = $after
    }
}

# POST /auth/logout with the given cookies / header. Empty string = omit that part.
function Post-Logout([string]$sid, [string]$csrfCookie, [string]$csrfToken, [string[]]$Extra = @()) {
    $cookieArg = @()
    $pairs = @()
    if ($sid)        { $pairs += "SID=$sid" }
    if ($csrfCookie) { $pairs += "CSRFID=$csrfCookie" }
    if ($pairs.Count -gt 0) { $cookieArg = @('-b', ($pairs -join '; ')) }

    $headerArg = @()
    if ($csrfToken) { $headerArg = @('-H', "X-CSRF-Token: $csrfToken") }

    return Invoke-Curl (@('--max-time', '10', '-X', 'POST', "$BaseUrl/auth/logout") + $cookieArg + $headerArg + $Extra)
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

# ---------------------------------------------------------------- 1) live session
Show-Step '1) Establish a live session (login)'
$login = Login-Seed
Report ($login.Response.Code -eq 200) 'seed login -> 200' "HTTP $($login.Response.Code) body: $($login.Response.Body)"
Report ([bool]$login.Sid) 'login returned a SID cookie' "sid length: $($login.Sid.Length)"
$sid = $login.Sid

# ---------------------------------------------------------------- 2) CSRF gate
Show-Step '2) Logout is gated by the CSRF pair, and a rejected logout must not revoke'
$noHeader = Post-Logout $sid $login.Csrf.Cookie $null
Report ($noHeader.Code -eq 403) 'CSRF cookie but no X-CSRF-Token -> 403' "HTTP $($noHeader.Code)"
$noHeaderJson = ConvertTo-JsonSafe $noHeader.Body
Report ($noHeaderJson.code -eq 'AUTH_CSRF_INVALID') 'missing token uses AUTH_CSRF_INVALID' "code: $($noHeaderJson.code)"
Report ($null -eq $noHeaderJson.data) 'error body carries data:null (contract shape)' "data: $($noHeaderJson.data)"

$forged = Post-Logout $sid 'forged-csrf-cookie-not-issued-by-us' $login.Csrf.Token
Report ($forged.Code -eq 403) 'forged CSRF cookie value -> 403' "HTTP $($forged.Code)"

$stillAlive = Invoke-Curl @('--max-time', '10', '-b', "SID=$sid", "$BaseUrl/me")
Report ($stillAlive.Code -eq 200) 'session survives the two rejected logouts' "HTTP $($stillAlive.Code)"

# ---------------------------------------------------------------- 3) no session
Show-Step '3) Logout without a session is 401 (auth is checked before CSRF)'
$noSid = Post-Logout $null $login.Csrf.Cookie $login.Csrf.Token
Report ($noSid.Code -eq 401) 'no SID cookie -> 401' "HTTP $($noSid.Code)"
$noSidJson = ConvertTo-JsonSafe $noSid.Body
Report ($noSidJson.code -eq 'AUTH_REQUIRED') 'no session uses AUTH_REQUIRED' "code: $($noSidJson.code)"
Report ($null -eq (Get-CookieValue $noSid.Headers 'SID')) 'rejected logout sets no SID cookie' ''

# ---------------------------------------------------------------- 4) happy path
Show-Step '4) Logout revokes the session and clears both cookies'
$out = Post-Logout $sid $login.Csrf.Cookie $login.Csrf.Token
Report ($out.Code -eq 200) 'valid session + CSRF pair -> 200' "HTTP $($out.Code) body: $($out.Body)"
$outJson = ConvertTo-JsonSafe $out.Body
Report ($outJson.code -eq '0') 'envelope code is "0"' "code: $($outJson.code)"
Report ($outJson.message -eq 'success') 'envelope message is "success"' "message: $($outJson.message)"
Report ($outJson.data.accepted -eq $true) 'data.accepted is true (ActionResult)' "accepted: $($outJson.data.accepted)"

# ActionResult has additionalProperties:false -> only "accepted" may appear.
$dataKeys = @()
if ($outJson.data) {
    $dataKeys = @($outJson.data.PSObject.Properties | ForEach-Object { $_.Name })
}
Report (($dataKeys.Count -eq 1) -and ($dataKeys[0] -eq 'accepted')) 'data has exactly one key: accepted' "keys: $($dataKeys -join ',')"
Report ($out.Body -notmatch [regex]::Escape($sid)) 'SID value never appears in the response body' ''

$traceHeader = Get-HeaderValue $out.Headers 'X-Trace-Id'
Report ([bool]$traceHeader -and $traceHeader -eq $outJson.traceId) 'traceId matches X-Trace-Id' "header: $traceHeader body: $($outJson.traceId)"

$sidClear = (Get-SetCookies $out.Headers | Where-Object { $_ -match '^SID=' } | Select-Object -First 1)
Report ([bool]$sidClear) 'logout sends a Set-Cookie for SID' "cookie: $sidClear"
if ($sidClear) {
    Report ($sidClear -match '(?i);\s*Max-Age=0') 'SID cookie is expired with Max-Age=0' $sidClear
    Report ($sidClear -match '(?i);\s*HttpOnly') 'cleared SID cookie keeps HttpOnly' $sidClear
    Report ($sidClear -match '(?i);\s*SameSite=Lax') 'cleared SID cookie keeps SameSite=Lax' $sidClear
    Report ($sidClear -match '(?i);\s*Path=/api/v1') 'cleared SID cookie keeps Path=/api/v1' $sidClear
    Report ($sidClear -match '^SID=;') 'cleared SID cookie carries no value' $sidClear
}
$csrfClear = (Get-SetCookies $out.Headers | Where-Object { $_ -match '^CSRFID=' } | Select-Object -First 1)
Report ([bool]$csrfClear -and ($csrfClear -match '(?i);\s*Max-Age=0')) 'CSRFID cookie is expired too' "cookie: $csrfClear"

# ---------------------------------------------------------------- 5) session is gone
Show-Step '5) The revoked session can no longer be used'
$meAfter = Invoke-Curl @('--max-time', '10', '-b', "SID=$sid", "$BaseUrl/me")
Report ($meAfter.Code -eq 401) 'GET /me with the logged-out SID -> 401' "HTTP $($meAfter.Code)"
$meJson = ConvertTo-JsonSafe $meAfter.Body
Report ($meJson.code -eq 'AUTH_SESSION_EXPIRED') 'revoked session uses AUTH_SESSION_EXPIRED' "code: $($meJson.code)"

$again = Post-Logout $sid $login.Csrf.Cookie $login.Csrf.Token
Report ($again.Code -eq 401) 'repeating logout with the dead SID -> 401 (not 200)' "HTTP $($again.Code)"

# ---------------------------------------------------------------- 6) database
Show-Step '6) Persistence in auth.user_session'
$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
$dbReady = (Test-Path $psql) -and [bool]$DB_PASSWORD
if (-not $dbReady) {
    Write-Host '  [SKIP] psql.exe or DB_PASSWORD missing; persistence checks skipped' -ForegroundColor Yellow
} else {
    $env:PGPASSWORD = $DB_PASSWORD
    $m = [regex]::Match($DB_URL, '//([^:/]+):(\d+)/(.+)$')
    $dbHost = $m.Groups[1].Value
    $dbPort = $m.Groups[2].Value
    $dbName = $m.Groups[3].Value

    function Query-Scalar([string]$sql) {
        $out = & $psql -h $dbHost -p $dbPort -U $DB_USER -d $dbName -t -A -c $sql 2>&1
        return (($out | Out-String).Trim())
    }

    $revoked = Query-Scalar "SELECT count(*) FROM auth.user_session WHERE sid_hash = sha256(convert_to('$sid','UTF8')) AND revoked_at IS NOT NULL;"
    Report ($revoked -eq '1') 'the session row is marked revoked' "revoked rows: $revoked"

    $rows = Query-Scalar "SELECT count(*) FROM auth.user_session WHERE sid_hash = sha256(convert_to('$sid','UTF8'));"
    Report ($rows -eq '1') 'the row is kept for audit (revoke, not delete)' "rows: $rows"

    $account = Query-Scalar "SELECT status FROM auth.app_user WHERE login_name = '$SeedLogin';"
    Report ($account -eq 'ACTIVE') 'logout does not touch the account status' "status: $account"

    $traceRow = Query-Scalar "SELECT count(*) FROM auth.user_session WHERE sid_hash = sha256(convert_to('$sid','UTF8')) AND revoked_at IS NOT NULL;"
    Report ($traceRow -eq '1') 'revocation timestamp persisted' "rows: $traceRow"
}

# ---------------------------------------------------------------- 7) log back in
Show-Step '7) Logging in again works right after logout'
$relogin = Login-Seed
Report ($relogin.Response.Code -eq 200) 're-login after logout -> 200' "HTTP $($relogin.Response.Code)"
Report ([bool]$relogin.Sid -and $relogin.Sid -ne $sid) 'a brand new SID was issued' "old: $($sid.Substring(0,6))... new: $($relogin.Sid.Substring(0,6))..."
$meNew = Invoke-Curl @('--max-time', '10', '-b', "SID=$($relogin.Sid)", "$BaseUrl/me")
Report ($meNew.Code -eq 200) 'the new session is usable (GET /me -> 200)' "HTTP $($meNew.Code)"
$cleanup = Post-Logout $relogin.Sid $relogin.Csrf.Cookie $relogin.Csrf.Token
Report ($cleanup.Code -eq 200) 'the new session logs out as well' "HTTP $($cleanup.Code)"

# ---------------------------------------------------------------- 8) contract guards
Show-Step '8) Routing and origin guards still follow the contract'
$getLogout = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/logout")
Report ($getLogout.Code -eq 405) 'GET /auth/logout -> 405' "HTTP $($getLogout.Code)"
$getLogoutJson = ConvertTo-JsonSafe $getLogout.Body
Report ($getLogoutJson.code -eq 'METHOD_NOT_ALLOWED') '405 uses METHOD_NOT_ALLOWED' "code: $($getLogoutJson.code)"
$allow = Get-HeaderValue $getLogout.Headers 'Allow'
Report ([bool]$allow -and ($allow -match 'POST')) '405 carries Allow: POST' "Allow: $allow"

$probe2 = Login-Seed
$crossOrigin = Post-Logout $probe2.Sid $probe2.Csrf.Cookie $probe2.Csrf.Token @('-H', 'Origin: https://evil.example.com')
Report ($crossOrigin.Code -eq 403) 'cross-origin logout -> 403' "HTTP $($crossOrigin.Code)"
$crossJson = ConvertTo-JsonSafe $crossOrigin.Body
Report ($crossJson.code -eq 'AUTH_CSRF_INVALID') 'cross-origin rejection uses the contract envelope' "code: $($crossJson.code)"
$stillValid = Invoke-Curl @('--max-time', '10', '-b', "SID=$($probe2.Sid)", "$BaseUrl/me")
Report ($stillValid.Code -eq 200) 'cross-origin logout did not revoke the session' "HTTP $($stillValid.Code)"
Post-Logout $probe2.Sid $probe2.Csrf.Cookie $probe2.Csrf.Token | Out-Null

# ---------------------------------------------------------------- result
Write-Host ''
if ($script:failed -eq 0) {
    Write-Host "RESULT: $($script:passed) passed, 0 failed" -ForegroundColor Green
    exit 0
} else {
    Write-Host "RESULT: $($script:passed) passed, $($script:failed) failed" -ForegroundColor Red
    exit 1
}
