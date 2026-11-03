# apps/api shared-layer regression check (CORS / 405 / traceId / envelope)
#
# Usage (the service must already be running; it needs the DB profile to boot):
#   cd apps/api
#   mvn -o -B spring-boot:run "-Dspring-boot.run.profiles=dev,db"
#   # in another terminal, from repo root:
#   powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/verify-skeleton.ps1
#   # pwsh works too where PowerShell 7+ is installed
#
# Why this script exists: the 2026-10-16 integration test report found four shared-layer
# defects that unit tests did not cover (and that Mockito blocks from running inside the
# sandbox):
#   1. CORS preflight returned 403 for an allowed Origin
#   2. POST to a GET-only route returned 500 instead of 405
#   3. traceId was taken from the client header
#   4. unknown JSON fields were silently ignored
# Defects 1-3 are asserted below against the real contract endpoints (GET /health for the
# probes, POST /health for the 405 case). Defect 4 no longer has a probe endpoint: it is
# covered in verify-login.ps1, which posts a real LoginRequest and asserts that an extra
# field comes back as 400 VALIDATION_ERROR. The dev-only /smoke probe was deleted once
# GET /health and GET /ready existed, so there is no second probe to keep in sync.
#
# Exit code: 0 = all checks passed, 1 = at least one check failed.
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 as ANSI unless the
# file has a UTF-8 BOM, so non-ASCII text here can break parsing on that host.

param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string]$AllowedOrigin = 'http://127.0.0.1:5180'
)

$ErrorActionPreference = 'Stop'
$script:failed = 0
$script:passed = 0

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

# Runs curl.exe and returns status code + headers + body.
function Invoke-Curl([string[]]$curlArgs) {
    $raw = & curl.exe -s -i @curlArgs 2>&1
    $text = ($raw | Out-String)
    $code = 0
    if ($text -match 'HTTP/\d(?:\.\d)?\s+(\d{3})') { $code = [int]$Matches[1] }
    # split headers and body at the first blank line
    $parts = $text -split "`r?`n`r?`n", 2
    $headers = if ($parts.Count -ge 1) { $parts[0] } else { '' }
    $body = if ($parts.Count -ge 2) { $parts[1] } else { '' }
    return [pscustomobject]@{ Code = $code; Headers = $headers; Body = $body; Raw = $text }
}

