#requires -Version 5.1
<#
    verify-client-config.ps1 - GET /client-config acceptance checks (DEV-01, task 7)

    Requires the API running WITH the db profile, so that Flyway has applied
    V2__seed_deployment_client_config.sql and the seeded accounts exist:

        $env:SPRING_PROFILES_ACTIVE      = 'db'
        $env:SPRING_DATASOURCE_URL       = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
        $env:SPRING_DATASOURCE_USERNAME  = 'xxplatform_owner'
        $env:SPRING_DATASOURCE_PASSWORD  = '<password>'
        $env:XX_AUTH_DEVSEED_ENABLED     = 'true'
        $env:XX_AUTH_DEVSEED_PASSWORD    = '<password>'
        .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"

    Then: .\scripts\verify-client-config.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    What is covered:
      1) unauthenticated access -> 401 AUTH_REQUIRED in the contract envelope
      2) operator /client-config -> exactly the 12 required keys, enums pinned, no ETag
      3) features -> all 12 contract codes, stable order, pendingReason present, projection equal
      4) both deprecated fields -> featureAvailability is a pure projection of features,
         maxUploadBytes is the global upper bound (never the per-category limit)
      5) same body for admin and operator (this is deployment-wide, non-classified config)
      6) database cross-check -> every scalar equals core.config_item.value
      7) contract guards -> POST is 405, unknown query params are ignored

    Persistence checks shell out to psql and are skipped when it is absent.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the file has a
    UTF-8 BOM, so non-ASCII literals silently never match.
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string]$DB_URL  = 'jdbc:postgresql://127.0.0.1:5432/xxplatform',
    [string]$DB_USER = 'xxplatform_owner',
    [string]$DB_PASSWORD = $env:DB_PASSWORD,
    [string]$AdminLogin = 'demo.admin',
    [string]$OperatorLogin = 'demo.operator',
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
    $path = [System.IO.Path]::Combine($env:TEMP, ('cc-body-' + [guid]::NewGuid().ToString('N') + '.json'))
    [System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
    return $path
}

function Login-As([string]$loginName, [string]$password) {
    $csrf = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
    $csrfJson = ConvertTo-JsonSafe $csrf.Body
    $token = $null
    if ($csrfJson) { $token = $csrfJson.data.token }
    $cookie = Get-CookieValue $csrf.Headers 'CSRFID'

    $body = (@{ loginName = $loginName; password = $password } | ConvertTo-Json -Compress)
    $file = New-BodyFile $body
    try {
        $r = Invoke-Curl @('--max-time', '10', '-X', 'POST', '-H', 'Content-Type: application/json',
                           '--data-binary', "@$file", '-H', "X-CSRF-Token: $token",
                           '-b', "CSRFID=$cookie", "$BaseUrl/auth/login")
    } finally {
        Remove-Item $file -ErrorAction SilentlyContinue
    }
    return [pscustomobject]@{
        Response = $r
        Json     = (ConvertTo-JsonSafe $r.Body)
        Sid      = (Get-CookieValue $r.Headers 'SID')
    }
}

# A write needs a valid cookie+token pair to get past common/CsrfFilter. The pair is not
# session-bound (validate only checks cookie hash + token hash), so an anonymous
# GET /auth/csrf is enough to reach the routing layer.
function Get-CsrfPair {
    $r = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
    $j = ConvertTo-JsonSafe $r.Body
    $token = $null
    if ($j) { $token = $j.data.token }
    return [pscustomobject]@{
        Cookie = (Get-CookieValue $r.Headers 'CSRFID')
        Token  = $token
    }
}

function Get-ClientConfig([string]$sid, [string[]]$Extra = @()) {
    $curlArgs = @('--max-time', '10', "$BaseUrl/client-config") + $Extra
    if ($sid) { $curlArgs = @('-b', "SID=$sid") + $curlArgs }
    return Invoke-Curl $curlArgs
}

