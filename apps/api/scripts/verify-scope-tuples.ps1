#requires -Version 5.1
<#
    verify-scope-tuples.ps1 - A01 regression: /me dataScope.scopeGrants is a tuple union.

    The dev seed gives every role data_all_sites=true, so no seeded account can show the
    cross-product bug. This script therefore builds a throw-away fixture directly in the
    database: one region, two sites A/B, two roles (PLATE only at A, FACE only at B) and a
    user that carries both roles. With the pre-A01 implementation the /me legacy fields
    would merge to dataTypes={PLATE,FACE} and siteIds={A,B}, which reads as PLATE@B and
    FACE@A - two grants nobody made. With the tuple implementation /me must return exactly
    two tuples and no cross-over.

    Requires: API running with the db profile, psql on PATH, DB credentials, seed password
    (the fixture user reuses the seeded admin password hash).

        .\scripts\verify-scope-tuples.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    Everything it inserts is deleted in the finally block; it never touches seeded rows.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the file has a
    UTF-8 BOM, so non-ASCII literals silently never match.
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

$SiteA = '11111111-1111-1111-1111-111111111111'
$SiteB = '22222222-2222-2222-2222-222222222222'
$Role1 = 'aaaaaaaa-0000-0000-0000-000000000001'
$Role2 = 'aaaaaaaa-0000-0000-0000-000000000002'
$UserId = 'bbbbbbbb-0000-0000-0000-000000000001'
$RegionCode = 'A01-REGION'
$LoginName = 'a01.tuple'

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

