#requires -Version 5.1
<#
    verify-me.ps1 - GET /me acceptance checks (DEV-01, task 6)

    Requires the API running WITH the db profile AND the dev seed enabled, so that the
    three seeded accounts/roles exist:

        $env:SPRING_PROFILES_ACTIVE      = 'db'
        $env:SPRING_DATASOURCE_URL       = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
        $env:SPRING_DATASOURCE_USERNAME  = 'xxplatform_owner'
        $env:SPRING_DATASOURCE_PASSWORD  = '<password>'
        $env:XX_AUTH_DEVSEED_ENABLED     = 'true'
        $env:XX_AUTH_DEVSEED_PASSWORD    = '<password>'
        .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"

    Then: .\scripts\verify-me.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    What is covered:
      1) unauthenticated access -> 401 AUTH_REQUIRED (never 403, never a partial body)
      2) admin  /me -> full shape, 58 permission codes, all sites, four data types
      3) operator /me -> ownOnly, data types narrowed to FENCE+PLATE, no capture:decrypt
      4) classified /me -> operator + capture:decrypt/audit:read
      5) session lifecycle -> idle timeout / revoked / disabled account all 401
      6) menu projection -> required_permission_code the user lacks is filtered out
      7) contract guards -> POST /me is 405, unknown query params are ignored, no ETag
      8) A01 -> dataScope.scopeGrants is the tuple union; the legacy five fields are
         only its projection (the cross-product case needs non-all-sites roles and is
         covered by verify-scope-tuples.ps1)

    Persistence checks shell out to psql and are skipped when it is absent.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the file has a
    UTF-8 BOM, so non-ASCII literals silently never match. (Assertion strings that must
    match Chinese API messages are therefore checked by code, not by message text.)
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string]$DB_URL  = 'jdbc:postgresql://127.0.0.1:5432/xxplatform',
    [string]$DB_USER = 'xxplatform_owner',
    [string]$DB_PASSWORD = $env:DB_PASSWORD,
    [string]$AdminLogin = 'demo.admin',
    [string]$OperatorLogin = 'demo.operator',
    [string]$ClassifiedLogin = 'demo.classified',
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
    $path = [System.IO.Path]::Combine($env:TEMP, ('me-body-' + [guid]::NewGuid().ToString('N') + '.json'))
    [System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
    return $path
}

function New-LoginBody([string]$loginName, [string]$password) {
    return (@{ loginName = $loginName; password = $password } | ConvertTo-Json -Compress)
}

