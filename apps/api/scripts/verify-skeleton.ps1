# apps/api skeleton regression check (CORS / 405 / traceId / unknown field)
#
# Usage (service must already be running under the dev profile):
#   cd apps/api
#   mvn -o -B spring-boot:run "-Dspring-boot.run.profiles=dev"
#   # in another terminal, from repo root:
#   powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/verify-skeleton.ps1
#   # pwsh works too where PowerShell 7+ is installed: pwsh -File apps/api/scripts/verify-skeleton.ps1
#
# Why this script exists: the 2026-10-16 integration test report found four skeleton defects
# that unit tests did not cover (and that Mockito blocks from running inside the sandbox):
#   1. CORS preflight returned 403 for an allowed Origin
#   2. POST to a GET-only route returned 500 instead of 405
#   3. traceId was taken from the client header
#   4. unknown JSON fields were silently ignored
# Each check below asserts the fixed behaviour AND its negative counterpart, so a future
# refactor that re-breaks any of them fails here instead of in the next integration round.
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

# ---------------------------------------------------------------- reachability
Show-Step 'Service reachability'
$probe = Invoke-Curl @("--max-time", "5", "$BaseUrl/smoke")
if ($probe.Code -ne 200) {
    Write-Host "  [FAIL] $BaseUrl/smoke did not return 200 (got $($probe.Code))." -ForegroundColor Red
    Write-Host '         Start it first: mvn -o -B spring-boot:run "-Dspring-boot.run.profiles=dev"' -ForegroundColor Yellow
    Write-Host '         (the /smoke routes only exist under the dev profile)' -ForegroundColor Yellow
    exit 1
}
Write-Host "  [OK]   service reachable at $BaseUrl" -ForegroundColor Green

# ---------------------------------------------------------------- 1) CORS
Show-Step '1) CORS preflight'
$cors = Invoke-Curl @(
    '-X', 'OPTIONS', "$BaseUrl/smoke",
    '-H', "Origin: $AllowedOrigin",
    '-H', 'Access-Control-Request-Method: POST'
)
$acao = Get-HeaderValue $cors.Headers 'Access-Control-Allow-Origin'
$acac = Get-HeaderValue $cors.Headers 'Access-Control-Allow-Credentials'
Report ($cors.Code -eq 200) 'allowed Origin preflight returns 200' "HTTP $($cors.Code)"
Report ($acao -eq $AllowedOrigin) 'Access-Control-Allow-Origin echoes the Origin' "got: $acao"
Report ($acac -eq 'true') 'Access-Control-Allow-Credentials is true' "got: $acac"

$corsEvil = Invoke-Curl @(
    '-X', 'OPTIONS', "$BaseUrl/smoke",
    '-H', 'Origin: http://evil.example.com',
    '-H', 'Access-Control-Request-Method: POST'
)
Report ($corsEvil.Code -eq 403) 'non-whitelisted Origin is still rejected' "HTTP $($corsEvil.Code)"

# ---------------------------------------------------------------- 2) 405
Show-Step '2) method not allowed'
$wrongMethod = Invoke-Curl @('-X', 'POST', "$BaseUrl/smoke")
Report ($wrongMethod.Code -eq 405) 'POST to a GET-only route returns 405' "HTTP $($wrongMethod.Code)"
Report ($wrongMethod.Body -match '"code"\s*:\s*"METHOD_NOT_ALLOWED"') 'error code is METHOD_NOT_ALLOWED' "body: $($wrongMethod.Body.Trim())"
Report ($wrongMethod.Body -notmatch 'SYSTEM_INTERNAL') 'not misreported as SYSTEM_INTERNAL' ''

# ---------------------------------------------------------------- 3) traceId
Show-Step '3) traceId is server generated'
$clientTrace = 'audit-client-controlled-123'
$trace = Invoke-Curl @("$BaseUrl/smoke", '-H', "X-Trace-Id: $clientTrace")
$headerTrace = Get-HeaderValue $trace.Headers 'X-Trace-Id'
$bodyTrace = $null
if ($trace.Body -match '"traceId"\s*:\s*"([^"]+)"') { $bodyTrace = $Matches[1] }
Report ($headerTrace -ne $clientTrace) 'client-supplied traceId is ignored' "header: $headerTrace"
Report ($headerTrace -eq $bodyTrace) 'header traceId equals body traceId' "header: $headerTrace / body: $bodyTrace"
Report ($headerTrace -match '^[0-9a-fA-F-]{36}$') 'traceId looks like a generated UUID' "got: $headerTrace"

