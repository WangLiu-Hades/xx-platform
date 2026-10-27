#requires -Version 5.1
<#
    verify-csrf-write-gate.ps1 - write-request CSRF + Origin gate checks (DEV-01, W2 task 3)

    Proves that the gate implemented by common/CsrfFilter is *global*: any
    POST/PUT/PATCH/DELETE is rejected with 403 AUTH_CSRF_INVALID before routing,
    while reads, preflight and the login exemption keep working.

    Requires the API running WITH the db profile (the token check reads
    auth.csrf_session). No login is needed; the "valid pair" case only needs
    GET /auth/csrf.

        $env:SPRING_PROFILES_ACTIVE      = 'db'
        $env:SPRING_DATASOURCE_URL       = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
        $env:SPRING_DATASOURCE_USERNAME  = 'xxplatform_owner'
        $env:SPRING_DATASOURCE_PASSWORD  = '<password>'
        .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the
    file has a UTF-8 BOM, so non-ASCII literals silently never match.

    Uses curl.exe rather than Invoke-WebRequest: on this host Invoke-WebRequest
    fails with 'Win32 internal error "Access is denied" 0x5 occurred while
    reading the console output buffer', which has nothing to do with the API
    and would make every HTTP assertion look like a server defect.
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string]$OwnOrigin = 'http://127.0.0.1:8080',
    [string]$AllowOrigin = 'http://127.0.0.1:5180'
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

function Get-CookieValue($response, [string]$name) {
    foreach ($c in [regex]::Matches($response.Headers, '(?im)^\s*Set-Cookie\s*:\s*(.+?)\s*$')) {
        $m = [regex]::Match($c.Groups[1].Value, "^$([regex]::Escape($name))=([^;]*)")
        if ($m.Success) { return $m.Groups[1].Value }
    }
    return $null
}

# A write with no credentials at all: the gate must answer, not the router.
function Write-WithoutCredentials([string]$method, [string]$path) {
    return Invoke-Curl @('--max-time', '10', '-X', $method, "$BaseUrl$path")
}

# ---------------------------------------------------------------- reachability
Show-Step 'Service reachability'
$probe = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
if ($probe.Code -eq 0) {
    Write-Host "  [FAIL] cannot reach $BaseUrl/auth/csrf" -ForegroundColor Red
    Write-Host '         Start it with the db profile first (see the header of this script).' -ForegroundColor Yellow
    exit 1
}
if ($probe.Code -eq 404) {
    Write-Host "  [FAIL] 404 - the endpoint is not mapped, or the service is running without the db profile" -ForegroundColor Red
    exit 1
}
Write-Host "  [OK]   reachable, HTTP $($probe.Code)" -ForegroundColor Green

# ------------------------------------------------- 1) reads are not behind the gate
Show-Step '1) Reads pass the gate untouched'
$health = Invoke-Curl @('--max-time', '10', "$BaseUrl/health")
Report ($health.Code -eq 200) 'GET /health -> 200' "HTTP $($health.Code)"

$me = Invoke-Curl @('--max-time', '10', "$BaseUrl/me")
Report ($me.Code -eq 401) 'GET /me without a session -> 401, not 403 (reads are not gated)' "HTTP $($me.Code)"
Report ((Get-EnvelopeCode $me) -eq 'AUTH_REQUIRED') 'GET /me uses the auth error code' "code: $(Get-EnvelopeCode $me)"

# ------------------------------- 2) every write method is gated before the router
Show-Step '2) POST/PUT/PATCH/DELETE are rejected before routing'
# These paths are NOT implemented yet (task/file/site/device handlers land later).
# A 403 here - rather than 404/405 - is the proof that the filter runs first.
foreach ($case in @(
        @{ Method = 'POST';   Path = '/tasks' },
        @{ Method = 'PUT';    Path = '/sites/1' },
        @{ Method = 'PATCH';  Path = '/devices/1' },
        @{ Method = 'DELETE'; Path = '/device-groups/1' }
    )) {
    $r = Write-WithoutCredentials $case.Method $case.Path
    Report ($r.Code -eq 403) "$($case.Method) $($case.Path) -> 403 (route not implemented yet)" "HTTP $($r.Code)"
    Report ((Get-EnvelopeCode $r) -eq 'AUTH_CSRF_INVALID') "$($case.Method) $($case.Path) uses AUTH_CSRF_INVALID" "code: $(Get-EnvelopeCode $r)"
}