function Post-Login([string]$bodyJson, [string[]]$Extra = @()) {
    $file = New-BodyFile $bodyJson
    try {
        $curlArgs = @('--max-time', '10', '-X', 'POST', '-H', 'Content-Type: application/json',
                      '--data-binary', "@$file", "$BaseUrl/auth/login") + $Extra
        return Invoke-Curl $curlArgs
    } finally {
        Remove-Item $file -ErrorAction SilentlyContinue
    }
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

# Full login round trip: returns the SID plus the SessionData the login endpoint reported,
# so /me can be compared against it.
function Login-As([string]$loginName, [string]$password) {
    $csrf = Get-CsrfPair
    $r = Post-Login (New-LoginBody $loginName $password) @(
        '-H', "X-CSRF-Token: $($csrf.Token)", '-b', "CSRFID=$($csrf.Cookie)")
    return [pscustomobject]@{
        Response = $r
        Json     = (ConvertTo-JsonSafe $r.Body)
        Sid      = (Get-CookieValue $r.Headers 'SID')
    }
}

function Get-Me([string]$sid, [string[]]$Extra = @()) {
    $curlArgs = @('--max-time', '10', "$BaseUrl/me") + $Extra
    if ($sid) { $curlArgs = @('-b', "SID=$sid") + $curlArgs }
    return Invoke-Curl $curlArgs
}

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
Show-Step '1) GET /me without a valid session is 401 AUTH_REQUIRED'
$anon = Get-Me $null
Report ($anon.Code -eq 401) 'no SID cookie -> 401' "HTTP $($anon.Code) body: $($anon.Body)"
$anonJson = ConvertTo-JsonSafe $anon.Body
if ($anonJson) {
    Report ($anonJson.code -eq 'AUTH_REQUIRED') 'code is AUTH_REQUIRED (not AUTH_SESSION_EXPIRED)' "code: $($anonJson.code)"
    Report ($null -eq $anonJson.data) 'data is null (contract error shape)' "data: $($anonJson.data)"
    Report ($null -ne $anonJson.details) 'details array is present' "details: $($anonJson.details)"
    $t = Get-HeaderValue $anon.Headers 'X-Trace-Id'
    Report ($t -eq $anonJson.traceId -and $anonJson.traceId -ne 'unknown-trace') 'traceId matches X-Trace-Id' "header: $t body: $($anonJson.traceId)"
} else {
    Report $false 'rejection body is the contract envelope, not plain text' "body: $($anon.Body)"
}

$forged = Get-Me ('forged-' + [guid]::NewGuid().ToString('N'))
Report ($forged.Code -eq 401) 'forged SID value -> 401 (never a 500)' "HTTP $($forged.Code)"
$forgedJson = ConvertTo-JsonSafe $forged.Body
Report ($forgedJson.code -eq 'AUTH_REQUIRED') 'forged SID uses AUTH_REQUIRED' "code: $($forgedJson.code)"

# ---------------------------------------------------------------- 2) admin
Show-Step '2) admin /me: full shape, all permissions, all sites, four data types'
$admin = Login-As $AdminLogin $SeedPassword
Report ($admin.Response.Code -eq 200) 'admin can log in' "HTTP $($admin.Response.Code) body: $($admin.Response.Body)"

$me = Get-Me $admin.Sid
Report ($me.Code -eq 200) 'GET /me with a live SID -> 200' "HTTP $($me.Code) body: $($me.Body)"
$meJson = ConvertTo-JsonSafe $me.Body
if (-not $meJson) {
    Write-Host '  [FAIL] /me did not return JSON; aborting' -ForegroundColor Red
    exit 1
}
Report ($meJson.code -eq '0') 'envelope code is "0"' "code: $($meJson.code)"
$t = Get-HeaderValue $me.Headers 'X-Trace-Id'
Report ([bool]$t -and $t -eq $meJson.traceId) 'X-Trace-Id header equals body traceId' "header: $t body: $($meJson.traceId)"
Report ($null -eq (Get-HeaderValue $me.Headers 'ETag')) 'no ETag (contract declares no conditional request)' ''

$data = $meJson.data
# Contract CurrentUserData: seven required properties, additionalProperties false.
$expectedKeys = @('user', 'roleIds', 'permissionCodes', 'menus', 'dataScope', 'siteScope', 'session')
$actualKeys = @($data.PSObject.Properties.Name)
Report (@($expectedKeys | Where-Object { $actualKeys -notcontains $_ }).Count -eq 0) 'all seven required data properties present' "keys: $($actualKeys -join ',')"
Report ($actualKeys.Count -eq 7) 'exactly seven data properties (no extra keys)' "count: $($actualKeys.Count)"

Report ($data.user.userId -match '^[0-9a-fA-F-]{36}$') 'user.userId is a uuid' "userId: $($data.user.userId)"
Report ($data.user.loginName -eq $AdminLogin) 'user.loginName echoes the login name' "got: $($data.user.loginName)"
Report ($data.user.displayName -ne $null -and $data.user.displayName -ne '') 'user.displayName is non-empty' "got: $($data.user.displayName)"
Report ($data.user.status -eq 'ACTIVE') 'user.status is ACTIVE' "got: $($data.user.status)"
if ($admin.Json) {
    Report ($data.user.userId -eq $admin.Json.data.userId) 'user.userId equals the login response userId' "me: $($data.user.userId) login: $($admin.Json.data.userId)"
}

$adminRoles = @($data.roleIds)
Report ($adminRoles.Count -eq 1) 'admin has exactly one seeded role' "count: $($adminRoles.Count)"

$adminPerms = @($data.permissionCodes)
Report ($adminPerms.Count -eq 58) 'SUPER_ADMIN carries all 58 permission codes' "count: $($adminPerms.Count)"
Report ($adminPerms -contains 'dashboard:read') 'permissionCodes contains dashboard:read' ''
Report ($adminPerms -contains 'capture:decrypt') 'permissionCodes contains capture:decrypt' ''
Report ($adminPerms -contains 'role:grant') 'permissionCodes contains role:grant' ''
# No menu rows are seeded on purpose: empty is a legal result, not an error.
Report ($null -ne $data.menus) 'menus is present (may be empty)' "count: $(@($data.menus).Count)"

Report ($data.dataScope.allSites -eq $true) 'dataScope.allSites is true for the seeded roles' "got: $($data.dataScope.allSites)"
Report (@($data.dataScope.siteIds).Count -eq 0) 'allSites=true implies siteIds is [] (contract text)' "siteIds: $(@($data.dataScope.siteIds).Count)"
Report ((@($data.dataScope.dataTypes) -join ',') -eq 'FENCE,MAC,PLATE,FACE') 'dataTypes ordered FENCE,MAC,PLATE,FACE' "got: $((@($data.dataScope.dataTypes) -join ',') )"
Report ($data.dataScope.ownOnly -eq $false) 'admin ownOnly is false' "got: $($data.dataScope.ownOnly)"
Report ($data.dataScope.scopeVersion -eq 0) 'scopeVersion is 0 without an auth.user_scope row' "got: $($data.dataScope.scopeVersion)"

# A01: scopeGrants is the authoritative grant set; the five siblings above are only a
# UI projection of it. The cross-product regression (PLATE@A + FACE@B) needs roles that
# do not carry all sites, so it lives in verify-scope-tuples.ps1.
$scopeKeys = @($data.dataScope.PSObject.Properties.Name)
Report ($scopeKeys.Count -eq 6 -and $scopeKeys -contains 'scopeGrants') 'dataScope carries exactly six properties including scopeGrants' "keys: $($scopeKeys -join ',')"
$adminGrants = @($data.dataScope.scopeGrants)
Report ($adminGrants.Count -eq 4) 'admin has one grant per seeded data type' "count: $($adminGrants.Count)"
Report ((@($adminGrants | ForEach-Object { $_.dataType }) -join ',') -eq 'FENCE,MAC,PLATE,FACE') 'admin grants are ordered by data type' "got: $((@($adminGrants | ForEach-Object { $_.dataType }) -join ','))"
Report (@($adminGrants | Where-Object { -not $_.siteScope.allSites }).Count -eq 0) 'every admin grant carries allSites' ''
Report (@($adminGrants | Where-Object { @($_.siteScope.siteIds).Count -ne 0 }).Count -eq 0) 'admin allSites grants carry siteIds=[]' ''
Report (@($adminGrants | Where-Object { $_.ownOnly }).Count -eq 0) 'admin grants are not ownOnly' ''
$grantShapeOk = @($adminGrants | Where-Object { (@($_.PSObject.Properties.Name) -join ',') -ne 'dataType,siteScope,ownOnly' }).Count -eq 0
Report $grantShapeOk 'each grant has exactly dataType/siteScope/ownOnly' 'contract additionalProperties=false'

Report ($data.siteScope.allSites -eq $true) 'siteScope.allSites is true' "got: $($data.siteScope.allSites)"
Report (@($data.siteScope.siteIds).Count -eq 0) 'siteScope.siteIds is [] when allSites' ''

Report ($data.session.userId -eq $data.user.userId) 'session.userId equals user.userId' ''
Report ($data.session.idleExpiresAt -match '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$') 'session.idleExpiresAt is ISO8601 UTC (Z)' "got: $($data.session.idleExpiresAt)"
Report ($data.session.absoluteExpiresAt -match '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$') 'session.absoluteExpiresAt is ISO8601 UTC (Z)' "got: $($data.session.absoluteExpiresAt)"
if ($admin.Json) {
    # Since A02 a normal authenticated request pushes the idle deadline forward, so this call
    # legitimately reports a later value than the login response. What must still hold: it never
    # goes backwards, and it never passes the absolute deadline.
    $meIdle = [datetimeoffset]::Parse($data.session.idleExpiresAt)
    $loginIdle = [datetimeoffset]::Parse($admin.Json.data.idleExpiresAt)
    $meAbsolute = [datetimeoffset]::Parse($data.session.absoluteExpiresAt)
    Report ($meIdle -ge $loginIdle) 'session.idleExpiresAt is not earlier than the login response' "me: $($data.session.idleExpiresAt) login: $($admin.Json.data.idleExpiresAt)"
    Report ($meIdle -le $meAbsolute) 'session.idleExpiresAt does not pass absoluteExpiresAt' "idle: $($data.session.idleExpiresAt) absolute: $($data.session.absoluteExpiresAt)"
    Report ($data.session.absoluteExpiresAt -eq $admin.Json.data.absoluteExpiresAt) 'session.absoluteExpiresAt equals the login response' ''
}

# ---------------------------------------------------------------- 3) operator
Show-Step '3) operator /me: narrowed data types and ownOnly'
$operator = Login-As $OperatorLogin $SeedPassword
Report ($operator.Response.Code -eq 200) 'operator can log in' "HTTP $($operator.Response.Code)"
$opMe = Get-Me $operator.Sid
Report ($opMe.Code -eq 200) 'operator GET /me -> 200' "HTTP $($opMe.Code)"
$opJson = ConvertTo-JsonSafe $opMe.Body
if ($opJson) {
    $opPerms = @($opJson.data.permissionCodes)
    Report ($opPerms.Count -eq 13) 'OPERATOR carries only the 13 seeded read permissions' "count: $($opPerms.Count)"
    Report (-not ($opPerms -contains 'capture:decrypt')) 'operator lacks capture:decrypt' ''
    Report (-not ($opPerms -contains 'role:grant')) 'operator lacks role:grant' ''
    Report ($opPerms -contains 'face:search') 'operator still has face:search' ''
    Report ($opJson.data.dataScope.ownOnly -eq $true) 'operator ownOnly is true' "got: $($opJson.data.dataScope.ownOnly)"
    Report ((@($opJson.data.dataScope.dataTypes) -join ',') -eq 'FENCE,PLATE') 'operator data types narrowed to FENCE,PLATE' "got: $((@($opJson.data.dataScope.dataTypes) -join ','))"
    Report ($opJson.data.dataScope.allSites -eq $true) 'operator allSites is true (roles carry all sites)' ''
    Report (@($opJson.data.roleIds).Count -eq 1) 'operator has exactly one role' ''
    $opGrants = @($opJson.data.dataScope.scopeGrants)
    Report ($opGrants.Count -eq 2) 'operator has two grants (FENCE, PLATE)' "count: $($opGrants.Count)"
    Report ((@($opGrants | ForEach-Object { $_.dataType }) -join ',') -eq 'FENCE,PLATE') 'operator grant data types match the legacy projection' "got: $((@($opGrants | ForEach-Object { $_.dataType }) -join ','))"
    Report (@($opGrants | Where-Object { -not $_.ownOnly }).Count -eq 0) 'every operator grant is ownOnly (the flag stays per tuple)' ''
    Report (@($opGrants | Where-Object { -not $_.siteScope.allSites }).Count -eq 0) 'operator grants are allSites' ''
    if (@($adminRoles).Count -eq 1) {
        Report ($opJson.data.roleIds[0] -ne $adminRoles[0]) 'operator role differs from admin role' "op: $($opJson.data.roleIds[0])"
    }
}

# ---------------------------------------------------------------- 4) classified
Show-Step '4) classified /me: operator plus decrypt/audit, four data types'
$classified = Login-As $ClassifiedLogin $SeedPassword
$clMe = Get-Me $classified.Sid
Report ($clMe.Code -eq 200) 'classified GET /me -> 200' "HTTP $($clMe.Code)"
$clJson = ConvertTo-JsonSafe $clMe.Body
if ($clJson) {
    $clPerms = @($clJson.data.permissionCodes)
    Report ($clPerms.Count -eq 15) 'CLASSIFIED carries 15 permission codes' "count: $($clPerms.Count)"
    Report ($clPerms -contains 'capture:decrypt') 'classified has capture:decrypt (HIGH risk)' ''
    Report ($clPerms -contains 'audit:read') 'classified has audit:read' ''
    Report ((@($clJson.data.dataScope.dataTypes) -join ',') -eq 'FENCE,MAC,PLATE,FACE') 'classified sees all four data types' "got: $((@($clJson.data.dataScope.dataTypes) -join ','))"
    Report ($clJson.data.dataScope.ownOnly -eq $false) 'classified ownOnly is false' ''
    $clGrants = @($clJson.data.dataScope.scopeGrants)
    Report ($clGrants.Count -eq 4) 'classified has four grants' "count: $($clGrants.Count)"
    Report ((@($clGrants | ForEach-Object { $_.dataType }) -join ',') -eq 'FENCE,MAC,PLATE,FACE') 'classified grant data types cover all four' ''
    Report (@($clGrants | Where-Object { $_.ownOnly }).Count -eq 0) 'classified grants are not ownOnly' ''
}

# ---------------------------------------------------------------- 5) database
Show-Step '5) Persistence and session lifecycle (database)'
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

    $adminId = Query-Scalar "SELECT id FROM auth.app_user WHERE login_name = '$AdminLogin';"
    Report ([bool]$adminId) 'seeded admin account exists (dev-seed enabled)' "id: $adminId"

    # The strongest cross-check: what the database says the role actually grants.
    $dbPermCount = Query-Scalar @"
SELECT count(*) FROM auth.role_permission rp
  JOIN auth.user_role ur ON ur.role_id = rp.role_id
  JOIN auth.app_user u ON u.id = ur.user_id
 WHERE u.login_name = '$AdminLogin';
"@
    Report ($dbPermCount -eq '58') 'admin role_permission rows == 58 in the database' "count: $dbPermCount"

    $sessionRow = Query-Scalar "SELECT count(*) FROM auth.user_session WHERE sid_hash = sha256(convert_to('$($admin.Sid)','UTF8'));"
    Report ($sessionRow -eq '1') 'the SID we hold maps to exactly one auth.user_session row' "count: $sessionRow"

    # Compare by epoch seconds instead of formatting timestamps in SQL: both sides are
    # truncated to whole seconds, so an exact match is expected and no timezone/format
    # translation can mask a real drift.
    $sessionEpochs = Query-Scalar "SELECT extract(epoch FROM idle_expires_at)::bigint || '|' || extract(epoch FROM absolute_expires_at)::bigint FROM auth.user_session WHERE sid_hash = sha256(convert_to('$($admin.Sid)','UTF8'));"
    if ($meJson -and $sessionEpochs) {
        $expectedEpochs = '{0}|{1}' -f ([datetimeoffset]::Parse($data.session.idleExpiresAt)).ToUnixTimeSeconds(),
                                    ([datetimeoffset]::Parse($data.session.absoluteExpiresAt)).ToUnixTimeSeconds()
        Report ($sessionEpochs -eq $expectedEpochs) '/me session times equal the stored row (no drift)' "db: $sessionEpochs api: $expectedEpochs"
    }

    # --- idle timeout: push idle_expires_at into the past, /me must reject with SESSION_EXPIRED
    Query-Scalar "UPDATE auth.user_session SET idle_expires_at = now() - interval '1 minute' WHERE sid_hash = sha256(convert_to('$($admin.Sid)','UTF8'));" | Out-Null
    $idleMe = Get-Me $admin.Sid
    $idleJson = ConvertTo-JsonSafe $idleMe.Body
    Report ($idleMe.Code -eq 401) 'idle-expired session -> 401' "HTTP $($idleMe.Code)"
    Report ($idleJson.code -eq 'AUTH_SESSION_EXPIRED') 'idle-expired session uses AUTH_SESSION_EXPIRED' "code: $($idleJson.code)"
    Query-Scalar "UPDATE auth.user_session SET idle_expires_at = now() + interval '30 minutes' WHERE sid_hash = sha256(convert_to('$($admin.Sid)','UTF8'));" | Out-Null
    $restored = Get-Me $admin.Sid
    Report ($restored.Code -eq 200) 'restoring idle_expires_at makes /me work again' "HTTP $($restored.Code)"

    # --- revoked: revoked_at set means the session is dead even if not expired
    Query-Scalar "UPDATE auth.user_session SET revoked_at = now() WHERE sid_hash = sha256(convert_to('$($admin.Sid)','UTF8'));" | Out-Null
    $revokedMe = Get-Me $admin.Sid
    $revokedJson = ConvertTo-JsonSafe $revokedMe.Body
    Report ($revokedMe.Code -eq 401) 'revoked session -> 401' "HTTP $($revokedMe.Code)"
    Report ($revokedJson.code -eq 'AUTH_SESSION_EXPIRED') 'revoked session uses AUTH_SESSION_EXPIRED' "code: $($revokedJson.code)"
    Query-Scalar "UPDATE auth.user_session SET revoked_at = NULL WHERE sid_hash = sha256(convert_to('$($admin.Sid)','UTF8'));" | Out-Null

    # --- disabled account: an existing session must stop working immediately
    $opId = Query-Scalar "SELECT id FROM auth.app_user WHERE login_name = '$OperatorLogin';"
    if ($opId) {
        Query-Scalar "UPDATE auth.app_user SET status = 'DISABLED' WHERE id = '$opId';" | Out-Null
        $disabledMe = Get-Me $operator.Sid
        $disabledJson = ConvertTo-JsonSafe $disabledMe.Body
        Report ($disabledMe.Code -eq 401) 'disabled account with a live session -> 401' "HTTP $($disabledMe.Code)"
        Report ($disabledJson.code -eq 'AUTH_SESSION_EXPIRED') 'disabled account uses AUTH_SESSION_EXPIRED (so the UI drops the login state)' "code: $($disabledJson.code)"
        Query-Scalar "UPDATE auth.app_user SET status = 'ACTIVE' WHERE id = '$opId';" | Out-Null
    }

    # --- menu projection: a menu requiring a permission the user lacks must be filtered out
    $opRoleId = Query-Scalar @"
SELECT r.id FROM auth.role r
  JOIN auth.user_role ur ON ur.role_id = r.id
  JOIN auth.app_user u ON u.id = ur.user_id
 WHERE u.login_name = '$OperatorLogin';
"@
    if ($opRoleId) {
        $suffix = [guid]::NewGuid().ToString('N').Substring(0, 8)
        # menu-a: no required permission   -> visible
        # menu-b: face:search (operator has) -> visible
        # menu-c: model:write (operator lacks) -> filtered out
        $menuA = Query-Scalar "INSERT INTO auth.menu (id, code, label, route, sort_order) VALUES (gen_random_uuid(), 'probe.open.$suffix', 'Probe Open', '/probe/open', 10) RETURNING id;"
        $menuB = Query-Scalar "INSERT INTO auth.menu (id, code, label, route, sort_order, required_permission_code) VALUES (gen_random_uuid(), 'probe.perm.$suffix', 'Probe Perm', '/probe/perm', 20, 'face:search') RETURNING id;"
        $menuC = Query-Scalar "INSERT INTO auth.menu (id, code, label, route, sort_order, required_permission_code) VALUES (gen_random_uuid(), 'probe.deny.$suffix', 'Probe Deny', '/probe/deny', 30, 'model:write') RETURNING id;"
        try {
            # RETURNING output includes the "INSERT 0 1" command tag, so use -match not -eq.
            Query-Scalar "INSERT INTO auth.role_menu (role_id, menu_id) SELECT '$opRoleId', id FROM auth.menu WHERE code LIKE 'probe.%.$suffix';" | Out-Null
            $menuMe = Get-Me $operator.Sid
            $menuJson = ConvertTo-JsonSafe $menuMe.Body
            Report ($menuMe.Code -eq 200) 'operator GET /me with menus -> 200' "HTTP $($menuMe.Code)"
            if ($menuJson) {
                $codes = @($menuJson.data.menus | ForEach-Object { $_.code })
                Report ($codes -contains "probe.open.$suffix") 'menu without required permission is visible' "codes: $($codes -join ',')"
                Report ($codes -contains "probe.perm.$suffix") 'menu whose permission the user holds is visible' ''
                Report (-not ($codes -contains "probe.deny.$suffix")) 'menu requiring a permission the user lacks is filtered out' ''
                Report ($codes.Count -eq 2) 'exactly the two visible probe menus came back' "count: $($codes.Count)"
                $one = @($menuJson.data.menus | Where-Object { $_.code -eq "probe.open.$suffix" })
                if ($one.Count -eq 1) {
                    $m0 = $one[0]
                    Report ($m0.menuId -match '^[0-9a-fA-F-]{36}$') 'menu.menuId is a uuid' ''
                    Report ($m0.label -eq 'Probe Open') 'menu.label round-trips' "got: $($m0.label)"
                    Report ($m0.route -eq '/probe/open') 'menu.route round-trips' "got: $($m0.route)"
                    Report ($m0.order -eq 10) 'menu.order maps from sort_order' "got: $($m0.order)"
                    Report ($null -eq $m0.requiredPermission) 'menu without required permission omits the property (not null)' "props: $(@($m0.PSObject.Properties.Name) -join ',')"
                }
            }
        } finally {
            Query-Scalar "DELETE FROM auth.role_menu WHERE menu_id IN (SELECT id FROM auth.menu WHERE code LIKE 'probe.%.$suffix');" | Out-Null
            Query-Scalar "DELETE FROM auth.menu WHERE code LIKE 'probe.%.$suffix';" | Out-Null
        }
        $clean = Query-Scalar "SELECT count(*) FROM auth.menu WHERE code LIKE 'probe.%.$suffix';"
        Report ($clean -eq '0') 'probe menus cleaned up' "remaining: $clean"
    }
}