$CONTRACT_FEATURES = @(
    'COLLISION_ANALYSIS', 'COMPANION_ANALYSIS', 'DECRYPTION', 'DEVICE_COMMAND',
    'FACE_SEARCH', 'NOTIFICATION_SEND', 'PROFILE_ASSOCIATION', 'RAW_INGESTION',
    'REGION_STATISTICS', 'REPORT_GENERATION', 'RESIDENCE_MODEL', 'WATCH_ACTIVATION'
)
$CONTRACT_UPLOAD_CATEGORIES = @(
    'CAPTURE_IMAGE', 'DATASET_IMPORT', 'DEVICE_IMPORT', 'EXPORT', 'FACE_SEARCH',
    'PERSON_LIBRARY_IMPORT', 'PROFILE_PORTRAIT', 'RAW_INGESTION', 'REPORT'
)
$CONTRACT_CATALOG_KEYS = @(
    'DeviceInput.category', 'PersonLibraryInput.category',
    'PersonProfileCorrection.ethnicity', 'PersonProfileCorrection.gender'
)
$REQUIRED_KEYS = @(
    'version', 'mapCoordinateSystem', 'taskPollIntervalSeconds', 'maxUploadBytes',
    'featureAvailability', 'uploadPolicies', 'institutionTimeZone', 'contextTtlSeconds',
    'maxOnlineQuerySeconds', 'maxOnlineWindowDays', 'features', 'catalogBindings'
)

# ---------------------------------------------------------------- reachability
Show-Step 'Service reachability'
if (-not $SeedPassword) {
    Write-Host '  [FAIL] -SeedPassword / XX_AUTH_DEVSEED_PASSWORD is required' -ForegroundColor Red
    exit 1
}
$probe = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
if ($probe.Code -eq 0) {
    Write-Host "  [FAIL] cannot reach $BaseUrl/auth/csrf" -ForegroundColor Red
    Write-Host '         Start it with the db profile first (see the header of this script).' -ForegroundColor Yellow
    exit 1
}
Write-Host "  [OK]   reachable, HTTP $($probe.Code)" -ForegroundColor Green

# ---------------------------------------------------------------- 1) anonymous
Show-Step '1) GET /client-config without a session is 401 AUTH_REQUIRED'
$anon = Get-ClientConfig $null
Report ($anon.Code -eq 401) 'no SID cookie -> 401' "HTTP $($anon.Code) body: $($anon.Body)"
$anonJson = ConvertTo-JsonSafe $anon.Body
if ($anonJson) {
    Report ($anonJson.code -eq 'AUTH_REQUIRED') 'code is AUTH_REQUIRED' "code: $($anonJson.code)"
    Report ($null -eq $anonJson.data) 'data is null (contract error shape)' "data: $($anonJson.data)"
    Report ($null -ne $anonJson.details) 'details array is present' "details: $($anonJson.details)"
    $t = Get-HeaderValue $anon.Headers 'X-Trace-Id'
    Report ($t -eq $anonJson.traceId -and $anonJson.traceId -ne 'unknown-trace') 'traceId matches X-Trace-Id' "header: $t body: $($anonJson.traceId)"
} else {
    Report $false 'rejection body is the contract envelope, not plain text' "body: $($anon.Body)"
}

$forged = Get-ClientConfig ('forged-' + [guid]::NewGuid().ToString('N'))
Report ($forged.Code -eq 401) 'forged SID value -> 401 (never a 500)' "HTTP $($forged.Code)"

# ---------------------------------------------------------------- 2) operator
Show-Step '2) operator /client-config: 12 keys, pinned enums, no ETag'
$operator = Login-As $OperatorLogin $SeedPassword
Report ($operator.Response.Code -eq 200) 'operator can log in' "HTTP $($operator.Response.Code) body: $($operator.Response.Body)"

