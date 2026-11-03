#requires -Version 5.1
<#
    verify-files.ps1 - upload / get / download acceptance checks (DEV-01, W2 tasks 10-11)

    Proves the file state machine and the two read endpoints:
      1. every endpoint needs a session; the upload is a write and therefore needs the
         CSRF pair; GET on /files/uploads is 405 (the route only accepts POST);
      2. upload rejects a missing/short Idempotency-Key (400 with the field name),
         an unknown category (400), a media type outside the category whitelist (415)
         and an oversized file (413 -- the container limit fires before the business
         limit, both must surface as the same contract code);
      3. a declared PNG whose bytes are not a PNG lands QUARANTINED with error_code
         FILE_INVALID, while a real PNG lands ACTIVE -- PROCESSING must never be the
         status a caller sees on the 202 response;
      4. get returns the contract FileAsset fields for the owner and hides foreign or
         unknown ids behind 404 (no existence oracle);
      5. download needs file:download (the OPERATOR seed lacks it -> real 403), then
         checks ownership, expiry (410) and status (non-ACTIVE -> 404); the response
         carries Content-Disposition with RFC 5987 filename*, Cache-Control: no-store
         and X-Content-Type-Options: nosniff, and the bytes match the upload hash.

    Requires the API running WITH the db profile AND the dev seed accounts enabled:

        SPRING_PROFILES_ACTIVE=db
        SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:55435/xxplatform
        SPRING_DATASOURCE_USERNAME=postgres
        SPRING_DATASOURCE_PASSWORD=''
        XX_AUTH_DEVSEED_ENABLED=true
        XX_AUTH_DEVSEED_LOGIN_NAME=demo.admin
        XX_AUTH_DEVSEED_PASSWORD=DevSeed2026!

    Then:

        .\scripts\verify-files.ps1 -DB_PASSWORD '' -SeedPassword 'DevSeed2026!'

    The file rows are removed at the end by file_name prefix so repeated runs stay
    deterministic. The 10 MiB cases write into %TEMP% and are deleted afterwards.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the file
    has a UTF-8 BOM, so non-ASCII literals silently never match. Uses curl.exe
    because Invoke-WebRequest fails on this host with 'Win32 internal error 0x5'.
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string]$DB_URL  = 'jdbc:postgresql://127.0.0.1:5432/xxplatform',
    [string]$DB_USER = 'xxplatform_owner',
    [string]$DB_PASSWORD = $env:DB_PASSWORD,
    [string]$SeedLogin = 'demo.admin',
    [string]$SeedPassword = $env:XX_AUTH_DEVSEED_PASSWORD,
    [string]$OperatorLogin = 'demo.operator'
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

function Invoke-Curl([string[]]$curlArgs) {
    $raw = & curl.exe -s -i @curlArgs 2>&1
    $text = ($raw | Out-String)
    $code = 0
    # Take the LAST status line: curl prints "HTTP/1.1 100 Continue" and then the
    # real response when an oversized body is rejected mid-upload.
    $matches = [regex]::Matches($text, 'HTTP/\d(?:\.\d)?\s+(\d{3})')
    if ($matches.Count -gt 0) { $code = [int]$matches[$matches.Count - 1].Groups[1].Value }
    $parts = $text -split "`r?`n`r?`n", 2
    $headers = if ($parts.Count -ge 1) { $parts[0] } else { '' }
    $body = if ($parts.Count -ge 2) { $parts[1] } else { '' }
    return [pscustomobject]@{ Code = $code; Headers = $headers; Body = $body; Raw = $text }
}

function Get-Json($response) {
    try { return $response.Body | ConvertFrom-Json } catch { }
    # 100-continue style responses put the real headers in front of the body; fall
    # back to the first '{' so the envelope is still parsed.
    $idx = $response.Raw.IndexOf('{')
    if ($idx -ge 0) {
        try { return $response.Raw.Substring($idx) | ConvertFrom-Json } catch { return $null }
    }
    return $null
}

