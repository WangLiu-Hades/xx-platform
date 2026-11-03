#requires -Version 5.1
<#
    verify-health.ps1 - GET /health and GET /ready acceptance checks (DEV-01, task 8)

    Requires the API running WITH the db profile (so Flyway has applied V1/V2 and the
    deployment config row exists):

        $env:SPRING_PROFILES_ACTIVE      = 'db'
        $env:SPRING_DATASOURCE_URL       = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
        $env:SPRING_DATASOURCE_USERNAME  = 'xxplatform_owner'
        $env:SPRING_DATASOURCE_PASSWORD  = '<password>'
        .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"

    Then: .\scripts\verify-health.ps1 -DB_PASSWORD '<db password>'

    What is covered:
      1) /health is PUBLIC and always 200 UP, even with a bogus SID cookie
         (liveness must not depend on the database: restarting the app cannot fix a dead DB)
      2) /health data has exactly the two contract keys (status, checkedAt) and no dependencies
      3) /ready with a healthy database -> 200 READY + three dependencies in fixed order
      4) the database really satisfies what /ready claims (no failed migration, migrations
         applied, client-config row readable)
      5) /ready when the config row is not readable -> 503 SYSTEM_UNAVAILABLE, data null,
         Retry-After >= 1, details names deployment-config, and the other items stay READY
      6) /ready when the migration history is unreadable -> 503, details names schema only
      7) after restoring both, /ready is READY again (the probe is not latched)
      8) contract guards -> POST is 405, unknown query params ignored, no ETag

    The two negative cases mutate the database for a few hundred milliseconds and restore
    it in a finally block. They are skipped when psql is not available.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the file has a
    UTF-8 BOM, so non-ASCII literals silently never match.
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string]$DB_URL  = 'jdbc:postgresql://127.0.0.1:5432/xxplatform',
    [string]$DB_USER = 'xxplatform_owner',
    [string]$DB_PASSWORD = $env:DB_PASSWORD
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

function Get-HeaderValue($headers, [string]$name) {
    $m = [regex]::Match($headers, "(?im)^\s*$([regex]::Escape($name))\s*:\s*(.+?)\s*$")
    if ($m.Success) { return $m.Groups[1].Value }
    return $null
}

function ConvertTo-JsonSafe($text) {
    try { return ($text | ConvertFrom-Json) } catch { return $null }
}

$RFC3339 = '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$'
$PSQL = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
$script:hasPsql = Test-Path $PSQL

function Exec-Sql([string]$sql) {
    $env:PGPASSWORD = $DB_PASSWORD
    $out = & $PSQL -h 127.0.0.1 -p 5432 -U $DB_USER -d xxplatform -t -A -v ON_ERROR_STOP=1 -c $sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $($out | Out-String)" }
    return (($out | Out-String).Trim())
}

function Get-Health([string]$sid, [string[]]$Extra = @()) {
    $curlArgs = @('--max-time', '10', "$BaseUrl/health") + $Extra
    if ($sid) { $curlArgs = @('-b', "SID=$sid") + $curlArgs }
    return Invoke-Curl $curlArgs
}

function Get-Ready([string]$sid, [string[]]$Extra = @()) {
    $curlArgs = @('--max-time', '10', "$BaseUrl/ready") + $Extra
    if ($sid) { $curlArgs = @('-b', "SID=$sid") + $curlArgs }
    return Invoke-Curl $curlArgs
}

function Get-DataKeys($json) {
    if (-not $json -or -not $json.data) { return @() }
    return @($json.data.PSObject.Properties | ForEach-Object { $_.Name } | Sort-Object)
}

function Get-DependencyNames($json) {
    if (-not $json -or -not $json.data -or -not $json.data.dependencies) { return @() }
    return @($json.data.dependencies | ForEach-Object { $_.name })
}

function Get-DependencyStatus($json, [string]$name) {
    if (-not $json -or -not $json.data -or -not $json.data.dependencies) { return $null }
    foreach ($d in @($json.data.dependencies)) {
        if ($d.name -eq $name) { return $d.status }
    }
    return $null
}

function Get-DetailFields($json) {
    if (-not $json -or -not $json.details) { return @() }
    return @($json.details | ForEach-Object { $_.field })
}

Write-Host "verify-health: $BaseUrl" -ForegroundColor Yellow
Write-Host "psql available: $script:hasPsql"

# ---------------------------------------------------------------- 1) /health is public
Show-Step '1) GET /health is public and always UP'

