#requires -Version 5.1
<#
    verify-export.ps1 - POST /exports acceptance checks (DEV-01, W2 task 6)

    Proves the submission half of the export contract:
      1. the endpoint is a gated write and needs a session (403 gate, 401 auth);
      2. Idempotency-Key is mandatory and shape-checked (400), and replaying the
         same key with the same body returns the same taskId without a second row;
      3. the same key with different parameters is 409 IDEMPOTENCY_CONFLICT;
      4. the context guards hold: unknown -> 404, expired -> 410, unbounded -> 422
         DATA_QUERY_TOO_BROAD, unsupported filter type -> 422 DATA_INVALID_FILTER;
      5. a successful call answers 202 + TaskCreated and leaves exactly one
         async_task row (PENDING, task_type EXPORT) plus one export_job row whose
         frozen source snapshot carries the 1,000,000 rows / 1 GiB budget;
      6. XLSX, CSV and ZIP all enqueue.

    Requires the API running WITH the db profile AND the dev seed account enabled:

        $env:SPRING_PROFILES_ACTIVE      = 'db'
        $env:SPRING_DATASOURCE_URL       = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
        $env:SPRING_DATASOURCE_USERNAME  = 'xxplatform_owner'
        $env:SPRING_DATASOURCE_PASSWORD  = '<password>'
        $env:XX_AUTH_DEVSEED_ENABLED     = 'true'
        $env:XX_AUTH_DEVSEED_PASSWORD    = '<password>'
        .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"

    Then: .\scripts\verify-export.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    There is no createContext endpoint yet, so the script seeds analysis_context
    rows itself through psql. Those cases are skipped when psql is unavailable.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the file
    has a UTF-8 BOM, so non-ASCII literals silently never match.

    Uses curl.exe rather than Invoke-WebRequest: on this host Invoke-WebRequest fails
    with 'Win32 internal error "Access is denied" 0x5' unrelated to the API.
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

function Get-FirstDetailField($response) {
    $json = Get-Json $response
    if ($null -eq $json -or $null -eq $json.details -or $json.details.Count -eq 0) { return $null }
    return $json.details[0].field
}