function Get-EnvelopeCode($response) {
    $json = Get-Json $response
    if ($null -eq $json) { return $null }
    return $json.code
}

function Get-FirstDetailField($response) {
    $json = Get-Json $response
    if ($null -eq $json -or $null -eq $json.details -or $json.details.Count -eq 0) { return $null }
    return $json.details[0].field
}

function Get-HeaderValue($response, [string]$name) {
    foreach ($line in ($response.Headers -split "`r?`n")) {
        $m = [regex]::Match($line, "^\s*$([regex]::Escape($name))\s*:\s*(.+?)\s*$", 'IgnoreCase')
        if ($m.Success) { return $m.Groups[1].Value }
    }
    return $null
}

function Get-CookieValue($response, [string]$name) {
    foreach ($c in [regex]::Matches($response.Headers, '(?im)^\s*Set-Cookie\s*:\s*(.+?)\s*$') | ForEach-Object { $_.Groups[1].Value }) {
        $m = [regex]::Match($c, "^$([regex]::Escape($name))=([^;]*)")
        if ($m.Success) { return $m.Groups[1].Value }
    }
    return $null
}

function Get-CsrfPair {
    $r = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
    $json = Get-Json $r
    return [pscustomobject]@{
        Cookie = (Get-CookieValue $r 'CSRFID')
        Token  = $(if ($json) { $json.data.token } else { $null })
    }
}

$script:tempFiles = @()
function New-TempFile([string]$prefix, [byte[]]$bytes, [string]$extension) {
    $file = Join-Path $env:TEMP ("verify-files-$prefix-" + [guid]::NewGuid().ToString('N') + $extension)
    [System.IO.File]::WriteAllBytes($file, $bytes)
    $script:tempFiles += $file
    return $file
}

function New-LoginBody([string]$login, [string]$password) {
    $file = Join-Path $env:TEMP ("files-login-" + [guid]::NewGuid().ToString('N') + ".json")
    [System.IO.File]::WriteAllText($file,
        (@{ loginName = $login; password = $password } | ConvertTo-Json -Compress),
        (New-Object System.Text.UTF8Encoding($false)))
    $script:tempFiles += $file
    return $file
}

function New-Session([string]$login, [string]$password) {
    $csrf = Get-CsrfPair
    $file = New-LoginBody $login $password
    $loginResponse = Invoke-Curl @(
        '--max-time', '15', '-X', 'POST',
        '-H', 'Content-Type: application/json',
        '-H', "X-CSRF-Token: $($csrf.Token)",
        '-b', "CSRFID=$($csrf.Cookie)",
        '--data-binary', "@$file",
        "$BaseUrl/auth/login")
    return [pscustomobject]@{
        Response = $loginResponse
        Json     = (Get-Json $loginResponse)
        Sid      = (Get-CookieValue $loginResponse 'SID')
        Csrf     = $csrf
    }
}

# Multipart upload. A fresh CSRF pair per call: the upload endpoint is a write.
function Invoke-Upload($session, [string]$category, [string]$path, [string]$contentType, [string]$key) {
    $pair = Get-CsrfPair
    $args = @(
        '--max-time', '90', '-X', 'POST',
        '-H', "Cookie: SID=$($session.Sid); CSRFID=$($pair.Cookie)",
        '-H', "X-CSRF-Token: $($pair.Token)")
    if ($key) { $args += @('-H', "Idempotency-Key: $key") }
    $args += @('-F', "category=$category", '-F', "file=@$path;type=$contentType")
    $args += "$BaseUrl/files/uploads"
    return Invoke-Curl $args
}

# Binary download: curl writes the body to a file so no CRLF splitting can corrupt it.
function Invoke-Download($session, [string]$fileId, [string]$outPath, [string]$headersPath) {
    $pair = Get-CsrfPair
    $code = & curl.exe -s -o $outPath -D $headersPath -w '%{http_code}' --max-time 60 `
        -H "Cookie: SID=$($session.Sid); CSRFID=$($pair.Cookie)" `
        "$BaseUrl/files/$fileId/download" 2>&1
    $headers = if (Test-Path $headersPath) { Get-Content $headersPath -Raw } else { '' }
    return [pscustomobject]@{ Code = [int]$code; Headers = $headers; BodyPath = $outPath }
}