$h = Get-Health
$hj = ConvertTo-JsonSafe $h.Body
Report ($h.Code -eq 200) 'GET /health -> 200 without any session' "status=$($h.Code)"
Report ($hj -and $hj.code -eq '0') 'health envelope code is "0"'
Report ($hj -and $hj.message -eq 'success') 'health envelope message is "success"'
$hTraceHeader = Get-HeaderValue $h.Headers 'X-Trace-Id'
Report ($hTraceHeader -and $hj -and $hTraceHeader -eq $hj.traceId) 'health traceId header equals body traceId' "header=$hTraceHeader"
$hKeys = @(Get-DataKeys $hj)
Report ($hKeys.Count -eq 2 -and $hKeys[0] -eq 'checkedAt' -and $hKeys[1] -eq 'status') 'health data has exactly status + checkedAt (additionalProperties:false)' ($hKeys -join ',')
Report ($hj -and $hj.data.status -eq 'UP') 'health status is UP'
Report ($hj -and $hj.data.checkedAt -match $RFC3339) 'health checkedAt is UTC RFC3339 with seconds' $(if ($hj) { $hj.data.checkedAt })
Report ($hj -and -not ($hj.data.PSObject.Properties.Name -contains 'dependencies')) 'health data has no dependencies key (HealthData has no such field)'

$hBogus = Get-Health 'this-is-not-a-real-sid'
$hBogusJson = ConvertTo-JsonSafe $hBogus.Body
Report ($hBogus.Code -eq 200 -and $hBogusJson -and $hBogusJson.data.status -eq 'UP') 'health ignores a bogus SID cookie (PUBLIC, identity not needed)' "status=$($hBogus.Code)"
Report (-not (Get-HeaderValue $h.Headers 'ETag')) 'health response has no ETag (contract declares only X-Trace-Id)'

# ---------------------------------------------------------------- 2) /ready when healthy
Show-Step '2) GET /ready with a healthy database'

$r = Get-Ready
$rj = ConvertTo-JsonSafe $r.Body
Report ($r.Code -eq 200) 'GET /ready -> 200 without any session' "status=$($r.Code)"
Report ($rj -and $rj.code -eq '0') 'ready envelope code is "0"'
$rTraceHeader = Get-HeaderValue $r.Headers 'X-Trace-Id'
Report ($rTraceHeader -and $rj -and $rTraceHeader -eq $rj.traceId) 'ready traceId header equals body traceId'
$rKeys = @(Get-DataKeys $rj)
Report ($rKeys.Count -eq 3) 'ready data has exactly three keys (status, checkedAt, dependencies)' ($rKeys -join ',')
Report ($rj -and $rj.data.status -eq 'READY') 'ready status is READY' $(if ($rj) { $rj.data.status })
Report ($rj -and $rj.data.checkedAt -match $RFC3339) 'ready checkedAt is UTC RFC3339 with seconds'
$depNames = @(Get-DependencyNames $rj)
Report ($depNames.Count -eq 3 -and $depNames[0] -eq 'database' -and $depNames[1] -eq 'schema' -and $depNames[2] -eq 'deployment-config') 'dependencies are exactly database, schema, deployment-config in fixed order' ($depNames -join ',')
foreach ($dn in @('database', 'schema', 'deployment-config')) {
    Report ((Get-DependencyStatus $rj $dn) -eq 'READY') "dependency $dn is READY"
}
Report (-not (Get-HeaderValue $r.Headers 'ETag')) 'ready response has no ETag (contract declares only X-Trace-Id)'
Report (-not (Get-HeaderValue $r.Headers 'Retry-After')) 'READY response carries no Retry-After (only 429/503 do)'

# ---------------------------------------------------------------- 3) DB really satisfies READY
Show-Step '3) the database really satisfies what READY claims'

if ($script:hasPsql) {
    $failedMigrations = Exec-Sql "SELECT count(*) FROM flyway_schema_history WHERE success = false;"
    Report ($failedMigrations -eq '0') 'flyway_schema_history has no failed migration' "count=$failedMigrations"

    $applied = [int](Exec-Sql "SELECT count(*) FROM flyway_schema_history;")
    Report ($applied -ge 2) 'at least V1 and V2 are applied' "applied=$applied"

    $configRows = Exec-Sql "SELECT count(*) FROM core.config_item WHERE config_key = 'client-config' AND enabled = true AND public_readable = true;"
    Report ($configRows -eq '1') 'exactly one enabled and publicly readable client-config row' "count=$configRows"
} else {
    Report $true 'database cross-checks skipped (psql not found)'
}

# ---------------------------------------------------------------- 4) NOT_READY: config row
Show-Step '4) /ready -> 503 when the config row is not readable'