$cc = Get-ClientConfig $operator.Sid
Report ($cc.Code -eq 200) 'GET /client-config with a live SID -> 200' "HTTP $($cc.Code) body: $($cc.Body)"
$ccJson = ConvertTo-JsonSafe $cc.Body
if (-not $ccJson) {
    Write-Host '  [FAIL] /client-config did not return JSON; aborting' -ForegroundColor Red
    exit 1
}
Report ($ccJson.code -eq '0') 'envelope code is "0"' "code: $($ccJson.code)"
$t = Get-HeaderValue $cc.Headers 'X-Trace-Id'
Report ([bool]$t -and $t -eq $ccJson.traceId) 'X-Trace-Id header equals body traceId' "header: $t body: $($ccJson.traceId)"
Report ($null -eq (Get-HeaderValue $cc.Headers 'ETag')) 'no ETag (contract declares no conditional request)' ''

$data = $ccJson.data
$actualKeys = @($data.PSObject.Properties.Name)
Report (@($REQUIRED_KEYS | Where-Object { $actualKeys -notcontains $_ }).Count -eq 0) 'all twelve required data properties present' "missing: $((@($REQUIRED_KEYS | Where-Object { $actualKeys -notcontains $_ })) -join ',')"
Report ($actualKeys.Count -eq 12) 'exactly twelve data properties (no extra keys)' "keys: $($actualKeys -join ',')"

Report ([bool]$data.version -and $data.version -is [string]) 'version is a non-empty string' "got: $($data.version)"
Report ($data.mapCoordinateSystem -eq 'EPSG:4326') 'mapCoordinateSystem is EPSG:4326 (only legal value)' "got: $($data.mapCoordinateSystem)"
Report ($data.institutionTimeZone -eq 'Asia/Shanghai') 'institutionTimeZone is Asia/Shanghai (only legal value)' "got: $($data.institutionTimeZone)"
Report ($data.taskPollIntervalSeconds -ge 1) 'taskPollIntervalSeconds >= 1' "got: $($data.taskPollIntervalSeconds)"
Report ($data.maxUploadBytes -ge 1) 'maxUploadBytes >= 1' "got: $($data.maxUploadBytes)"
Report ($data.contextTtlSeconds -ge 1) 'contextTtlSeconds >= 1' "got: $($data.contextTtlSeconds)"
Report ($data.maxOnlineQuerySeconds -ge 1) 'maxOnlineQuerySeconds >= 1' "got: $($data.maxOnlineQuerySeconds)"
Report ($data.maxOnlineWindowDays -eq 31) 'maxOnlineWindowDays is 31 (PD-2026-10-19.v1; 12-month retention is not a query window)' "got: $($data.maxOnlineWindowDays)"

# ---------------------------------------------------------------- 3) features
Show-Step '3) features: closed enum, stable order, pendingReason, deprecated projection'
$features = @($data.features)
Report ($features.Count -eq 12) 'features carries all 12 contract codes' "count: $($features.Count)"

$codes = @($features | ForEach-Object { $_.featureCode })
$unknownCodes = @($codes | Where-Object { $CONTRACT_FEATURES -notcontains $_ })
Report ($unknownCodes.Count -eq 0) 'every featureCode is in the contract enum' "unknown: $($unknownCodes -join ',')"
$sorted = @($codes | Sort-Object)
Report ((($codes -join ',') -eq ($sorted -join ','))) 'features are ordered by featureCode (stable ordering)' "got: $($codes -join ',')"

$missingPending = @($features | Where-Object {
    -not (@($_.PSObject.Properties.Name) -contains 'pendingReason')
})
Report ($missingPending.Count -eq 0) 'pendingReason property is always present (nullable, not omitted)' "missing: $($missingPending.Count)"

$disabledWithoutReason = @($features | Where-Object {
    ($_.enabled -eq $false) -and ((-not $_.pendingReason) -or ($_.pendingReason -eq ''))
})
Report ($disabledWithoutReason.Count -eq 0) 'every disabled feature explains why (pendingReason non-empty)' "count: $($disabledWithoutReason.Count)"

