#requires -Version 5.1
<#
Verifies W2 rows 12/13: create, read, list and decide sensitive action approvals.

Prerequisites:
  - API started with the db profile and xx.approval.payload-key configured
    (application-db.yml carries a local-only development key);
  - dev seed created demo.admin / demo.operator / demo.classified with one password;
  - PostgreSQL reachable (used to expire an approval and to grant capture:decrypt).

Usage:
  powershell -File apps/api/scripts/verify-approvals.ps1 -SeedPassword 'DevSeed2026!' `
    -DB_URL 'jdbc:postgresql://127.0.0.1:55435/xxplatform' -DB_USER postgres

The file is ASCII-only: PowerShell 5.1 reads a BOM-less .ps1 with the ANSI code page.
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string]$DB_URL = 'jdbc:postgresql://127.0.0.1:5432/xxplatform',
    [string]$DB_USER = 'xxplatform_owner',
    [string]$DB_PASSWORD = $env:DB_PASSWORD,
    [string]$SeedPassword = $env:XX_AUTH_DEVSEED_PASSWORD,
    [string]$AdminLogin = 'demo.admin',
    [string]$OperatorLogin = 'demo.operator',
    [string]$ClassifiedLogin = 'demo.classified'
)

$ErrorActionPreference = 'Stop'
$script:passed = 0
$script:failed = 0

function Show-Step([string]$text) {
    Write-Host ""
    Write-Host "== $text" -ForegroundColor Cyan
}

function Report([bool]$ok, [string]$name, [string]$detail) {
    if ($ok) {
        $script:passed++
        Write-Host ("  PASS  " + $name)
    } else {
        $script:failed++
        Write-Host ("  FAIL  " + $name + "  -- " + $detail) -ForegroundColor Red
    }
}

function Invoke-Curl([string[]]$curlArgs) {
    $output = & curl.exe -s -i @curlArgs 2>&1
    $raw = ($output | Out-String)
    $status = 0
    # A large body first yields 100 Continue, so the real status line is the last HTTP/* line.
    $statusLine = ($raw -split "\r?\n" | Where-Object { $_ -match '^HTTP/' } | Select-Object -Last 1)
    if ($statusLine -and $statusLine -match 'HTTP/\d(?:\.\d)?\s+(\d{3})') {
        $status = [int]$Matches[1]
    }
    $parts = $raw -split "\r?\n\r?\n", 2
    $headers = if ($parts.Count -gt 1) { $parts[0] } else { '' }
    $body = if ($parts.Count -gt 1) { $parts[1] } else { $raw }
    return [pscustomobject]@{ Code = $status; Headers = $headers; Body = $body; Raw = $raw }
}

function Get-Json($response) {
    try {
        return $response.Body | ConvertFrom-Json
    } catch {
        $start = $response.Body.IndexOf('{')
        if ($start -ge 0) {
            return $response.Body.Substring($start) | ConvertFrom-Json
        }
        return $null
    }
}

function Get-EnvelopeCode($response) {
    $json = Get-Json $response
    if ($null -eq $json) { return $null }
    return $json.code
}

function Get-CookieValue($response, [string]$name) {
    $cookies = [regex]::Matches($response.Headers, '(?im)^\s*Set-Cookie\s*:\s*(.+?)\s*$') |
        ForEach-Object { $_.Groups[1].Value }
    foreach ($cookie in $cookies) {
        $match = [regex]::Match($cookie, '^' + [regex]::Escape($name) + '=([^;]*)')
        if ($match.Success) { return $match.Groups[1].Value }
    }
    return $null
}

function Get-CsrfPair {
    $response = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
    $cookie = Get-CookieValue $response 'CSRFID'
    $json = Get-Json $response
    $token = $null
    if ($null -ne $json -and $null -ne $json.data) { $token = $json.data.token }
    return [pscustomobject]@{ Cookie = $cookie; Token = $token }
}

function Write-JsonBody([string]$json) {
    $file = Join-Path $env:TEMP ("xx-approval-" + [guid]::NewGuid().ToString('N') + ".json")
    [System.IO.File]::WriteAllText($file, $json, (New-Object System.Text.UTF8Encoding($false)))
    return $file
}

function Post-Json($path, [string]$json, [string]$cookie, [string]$token, [string]$idempotencyKey) {
    $args = @('--max-time', '20', '-X', 'POST', "$BaseUrl$path",
        '-H', 'Content-Type: application/json')
    if ($cookie) { $args += @('-H', "Cookie: $cookie") }
    if ($token) { $args += @('-H', "X-CSRF-Token: $token") }
    if ($idempotencyKey) { $args += @('-H', "Idempotency-Key: $idempotencyKey") }
    $file = Write-JsonBody $json
    $args += @('--data-binary', "@$file")
    try {
        return Invoke-Curl $args
    } finally {
        Remove-Item $file -ErrorAction SilentlyContinue
    }
}

function New-Session([string]$login) {
    $csrf = Get-CsrfPair
    $json = @{ loginName = $login; password = $SeedPassword } | ConvertTo-Json -Compress
    $response = Post-Json '/auth/login' $json "CSRFID=$($csrf.Cookie)" $csrf.Token $null
    $sid = Get-CookieValue $response 'SID'
    if ($null -eq $sid) {
        throw "log in failed for $login : HTTP $($response.Code) $($response.Body)"
    }
    # Login binds this pair to the new session; a freshly fetched pair would be unbound.
    return [pscustomobject]@{
        Sid  = $sid
        Csrf = $csrf
        User = (Get-Json $response).data
    }
}

function Session-Cookie($session) {
    return "SID=$($session.Sid); CSRFID=$($session.Csrf.Cookie)"
}

function Get-Api($session, [string]$path) {
    return Invoke-Curl @('--max-time', '20', '-H', "Cookie: $(Session-Cookie $session)", "$BaseUrl$path")
}

function Post-Api($session, [string]$path, [string]$json) {
    return Post-Json $path $json (Session-Cookie $session) $session.Csrf.Token $(New-IdempotencyKey 'post')
}

function New-IdempotencyKey([string]$suffix) {
    return ("verify-approvals-" + $suffix + "-" + [guid]::NewGuid().ToString('N').Substring(0, 12))
}

$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
$dbMatch = [regex]::Match($DB_URL, '//([^:/]+):(\d+)/(.+)$')
if (-not $dbMatch.Success) { throw "cannot parse DB_URL: $DB_URL" }
$dbHost = $dbMatch.Groups[1].Value
$dbPort = $dbMatch.Groups[2].Value
$dbName = $dbMatch.Groups[3].Value
$hasPsql = Test-Path $psql
if ($hasPsql -and $DB_PASSWORD) { $env:PGPASSWORD = $DB_PASSWORD }

function Invoke-Sql([string]$sql) {
    if (-not $hasPsql) { return $null }
    $out = $sql | & $psql -h $dbHost -p $dbPort -U $DB_USER -d $dbName -t -A -f - 2>&1
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $out" }
    return ($out | Out-String).Trim()
}

$proposalJson = '{"proposal":{"operationId":"createExport","requestBody":{"contextId":"00000000-0000-4000-8000-000000000001","format":"XLSX"}},"reason":"Synthetic export request used by the approval verification."}'
$approveJson = '{"decision":"APPROVE","reason":"Independent reviewer accepted this request.","currentPassword":"' + $SeedPassword + '"}'
$rejectJson = '{"decision":"REJECT","reason":"Purpose is too vague, please refine the request first.","currentPassword":"' + $SeedPassword + '"}'
$wrongPasswordJson = '{"decision":"APPROVE","reason":"A wrong password must never record a decision.","currentPassword":"definitely-wrong"}'

Show-Step '0) reachability'
$ping = Invoke-Curl @('-s', '-o', 'NUL', '-w', '%{http_code}', "$BaseUrl/health")
if ($ping.Raw.Trim() -ne '200') {
    Write-Host "API is not reachable at $BaseUrl" -ForegroundColor Red
    exit 1
}
Report $true 'API reachable' ''

Show-Step '1) auth gates'
$anonymous = Get-Api $null '/action-approvals'
Report ($anonymous.Code -eq 401) 'anonymous list is 401' "got $($anonymous.Code)"
$admin = New-Session $AdminLogin
$operator = New-Session $OperatorLogin
$classified = New-Session $ClassifiedLogin
Report ([bool]$admin.Sid) 'demo.admin logged in' ''
Report ([bool]$operator.Sid) 'demo.operator logged in' ''
Report ([bool]$classified.Sid) 'demo.classified logged in' ''

$noCsrf = Invoke-Curl @('--max-time', '20', '-X', 'POST', "$BaseUrl/action-approvals",
    '-H', "Cookie: SID=$($admin.Sid)", '-H', 'Content-Type: application/json',
    '-H', 'Idempotency-Key: verify-approvals-nocsrf-key', '--data-binary', $proposalJson)
Report ($noCsrf.Code -eq 403 -and (Get-EnvelopeCode $noCsrf) -eq 'AUTH_CSRF_INVALID') 'write without csrf pair is 403' "got $($noCsrf.Code) $(Get-EnvelopeCode $noCsrf)"

Show-Step '2) Idempotency-Key shape'
$missing = Post-Json '/action-approvals' $proposalJson (Session-Cookie $admin) $admin.Csrf.Token $null
Report ($missing.Code -eq 400 -and (Get-EnvelopeCode $missing) -eq 'VALIDATION_ERROR') 'missing Idempotency-Key is 400' "got $($missing.Code) $(Get-EnvelopeCode $missing)"
$short = Post-Json '/action-approvals' $proposalJson (Session-Cookie $admin) $admin.Csrf.Token 'short-key'
Report ($short.Code -eq 400 -and (Get-EnvelopeCode $short) -eq 'VALIDATION_ERROR') 'short Idempotency-Key is 400' "got $($short.Code)"

Show-Step '3) proposal validation'
$badOperation = '{"proposal":{"operationId":"dropDatabase"},"reason":"An unregistered operation must be rejected instead of stored."}'
$unknown = Post-Json '/action-approvals' $badOperation (Session-Cookie $admin) $admin.Csrf.Token (New-IdempotencyKey 'badop')
Report ($unknown.Code -eq 400 -and (Get-EnvelopeCode $unknown) -eq 'VALIDATION_ERROR') 'unknown operation is 400' "got $($unknown.Code)"
$unknownJson = Get-Json $unknown
Report ($null -ne $unknownJson -and $unknownJson.details[0].field -eq 'proposal') 'unknown operation points at proposal field' ''
$extraKey = '{"proposal":{"operationId":"createExport","requestBody":{"contextId":"x","format":"XLSX"},"extra":true},"reason":"Extra proposal keys must be rejected rather than silently ignored."}'
$badKey = Post-Json '/action-approvals' $extraKey (Session-Cookie $admin) $admin.Csrf.Token (New-IdempotencyKey 'badkey')
Report ($badKey.Code -eq 400) 'unknown proposal key is 400' "got $($badKey.Code)"

Show-Step '4) create, detail and list'
$create = Post-Json '/action-approvals' $proposalJson (Session-Cookie $admin) $admin.Csrf.Token (New-IdempotencyKey 'create')
$created = Get-Json $create
Report ($create.Code -eq 201) 'create returns 201' "got $($create.Code)"
Report ($null -ne $created -and $created.data.status -eq 'PENDING') 'new approval is PENDING' ''
Report ($null -ne $created -and $created.data.requiredApprovals -eq 1) 'createExport needs one approval' ''
$digest = ''
if ($null -ne $created) { $digest = [string]$created.data.requestDigest }
Report ($digest -match '^[0-9a-f]{64}$') 'request digest is 64 hex chars' $digest
$reviewItems = @()
if ($null -ne $created) { $reviewItems = @($created.data.reviewItems) }
Report ($reviewItems.Count -ge 1) 'review items are present' ''
$exportApprovalId = ''
if ($null -ne $created) { $exportApprovalId = [string]$created.data.approvalId }
Report (-not [string]::IsNullOrWhiteSpace($exportApprovalId)) 'approval id returned' ''

$detail = Get-Api $admin "/action-approvals/$exportApprovalId"
Report ($detail.Code -eq 200) 'requester reads detail' "got $($detail.Code)"
$detailByOperator = Get-Api $operator "/action-approvals/$exportApprovalId"
Report ($detailByOperator.Code -eq 200) 'reviewer with capture:export reads detail' "got $($detailByOperator.Code)"
$unknownDetail = Get-Api $admin '/action-approvals/00000000-0000-4000-8000-0000000000ff'
Report ($unknownDetail.Code -eq 404 -and (Get-EnvelopeCode $unknownDetail) -eq 'DATA_NOT_FOUND') 'unknown approval is 404' "got $($unknownDetail.Code)"
$page = Get-Api $admin '/action-approvals?pageSize=20'
Report ($page.Code -eq 200) 'list returns 200' "got $($page.Code)"
$pageItems = @()
if ($page.Code -eq 200) { $pageItems = @((Get-Json $page).data.items) }
Report ($pageItems.Count -ge 1) 'requester sees own approval in list' ''

Show-Step '5) self approval'
$self = Post-Json "/action-approvals/$exportApprovalId/decisions" $approveJson (Session-Cookie $admin) $admin.Csrf.Token (New-IdempotencyKey 'self')
Report ($self.Code -eq 403 -and (Get-EnvelopeCode $self) -eq 'PERMISSION_DENIED') 'requester cannot self approve' "got $($self.Code) $(Get-EnvelopeCode $self)"

Show-Step '6) current password check'
$wrong = Post-Json "/action-approvals/$exportApprovalId/decisions" $wrongPasswordJson (Session-Cookie $operator) $operator.Csrf.Token (New-IdempotencyKey 'wrongpw')
Report ($wrong.Code -eq 400 -and (Get-EnvelopeCode $wrong) -eq 'VALIDATION_ERROR') 'wrong password is 400' "got $($wrong.Code)"
$wrongJson = Get-Json $wrong
Report ($null -ne $wrongJson -and $wrongJson.details[0].field -eq 'currentPassword') 'wrong password points at currentPassword' ''
$stillPending = Get-Json (Get-Api $admin "/action-approvals/$exportApprovalId")
Report ($stillPending.data.status -eq 'PENDING' -and @($stillPending.data.decisions).Count -eq 0) 'wrong password records nothing' ''

Show-Step '7) reject'
$reject = Post-Json "/action-approvals/$exportApprovalId/decisions" $rejectJson (Session-Cookie $operator) $operator.Csrf.Token (New-IdempotencyKey 'reject')
$rejected = Get-Json $reject
Report ($reject.Code -eq 200 -and $rejected.data.status -eq 'REJECTED') 'reject ends the approval' "got $($reject.Code)"
Report (@($rejected.data.decisions).Count -eq 1) 'one decision recorded' ''
Report ($null -eq $rejected.data.executionGrantExpiresAt) 'rejected approval carries no grant' ''
$adminSeesReject = Get-Json (Get-Api $admin "/action-approvals/$exportApprovalId")
Report ($adminSeesReject.data.status -eq 'REJECTED') 'requester sees rejection' ''

Show-Step '8) approve'
$create2 = Post-Json '/action-approvals' $proposalJson (Session-Cookie $admin) $admin.Csrf.Token (New-IdempotencyKey 'create2')
$approval2 = [string](Get-Json $create2).data.approvalId
$approve = Post-Json "/action-approvals/$approval2/decisions" $approveJson (Session-Cookie $operator) $operator.Csrf.Token (New-IdempotencyKey 'approve')
$approved = Get-Json $approve
Report ($approve.Code -eq 200 -and $approved.data.status -eq 'APPROVED') 'approve grants the action' "got $($approve.Code)"
$grant = $approved.data.executionGrantExpiresAt
Report ($null -ne $grant) 'execution grant timestamp present' ''
if ($null -ne $grant) {
    $grantAt = [datetimeoffset]::Parse($grant)
    $expiresAt = [datetimeoffset]::Parse($approved.data.expiresAt)
    $now = [datetimeoffset]::UtcNow
    Report ($grantAt -le $expiresAt) 'grant never outlives the approval' ''
    Report ($grantAt -gt $now -and $grantAt -le $now.AddMinutes(11)) 'grant is about ten minutes' "$grant"
}

Show-Step '9) idempotent replay'
$replay = Post-Json "/action-approvals/$approval2/decisions" $approveJson (Session-Cookie $operator) $operator.Csrf.Token (New-IdempotencyKey 'replay')
$replayed = Get-Json $replay
Report ($replay.Code -eq 200 -and $replayed.data.status -eq 'APPROVED') 'same decision replays as 200' "got $($replay.Code)"
Report (@($replayed.data.decisions).Count -eq 1) 'replay records no second decision' ''
$different = '{"decision":"APPROVE","reason":"A different reason must be treated as a duplicate decision.","currentPassword":"' + $SeedPassword + '"}'
$conflict = Post-Json "/action-approvals/$approval2/decisions" $different (Session-Cookie $operator) $operator.Csrf.Token (New-IdempotencyKey 'dup')
Report ($conflict.Code -eq 409) 'different duplicate decision is 409' "got $($conflict.Code)"
Report (-not [string]::IsNullOrWhiteSpace((Get-Json $conflict).message)) 'conflict carries a message' ''

Show-Step '10) expiry'
$create3 = Post-Json '/action-approvals' $proposalJson (Session-Cookie $admin) $admin.Csrf.Token (New-IdempotencyKey 'create3')
$approval3 = [string](Get-Json $create3).data.approvalId
if ($hasPsql) {
    Invoke-Sql ("UPDATE operation.action_approval SET created_at = now() - interval '2 minutes', expires_at = now() - interval '1 minute' WHERE id = '" + $approval3 + "';") | Out-Null
    $expired = Post-Json "/action-approvals/$approval3/decisions" $approveJson (Session-Cookie $operator) $operator.Csrf.Token (New-IdempotencyKey 'expired')
    Report ($expired.Code -eq 409) 'expired approval cannot be decided' "got $($expired.Code)"
    $status = Invoke-Sql ("SELECT status FROM operation.action_approval WHERE id = '" + $approval3 + "';")
    Report ($status -eq 'EXPIRED') 'expired approval is marked EXPIRED' $status
} else {
    Report $true 'psql unavailable, expiry checks skipped' ''
}

Show-Step '11) two-person approval for decryption'
# The grant below is written into the database. Drop it first so a previous run
# cannot make the "reviewer without capture:decrypt" case pass silently.
if ($hasPsql) {
    Invoke-Sql "DELETE FROM auth.role_permission WHERE permission_code = 'capture:decrypt' AND role_id IN (SELECT id FROM auth.role WHERE role_code = 'OPERATOR');" | Out-Null
}
$decryptProposal = '{"proposal":{"operationId":"readDecryptionResult","pathParameters":{"requestId":"00000000-0000-4000-8000-000000000009"}},"reason":"Requesting decrypted output for a compliance review."}'
$decryptCreate = Post-Json '/action-approvals' $decryptProposal (Session-Cookie $admin) $admin.Csrf.Token (New-IdempotencyKey 'decrypt')
$decryptCreated = Get-Json $decryptCreate
Report ($decryptCreate.Code -eq 201 -and $decryptCreated.data.requiredApprovals -eq 2) 'decryption needs two approvals' "got $($decryptCreate.Code)"
$decryptId = [string]$decryptCreated.data.approvalId

$operatorCannotReview = Post-Json "/action-approvals/$decryptId/decisions" $approveJson (Session-Cookie $operator) $operator.Csrf.Token (New-IdempotencyKey 'nodecrypt')
Report ($operatorCannotReview.Code -eq 403) 'reviewer without capture:decrypt is 403' "got $($operatorCannotReview.Code)"
$operatorHidden = Get-Api $operator "/action-approvals/$decryptId"
Report ($operatorHidden.Code -eq 404) 'approval invisible without the operation permission' "got $($operatorHidden.Code)"

if ($hasPsql) {
    Invoke-Sql "INSERT INTO auth.role_permission (role_id, permission_code) SELECT r.id, 'capture:decrypt' FROM auth.role r WHERE r.role_code = 'OPERATOR' ON CONFLICT DO NOTHING;" | Out-Null
}
$firstJson = '{"decision":"APPROVE","reason":"First reviewer agrees to this decryption request.","currentPassword":"' + $SeedPassword + '"}'
$first = Post-Json "/action-approvals/$decryptId/decisions" $firstJson (Session-Cookie $operator) $operator.Csrf.Token (New-IdempotencyKey 'dec1')
$afterFirst = Get-Json $first
Report ($first.Code -eq 200 -and $afterFirst.data.status -eq 'PENDING') 'first approval keeps it pending' "got $($first.Code)"
Report ($null -eq $afterFirst.data.executionGrantExpiresAt) 'no grant before the second vote' ''
$secondJson = '{"decision":"APPROVE","reason":"Second reviewer agrees to this decryption request.","currentPassword":"' + $SeedPassword + '"}'
$second = Post-Json "/action-approvals/$decryptId/decisions" $secondJson (Session-Cookie $classified) $classified.Csrf.Token (New-IdempotencyKey 'dec2')
$afterSecond = Get-Json $second
Report ($second.Code -eq 200 -and $afterSecond.data.status -eq 'APPROVED') 'second approval grants decryption' "got $($second.Code)"
Report ($null -ne $afterSecond.data.executionGrantExpiresAt) 'decryption grant timestamp present' ''
if ($null -ne $afterSecond.data.executionGrantExpiresAt) {
    $decryptNow = [datetimeoffset]::UtcNow
    $decryptGrant = [datetimeoffset]::Parse($afterSecond.data.executionGrantExpiresAt)
    Report ($decryptGrant -le $decryptNow.AddMinutes(6)) 'decryption grant is about five minutes' "$decryptGrant"
}

Write-Host ""
Write-Host ("passed={0} failed={1}" -f $script:passed, $script:failed)
if ($script:failed -gt 0) { exit 1 }
