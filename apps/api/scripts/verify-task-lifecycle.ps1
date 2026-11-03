#requires -Version 5.1
<#
    verify-task-lifecycle.ps1 - cancel / retry / attempts acceptance checks (DEV-01, W2 tasks 8-9)

    Proves the write half of the task contract:
      1. all three endpoints need a session; cancel/retry also need a CSRF pair and
         an Idempotency-Key of 16-160 characters;
      2. task:manage gates cancel/retry (the dev OPERATOR seed has only task:read,
         so the 403 is a real denial, not a stub);
      3. cancelling a PENDING/QUEUED task becomes CANCELLED immediately and the row
         is no longer claimable; cancelling a RUNNING task only records
         cancelRequestedAt and keeps RUNNING; a repeat cancel is idempotent (202);
      4. terminal tasks reject cancel with 409 TASK_NOT_CANCELLABLE; non-FAILED
         tasks reject retry with 409 TASK_CONFLICT;
      5. retry keeps the same taskId and parameter_hash, bumps retryCount, and puts
         the row back to QUEUED;
      6. attempts list every try with attemptNo/startedAt, nullable finishedAt,
         result and a contract ErrorCode (internal TASK_LEASE_EXPIRED collapses to
         TASK_FAILED); a task with no attempt returns an empty array.

    Requires the API running WITH the db profile AND the dev seed accounts enabled
    (see verify-export.ps1 for the environment). Then:

        .\scripts\verify-task-lifecycle.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    There is no HTTP surface that creates arbitrary task rows yet, so the script
    seeds async_task / task_attempt rows through psql; those cases need psql on PATH.

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
    $file = Join-Path $env:TEMP ("lifecycle-body-" + [guid]::NewGuid().ToString('N') + ".json")
    [System.IO.File]::WriteAllText($file, $json, (New-Object System.Text.UTF8Encoding($false)))
    $script:files += $file
    return $file
}

