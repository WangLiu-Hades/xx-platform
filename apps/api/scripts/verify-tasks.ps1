#requires -Version 5.1
<#
    verify-tasks.ps1 - GET /tasks and GET /tasks/{taskId} acceptance checks (DEV-01, W2 task 7)

    Proves the read half of the task contract:
      1. both endpoints need a session and task:read (401 without a session);
      2. the default page is 1 / 20, sorting is createdAt DESC with the id as a
         stable tie-break, and pageSize is capped at 100;
      3. filters (taskType repeat, status, createdFrom/createdTo) actually narrow
         the result, and total stays the count under the same filter;
      4. different pages never repeat a row;
      5. visibility: another user's task and a deleted task are both 404
         DATA_NOT_FOUND, never 403 - the contract hides existence;
      6. Task payload shape: parameterSnapshot exposes only schemaVersion /
         contextId / submittedAt / parameterHash; a FAILED row maps its internal
         diagnostic code to TASK_FAILED.

    Requires the API running WITH the db profile AND the dev seed account enabled
    (see verify-export.ps1 for the environment). Then:

        .\scripts\verify-tasks.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    There is no task-creating HTTP surface for arbitrary types yet, so the script
    seeds async_task rows through psql; those cases need psql on PATH.

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
    $file = Join-Path $env:TEMP ("tasks-body-" + [guid]::NewGuid().ToString('N') + ".json")
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