# ------------------------------------------------------------- 3) rejection shape
Show-Step '3) Rejection is the contract error envelope'
$bare = Write-WithoutCredentials 'POST' '/auth/logout'
$bareJson = Get-Json $bare
Report ($bare.Code -eq 403) 'POST /auth/logout with no cookies -> 403' "HTTP $($bare.Code)"
Report ($bareJson -and $bareJson.code -eq 'AUTH_CSRF_INVALID') 'rejection code is AUTH_CSRF_INVALID' "code: $($bareJson.code)"
Report ($bareJson -and $null -eq $bareJson.data) 'rejection carries data:null' "data: $($bareJson.data)"
Report ($bareJson -and $null -ne $bareJson.details) 'rejection carries a details array' "details: $($bareJson.details)"
$hdrTrace = Get-HeaderValue $bare 'X-Trace-Id'
Report ($bareJson -and $hdrTrace -eq $bareJson.traceId -and $bareJson.traceId -ne 'unknown-trace') `
    'rejection traceId matches X-Trace-Id' "header: $hdrTrace body: $($bareJson.traceId)"
# The gate runs before authentication, so an anonymous caller must NOT get 401 here.
Report ($bare.Code -ne 401) 'gate wins over authentication (403, not 401)' "HTTP $($bare.Code)"

# ------------------------------------------------------------------- 4) origin
Show-Step '4) Foreign Origin is rejected on writes'
$evil = Invoke-Curl @('--max-time', '10', '-X', 'POST', '-H', 'Origin: https://evil.example.com', "$BaseUrl/tasks")
Report ($evil.Code -eq 403) 'POST with foreign Origin -> 403' "HTTP $($evil.Code)"
Report ((Get-EnvelopeCode $evil) -eq 'AUTH_CSRF_INVALID') 'foreign Origin uses AUTH_CSRF_INVALID' "code: $(Get-EnvelopeCode $evil)"

$own = Invoke-Curl @('--max-time', '10', '-X', 'POST', '-H', "Origin: $OwnOrigin", "$BaseUrl/tasks")
Report ($own.Code -eq 403) 'POST from own Origin without a token -> 403 (token still required)' "HTTP $($own.Code)"

# ------------------------------------------------------------ 5) login exemption
Show-Step '5) POST /auth/login is token-exempt but still validated'
# Empty body + no CSRF pair: the controller must answer 400 VALIDATION_ERROR.
# A 403 here would mean the gate swallowed the exemption.
$login = Invoke-Curl @('--max-time', '10', '-X', 'POST', '-H', 'Content-Type: application/json', '-d', '{}', "$BaseUrl/auth/login")
Report ($login.Code -eq 400) 'POST /auth/login without a CSRF pair -> 400 from the controller' "HTTP $($login.Code)"
Report ((Get-EnvelopeCode $login) -eq 'VALIDATION_ERROR') 'login reaches body validation' "code: $(Get-EnvelopeCode $login)"

# --------------------------------------------------- 6) gate passes with a valid pair
Show-Step '6) A valid cookie+token pair passes the gate'
$csrf = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
$csrfJson = Get-Json $csrf
$csrfCookie = Get-CookieValue $csrf 'CSRFID'
Report ([bool]($csrfJson -and $csrfJson.data.token -and $csrfCookie)) 'fetched a CSRF pair' "cookie: $([bool]$csrfCookie)"

if ($csrfJson -and $csrfCookie) {
    $pair = Invoke-Curl @(
        '--max-time', '10', '-X', 'POST',
        '-H', "Cookie: CSRFID=$csrfCookie",
        '-H', "X-CSRF-Token: $($csrfJson.data.token)",
        "$BaseUrl/auth/logout")
    # No SID in the request: the gate passes, authentication then rejects with 401.
    Report ($pair.Code -eq 401) 'POST /auth/logout with a valid pair but no session -> 401' "HTTP $($pair.Code)"
    Report ((Get-EnvelopeCode $pair) -eq 'AUTH_REQUIRED') 'auth error code after the gate' "code: $(Get-EnvelopeCode $pair)"
}

# -------------------------------------------------------------- 7) preflight
Show-Step '7) CORS preflight is not blocked by the gate'
$preflight = Invoke-Curl @(
    '--max-time', '10', '-X', 'OPTIONS',
    '-H', "Origin: $AllowOrigin",
    '-H', 'Access-Control-Request-Method: POST',
    '-H', 'Access-Control-Request-Headers: X-CSRF-Token',
    "$BaseUrl/tasks")
Report ($preflight.Code -ne 403) 'OPTIONS preflight -> not 403' "HTTP $($preflight.Code)"
Report ($null -ne (Get-HeaderValue $preflight 'Access-Control-Allow-Methods')) 'preflight carries Access-Control-Allow-Methods' `
    "value: $(Get-HeaderValue $preflight 'Access-Control-Allow-Methods')"

# ------------------------------------------------- 8) GET on a write-only route
Show-Step '8) GET on a write-only route is not the gate''s business'
$get = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/logout")
Report ($get.Code -eq 405) 'GET /auth/logout -> 405 (gate skips reads, MVC answers)' "HTTP $($get.Code)"
Report ((Get-EnvelopeCode $get) -eq 'METHOD_NOT_ALLOWED') 'method mismatch uses METHOD_NOT_ALLOWED' "code: $(Get-EnvelopeCode $get)"

# ---------------------------------------------------------------------- result
Write-Host ''
if ($script:failed -eq 0) {
    Write-Host "RESULT: $($script:passed) passed, 0 failed" -ForegroundColor Green
    exit 0
} else {
    Write-Host "RESULT: $($script:passed) passed, $($script:failed) failed" -ForegroundColor Red
    exit 1
}