if ($script:hasPsql) {
    $probeOk = $false
    try {
        Exec-Sql "UPDATE core.config_item SET public_readable = false WHERE config_key = 'client-config';" | Out-Null
        $bad = Get-Ready
        $bj = ConvertTo-JsonSafe $bad.Body
        Report ($bad.Code -eq 503) 'not-ready config -> HTTP 503' "status=$($bad.Code)"
        Report ($bj -and $bj.code -eq 'SYSTEM_UNAVAILABLE') 'not-ready code is SYSTEM_UNAVAILABLE' $(if ($bj) { $bj.code })
        Report ($bj -and $null -eq $bj.data) 'not-ready data is null (error envelope)'
        Report ($bj -and $bj.traceId -and $bj.traceId -eq (Get-HeaderValue $bad.Headers 'X-Trace-Id')) 'not-ready traceId header equals body traceId'
        $retryAfter = Get-HeaderValue $bad.Headers 'Retry-After'
        $retryOk = $false
        if ($retryAfter) { $retryOk = ([int]$retryAfter -ge 1) }
        Report $retryOk 'not-ready carries Retry-After >= 1 (contract: 429/503 only)' "Retry-After=$retryAfter"
        $detailFields = @(Get-DetailFields $bj)
        Report ($detailFields -contains 'deployment-config') 'details names the failing dependency (deployment-config)' ($detailFields -join ',')
        Report (-not ($detailFields -contains 'database')) 'details does not blame database (failure isolation)'

        $badJson = ConvertTo-JsonSafe $bad.Body
        Report ($badJson -and $badJson.details.Count -ge 1) 'details is a non-empty array'
        # Restore before asserting the happy path again.
        Exec-Sql "UPDATE core.config_item SET public_readable = true WHERE config_key = 'client-config';" | Out-Null
        $back = Get-Ready
        Report ($back.Code -eq 200) 'after restoring the config row /ready is 200 again' "status=$($back.Code)"
        $probeOk = $true
    } finally {
        if (-not $probeOk) {
            try { Exec-Sql "UPDATE core.config_item SET public_readable = true WHERE config_key = 'client-config';" | Out-Null } catch { }
        }
    }
} else {
    Report $true 'config-row negative case skipped (psql not found)'
}

# ---------------------------------------------------------------- 5) NOT_READY: schema
Show-Step '5) /ready -> 503 when the migration history is unreadable'

if ($script:hasPsql) {
    $restored = $false
    try {
        Exec-Sql "ALTER TABLE flyway_schema_history RENAME TO flyway_schema_history_probe;" | Out-Null
        try {
            $noHistory = Get-Ready
            $nj = ConvertTo-JsonSafe $noHistory.Body
            Report ($noHistory.Code -eq 503) 'unreadable migration history -> HTTP 503' "status=$($noHistory.Code)"
            Report ($nj -and $nj.code -eq 'SYSTEM_UNAVAILABLE') 'unreadable migration history code is SYSTEM_UNAVAILABLE'
            $nFields = @(Get-DetailFields $nj)
            Report ($nFields -contains 'schema') 'details names schema' ($nFields -join ',')
            Report (-not ($nFields -contains 'database')) 'database still reported READY (per-item isolation)'
            Report ($nj -and $null -eq $nj.data) 'unreadable migration history data is null'
        } finally {
            Exec-Sql "ALTER TABLE flyway_schema_history_probe RENAME TO flyway_schema_history;" | Out-Null
            $restored = $true
        }
        $after = Get-Ready
        Report ($after.Code -eq 200) 'after restoring the migration history /ready is 200 again' "status=$($after.Code)"
    } finally {
        if (-not $restored) {
            try { Exec-Sql "ALTER TABLE IF EXISTS flyway_schema_history_probe RENAME TO flyway_schema_history;" | Out-Null } catch { }
        }
    }
} else {
    Report $true 'schema negative case skipped (psql not found)'
}

# ---------------------------------------------------------------- 6) contract guards
Show-Step '6) contract guards'

$noAuth = Get-Ready
Report ($noAuth.Code -eq 200) 'ready needs no session (PUBLIC)'

$unknown = Get-Health $null @('--get', '--data-urlencode', 'verbose=1')
Report ($unknown.Code -eq 200) 'unknown query parameter is ignored by /health' "status=$($unknown.Code)"

$postReady = Invoke-Curl @('--max-time', '10', '-X', 'POST', "$BaseUrl/ready")
$prJson = ConvertTo-JsonSafe $postReady.Body
Report ($postReady.Code -eq 405) 'POST /ready -> 405' "status=$($postReady.Code)"
Report ($prJson -and $prJson.code -eq 'METHOD_NOT_ALLOWED') 'POST /ready code is METHOD_NOT_ALLOWED' $(if ($prJson) { $prJson.code })
Report ($prJson -and $null -eq $prJson.data) 'POST /ready data is null'

$postHealth = Invoke-Curl @('--max-time', '10', '-X', 'POST', "$BaseUrl/health")
$phJson = ConvertTo-JsonSafe $postHealth.Body
Report ($postHealth.Code -eq 405) 'POST /health -> 405' "status=$($postHealth.Code)"
Report ($phJson -and $phJson.code -eq 'METHOD_NOT_ALLOWED') 'POST /health code is METHOD_NOT_ALLOWED'

Write-Host "`nRESULT: $($script:passed) passed, $($script:failed) failed" -ForegroundColor $(if ($script:failed -eq 0) { 'Green' } else { 'Red' })
if ($script:failed -gt 0) { exit 1 }