function Get-FileSha256([string]$path) {
    return (Get-FileHash -Algorithm SHA256 -Path $path).Hash.ToLowerInvariant()
}

# ------------------------------------------------------------------ reachability
Show-Step 'Service reachability'
if (-not $SeedPassword) {
    Write-Host '  [FAIL] -SeedPassword / XX_AUTH_DEVSEED_PASSWORD is required' -ForegroundColor Red
    exit 1
}
$probe = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
if ($probe.Code -eq 0) {
    Write-Host "  [FAIL] cannot reach $BaseUrl/auth/csrf" -ForegroundColor Red
    Write-Host '         Start the API with the db profile first (see this script header).' -ForegroundColor Yellow
    exit 1
}

# psql wiring
$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
$dbHost = $null; $dbPort = $null; $dbName = $null; $dbReady = $false
if (Test-Path $psql) {
    $m = [regex]::Match($DB_URL, '//([^:/]+):(\d+)/(.+)$')
    $dbHost = $m.Groups[1].Value
    $dbPort = $m.Groups[2].Value
    $dbName = $m.Groups[3].Value
    if ([bool]$DB_PASSWORD) { $env:PGPASSWORD = $DB_PASSWORD }
    function Query-Scalar([string]$sql) {
        # SQL goes through stdin: PowerShell strips inner double quotes from native
        # command arguments, which corrupts literals passed via -c.
        $out = $sql | & $psql -h $dbHost -p $dbPort -U $DB_USER -d $dbName -t -A -f - 2>&1
        return (($out | Out-String).Trim())
    }
    try { $dbReady = (Query-Scalar 'select 1') -eq '1' } catch { $dbReady = $false }
}
if (-not $dbReady) {
    Write-Host '  [FAIL] psql cannot reach the database; expiry/sha checks need it' -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------ sessions
Show-Step '0) Sessions (admin has file:download, operator does not)'
$session = New-Session $SeedLogin $SeedPassword
Report ($session.Response.Code -eq 200) 'admin logs in' "HTTP $($session.Response.Code)"
if (-not $session.Sid) {
    Write-Host '  [FAIL] no SID; remaining cases need a session' -ForegroundColor Red
    exit 1
}
$userId = $session.Json.data.userId
$operator = New-Session $OperatorLogin $SeedPassword
Report ($operator.Response.Code -eq 200) 'operator logs in' "HTTP $($operator.Response.Code)"
$operatorId = if ($operator.Json) { $operator.Json.data.userId } else { $null }
if (-not $operator.Sid) {
    Write-Host '  [FAIL] no operator SID' -ForegroundColor Red
    exit 1
}

# Earlier runs leave rows behind; removing them keeps the later DB assertions honest.
$null = Query-Scalar "DELETE FROM core.file_asset WHERE file_name LIKE 'verify-files-%'"

# ------------------------------------------------------------------ fixtures
$pngBytes = [byte[]](0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02, 0x03)
$fakePng = [System.Text.Encoding]::ASCII.GetBytes('definitely not a png')
$csvBytes = [System.Text.Encoding]::ASCII.GetBytes("captureId,dataType`n1,FENCE`n")
$bigBytes = New-Object byte[] (11 * 1024 * 1024)
[Array]::Copy($pngBytes, $bigBytes, $pngBytes.Length)

$pngFile = New-TempFile 'png' $pngBytes '.png'
$fakeFile = New-TempFile 'fake' $fakePng '.png'
$csvFile = New-TempFile 'csv' $csvBytes '.csv'
$bigFile = New-TempFile 'big' $bigBytes '.png'

# ------------------------------------------------------------ 1) gate / contract
Show-Step '1) gate and method contract'
$anonymous = Invoke-Curl @(
    '--max-time', '10', '-X', 'POST',
    '-F', "category=FACE_SEARCH", '-F', "file=@$pngFile;type=image/png",
    '-H', 'Idempotency-Key: 0123456789abcdef',
    "$BaseUrl/files/uploads")
Report ($anonymous.Code -eq 403 -and (Get-EnvelopeCode $anonymous) -eq 'AUTH_CSRF_INVALID') `
    'anonymous upload is stopped by the write gate (403 AUTH_CSRF_INVALID)' "HTTP $($anonymous.Code)"

$pairOnly = Get-CsrfPair
$noSession = Invoke-Curl @(
    '--max-time', '10', '-X', 'POST',
    '-H', "X-CSRF-Token: $($pairOnly.Token)",
    '-b', "CSRFID=$($pairOnly.Cookie)",
    '-H', 'Idempotency-Key: 0123456789abcdef',
    '-F', "category=FACE_SEARCH", '-F', "file=@$pngFile;type=image/png",
    "$BaseUrl/files/uploads")
Report ($noSession.Code -eq 401 -and (Get-EnvelopeCode $noSession) -eq 'AUTH_REQUIRED') `
    'upload without session is 401 AUTH_REQUIRED' "HTTP $($noSession.Code)"

$methodPair = Get-CsrfPair
$wrongMethod = Invoke-Curl @('--max-time', '10', '-X', 'PUT',
    '-H', "Cookie: SID=$($session.Sid); CSRFID=$($methodPair.Cookie)",
    '-H', "X-CSRF-Token: $($methodPair.Token)",
    '-H', 'Idempotency-Key: 0123456789abcdef',
    "$BaseUrl/files/uploads")
Report ($wrongMethod.Code -eq 405) 'PUT /files/uploads is 405 (only POST is accepted)' "HTTP $($wrongMethod.Code)"

$getAnon = Invoke-Curl @('--max-time', '10', "$BaseUrl/files/00000000-0000-0000-0000-000000000000")
Report ($getAnon.Code -eq 401 -and (Get-EnvelopeCode $getAnon) -eq 'AUTH_REQUIRED') `
    'GET file without session is 401' "HTTP $($getAnon.Code)"

# ------------------------------------------------------------ 2) upload validation
Show-Step '2) upload validation'
$noKey = Invoke-Upload $session 'FACE_SEARCH' $pngFile 'image/png' $null
Report ($noKey.Code -eq 400 -and (Get-FirstDetailField $noKey) -eq 'Idempotency-Key') `
    'missing Idempotency-Key is 400 with the field name' "HTTP $($noKey.Code) body: $($noKey.Body)"

$shortKey = Invoke-Upload $session 'FACE_SEARCH' $pngFile 'image/png' 'short'
Report ($shortKey.Code -eq 400 -and (Get-FirstDetailField $shortKey) -eq 'Idempotency-Key') `
    'short Idempotency-Key is 400' "HTTP $($shortKey.Code)"

$badCategory = Invoke-Upload $session 'REPORT' $pngFile 'image/png' '0123456789abcdef'
Report ($badCategory.Code -eq 400 -and (Get-FirstDetailField $badCategory) -eq 'category') `
    'server-generated category is 400 field=category' "HTTP $($badCategory.Code)"

$badMime = Invoke-Upload $session 'FACE_SEARCH' $csvFile 'text/csv' '0123456789abcdef'
Report ($badMime.Code -eq 415 -and (Get-EnvelopeCode $badMime) -eq 'FILE_MEDIA_UNSUPPORTED') `
    'CSV declared as FACE_SEARCH is 415' "HTTP $($badMime.Code) body: $($badMime.Body)"

$tooBig = Invoke-Upload $session 'FACE_SEARCH' $bigFile 'image/png' '0123456789abcdef'
Report ($tooBig.Code -eq 413 -and (Get-EnvelopeCode $tooBig) -eq 'FILE_TOO_LARGE') `
    '11 MiB upload is 413 FILE_TOO_LARGE' "HTTP $($tooBig.Code) body: $($tooBig.Body)"

$quarantined = Invoke-Upload $session 'FACE_SEARCH' $fakeFile 'image/png' '0123456789abcdef'
$quarantinedJson = Get-Json $quarantined
$quarantinedId = if ($quarantinedJson) { $quarantinedJson.data.fileAssetId } else { $null }
Report ($quarantined.Code -eq 202 -and $quarantinedJson.data.status -eq 'QUARANTINED') `
    'fake PNG is quarantined, not active' "HTTP $($quarantined.Code) status: $($quarantinedJson.data.status)"

# ------------------------------------------------------------ 3) upload success
Show-Step '3) upload succeeds and lands ACTIVE'
$upload = Invoke-Upload $session 'FACE_SEARCH' $pngFile 'image/png' '0123456789abcdef'
$uploadJson = Get-Json $upload
$fileId = if ($uploadJson) { $uploadJson.data.fileAssetId } else { $null }
$uploadTrace = Get-HeaderValue $upload 'X-Trace-Id'
Report ($upload.Code -eq 202) 'upload is 202' "HTTP $($upload.Code) body: $($upload.Body)"
Report ($uploadJson.code -eq '0') 'envelope code is "0"' "code: $($uploadJson.code)"
Report ($uploadJson.data.status -eq 'ACTIVE') 'response status is ACTIVE (not PROCESSING)' "status: $($uploadJson.data.status)"
$dataKeys = @($uploadJson.data.PSObject.Properties.Name | Sort-Object) -join ','
Report ($dataKeys -eq 'fileAssetId,status') 'data has exactly fileAssetId,status' "keys: $dataKeys"
Report ([bool]$uploadJson.traceId -and $uploadJson.traceId -eq $uploadTrace) `
    'envelope traceId equals X-Trace-Id' "envelope $($uploadJson.traceId) / header $uploadTrace"
$uuidOk = $fileId -match '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
Report ($uuidOk) 'fileAssetId is a uuid' "fileAssetId: $fileId"

$localSha = Get-FileSha256 $pngFile
$dbStatus = Query-Scalar "SELECT status || '|' || mime_type || '|' || byte_size || '|' || sha256 FROM core.file_asset WHERE id = '$fileId'"
Report ($dbStatus -eq "ACTIVE|image/png|$($pngBytes.Length)|$localSha") `
    'row is ACTIVE with mime, size and sha256 of the bytes' "db: $dbStatus"

# ------------------------------------------------------------ 4) get
Show-Step '4) get asset'
$getOwn = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/files/$fileId")
$getOwnJson = Get-Json $getOwn
Report ($getOwn.Code -eq 200 -and $getOwnJson.data.fileAssetId -eq $fileId) `
    'owner reads own file' "HTTP $($getOwn.Code) body: $($getOwn.Body)"
Report ($getOwnJson.data.status -eq 'ACTIVE' -and $getOwnJson.data.category -eq 'FACE_SEARCH') `
    'get returns status and category' "status/category: $($getOwnJson.data.status)/$($getOwnJson.data.category)"
Report ($getOwnJson.data.fileName -eq (Split-Path $pngFile -Leaf)) `
    'get returns the original file name' "fileName: $($getOwnJson.data.fileName)"

$getForeign = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($operator.Sid)", "$BaseUrl/files/$fileId")
Report ($getForeign.Code -eq 404 -and (Get-EnvelopeCode $getForeign) -eq 'DATA_NOT_FOUND') `
    'another user gets 404 (no existence oracle)' "HTTP $($getForeign.Code)"

$getUnknown = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/files/00000000-0000-0000-0000-000000000000")
Report ($getUnknown.Code -eq 404) 'unknown id is 404' "HTTP $($getUnknown.Code)"

# ------------------------------------------------------------ 5) download
Show-Step '5) download'
# A real permission denial: the operator uploads their own file (upload only needs a
# session) but lacks file:download, so the denial is about the permission bit, not
# about ownership.
$opUpload = Invoke-Upload $operator 'DATASET_IMPORT' $csvFile 'text/csv' '0123456789abcdef'
$opUploadJson = Get-Json $opUpload
$opFileId = if ($opUploadJson) { $opUploadJson.data.fileAssetId } else { $null }
Report ($opUpload.Code -eq 202 -and $opUploadJson.data.status -eq 'ACTIVE') `
    'operator can upload their own file (AUTHENTICATED only)' "HTTP $($opUpload.Code) status: $($opUploadJson.data.status)"

$opDownload = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($operator.Sid)", "$BaseUrl/files/$opFileId/download")
Report ($opDownload.Code -eq 403 -and (Get-EnvelopeCode $opDownload) -eq 'PERMISSION_DENIED') `
    'operator without file:download is 403 on own file' "HTTP $($opDownload.Code) body: $($opDownload.Body)"

$foreignDownload = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($operator.Sid)", "$BaseUrl/files/$fileId/download")
Report ($foreignDownload.Code -eq 404) `
    'foreign download is 404 even before the permission bit is judged' "HTTP $($foreignDownload.Code)"

$outPath = New-TempFile 'out' ([byte[]]@())
$headersPath = $outPath + '.headers'
$download = Invoke-Download $session $fileId $outPath $headersPath
Report ($download.Code -eq 200) 'admin download is 200' "HTTP $($download.Code)"
Report ((Get-HeaderValue ([pscustomobject]@{ Headers = $download.Headers }) 'Content-Type') -like 'image/png*') `
    'Content-Type is image/png' "Content-Type: $((Get-HeaderValue ([pscustomobject]@{ Headers = $download.Headers }) 'Content-Type'))"
$disposition = Get-HeaderValue ([pscustomobject]@{ Headers = $download.Headers }) 'Content-Disposition'
Report ([bool]$disposition -and $disposition.StartsWith('attachment; filename*=UTF-8')) `
    'Content-Disposition is attachment with RFC 5987 filename*' "disposition: $disposition"
$cacheControl = Get-HeaderValue ([pscustomobject]@{ Headers = $download.Headers }) 'Cache-Control'
Report ($cacheControl -eq 'no-store') 'Cache-Control is no-store' "Cache-Control: $cacheControl"
$nosniff = Get-HeaderValue ([pscustomobject]@{ Headers = $download.Headers }) 'X-Content-Type-Options'
Report ($nosniff -eq 'nosniff') 'X-Content-Type-Options is nosniff' "value: $nosniff"
Report ((Get-FileSha256 $outPath) -eq $localSha) 'downloaded bytes match the upload sha256' `
    "downloaded $((Get-FileSha256 $outPath))"

$quarantineDownload = Invoke-Download $session $quarantinedId (New-TempFile 'q' ([byte[]]@())) ((New-TempFile 'qh' ([byte[]]@())) + '.h')
Report ($quarantineDownload.Code -eq 404) 'quarantined file is not downloadable (404)' "HTTP $($quarantineDownload.Code)"

$null = Query-Scalar "UPDATE core.file_asset SET expires_at = now() - interval '1 minute' WHERE id = '$fileId'"
$expiredDownload = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/files/$fileId/download")
Report ($expiredDownload.Code -eq 410 -and (Get-EnvelopeCode $expiredDownload) -eq 'FILE_EXPIRED') `
    'expired file is 410 FILE_EXPIRED' "HTTP $($expiredDownload.Code) body: $($expiredDownload.Body)"

$missingDownload = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/files/00000000-0000-0000-0000-000000000000/download")
Report ($missingDownload.Code -eq 404) 'unknown download is 404' "HTTP $($missingDownload.Code)"

# ------------------------------------------------------------------ cleanup
Show-Step 'Result'
$null = Query-Scalar "DELETE FROM core.file_asset WHERE file_name LIKE 'verify-files-%'"
foreach ($f in $script:tempFiles) {
    if (Test-Path $f) { Remove-Item -Force $f }
}
Write-Host ("  {0} passed, {1} failed" -f $script:passed, $script:failed) -ForegroundColor $(if ($script:failed -eq 0) { 'Green' } else { 'Red' })
if ($script:failed -eq 0) { exit 0 } else { exit 1 }