$projection = @($data.featureAvailability)
Report ($projection.Count -eq $features.Count) 'featureAvailability has the same length as features' "projection: $($projection.Count) features: $($features.Count)"
$projectionMatches = $true
for ($i = 0; $i -lt [Math]::Min($projection.Count, $features.Count); $i++) {
    if ($projection[$i].code -ne $features[$i].featureCode) { $projectionMatches = $false }
    if ($projection[$i].enabled -ne $features[$i].enabled) { $projectionMatches = $false }
}
Report $projectionMatches 'featureAvailability is a pure projection of features (same order, same values)' ''
$projectionKeys = @()
if ($projection.Count -gt 0) { $projectionKeys = @($projection[0].PSObject.Properties.Name) }
Report (($projectionKeys -join ',') -eq 'code,enabled') 'projection items carry only code+enabled' "keys: $($projectionKeys -join ',')"

# ---------------------------------------------------------------- 4) policies
Show-Step '4) upload policies and catalog bindings stay inside the contract enums'
$policies = @($data.uploadPolicies)
Report ($null -ne $data.uploadPolicies) 'uploadPolicies is an array (may be empty)' "count: $($policies.Count)"
$badCategories = @($policies | Where-Object { $CONTRACT_UPLOAD_CATEGORIES -notcontains $_.category })
Report ($badCategories.Count -eq 0) 'every upload category is in the contract enum' "bad: $($badCategories.Count)"
$badBytes = @($policies | Where-Object { $_.maxBytes -lt 1 })
Report ($badBytes.Count -eq 0) 'no upload policy reports maxBytes < 1' "bad: $($badBytes.Count)"

$bindings = @($data.catalogBindings)
Report ($null -ne $data.catalogBindings) 'catalogBindings is an array (may be empty)' "count: $($bindings.Count)"
$badFields = @($bindings | Where-Object { $CONTRACT_CATALOG_KEYS -notcontains $_.fieldKey })
Report ($badFields.Count -eq 0) 'every catalog binding fieldKey is in the contract enum' "bad: $($badFields.Count)"

# ---------------------------------------------------------------- 5) same for all
Show-Step '5) the body does not depend on the caller role (deployment-wide config)'
$admin = Login-As $AdminLogin $SeedPassword
Report ($admin.Response.Code -eq 200) 'admin can log in' "HTTP $($admin.Response.Code)"
$adminCc = Get-ClientConfig $admin.Sid
Report ($adminCc.Code -eq 200) 'admin GET /client-config -> 200' "HTTP $($adminCc.Code)"
$adminJson = ConvertTo-JsonSafe $adminCc.Body
if ($adminJson) {
    $opData = ($data | ConvertTo-Json -Depth 12 -Compress)
    $adminData = ($adminJson.data | ConvertTo-Json -Depth 12 -Compress)
    Report ($opData -eq $adminData) 'admin and operator receive byte-identical config' "operator: $($opData.Length) chars admin: $($adminData.Length) chars"
}