function Get-HeaderValue($headers, [string]$name) {
    $m = [regex]::Match($headers, "(?im)^\s*$([regex]::Escape($name))\s*:\s*(.+?)\s*$")
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function Get-CookieValue($headers, [string]$name) {
    $m = [regex]::Match($headers, "(?im)^\s*Set-Cookie\s*:\s*$([regex]::Escape($name))=([^;]+)")
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

# A write needs a valid cookie+token pair to get past common/CsrfFilter. The pair is not
# tied to a session (CsrfTokenService.validate only checks cookie hash + token hash), so
# an anonymous GET /auth/csrf is enough to reach the routing layer.
function Get-CsrfPair {
    $r = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
    $token = $null
    if ($r.Body -match '"token"\s*:\s*"([^"]+)"') { $token = $Matches[1] }
    return [pscustomobject]@{
        Cookie = (Get-CookieValue $r.Headers 'CSRFID')
        Token  = $token
    }
}

# ---------------------------------------------------------------- reachability
Show-Step 'Service reachability'
$probe = Invoke-Curl @("--max-time", "5", "$BaseUrl/health")
if ($probe.Code -ne 200) {
    Write-Host "  [FAIL] $BaseUrl/health did not return 200 (got $($probe.Code))." -ForegroundColor Red
    Write-Host '         Start it first: mvn -o -B spring-boot:run "-Dspring-boot.run.profiles=dev,db"' -ForegroundColor Yellow
    exit 1
}
Write-Host "  [OK]   service reachable at $BaseUrl" -ForegroundColor Green

# ---------------------------------------------------------------- 1) CORS
# Preflight is answered by the CorsFilter, so the target route only needs to exist;
# GET /health is used because it is public and stable (no session, no DB round trip).
Show-Step '1) CORS preflight'
$cors = Invoke-Curl @(
    '-X', 'OPTIONS', "$BaseUrl/health",
    '-H', "Origin: $AllowedOrigin",
    '-H', 'Access-Control-Request-Method: POST'
)
$acao = Get-HeaderValue $cors.Headers 'Access-Control-Allow-Origin'
$acac = Get-HeaderValue $cors.Headers 'Access-Control-Allow-Credentials'
Report ($cors.Code -eq 200) 'allowed Origin preflight returns 200' "HTTP $($cors.Code)"
Report ($acao -eq $AllowedOrigin) 'Access-Control-Allow-Origin echoes the Origin' "got: $acao"
Report ($acac -eq 'true') 'Access-Control-Allow-Credentials is true' "got: $acac"

$corsEvil = Invoke-Curl @(
    '-X', 'OPTIONS', "$BaseUrl/health",
    '-H', 'Origin: http://evil.example.com',
    '-H', 'Access-Control-Request-Method: POST'
)
Report ($corsEvil.Code -eq 403) 'non-whitelisted Origin is still rejected' "HTTP $($corsEvil.Code)"

# ---------------------------------------------------------------- 2) 405
# GET /health is a GET-only mapping; a trusted POST must be 405 METHOD_NOT_ALLOWED, never
# the generic 500 that the catch-all handler used to produce.
#
# Since the write gate (common/CsrfFilter) runs before the DispatcherServlet, an untokened
# POST is rejected with 403 AUTH_CSRF_INVALID first. Both layers are asserted here: the
# 403 proves the gate wins over routing, the 405 proves method mismatch is still handled
# correctly once the request is trusted.
Show-Step '2) write gate, then method not allowed'
$untokened = Invoke-Curl @('-X', 'POST', "$BaseUrl/health")
Report ($untokened.Code -eq 403) 'POST without a CSRF pair is rejected by the gate (403)' "HTTP $($untokened.Code)"
Report ($untokened.Body -match '"code"\s*:\s*"AUTH_CSRF_INVALID"') 'gate rejection code is AUTH_CSRF_INVALID' "body: $($untokened.Body.Trim())"

$pair = Get-CsrfPair
$wrongMethod = Invoke-Curl @(
    '-X', 'POST', "$BaseUrl/health",
    '-b', "CSRFID=$($pair.Cookie)",
    '-H', "X-CSRF-Token: $($pair.Token)"
)
Report ($wrongMethod.Code -eq 405) 'trusted POST to a GET-only route returns 405' "HTTP $($wrongMethod.Code)"
Report ($wrongMethod.Body -match '"code"\s*:\s*"METHOD_NOT_ALLOWED"') 'error code is METHOD_NOT_ALLOWED' "body: $($wrongMethod.Body.Trim())"
Report ($wrongMethod.Body -notmatch 'SYSTEM_INTERNAL') 'not misreported as SYSTEM_INTERNAL' ''
$allow = Get-HeaderValue $wrongMethod.Headers 'Allow'
Report ($allow -and $allow -match 'GET') 'hop-by-hop Allow header still lists GET' "Allow: $allow"

# ---------------------------------------------------------------- 3) traceId
Show-Step '3) traceId is server generated'
$clientTrace = 'audit-client-controlled-123'
$trace = Invoke-Curl @("$BaseUrl/health", '-H', "X-Trace-Id: $clientTrace")
$headerTrace = Get-HeaderValue $trace.Headers 'X-Trace-Id'
$bodyTrace = $null
if ($trace.Body -match '"traceId"\s*:\s*"([^"]+)"') { $bodyTrace = $Matches[1] }
Report ($headerTrace -ne $clientTrace) 'client-supplied traceId is ignored' "header: $headerTrace"
Report ($headerTrace -eq $bodyTrace) 'header traceId equals body traceId' "header: $headerTrace / body: $bodyTrace"
Report ($headerTrace -match '^[0-9a-fA-F-]{36}$') 'traceId looks like a generated UUID' "got: $headerTrace"

# ---------------------------------------------------------------- 4) envelope
Show-Step '4) contract envelope'
Report ($probe.Body -match '"code"\s*:\s*"0"') 'success envelope uses string code "0"' ''
Report ($probe.Body -match '"status"\s*:\s*"UP"') 'GET /health reports status UP' "body: $($probe.Body.Trim())"
# checkedAt must be RFC3339 with an offset, not a millisecond number (contract section 3).
Report ($probe.Body -match '"checkedAt"\s*:\s*"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}') 'checkedAt is an ISO-8601 timestamp' ''
Report ($probe.Body -match '"checkedAt"\s*:\s*"[^"]*(Z|[+-]\d{2}:\d{2})"') 'checkedAt carries a UTC offset' ''

$ready = Invoke-Curl @("$BaseUrl/ready")
Report ($ready.Code -eq 200) 'GET /ready returns 200 when every dependency is up' "HTTP $($ready.Code)"
Report ($ready.Body -match '"status"\s*:\s*"READY"') 'readiness status is READY' "body: $($ready.Body.Trim())"
Report ($ready.Body -match '"name"\s*:\s*"database"') 'readiness lists the database dependency' ''
Report ($ready.Body -match '"name"\s*:\s*"schema"') 'readiness lists the schema dependency' ''
Report ($ready.Body -match '"name"\s*:\s*"deployment-config"') 'readiness lists the deployment-config dependency' ''

# ---------------------------------------------------------------- 5) regression
Show-Step '5) unknown route regression'
$notFound = Invoke-Curl @("$BaseUrl/no-such-route")
Report ($notFound.Code -eq 404) 'unknown route returns 404' "HTTP $($notFound.Code)"
Report ($notFound.Body -match '"code"\s*:\s*"DATA_NOT_FOUND"') 'error code is DATA_NOT_FOUND' ''
Report ($notFound.Body -match '"data"\s*:\s*null') 'error body carries data:null' ''
Report ($notFound.Body -match '"details"\s*:\s*\[\s*\]') 'error body carries an empty details array' ''
$nfHeader = Get-HeaderValue $notFound.Headers 'X-Trace-Id'
$nfBody = $null
if ($notFound.Body -match '"traceId"\s*:\s*"([^"]+)"') { $nfBody = $Matches[1] }
Report ($nfHeader -and $nfHeader -eq $nfBody) '404 header traceId equals body traceId' "header: $nfHeader / body: $nfBody"

# ---------------------------------------------------------------- summary
Write-Host "`n== Summary" -ForegroundColor Cyan
Write-Host "  passed: $script:passed   failed: $script:failed" -ForegroundColor $(if ($script:failed -eq 0) { 'Green' } else { 'Red' })
if ($script:failed -gt 0) {
    Write-Host '  Shared-layer regression detected. Fix before reporting progress.' -ForegroundColor Red
    exit 1
}
Write-Host '  All shared-layer checks passed.' -ForegroundColor Green
exit 0