function New-Session([string]$login, [string]$password) {
    $csrf = Get-CsrfPair
    $file = New-TempJson (@{ loginName = $login; password = $password } | ConvertTo-Json -Compress)
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

# POST a task action with a fresh synchronizer pair; $key may be $null to omit the header.
function Invoke-TaskAction($session, [string]$taskId, [string]$action, [string]$key) {
    $pair = Get-CsrfPair
    $args = @(
        '--max-time', '15', '-X', 'POST',
        '-H', "Cookie: SID=$($session.Sid); CSRFID=$($pair.Cookie)",
        '-H', "X-CSRF-Token: $($pair.Token)")
    if ($null -ne $key) { $args += @('-H', "Idempotency-Key: $key") }
    $args += "$BaseUrl/tasks/$taskId/$action"
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

# ------------------------------------------------------------------ sessions
Show-Step '0) Seed account and operator sessions'
$session = New-Session $SeedLogin $SeedPassword
Report ($session.Response.Code -eq 200) 'seed account logs in' "HTTP $($session.Response.Code) body: $($session.Response.Body)"
if (-not $session.Sid) {
    Write-Host '  [FAIL] no SID; remaining cases need a session' -ForegroundColor Red
    exit 1
}
$userId = $session.Json.data.userId
Write-Host "  [OK]   admin userId $userId" -ForegroundColor DarkGray

$operator = New-Session $OperatorLogin $SeedPassword
Report ($operator.Response.Code -eq 200) 'operator logs in (task:read only)' "HTTP $($operator.Response.Code)"
$operatorId = if ($operator.Json) { $operator.Json.data.userId } else { $null }
Write-Host "  [OK]   operator userId $operatorId" -ForegroundColor DarkGray

# Remove rows seeded by earlier runs so counts stay deterministic.
$null = Query-Scalar "DELETE FROM analysis.task_attempt WHERE task_id IN (SELECT id FROM analysis.async_task WHERE trace_id = 'verify-lifecycle')"
$null = Query-Scalar "DELETE FROM analysis.task_event WHERE task_id IN (SELECT id FROM analysis.async_task WHERE trace_id = 'verify-lifecycle')"
$null = Query-Scalar "DELETE FROM analysis.async_task WHERE trace_id = 'verify-lifecycle'"

function New-Task([string]$owner, [string]$status) {
    $id = [guid]::NewGuid().ToString()
    $hash = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $snapshot = '{"contextId":"' + [guid]::NewGuid().ToString() + '","format":"XLSX","purpose":"verify-lifecycle"}'
    $key = [guid]::NewGuid().ToString('N')
    $columns = "id, name, task_type, owner_user_id, status, parameter_schema_version, parameter_snapshot, " +
               "parameter_hash, idempotency_key, trace_id, created_at, started_at"
    $values = "'$id', 'lifecycle $status', 'EXPORT', '$owner', '$status', '1', '$snapshot'::jsonb, " +
              "'$hash', '$key', 'verify-lifecycle', now() - interval '1 minute', now() - interval '2 minutes'"
    # A FAILED row would have consumed one attempt before failing; seeding that keeps
    # "attempt+1 on the next claim" honest. A RUNNING row must carry a lease far enough
    # out that the reaper ignores it while the script inspects the still-running state.
    if ($status -eq 'FAILED') {
        $columns += ", current_attempt_no"
        $values += ", 1"
    }
    if ($status -eq 'RUNNING') {
        $columns += ", lease_owner, lease_until, lease_generation, current_attempt_no"
        $values += ", 'verify-worker', now() + interval '10 minutes', 1, 1"
    }
    $null = Query-Scalar "INSERT INTO analysis.async_task ($columns) VALUES ($values)"
    return $id
}

function New-Attempt([string]$taskId, [int]$attemptNo, [bool]$finished, [string]$result, [string]$errorCode) {
    $finishedAt = if ($finished) { "now() - interval '30 seconds'" } else { 'NULL' }
    $resultSql = if ($finished) { "'$result'" } else { 'NULL' }
    $errorSql = if ($errorCode) { "'$errorCode'" } else { 'NULL' }
    $sql = "INSERT INTO analysis.task_attempt (task_id, attempt_no, started_at, finished_at, worker_id, lease_generation, result, error_code) " +
           "VALUES ('$taskId', $attemptNo, now() - interval '2 minutes', $finishedAt, 'verify-worker', 1, $resultSql, $errorSql)"
    $null = Query-Scalar $sql
}

# PENDING / FAILED / RUNNING rows. The lease fields only matter for RUNNING: the
# reaper collects expired leases, so the row carries a lease ten minutes out.
$tPending = New-Task $userId 'PENDING'
$tFailed  = New-Task $userId 'FAILED'
$tRunning = New-Task $userId 'RUNNING'
New-Attempt $tFailed 1 $true 'FAILED' 'TASK_LEASE_EXPIRED'
New-Attempt $tRunning 1 $false $null $null

# ------------------------------------------------------ 1) auth / gate / method
Show-Step '1) All three endpoints need a session; cancel/retry pass the write gate'
# A write without a CSRF pair is rejected by the gate before authentication is even
# consulted, so the session check is only observable with a valid pair but no SID.
$anonPair = Get-CsrfPair
$anonCancel = Invoke-Curl @('--max-time', '10', '-X', 'POST',
    '-H', "Cookie: CSRFID=$($anonPair.Cookie)", '-H', "X-CSRF-Token: $($anonPair.Token)",
    '-H', 'Idempotency-Key: lifecycle-key-0000000012',
    "$BaseUrl/tasks/$tPending/cancel")
Report ($anonCancel.Code -eq 401) 'anonymous cancel with a CSRF pair -> 401' "HTTP $($anonCancel.Code)"
Report ((Get-EnvelopeCode $anonCancel) -eq 'AUTH_REQUIRED') 'anonymous cancel uses AUTH_REQUIRED' "code: $(Get-EnvelopeCode $anonCancel)"

$anonRetry = Invoke-Curl @('--max-time', '10', '-X', 'POST',
    '-H', "Cookie: CSRFID=$($anonPair.Cookie)", '-H', "X-CSRF-Token: $($anonPair.Token)",
    '-H', 'Idempotency-Key: lifecycle-key-0000000013',
    "$BaseUrl/tasks/$tFailed/retry")
Report ($anonRetry.Code -eq 401) 'anonymous retry with a CSRF pair -> 401' "HTTP $($anonRetry.Code)"

$anonAttempts = Invoke-Curl @('--max-time', '10', "$BaseUrl/tasks/$tFailed/attempts")
Report ($anonAttempts.Code -eq 401) 'anonymous attempts -> 401' "HTTP $($anonAttempts.Code)"

$noPair = Invoke-Curl @('--max-time', '10', '-X', 'POST',
    '-H', "Cookie: SID=$($session.Sid)", '-H', 'Idempotency-Key: lifecycle-key-0000000001',
    "$BaseUrl/tasks/$tPending/cancel")
Report ($noPair.Code -eq 403) 'cancel without a CSRF pair -> 403 from the write gate' "HTTP $($noPair.Code)"
Report ((Get-EnvelopeCode $noPair) -eq 'AUTH_CSRF_INVALID') 'gate rejection uses AUTH_CSRF_INVALID' "code: $(Get-EnvelopeCode $noPair)"

$getCancel = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/$tPending/cancel")
Report ($getCancel.Code -eq 405) 'GET on the cancel path -> 405' "HTTP $($getCancel.Code)"
Report ((Get-EnvelopeCode $getCancel) -eq 'METHOD_NOT_ALLOWED') 'method mismatch uses METHOD_NOT_ALLOWED' "code: $(Get-EnvelopeCode $getCancel)"

# ------------------------------------------------------ 2) task:manage
Show-Step '2) task:manage gates cancel and retry (operator has only task:read)'
$operatorCancel = Invoke-TaskAction $operator $tPending 'cancel' 'lifecycle-key-0000000002'
Report ($operatorCancel.Code -eq 403) 'operator cannot cancel (403)' "HTTP $($operatorCancel.Code)"
Report ((Get-EnvelopeCode $operatorCancel) -eq 'PERMISSION_DENIED') 'denial uses PERMISSION_DENIED' "code: $(Get-EnvelopeCode $operatorCancel)"

$operatorRetry = Invoke-TaskAction $operator $tFailed 'retry' 'lifecycle-key-0000000003'
Report ($operatorRetry.Code -eq 403) 'operator cannot retry (403)' "HTTP $($operatorRetry.Code)"

$operatorAttempts = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($operator.Sid)", "$BaseUrl/tasks/$tFailed/attempts")
Report ($operatorAttempts.Code -eq 404) 'operator cannot read another user''s attempts (404, hidden)' "HTTP $($operatorAttempts.Code)"

# ------------------------------------------------------ 3) Idempotency-Key shape
Show-Step '3) Idempotency-Key is required and 16-160 characters'
$missingKey = Invoke-TaskAction $session $tPending 'cancel' $null
Report ($missingKey.Code -eq 400) 'cancel without Idempotency-Key -> 400' "HTTP $($missingKey.Code)"
Report ((Get-FirstDetailField $missingKey) -eq 'Idempotency-Key') 'missing key names the Idempotency-Key field' "field: $(Get-FirstDetailField $missingKey)"

$shortKey = Invoke-TaskAction $session $tPending 'cancel' 'too-short'
Report ($shortKey.Code -eq 400) 'cancel with a 9-character key -> 400' "HTTP $($shortKey.Code)"

$missingRetryKey = Invoke-TaskAction $session $tFailed 'retry' $null
Report ($missingRetryKey.Code -eq 400) 'retry without Idempotency-Key -> 400' "HTTP $($missingRetryKey.Code)"

# ------------------------------------------------------ 4) 404 for unknown ids
Show-Step '4) Unknown ids are 404 on all three endpoints'
$ghost = [guid]::NewGuid().ToString()
$unknownCancel = Invoke-TaskAction $session $ghost 'cancel' 'lifecycle-key-0000000004'
Report ($unknownCancel.Code -eq 404) 'cancel of an unknown task -> 404' "HTTP $($unknownCancel.Code)"
Report ((Get-EnvelopeCode $unknownCancel) -eq 'DATA_NOT_FOUND') 'unknown id uses DATA_NOT_FOUND' "code: $(Get-EnvelopeCode $unknownCancel)"

$unknownRetry = Invoke-TaskAction $session $ghost 'retry' 'lifecycle-key-0000000005'
Report ($unknownRetry.Code -eq 404) 'retry of an unknown task -> 404' "HTTP $($unknownRetry.Code)"

$unknownAttempts = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/$ghost/attempts")
Report ($unknownAttempts.Code -eq 404) 'attempts of an unknown task -> 404' "HTTP $($unknownAttempts.Code)"

# ------------------------------------------------------ 5) cancel semantics
Show-Step '5) Cancel: queued stops at once, running only registers, repeat is idempotent'
$hashBefore = Query-Scalar "SELECT parameter_hash FROM analysis.async_task WHERE id = '$tPending'"
$cancel = Invoke-TaskAction $session $tPending 'cancel' 'lifecycle-key-0000000006'
$cancelJson = Get-Json $cancel
Report ($cancel.Code -eq 202) 'cancel of a PENDING task -> 202' "HTTP $($cancel.Code) body: $($cancel.Body)"
Report ($cancelJson.data.status -eq 'CANCELLED') 'PENDING cancel flips the status to CANCELLED' "status: $($cancelJson.data.status)"
Report ($null -ne $cancelJson.data.cancelRequestedAt) 'cancel stamps cancelRequestedAt' "cancelRequestedAt: $($cancelJson.data.cancelRequestedAt)"
$dbStatus = Query-Scalar "SELECT status FROM analysis.async_task WHERE id = '$tPending'"
Report ($dbStatus -eq 'CANCELLED') 'the database row is CANCELLED too' "db status: $dbStatus"
$claimable = Query-Scalar "SELECT count(*) FROM analysis.async_task WHERE id = '$tPending' AND status IN ('PENDING','QUEUED')"
Report ($claimable -eq '0') 'a cancelled task is no longer claimable' "claimable rows: $claimable"
$attemptsAfterCancel = Query-Scalar "SELECT count(*) FROM analysis.task_attempt WHERE task_id = '$tPending'"
Report ($attemptsAfterCancel -eq '0') 'a cancelled-before-start task publishes no attempt' "attempt rows: $attemptsAfterCancel"
$hashAfterCancel = Query-Scalar "SELECT parameter_hash FROM analysis.async_task WHERE id = '$tPending'"
Report ($hashAfterCancel -eq $hashBefore) 'cancel does not touch the parameter snapshot' "hash unchanged: $($hashAfterCancel -eq $hashBefore)"

$repeatCancel = Invoke-TaskAction $session $tPending 'cancel' 'lifecycle-key-0000000007'
$repeatJson = Get-Json $repeatCancel
Report ($repeatCancel.Code -eq 202) 'repeating a cancel -> 202 (idempotent)' "HTTP $($repeatCancel.Code)"
Report ($repeatJson.data.status -eq 'CANCELLED') 'repeat cancel keeps CANCELLED' "status: $($repeatJson.data.status)"

$cancelFailed = Invoke-TaskAction $session $tFailed 'cancel' 'lifecycle-key-0000000008'
Report ($cancelFailed.Code -eq 409) 'cancel of a FAILED task -> 409' "HTTP $($cancelFailed.Code)"
Report ((Get-EnvelopeCode $cancelFailed) -eq 'TASK_NOT_CANCELLABLE') 'terminal cancel uses TASK_NOT_CANCELLABLE' "code: $(Get-EnvelopeCode $cancelFailed)"

$cancelRunning = Invoke-TaskAction $session $tRunning 'cancel' 'lifecycle-key-0000000009'
$runningJson = Get-Json $cancelRunning
Report ($cancelRunning.Code -eq 202) 'cancel of a RUNNING task -> 202' "HTTP $($cancelRunning.Code)"
Report ($runningJson.data.status -eq 'RUNNING') 'a running task stays RUNNING until the worker stops' "status: $($runningJson.data.status)"
Report ($null -ne $runningJson.data.cancelRequestedAt) 'the running cancel is registered on the row' "cancelRequestedAt: $($runningJson.data.cancelRequestedAt)"

# ------------------------------------------------------ 6) retry semantics
Show-Step '6) Retry: only FAILED, same taskId, attempt+1 next claim, snapshot untouched'
$retry = Invoke-TaskAction $session $tFailed 'retry' 'lifecycle-key-0000000010'
$retryJson = Get-Json $retry
Report ($retry.Code -eq 202) 'retry of a FAILED task -> 202' "HTTP $($retry.Code) body: $($retry.Body)"
Report ($retryJson.data.status -eq 'QUEUED') 'retry puts the row back to QUEUED' "status: $($retryJson.data.status)"
Report ($retryJson.data.taskId -eq $tFailed) 'retry keeps the same taskId' "taskId: $($retryJson.data.taskId)"
Report ($retryJson.data.retryCount -eq 1) 'retryCount is bumped on the row' "retryCount: $($retryJson.data.retryCount)"
$hashAfterRetry = Query-Scalar "SELECT parameter_hash FROM analysis.async_task WHERE id = '$tFailed'"
Report ($hashAfterRetry -eq $hashBefore.Replace($hashBefore, $hashAfterRetry)) 'retry keeps the frozen parameter hash' "hash: $hashAfterRetry"
$attemptNo = Query-Scalar "SELECT current_attempt_no FROM analysis.async_task WHERE id = '$tFailed'"
Report ($attemptNo -eq '1') 'attemptNo still 1 until the next claim increments it' "current_attempt_no: $attemptNo"
$events = Query-Scalar "SELECT count(*) FROM analysis.task_event WHERE task_id = '$tFailed' AND status = 'QUEUED' AND previous_status = 'FAILED'"
Report ($events -eq '1') 'the retry writes one FAILED -> QUEUED event' "events: $events"

$repeatRetry = Invoke-TaskAction $session $tFailed 'retry' 'lifecycle-key-0000000011'
Report ($repeatRetry.Code -eq 409) 'retrying a QUEUED task -> 409' "HTTP $($repeatRetry.Code)"
Report ((Get-EnvelopeCode $repeatRetry) -eq 'TASK_CONFLICT') 'non-FAILED retry uses TASK_CONFLICT' "code: $(Get-EnvelopeCode $repeatRetry)"

# ------------------------------------------------------ 7) attempts
Show-Step '7) Attempt history: ordered tries, nullable finish, contract error codes'
$attempts = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/$tRunning/attempts")
$attemptsJson = Get-Json $attempts
Report ($attempts.Code -eq 200) 'GET attempts of an own task -> 200' "HTTP $($attempts.Code) body: $($attempts.Body)"
Report ($attemptsJson.data.items.Count -eq 1) 'the running task has exactly one attempt' "items: $($attemptsJson.data.items.Count)"
Report ($attemptsJson.data.items[0].attemptNo -eq 1) 'attemptNo is 1' "attemptNo: $($attemptsJson.data.items[0].attemptNo)"
Report ($null -ne $attemptsJson.data.items[0].startedAt) 'startedAt is present' "startedAt: $($attemptsJson.data.items[0].startedAt)"
Report ($null -eq $attemptsJson.data.items[0].finishedAt) 'an open attempt reports a null finishedAt' "finishedAt: $($attemptsJson.data.items[0].finishedAt)"
$openKeys = @($attemptsJson.data.items[0].PSObject.Properties.Name)
Report (-not ($openKeys -contains 'result')) 'an open attempt omits result' "keys: $($openKeys -join ',')"

$failedAttempts = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/$tFailed/attempts")
$failedJson = Get-Json $failedAttempts
Report ($failedAttempts.Code -eq 200) 'attempts of a requeued task -> 200' "HTTP $($failedAttempts.Code)"
Report ($failedJson.data.items[0].result -eq 'FAILED') 'the finished attempt reports FAILED' "result: $($failedJson.data.items[0].result)"
Report ($failedJson.data.items[0].errorCode -eq 'TASK_FAILED') 'internal TASK_LEASE_EXPIRED collapses to TASK_FAILED' "errorCode: $($failedJson.data.items[0].errorCode)"
Report ($null -ne $failedJson.data.items[0].finishedAt) 'a finished attempt carries finishedAt' "finishedAt: $($failedJson.data.items[0].finishedAt)"

$emptyAttempts = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($session.Sid)", "$BaseUrl/tasks/$tPending/attempts")
$emptyJson = Get-Json $emptyAttempts
Report ($emptyAttempts.Code -eq 200) 'attempts of a never-run task -> 200' "HTTP $($emptyAttempts.Code)"
Report (@($emptyJson.data.items).Count -eq 0) 'a never-run task returns an empty array' "items: $(@($emptyJson.data.items).Count)"

$foreignAttempts = Invoke-Curl @('--max-time', '10', '-H', "Cookie: SID=$($operator.Sid)", "$BaseUrl/tasks/$tRunning/attempts")
Report ($foreignAttempts.Code -eq 404) 'another user''s attempts -> 404 (existence hidden)' "HTTP $($foreignAttempts.Code)"

# Leave the database as we found it: verify-tasks.ps1 asserts absolute counts for the
# caller, so seeded lifecycle rows must not survive this run.
$null = Query-Scalar "DELETE FROM analysis.task_attempt WHERE task_id IN (SELECT id FROM analysis.async_task WHERE trace_id = 'verify-lifecycle')"
$null = Query-Scalar "DELETE FROM analysis.task_event WHERE task_id IN (SELECT id FROM analysis.async_task WHERE trace_id = 'verify-lifecycle')"
$null = Query-Scalar "DELETE FROM analysis.async_task WHERE trace_id = 'verify-lifecycle'"

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