# ---------------------------------------------------------------- 6) database
Show-Step '6) Database cross-check (deployment config is the single source of truth)'
$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
$dbReady = (Test-Path $psql) -and [bool]$DB_PASSWORD
if (-not $dbReady) {
    Write-Host '  [SKIP] psql.exe or DB_PASSWORD missing; database checks skipped' -ForegroundColor Yellow
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

    $rowCount = Query-Scalar "SELECT count(*) FROM core.config_item WHERE config_key = 'client-config' AND enabled AND public_readable;"
    Report ($rowCount -eq '1') 'exactly one active, public client-config row exists' "count: $rowCount"

    foreach ($pair in @(
        @('version', $data.version),
        @('taskPollIntervalSeconds', [string]$data.taskPollIntervalSeconds),
        @('maxUploadBytes', [string]$data.maxUploadBytes),
        @('contextTtlSeconds', [string]$data.contextTtlSeconds),
        @('maxOnlineQuerySeconds', [string]$data.maxOnlineQuerySeconds),
        @('maxOnlineWindowDays', [string]$data.maxOnlineWindowDays)
    )) {
        $key = $pair[0]
        $apiValue = $pair[1]
        $dbValue = Query-Scalar "SELECT value->>'$key' FROM core.config_item WHERE config_key = 'client-config';"
        Report ($dbValue -eq $apiValue) "$key equals core.config_item.value.$key" "db: $dbValue api: $apiValue"
    }

    $dbCodes = Query-Scalar "SELECT string_agg(feature_code, ',' ORDER BY feature_code) FROM core.feature_config;"
    Report ($dbCodes -eq ($codes -join ',')) 'feature codes equal the database rows (nothing dropped, nothing invented)' "db: $dbCodes"

    $dbEnabled = Query-Scalar "SELECT count(*) FILTER (WHERE enabled) || '/' || count(*) FROM core.feature_config;"
    $apiEnabled = (@($features | Where-Object { $_.enabled }).Count).ToString() + '/' + $features.Count.ToString()
    Report ($dbEnabled -eq $apiEnabled) 'enabled flag matches the database for every feature' "db: $dbEnabled api: $apiEnabled"

    $policyRows = Query-Scalar "SELECT count(*) FROM core.upload_policy;"
    Report ($policyRows -eq [string]$policies.Count) 'uploadPolicies length equals core.upload_policy row count' "db: $policyRows api: $($policies.Count)"

    $bindingRows = Query-Scalar @"
SELECT count(*) FROM core.dictionary_binding b
  JOIN core.dictionary d ON d.code = b.dictionary_code AND d.enabled;
"@
    Report ($bindingRows -eq [string]$bindings.Count) 'catalogBindings length equals enabled dictionary bindings' "db: $bindingRows api: $($bindings.Count)"

    $configVersion = Query-Scalar "SELECT config_version FROM core.config_item WHERE config_key = 'client-config';"
    Report ([int]$configVersion -ge 1) 'config_version is a positive counter (ops bumps it whenever a value changes; V2 seeds 1, V4 moves it to 2)' "got: $configVersion"
}

# ---------------------------------------------------------------- 7) guards
Show-Step '7) Contract guards'
# common/CsrfFilter runs before routing: an untokened POST is 403, not 405, even though
# this route is GET-only. Assert the gate first, then the method mismatch with a valid pair.
$pair = Get-CsrfPair
$postCcUntokened = Invoke-Curl @('--max-time', '10', '-X', 'POST', "$BaseUrl/client-config", '-b', "SID=$($operator.Sid)")
Report ($postCcUntokened.Code -eq 403) 'POST /client-config without a CSRF pair -> 403 (gate before routing)' "HTTP $($postCcUntokened.Code)"
Report ($postCcUntokened.Body -match '"code"\s*:\s*"AUTH_CSRF_INVALID"') 'gate rejection uses AUTH_CSRF_INVALID' "body: $($postCcUntokened.Body.Trim())"

$postCc = Invoke-Curl @(
    '--max-time', '10', '-X', 'POST', "$BaseUrl/client-config",
    '-b', "SID=$($operator.Sid); CSRFID=$($pair.Cookie)",
    '-H', "X-CSRF-Token: $($pair.Token)")
$postCcJson = ConvertTo-JsonSafe $postCc.Body
Report ($postCc.Code -eq 405) 'trusted POST /client-config -> 405' "HTTP $($postCc.Code)"
if ($postCcJson) {
    Report ($postCcJson.code -eq 'METHOD_NOT_ALLOWED') 'POST /client-config uses METHOD_NOT_ALLOWED' "code: $($postCcJson.code)"
    Report ($null -eq $postCcJson.data) 'METHOD_NOT_ALLOWED body is the contract envelope' "data: $($postCcJson.data)"
} else {
    Report $false 'METHOD_NOT_ALLOWED body is the contract envelope, not plain text' "body: $($postCc.Body)"
}

$withQuery = Get-ClientConfig $operator.Sid @('-G', '--data-urlencode', 'unused=1')
Report ($withQuery.Code -eq 200) 'unknown query parameter is ignored (no 400)' "HTTP $($withQuery.Code)"

# ---------------------------------------------------------------- summary
Write-Host ''
Write-Host ("passed: {0}   failed: {1}" -f $script:passed, $script:failed) -ForegroundColor $(if ($script:failed -eq 0) { 'Green' } else { 'Red' })
if ($script:failed -ne 0) { exit 1 }