function ConvertTo-JsonSafe($text) {
    try { return ($text | ConvertFrom-Json) } catch { return $null }
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

function Get-CookieValue($headers, [string]$name) {
    foreach ($c in [regex]::Matches($headers, '(?im)^\s*Set-Cookie\s*:\s*(.+?)\s*$')) {
        $m = [regex]::Match($c.Groups[1].Value, "^$([regex]::Escape($name))=([^;]*)")
        if ($m.Success) { return $m.Groups[1].Value }
    }
    return $null
}

# Login is gated by the synchronizer token: the CSRFID cookie and the matching
# X-CSRF-Token header must both be present (see verify-login.ps1 section 1).
function New-LoginResponse([string]$loginName, [string]$password) {
    $csrf = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
    $csrfJson = ConvertTo-JsonSafe $csrf.Body
    $token = $null
    if ($csrfJson) { $token = $csrfJson.data.token }
    $cookie = Get-CookieValue $csrf.Headers 'CSRFID'
    $bodyFile = [System.IO.Path]::Combine($env:TEMP, ('a01-' + [guid]::NewGuid().ToString('N') + '.json'))
    [System.IO.File]::WriteAllText($bodyFile, (@{ loginName = $loginName; password = $password } | ConvertTo-Json -Compress), (New-Object System.Text.UTF8Encoding($false)))
    try {
        return Invoke-Curl @('--max-time', '10', '-X', 'POST', '-H', 'Content-Type: application/json',
                             '-H', "X-CSRF-Token: $token", '-b', "CSRFID=$cookie",
                             '--data-binary', "@$bodyFile", "$BaseUrl/auth/login")
    } finally {
        Remove-Item $bodyFile -ErrorAction SilentlyContinue
    }
}

$psql = Get-Command psql.exe -ErrorAction SilentlyContinue
if (-not $psql) { $psql = Get-Command psql -ErrorAction SilentlyContinue }
if (-not $psql -or -not $DB_PASSWORD) {
    Write-Host "  [SKIP] psql.exe or DB_PASSWORD missing; tuple fixture cannot be built" -ForegroundColor Yellow
    exit 0
}

if ($DB_URL -notmatch '^jdbc:postgresql://([^:/]+):(\d+)/(.+)$') {
    Write-Host "  [FAIL] DB_URL is not a jdbc:postgresql URL: $DB_URL" -ForegroundColor Red
    exit 2
}
$dbHost = $Matches[1]
$dbPort = $Matches[2]
$dbName = $Matches[3]

function Invoke-Sql([string]$sql) {
    $env:PGPASSWORD = $DB_PASSWORD
    $out = & $psql.Source -h $dbHost -p $dbPort -U $DB_USER -d $dbName -v ON_ERROR_STOP=1 -tA -c $sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $($out -join ' ')" }
    return ($out | Out-String).Trim()
}

function Remove-Fixture {
    $teardown = @"
BEGIN;
DELETE FROM auth.csrf_session WHERE session_id IN (SELECT id FROM auth.user_session WHERE user_id = '$UserId');
DELETE FROM auth.user_session WHERE user_id = '$UserId';
DELETE FROM auth.user_role WHERE user_id = '$UserId';
DELETE FROM auth.app_user WHERE id = '$UserId';
DELETE FROM auth.role_data_type WHERE role_id IN ('$Role1','$Role2');
DELETE FROM auth.role_site WHERE role_id IN ('$Role1','$Role2');
DELETE FROM auth.role WHERE id IN ('$Role1','$Role2');
DELETE FROM core.site WHERE id IN ('$SiteA','$SiteB');
DELETE FROM core.region WHERE region_code = '$RegionCode';
COMMIT;
"@
    try { Invoke-Sql $teardown | Out-Null } catch { Write-Host "  [WARN] teardown failed: $($_.Exception.Message)" -ForegroundColor Yellow }
}

Show-Step '1) build the two-role fixture (PLATE@A + FACE@B)'
Remove-Fixture
$setup = @"
BEGIN;
INSERT INTO core.region (region_code, name, boundary_version)
VALUES ('$RegionCode', 'A01 fixture region', 'v1');
INSERT INTO core.site (id, site_code, name, region_code)
VALUES ('$SiteA', 'A01-SITE-A', 'A01 site A', '$RegionCode'),
       ('$SiteB', 'A01-SITE-B', 'A01 site B', '$RegionCode');
INSERT INTO auth.role (id, role_code, name, management_all_sites, data_all_sites, own_only)
VALUES ('$Role1', 'A01_PLATE_A', 'A01 plate at A', false, false, false),
       ('$Role2', 'A01_FACE_B',  'A01 face at B',  false, false, false);
INSERT INTO auth.role_site (role_id, site_id, scope_kind)
VALUES ('$Role1', '$SiteA', 'DATA'),
       ('$Role2', '$SiteB', 'DATA');
INSERT INTO auth.role_data_type (role_id, data_type)
VALUES ('$Role1', 'PLATE'),
       ('$Role2', 'FACE');
INSERT INTO auth.app_user (id, user_code, login_name, display_name, password_hash, status)
SELECT '$UserId', 'A01-USER', '$LoginName', 'A01 tuple user', u.password_hash, 'ACTIVE'
  FROM auth.app_user u WHERE u.login_name = '$SeedLogin';
INSERT INTO auth.user_role (user_id, role_id) VALUES ('$UserId', '$Role1'), ('$UserId', '$Role2');
COMMIT;
"@
Invoke-Sql $setup | Out-Null
Report ((Invoke-Sql "SELECT count(*) FROM auth.user_role WHERE user_id = '$UserId';") -eq '2') 'fixture user carries two roles' ''

try {
    Show-Step '2) login as the fixture user and read /me'
    $login = New-LoginResponse $LoginName $SeedPassword
    $sid = Get-CookieValue $login.Headers 'SID'
    Report ($login.Code -eq 200) 'fixture user can log in' "HTTP $($login.Code) body: $($login.Body)"
    if (-not $sid) {
        Report $false 'login returned a SID cookie' 'no SID; remaining checks skipped'
    } else {
        $meRaw = & curl.exe -s --max-time 10 -H "Cookie: SID=$sid" "$BaseUrl/me" 2>&1
        $me = ConvertTo-JsonSafe (($meRaw | Out-String))
        if (-not $me) {
            Report $false '/me returned JSON' "body: $($meRaw | Out-String)"
        } else {
            $grants = @($me.data.dataScope.scopeGrants)
            Show-Step '3) scopeGrants is the tuple union, not the cross product'
            Report ($grants.Count -eq 2) 'exactly two grants (PLATE@A, FACE@B)' "count: $($grants.Count)"
            $plate = @($grants | Where-Object { $_.dataType -eq 'PLATE' })
            $face  = @($grants | Where-Object { $_.dataType -eq 'FACE' })
            Report ($plate.Count -eq 1) 'one PLATE grant' "count: $($plate.Count)"
            Report ($face.Count -eq 1) 'one FACE grant' "count: $($face.Count)"
            if ($plate.Count -eq 1) {
                Report (@($plate[0].siteScope.siteIds) -join ',' -eq $SiteA) 'PLATE grant is scoped to site A only' "siteIds: $(@($plate[0].siteScope.siteIds) -join ',')"
                Report ($plate[0].siteScope.allSites -eq $false) 'PLATE grant is not allSites' ''
            }
            if ($face.Count -eq 1) {
                Report (@($face[0].siteScope.siteIds) -join ',' -eq $SiteB) 'FACE grant is scoped to site B only' "siteIds: $(@($face[0].siteScope.siteIds) -join ',')"
            }
            $crossed = @($grants | Where-Object {
                ($_.dataType -eq 'PLATE' -and (@($_.siteScope.siteIds) -contains $SiteB)) -or
                ($_.dataType -eq 'FACE'  -and (@($_.siteScope.siteIds) -contains $SiteA))
            })
            Report ($crossed.Count -eq 0) 'no PLATE@B and no FACE@A (the A01 cross-product bug)' "crossed: $($crossed.Count)"
            $shapeOk = @($grants | Where-Object {
                (@($_.PSObject.Properties.Name) -join ',') -ne 'dataType,siteScope,ownOnly'
            }).Count -eq 0
            Report $shapeOk 'each grant has exactly dataType/siteScope/ownOnly' 'contract additionalProperties=false'
            Report ($me.data.dataScope.allSites -eq $false) 'legacy allSites projection is false (union is not all sites)' ''
            Report ((@($me.data.dataScope.dataTypes) -join ',') -eq 'PLATE,FACE') 'legacy dataTypes projection lists PLATE,FACE in enum order' "got: $((@($me.data.dataScope.dataTypes) -join ','))"
            Report ($me.data.dataScope.ownOnly -eq $false) 'legacy ownOnly projection is false' ''
            Report (@($me.data.dataScope.siteIds).Count -eq 2) 'legacy siteIds projection unions both sites (UI hint only)' "count: $(@($me.data.dataScope.siteIds).Count)"
        }
    }
} finally {
    Show-Step '4) teardown'
    Remove-Fixture
    $left = Invoke-Sql "SELECT count(*) FROM auth.app_user WHERE login_name = '$LoginName';"
    Report ($left -eq '0') 'fixture rows removed' "remaining users: $left"
}

Write-Host ""
if ($script:failed -gt 0) {
    Write-Host ("RESULT: {0} passed, {1} failed" -f $script:passed, $script:failed) -ForegroundColor Red
    exit 1
}
Write-Host ("RESULT: {0} passed, 0 failed" -f $script:passed) -ForegroundColor Green
exit 0