function Get-HeaderValue($response, [string]$name) {
    $m = [regex]::Match($response.Headers, "(?im)^\s*$([regex]::Escape($name))\s*:\s*(.+?)\s*$")
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function Get-CookieValue($response, [string]$name) {
    foreach ($c in [regex]::Matches($response.Headers, '(?im)^\s*Set-Cookie\s*:\s*(.+?)\s*$') | ForEach-Object { $_.Groups[1].Value }) {
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

$script:files = @()
function New-TempJson([string]$json) {
    $file = Join-Path $env:TEMP ("export-body-" + [guid]::NewGuid().ToString('N') + ".json")
    [System.IO.File]::WriteAllText($file, $json, (New-Object System.Text.UTF8Encoding($false)))
    $script:files += $file
    return $file
}

function New-Session {
    $csrf = Get-CsrfPair
    $file = New-TempJson (@{ loginName = $SeedLogin; password = $SeedPassword } | ConvertTo-Json -Compress)
    $login = Invoke-Curl @(
        '--max-time', '15', '-X', 'POST',
        '-H', 'Content-Type: application/json',
        '-H', "X-CSRF-Token: $($csrf.Token)",
        '-b', "CSRFID=$($csrf.Cookie)",
        '--data-binary', "@$file",
        "$BaseUrl/auth/login")
    return [pscustomobject]@{
        Response = $login
        Json     = (Get-Json $login)
        Sid      = (Get-CookieValue $login 'SID')
        Csrf     = $csrf
    }
}

# POST /exports with optional session, synchronizer pair and Idempotency-Key.
function Post-Export($session, $csrf, [string]$key, [string]$json) {
    $file = New-TempJson $json
    $args = @('--max-time', '15', '-X', 'POST', '-H', 'Content-Type: application/json')
    if ($key) { $args += @('-H', "Idempotency-Key: $key") }
    if ($csrf -and $csrf.Token) { $args += @('-H', "X-CSRF-Token: $($csrf.Token)") }
    if ($session -and $session.Sid -and $csrf) {
        $args += @('-H', "Cookie: SID=$($session.Sid); CSRFID=$($csrf.Cookie)")
    } elseif ($csrf) {
        $args += @('-H', "Cookie: CSRFID=$($csrf.Cookie)")
    }
    $args += @('--data-binary', "@$file", "$BaseUrl/exports")
    return Invoke-Curl $args
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
Write-Host "  [OK]   reachable, HTTP $($probe.Code)" -ForegroundColor Green

# psql wiring (context seeding and row assertions both need it)
$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
$dbHost = $null; $dbPort = $null; $dbName = $null; $dbReady = $false
if ((Test-Path $psql) -and [bool]$DB_PASSWORD) { $env:PGPASSWORD = $DB_PASSWORD }
if (Test-Path $psql) {
    $m = [regex]::Match($DB_URL, '//([^:/]+):(\d+)/(.+)$')
    $dbHost = $m.Groups[1].Value
    $dbPort = $m.Groups[2].Value
    $dbName = $m.Groups[3].Value
    function Query-Scalar([string]$sql) {
        # Feed SQL through stdin: PowerShell strips inner double quotes from native
        # command arguments, which corrupts jsonb literals passed via -c.
        $out = $sql | & $psql -h $dbHost -p $dbPort -U $DB_USER -d $dbName -t -A -f - 2>&1
        return (($out | Out-String).Trim())
    }
    try { $dbReady = (Query-Scalar 'select 1') -eq '1' } catch { $dbReady = $false }
}
if (-not $dbReady) {
    Write-Host '  [FAIL] psql cannot reach the database; context seeding is required for this script' -ForegroundColor Red
    exit 1
}

# ------------------------------------------------------------------ session
Show-Step '0) Seed account session'
$session = New-Session
Report ($session.Response.Code -eq 200) 'seed account logs in' "HTTP $($session.Response.Code) body: $($session.Response.Body)"
if (-not $session.Sid) {
    Write-Host '  [FAIL] no SID; remaining cases need a session' -ForegroundColor Red
    exit 1
}
$userId = $session.Json.data.userId
Write-Host "  [OK]   userId $userId" -ForegroundColor DarkGray

# Seed one context row. validMinutes/ageMinutes control expiry; created_at is
# backdated so the CHECK (expires_at > created_at) holds for expired rows too.
function New-Context([string]$filterType, [string]$snapshot, [int]$validMinutes, [int]$ageMinutes) {
    $id = [guid]::NewGuid().ToString()
    $sql = "INSERT INTO analysis.analysis_context (id, owner_user_id, source_module, filter_type, filter_snapshot, permission_version, expires_at, created_at, trace_id) " +
           "VALUES ('$id', '$userId', 'M05', '$filterType', '$snapshot'::jsonb, 7, now() + interval '$validMinutes minutes', now() - interval '$ageMinutes minutes', 'verify-export')"
    $null = Query-Scalar $sql
    return $id
}

function New-Key { return ([guid]::NewGuid().ToString('N') + 'export') }   # >= 16 chars

function Body([string]$contextId, [string]$format) {
    return (@{ contextId = $contextId; format = $format; purpose = 'verify-export' } | ConvertTo-Json -Compress)
}

$captureFilter = '{"filterType":"CAPTURE_SEARCH","filterVersion":1,"captureFilter":{"dataType":"PLATE","timeRange":{"from":"2026-10-01T00:00:00Z","to":"2026-10-27T00:00:00Z"}}}'
$plateContext = New-Context 'CAPTURE_SEARCH' $captureFilter 60 1
$expiredContext = New-Context 'CAPTURE_SEARCH' $captureFilter -60 120
$unboundedContext = New-Context 'GENERAL' '{"filterType":"GENERAL","filterVersion":1}' 60 1
$profileContext = New-Context 'PROFILE' '{"filterType":"PROFILE"}' 60 1
$unknownContext = [guid]::NewGuid().ToString()

# ------------------------------------------------------ 1) gate and method
Show-Step '1) POST /exports is a gated write, not an open door'
$gate = Post-Export $null $null (New-Key) (Body $plateContext 'XLSX')
Report ($gate.Code -eq 403) 'POST without a CSRF pair -> 403 from the write gate' "HTTP $($gate.Code)"
Report ((Get-EnvelopeCode $gate) -eq 'AUTH_CSRF_INVALID') 'gate rejection uses AUTH_CSRF_INVALID' "code: $(Get-EnvelopeCode $gate)"

$anonPair = Get-CsrfPair
$noSession = Post-Export $null $anonPair (New-Key) (Body $plateContext 'XLSX')
Report ($noSession.Code -eq 401) 'valid pair but no SID -> 401 (gate passes, auth rejects)' "HTTP $($noSession.Code)"
Report ((Get-EnvelopeCode $noSession) -eq 'AUTH_REQUIRED') 'no-session case uses AUTH_REQUIRED' "code: $(Get-EnvelopeCode $noSession)"

$get = Invoke-Curl @('--max-time', '10', "$BaseUrl/exports")
Report ($get.Code -eq 405) 'GET -> 405 (exports is POST-only)' "HTTP $($get.Code)"
Report ((Get-EnvelopeCode $get) -eq 'METHOD_NOT_ALLOWED') 'method mismatch uses METHOD_NOT_ALLOWED' "code: $(Get-EnvelopeCode $get)"

# -------------------------------------------------- 2) Idempotency-Key shape
Show-Step '2) Idempotency-Key is mandatory and shape-checked'
$noKey = Post-Export $session $session.Csrf $null (Body $plateContext 'XLSX')
Report ($noKey.Code -eq 400) 'missing Idempotency-Key -> 400' "HTTP $($noKey.Code)"
Report ((Get-EnvelopeCode $noKey) -eq 'VALIDATION_ERROR') 'missing key uses VALIDATION_ERROR' "code: $(Get-EnvelopeCode $noKey)"
Report ((Get-FirstDetailField $noKey) -eq 'Idempotency-Key') 'details names the Idempotency-Key field' "field: $(Get-FirstDetailField $noKey)"

$shortKey = Post-Export $session $session.Csrf 'too-short' (Body $plateContext 'XLSX')
Report ($shortKey.Code -eq 400) '15-char Idempotency-Key -> 400' "HTTP $($shortKey.Code)"
Report ((Get-FirstDetailField $shortKey) -eq 'Idempotency-Key') 'short key reports Idempotency-Key' "field: $(Get-FirstDetailField $shortKey)"

$badFormat = Post-Export $session $session.Csrf (New-Key) (Body $plateContext 'PDF')
Report ($badFormat.Code -eq 400) 'unsupported format PDF -> 400' "HTTP $($badFormat.Code)"
Report ((Get-FirstDetailField $badFormat) -eq 'format') 'bad format reports the format field' "field: $(Get-FirstDetailField $badFormat)"

$badColumn = Post-Export $session $session.Csrf (New-Key) (@{ contextId = $plateContext; format = 'XLSX'; columns = @('bogusColumn') } | ConvertTo-Json -Compress)
Report ($badColumn.Code -eq 400) 'unknown column -> 400' "HTTP $($badColumn.Code)"
Report ((Get-FirstDetailField $badColumn) -eq 'columns') 'unknown column reports the columns field' "field: $(Get-FirstDetailField $badColumn)"

# ------------------------------------------------------ 3) context guards
Show-Step '3) Context guards: 404 / 410 / 422'
$unknown = Post-Export $session $session.Csrf (New-Key) (Body $unknownContext 'XLSX')
Report ($unknown.Code -eq 404) 'unknown context -> 404' "HTTP $($unknown.Code)"
Report ((Get-EnvelopeCode $unknown) -eq 'DATA_NOT_FOUND') 'unknown context uses DATA_NOT_FOUND' "code: $(Get-EnvelopeCode $unknown)"

$expired = Post-Export $session $session.Csrf (New-Key) (Body $expiredContext 'XLSX')
Report ($expired.Code -eq 410) 'expired context -> 410' "HTTP $($expired.Code)"
Report ((Get-EnvelopeCode $expired) -eq 'CONTEXT_EXPIRED') 'expired context uses CONTEXT_EXPIRED' "code: $(Get-EnvelopeCode $expired)"

$unbounded = Post-Export $session $session.Csrf (New-Key) (Body $unboundedContext 'XLSX')
Report ($unbounded.Code -eq 422) 'boundless GENERAL context -> 422' "HTTP $($unbounded.Code)"
Report ((Get-EnvelopeCode $unbounded) -eq 'DATA_QUERY_TOO_BROAD') 'boundless context uses DATA_QUERY_TOO_BROAD (contract says 422, not 413)' "code: $(Get-EnvelopeCode $unbounded)"

$profile = Post-Export $session $session.Csrf (New-Key) (Body $profileContext 'XLSX')
Report ($profile.Code -eq 422) 'PROFILE context -> 422 (unsupported filter type)' "HTTP $($profile.Code)"
Report ((Get-EnvelopeCode $profile) -eq 'DATA_INVALID_FILTER') 'unsupported type uses DATA_INVALID_FILTER' "code: $(Get-EnvelopeCode $profile)"

# --------------------------------------------------------- 4) happy path
Show-Step '4) 202 + TaskCreated + one async_task and one export_job row'
$key1 = New-Key
$created = Post-Export $session $session.Csrf $key1 (Body $plateContext 'XLSX')
$createdJson = Get-Json $created
Report ($created.Code -eq 202) 'accepted export -> 202' "HTTP $($created.Code) body: $($created.Body)"
Report ($createdJson.code -eq '0') 'envelope code is "0"' "code: $($createdJson.code)"
$taskId = $createdJson.data.taskId
Report ($taskId -match '^[0-9a-fA-F-]{36}$') 'data.taskId is a UUID' "taskId: $taskId"
Report ($createdJson.data.status -eq 'PENDING') 'data.status is PENDING' "status: $($createdJson.data.status)"
Report ($createdJson.data.resourceType -eq 'EXPORT') 'data.resourceType is EXPORT' "resourceType: $($createdJson.data.resourceType)"
Report ($createdJson.traceId -eq (Get-HeaderValue $created 'X-Trace-Id') -and $createdJson.traceId -ne 'unknown-trace') 'traceId matches X-Trace-Id' "header: $(Get-HeaderValue $created 'X-Trace-Id')"
Report (@(Get-Json $created).data.PSObject.Properties.Name.Count -eq 4) 'data carries exactly TaskCreated fields' "keys: $((Get-Json $created).data.PSObject.Properties.Name -join ',')"

$taskRow = Query-Scalar "select task_type || '|' || status || '|' || owner_user_id from analysis.async_task where id = '$taskId'"
Report ($taskRow -eq "EXPORT|PENDING|$userId") 'async_task row is EXPORT/PENDING owned by the caller' "row: $taskRow"

$jobRow = Query-Scalar "select format || '|' || source_type || '|' || export_scope || '|' || array_to_string(columns, ',') from analysis.export_job where task_id = '$taskId'"
Report ($jobRow -like 'XLSX|CAPTURE|CAPTURE_SEARCH|*') 'export_job row is XLSX/CAPTURE/CAPTURE_SEARCH' "row: $jobRow"
Report ($jobRow -like '*plateMasked*' -and $jobRow -like '*captureId*') 'default columns include the PLATE-only plateMasked and the common captureId' "columns: $jobRow"

$budget = Query-Scalar "select (source_snapshot->'limits'->>'maxRows') || '|' || (source_snapshot->'limits'->>'maxBytes') || '|' || (source_snapshot->'limits'->>'shardRows') from analysis.export_job where task_id = '$taskId'"
Report ($budget -eq '1000000|1073741824|50000') 'frozen budget is 1e6 rows / 1 GiB / 50k shards' "limits: $budget"

# ---------------------------------------------------------- 5) three formats
Show-Step '5) XLSX, CSV and ZIP all enqueue'
$csv = Post-Export $session $session.Csrf (New-Key) (Body $plateContext 'CSV')
Report ($csv.Code -eq 202) 'CSV -> 202' "HTTP $($csv.Code) body: $($csv.Body)"
$csvTask = (Get-Json $csv).data.taskId
$csvFormat = Query-Scalar "select format from analysis.export_job where task_id = '$csvTask'"
Report ($csvFormat -eq 'CSV') 'CSV export_job row persisted' "format: $csvFormat"

$zip = Post-Export $session $session.Csrf (New-Key) (Body $plateContext 'ZIP')
Report ($zip.Code -eq 202) 'ZIP -> 202' "HTTP $($zip.Code) body: $($zip.Body)"
$zipTask = (Get-Json $zip).data.taskId
$zipFormat = Query-Scalar "select format from analysis.export_job where task_id = '$zipTask'"
Report ($zipFormat -eq 'ZIP') 'ZIP export_job row persisted' "format: $zipFormat"

# ------------------------------------------------------ 6) idempotency
Show-Step '6) Replaying the same key returns the same task; a different body conflicts'
$replay = Post-Export $session $session.Csrf $key1 (Body $plateContext 'XLSX')
Report ($replay.Code -eq 202) 'replay of the same key + body -> 202' "HTTP $($replay.Code) body: $($replay.Body)"
Report ((Get-Json $replay).data.taskId -eq $taskId) 'replay returns the very same taskId' "taskId: $((Get-Json $replay).data.taskId) vs $taskId"

$rowsForFirstKey = Query-Scalar "select count(*) from analysis.async_task where owner_user_id = '$userId' and idempotency_key = '$key1'"
Report ($rowsForFirstKey -eq '1') 'replay created no second async_task row' "rows: $rowsForFirstKey"

$conflict = Post-Export $session $session.Csrf $key1 (Body $plateContext 'CSV')
Report ($conflict.Code -eq 409) 'same key with a different body -> 409' "HTTP $($conflict.Code) body: $($conflict.Body)"
Report ((Get-EnvelopeCode $conflict) -eq 'IDEMPOTENCY_CONFLICT') 'conflict uses IDEMPOTENCY_CONFLICT' "code: $(Get-EnvelopeCode $conflict)"

# Other scripts and earlier runs also create EXPORT tasks in this database, so
# assert on the three ids this run created instead of on a global count.
$createdTasks = Query-Scalar "select count(distinct id) from analysis.async_task where id in ('$taskId', '$csvTask', '$zipTask') and task_type = 'EXPORT'"
Report ($createdTasks -eq '3') 'the three accepted exports are three distinct EXPORT tasks' "rows: $createdTasks"

# ---------------------------------------------------------------------- result
foreach ($f in $script:files) { Remove-Item $f -ErrorAction SilentlyContinue }
Write-Host ''
if ($script:failed -eq 0) {
    Write-Host "RESULT: $($script:passed) passed, 0 failed" -ForegroundColor Green
    exit 0
} else {
    Write-Host "RESULT: $($script:passed) passed, $($script:failed) failed" -ForegroundColor Red
    exit 1
}