# GET relative to /tasks with the session cookie already attached.
function Get-Tasks($session, [string]$query) {
    $url = "$BaseUrl/tasks"
    if ($query) { $url = $url + '?' + $query }
    return Invoke-Curl @('--max-time', '15', '-H', "Cookie: SID=$($session.Sid)", $url)
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
if ((Test-Path $psql) -and [bool]$DB_PASSWORD) { $env:PGPASSWORD = $DB_PASSWORD }
if (Test-Path $psql) {
    $m = [regex]::Match($DB_URL, '//([^:/]+):(\d+)/(.+)$')
    $dbHost = $m.Groups[1].Value
    $dbPort = $m.Groups[2].Value
    $dbName = $m.Groups[3].Value
    function Query-Scalar([string]$sql) {
        # SQL goes through stdin: PowerShell strips inner double quotes from native
        # command arguments, which corrupts jsonb literals passed via -c.
        $out = $sql | & $psql -h $dbHost -p $dbPort -U $DB_USER -d $dbName -t -A -f - 2>&1
        return (($out | Out-String).Trim())
    }
    try { $dbReady = (Query-Scalar 'select 1') -eq '1' } catch { $dbReady = $false }
}
if (-not $dbReady) {
    Write-Host '  [FAIL] psql cannot reach the database; task seeding is required for this script' -ForegroundColor Red
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

# Remove rows seeded by earlier runs so totals stay deterministic.
$null = Query-Scalar "DELETE FROM analysis.task_attempt WHERE task_id IN (SELECT id FROM analysis.async_task WHERE trace_id = 'verify-tasks')"
$null = Query-Scalar "DELETE FROM analysis.task_event WHERE task_id IN (SELECT id FROM analysis.async_task WHERE trace_id = 'verify-tasks')"
$null = Query-Scalar "DELETE FROM analysis.async_task WHERE trace_id = 'verify-tasks'"

# A second account that owns one task; the caller must never see it. The user_code
# carries a random suffix so repeated runs never collide on app_user_user_code_key.
$otherUser = [guid]::NewGuid().ToString()
$otherCode = 'VT-' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$null = Query-Scalar "INSERT INTO auth.app_user (id, user_code, login_name, display_name, password_hash) VALUES ('$otherUser', '$otherCode', 'verify.tasks.$($otherCode.ToLower())', 'verify tasks other', 'x') ON CONFLICT (id) DO NOTHING"

# Seed async_task rows. created_at is spaced one minute apart so the ordering
# assertions are meaningful; the statuses/types cover the filters below.
function New-Task([string]$owner, [string]$taskType, [string]$status, [int]$minutesAgo, [bool]$deleted) {
    $id = [guid]::NewGuid().ToString()
    $hash = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $snapshot = '{"contextId":"' + [guid]::NewGuid().ToString() + '","format":"XLSX","purpose":"verify-tasks","columns":["captureId"]}'
    $errCode = if ($status -eq 'FAILED') { "'TASK_LEASE_EXPIRED'" } else { 'NULL' }
    $errText = if ($status -eq 'FAILED') { "'lease expired during verification'" } else { 'NULL' }
    $sql = "INSERT INTO analysis.async_task (id, name, task_type, owner_user_id, status, parameter_schema_version, " +
           "parameter_snapshot, parameter_hash, idempotency_key, trace_id, created_at, finished_at, error_code, error_message, deleted) " +
           "VALUES ('$id', 'verify $taskType', '$taskType', '$owner', '$status', '1', '$snapshot'::jsonb, '$hash', " +
           "'" + [guid]::NewGuid().ToString('N') + "', 'verify-tasks', now() - interval '$minutesAgo minutes', " +
           "$(if ($status -eq 'SUCCESS' -or $status -eq 'FAILED') { "now() - interval '$minutesAgo minutes'" } else { 'NULL' }), " +
           "$errCode, $errText, $($deleted.ToString().ToLower()))"
    $null = Query-Scalar $sql
    return $id
}

$tNewest  = New-Task $userId 'EXPORT'  'PENDING'   1  $false
$tMiddle  = New-Task $userId 'REPORT'  'FAILED'    3  $false
$tOldest  = New-Task $userId 'ANALYSIS' 'SUCCESS'  5  $false
$tQueued  = New-Task $userId 'EXPORT'  'QUEUED'    7  $false
$tDeleted = New-Task $userId 'EXPORT'  'PENDING'   9  $true
$tOther   = New-Task $otherUser 'EXPORT' 'PENDING' 2  $false

# ------------------------------------------------------ 1) auth and method
Show-Step '1) Both endpoints require a session; /tasks is GET-only'
$anonList = Invoke-Curl @('--max-time', '10', "$BaseUrl/tasks")
Report ($anonList.Code -eq 401) 'GET /tasks without a session -> 401' "HTTP $($anonList.Code)"
Report ((Get-EnvelopeCode $anonList) -eq 'AUTH_REQUIRED') 'anonymous list uses AUTH_REQUIRED' "code: $(Get-EnvelopeCode $anonList)"

$anonOne = Invoke-Curl @('--max-time', '10', "$BaseUrl/tasks/$tNewest")
Report ($anonOne.Code -eq 401) 'GET /tasks/{id} without a session -> 401' "HTTP $($anonOne.Code)"

$postNoPair = Invoke-Curl @('--max-time', '10', '-X', 'POST', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks")
Report ($postNoPair.Code -eq 403) 'POST /tasks without a synchronizer pair -> 403 from the write gate' "HTTP $($postNoPair.Code)"
Report ((Get-EnvelopeCode $postNoPair) -eq 'AUTH_CSRF_INVALID') 'gate rejection uses AUTH_CSRF_INVALID' "code: $(Get-EnvelopeCode $postNoPair)"

$postPair = Get-CsrfPair
$post = Invoke-Curl @('--max-time', '10', '-X', 'POST',
    '-H', "Cookie: SID=$($session.Sid); CSRFID=$($postPair.Cookie)",
    '-H', "X-CSRF-Token: $($postPair.Token)", "$BaseUrl/tasks")
Report ($post.Code -eq 405) 'POST /tasks with a valid pair -> 405' "HTTP $($post.Code)"
Report ((Get-EnvelopeCode $post) -eq 'METHOD_NOT_ALLOWED') 'method mismatch uses METHOD_NOT_ALLOWED' "code: $(Get-EnvelopeCode $post)"

$notUuid = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/not-a-uuid")
Report ($notUuid.Code -eq 400) 'non-UUID taskId -> 400' "HTTP $($notUuid.Code)"
Report ((Get-FirstDetailField $notUuid) -eq 'taskId') 'path parse error names the taskId field' "field: $(Get-FirstDetailField $notUuid)"

# ------------------------------------------------------ 2) default page
Show-Step '2) Default page is 1 / 20, createdAt DESC, deleted and foreign rows excluded'
$list = Get-Tasks $session ''
$listJson = Get-Json $list
Report ($list.Code -eq 200) 'GET /tasks -> 200' "HTTP $($list.Code) body: $($list.Body)"
Report ($listJson.data.page -eq 1 -and $listJson.data.pageSize -eq 20) 'default page/pageSize are 1 and 20' "page: $($listJson.data.page), pageSize: $($listJson.data.pageSize)"
Report ($listJson.data.total -eq 4) 'total counts only the caller''s 4 live tasks' "total: $($listJson.data.total), expected 4"
Report ($listJson.data.items[0].taskId -eq $tNewest) 'default order starts with the newest task' "first: $($listJson.data.items[0].taskId) vs $tNewest"
$ids = @($listJson.data.items | ForEach-Object { $_.taskId })
Report (-not ($ids -contains $tDeleted)) 'a soft-deleted task is not listed' "ids: $($ids -join ',')"
Report (-not ($ids -contains $tOther)) 'another user''s task is not listed' "ids: $($ids -join ',')"

# ------------------------------------------------------ 3) sorting
Show-Step '3) Sort keys: createdAt and status, both directions, id as tie-break'
$asc = Get-Json (Get-Tasks $session 'sortBy=createdAt&sortOrder=ASC')
Report ($asc.data.items[0].taskId -eq $tQueued) 'createdAt ASC starts with the oldest task' "first: $($asc.data.items[0].taskId) vs $tQueued"
Report ($asc.data.items[0].taskId -ne $asc.data.items[1].taskId) 'no duplicated row across neighbours' "ids: $(@($asc.data.items | ForEach-Object { $_.taskId }) -join ',')"

$byStatus = Get-Json (Get-Tasks $session 'sortBy=status&sortOrder=ASC&pageSize=100')
$statuses = @($byStatus.data.items | ForEach-Object { $_.status })
$sorted = @($statuses | Sort-Object)
Report (($statuses -join ',') -eq ($sorted -join ',')) 'sortBy=status returns non-decreasing statuses' "statuses: $($statuses -join ',')"

# ------------------------------------------------------ 4) filters
Show-Step '4) Filters narrow the same query that produces total'
$exports = Get-Json (Get-Tasks $session 'taskType=EXPORT&taskType=REPORT&pageSize=100')
Report ($exports.data.total -eq 3) 'repeatable taskType ORs through the filter' "total: $($exports.data.total), expected 3"
$types = @($exports.data.items | ForEach-Object { $_.taskType } | Sort-Object -Unique)
Report (($types -join ',') -eq 'EXPORT,REPORT') 'repeatable taskType keeps only requested types' "types: $($types -join ',')"

$failedPage = Get-Json (Get-Tasks $session 'status=FAILED')
Report ($failedPage.data.total -eq 1 -and $failedPage.data.items[0].taskId -eq $tMiddle) 'status=FAILED returns exactly the failed task' "total: $($failedPage.data.total)"

$window = Get-Json (Get-Tasks $session ('createdFrom=' + [uri]::EscapeDataString((Get-Date).ToUniversalTime().AddMinutes(-4).ToString('yyyy-MM-ddTHH:mm:ssZ'))))
Report ($window.data.total -eq 2) 'createdFrom keeps only tasks inside the window' "total: $($window.data.total), expected 2"

# ------------------------------------------------------ 5) pagination
Show-Step '5) Pages are disjoint and total is stable'
$p1 = Get-Json (Get-Tasks $session 'pageSize=2&page=1&sortBy=createdAt&sortOrder=ASC')
$p2 = Get-Json (Get-Tasks $session 'pageSize=2&page=2&sortBy=createdAt&sortOrder=ASC')
$pageOneIds = @($p1.data.items | ForEach-Object { $_.taskId })
$pageTwoIds = @($p2.data.items | ForEach-Object { $_.taskId })
$overlap = @($pageOneIds | Where-Object { $pageTwoIds -contains $_ })
Report ($p1.data.items.Count -eq 2 -and $p2.data.items.Count -eq 2) 'both pages carry two items' "p1: $($p1.data.items.Count), p2: $($p2.data.items.Count)"
Report ($overlap.Count -eq 0) 'page 2 does not repeat page 1' "overlap: $($overlap -join ',')"
Report ($p1.data.total -eq $p2.data.total -and $p1.data.total -eq 4) 'total is the same on both pages' "p1: $($p1.data.total), p2: $($p2.data.total)"

# ------------------------------------------------------ 6) validation
Show-Step '6) Page and filter parameters are validated'
$badPage = Get-Tasks $session 'page=0'
Report ($badPage.Code -eq 400) 'page=0 -> 400' "HTTP $($badPage.Code)"
Report ((Get-FirstDetailField $badPage) -eq 'page') 'page=0 names the page field' "field: $(Get-FirstDetailField $badPage)"

$badSize = Get-Tasks $session 'pageSize=101'
Report ($badSize.Code -eq 400) 'pageSize=101 -> 400' "HTTP $($badSize.Code)"
Report ((Get-FirstDetailField $badSize) -eq 'pageSize') 'pageSize=101 names the pageSize field' "field: $(Get-FirstDetailField $badSize)"

$badSort = Get-Tasks $session 'sortBy=updatedAt'
Report ($badSort.Code -eq 400) 'unknown sortBy -> 400' "HTTP $($badSort.Code)"
Report ((Get-FirstDetailField $badSort) -eq 'sortBy') 'unknown sortBy names the sortBy field' "field: $(Get-FirstDetailField $badSort)"

$badStatus = Get-Tasks $session 'status=ACKNOWLEDGED'
Report ($badStatus.Code -eq 400) 'ACKNOWLEDGED is not a TaskStatus -> 400' "HTTP $($badStatus.Code)"

$badFrom = Get-Tasks $session ('createdFrom=' + [uri]::EscapeDataString((Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')) + '&createdTo=' + [uri]::EscapeDataString((Get-Date).ToUniversalTime().AddDays(-1).ToString('yyyy-MM-ddTHH:mm:ssZ')))
Report ($badFrom.Code -eq 400) 'createdFrom after createdTo -> 400' "HTTP $($badFrom.Code)"

# ------------------------------------------------------ 7) getTask
Show-Step '7) getTask returns the contract Task shape and hides others'
$one = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/$tMiddle")
$oneJson = Get-Json $one
Report ($one.Code -eq 200) 'GET /tasks/{own id} -> 200' "HTTP $($one.Code) body: $($one.Body)"
Report ($oneJson.data.taskId -eq $tMiddle) 'returned taskId matches the request' "taskId: $($oneJson.data.taskId)"
$required = 'taskId','taskType','status','progress','ownerUserId','createdAt','retryCount','parameterSnapshot','traceId','name'
$missing = @($required | Where-Object { -not ($oneJson.data.PSObject.Properties.Name -contains $_) })
Report ($missing.Count -eq 0) 'all required Task fields are present' "missing: $($missing -join ',')"
$snapshotKeys = @($oneJson.data.parameterSnapshot.PSObject.Properties.Name)
Report (($snapshotKeys -join ',') -eq 'schemaVersion,contextId,submittedAt,parameterHash') 'parameterSnapshot exposes only the four contract keys' "keys: $($snapshotKeys -join ',')"
Report ($oneJson.data.errorCode -eq 'TASK_FAILED') 'a FAILED row is reported as TASK_FAILED' "errorCode: $($oneJson.data.errorCode)"
Report ($oneJson.data.status -eq 'FAILED') 'status stays FAILED (not ACKNOWLEDGED)' "status: $($oneJson.data.status)"

$foreign = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/$tOther")
Report ($foreign.Code -eq 404) 'another user''s task -> 404' "HTTP $($foreign.Code)"
Report ((Get-EnvelopeCode $foreign) -eq 'DATA_NOT_FOUND') 'hiding another user''s task uses DATA_NOT_FOUND' "code: $(Get-EnvelopeCode $foreign)"

$unknown = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/$([guid]::NewGuid().ToString())")
Report ($unknown.Code -eq 404) 'unknown task -> 404' "HTTP $($unknown.Code)"

$deleted = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/$tDeleted")
Report ($deleted.Code -eq 404) 'soft-deleted task -> 404' "HTTP $($deleted.Code)"

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