# ---------------------------------------------------------------- 6) contract guards
Show-Step '6) Contract guards'
# The write gate (common/CsrfFilter) runs before routing, so an untokened POST is 403 even
# though /me is GET-only. Both layers are asserted: 403 proves the gate wins over routing,
# 405 proves the method mismatch is still handled once the request is trusted.
$pair = Get-CsrfPair
$postMeUntokened = Invoke-Curl @('--max-time', '10', '-X', 'POST', "$BaseUrl/me", '-b', "SID=$($admin.Sid)")
Report ($postMeUntokened.Code -eq 403) 'POST /me without a CSRF pair -> 403 (gate before routing)' "HTTP $($postMeUntokened.Code)"
Report ($postMeUntokened.Body -match '"code"\s*:\s*"AUTH_CSRF_INVALID"') 'gate rejection uses AUTH_CSRF_INVALID' "body: $($postMeUntokened.Body.Trim())"

$postMe = Invoke-Curl @(
    '--max-time', '10', '-X', 'POST', "$BaseUrl/me",
    '-b', "SID=$($admin.Sid); CSRFID=$($pair.Cookie)",
    '-H', "X-CSRF-Token: $($pair.Token)")
$postMeJson = ConvertTo-JsonSafe $postMe.Body
Report ($postMe.Code -eq 405) 'trusted POST /me -> 405' "HTTP $($postMe.Code)"
if ($postMeJson) {
    Report ($postMeJson.code -eq 'METHOD_NOT_ALLOWED') 'POST /me uses METHOD_NOT_ALLOWED' "code: $($postMeJson.code)"
    Report ($null -eq $postMeJson.data) 'METHOD_NOT_ALLOWED body is the contract envelope' "data: $($postMeJson.data)"
} else {
    Report $false 'METHOD_NOT_ALLOWED body is the contract envelope, not plain text' "body: $($postMe.Body)"
}

$withQuery = Get-Me $admin.Sid @('-G', '--data-urlencode', 'unused=1')
Report ($withQuery.Code -eq 200) 'unknown query parameter is ignored (no 400)' "HTTP $($withQuery.Code)"

# ---------------------------------------------------------------- summary
Write-Host ''
Write-Host ("passed: {0}   failed: {1}" -f $script:passed, $script:failed) -ForegroundColor $(if ($script:failed -eq 0) { 'Green' } else { 'Red' })
if ($script:failed -ne 0) { exit 1 }
exit 0