# ---------------------------------------------------------------- 4) unknown field
Show-Step '4) unknown field rejection'
# NOTE: JSON bodies are passed to curl as @file, not as an inline argument.
# Windows PowerShell 5.1's native-argument parser strips/re-wraps the quotes inside a JSON
# argument, so curl receives broken JSON and every assertion here would pass or fail for
# the wrong reason (it still "returned 400", just not because of an unknown field).
$bodyDir = Join-Path ([System.IO.Path]::GetTempPath()) ("xx-skel-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $bodyDir -Force | Out-Null

function Write-BodyFile([string]$content) {
    $path = Join-Path $bodyDir ([guid]::NewGuid().ToString('N') + '.json')
    [System.IO.File]::WriteAllText($path, $content, (New-Object System.Text.UTF8Encoding($false)))
    return $path
}

try {
    $validFile = Write-BodyFile '{"name":"abc","count":3}'
    $valid = Invoke-Curl @(
        '-X', 'POST', "$BaseUrl/smoke/echo",
        '-H', 'Content-Type: application/json',
        '-d', "@$validFile"
    )
    Report ($valid.Code -eq 200) 'declared fields are accepted' "HTTP $($valid.Code) body: $($valid.Body.Trim())"

    $unknownFile = Write-BodyFile '{"name":"abc","count":3,"typoField":true}'
    $unknown = Invoke-Curl @(
        '-X', 'POST', "$BaseUrl/smoke/echo",
        '-H', 'Content-Type: application/json',
        '-d', "@$unknownFile"
    )
    Report ($unknown.Code -eq 400) 'unknown field returns 400' "HTTP $($unknown.Code)"
    Report ($unknown.Body -match '"code"\s*:\s*"VALIDATION_ERROR"') 'error code is VALIDATION_ERROR' "body: $($unknown.Body.Trim())"
    # Assert on `details.field` rather than on the Chinese message: this script is ASCII-only
    # (Windows PowerShell 5.1 reads .ps1 as ANSI without a BOM), so a Chinese literal here
    # would not match the UTF-8 response body.
    Report ($unknown.Body -match '"field"\s*:\s*"typoField"') 'details.field names the offending field' "body: $($unknown.Body.Trim())"
    Report ($unknown.Body -match '"data"\s*:\s*null') 'unknown-field error carries data:null' ''

    $malformedFile = Write-BodyFile '{bad json'
    $malformed = Invoke-Curl @(
        '-X', 'POST', "$BaseUrl/smoke/echo",
        '-H', 'Content-Type: application/json',
        '-d', "@$malformedFile"
    )
    Report ($malformed.Code -eq 400) 'malformed JSON returns 400' "HTTP $($malformed.Code)"
    # A malformed body must NOT be reported as an unknown field (it has no field name to report).
    Report ($malformed.Body -notmatch '"field"') 'malformed JSON does not fabricate a field name' "body: $($malformed.Body.Trim())"
} finally {
    Remove-Item -Path $bodyDir -Recurse -Force -ErrorAction SilentlyContinue
}

# ---------------------------------------------------------------- 5) regression
Show-Step '5) contract envelope regression'
$notFound = Invoke-Curl @("$BaseUrl/no-such-route")
Report ($notFound.Code -eq 404) 'unknown route returns 404' "HTTP $($notFound.Code)"
Report ($notFound.Body -match '"code"\s*:\s*"DATA_NOT_FOUND"') 'error code is DATA_NOT_FOUND' ''
Report ($notFound.Body -match '"data"\s*:\s*null') 'error body carries data:null' ''
$nfHeader = Get-HeaderValue $notFound.Headers 'X-Trace-Id'
$nfBody = $null
if ($notFound.Body -match '"traceId"\s*:\s*"([^"]+)"') { $nfBody = $Matches[1] }
Report ($nfHeader -and $nfHeader -eq $nfBody) '404 header traceId equals body traceId' "header: $nfHeader / body: $nfBody"

Report ($probe.Body -match '"code"\s*:\s*"0"') 'success envelope uses string code "0"' ''

# ---------------------------------------------------------------- summary
Write-Host "`n== Summary" -ForegroundColor Cyan
Write-Host "  passed: $script:passed   failed: $script:failed" -ForegroundColor $(if ($script:failed -eq 0) { 'Green' } else { 'Red' })
if ($script:failed -gt 0) {
    Write-Host '  Skeleton regression detected. Fix before reporting progress.' -ForegroundColor Red
    exit 1
}
Write-Host '  All skeleton checks passed.' -ForegroundColor Green
exit 0
