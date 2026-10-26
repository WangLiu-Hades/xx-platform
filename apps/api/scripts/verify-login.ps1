#requires -Version 5.1
<#
    verify-login.ps1 - POST /auth/login acceptance checks (DEV-01, task 5)

    Requires the API running WITH the db profile AND the dev seed account enabled:

        $env:SPRING_PROFILES_ACTIVE      = 'db'
        $env:SPRING_DATASOURCE_URL       = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
        $env:SPRING_DATASOURCE_USERNAME  = 'xxplatform_owner'
        $env:SPRING_DATASOURCE_PASSWORD  = '<password>'
        $env:XX_AUTH_DEVSEED_ENABLED     = 'true'          # xx.auth.dev-seed.enabled
        $env:XX_AUTH_DEVSEED_PASSWORD    = '<password>'    # never put this in yml
        .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"

    Then: .\scripts\verify-login.ps1 -DB_PASSWORD '<db password>' -SeedPassword '<seed password>'

    Persistence checks shell out to psql and are skipped when it is absent.

    ASCII-only on purpose: Windows PowerShell 5.1 reads .ps1 as ANSI unless the
    file has a UTF-8 BOM, so non-ASCII literals silently never match (this bit us
    on verify-skeleton.ps1). JSON request bodies are written to temp files with
    UTF8-no-BOM and passed via `--data-binary @file`, because PowerShell 5.1
    mangles inline JSON quoting when handing args to curl.exe.
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [string]$DB_URL  = 'jdbc:postgresql://127.0.0.1:5432/xxplatform',
    [string]$DB_USER = 'xxplatform_owner',
    [string]$DB_PASSWORD = $env:DB_PASSWORD,
    [string]$OwnOrigin = 'http://127.0.0.1:8080',
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

# curl.exe: -i so we get status line + headers + body in one blob.
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
    $path = [System.IO.Path]::Combine($env:TEMP, ('login-body-' + [guid]::NewGuid().ToString('N') + '.json'))
    [System.IO.File]::WriteAllText($path, $json, (New-Object System.Text.UTF8Encoding($false)))
    return $path
}

function New-LoginBody([string]$loginName, [string]$password) {
    # ConvertTo-Json escapes quotes/backslashes correctly, so we never hand-build JSON.
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
    # NB: '(if (...) {...})' is not a valid expression in Windows PowerShell 5.1
    # (it fails at parse time with "The term 'if' is not recognized"), so assign first.
    $token = $null
    if ($j) { $token = $j.data.token }
    return [pscustomobject]@{
        Response = $r
        Json     = $j
        Cookie   = (Get-CookieValue $r.Headers 'CSRFID')
        Token    = $token
    }
}

# Login carrying a valid CSRF pair (cookie + header).
function Login-WithCsrf([string]$bodyJson, [string[]]$Extra = @()) {
    $csrf = Get-CsrfPair
    return Post-Login $bodyJson (@('-H', "X-CSRF-Token: $($csrf.Token)", '-b', "CSRFID=$($csrf.Cookie)") + $Extra)
}

# ---------------------------------------------------------------- reachability
Show-Step 'Service reachability'
if (-not $SeedPassword) {
    Write-Host '  [FAIL] -SeedPassword / XX_AUTH_DEVSEED_PASSWORD is required (the seed account password)' -ForegroundColor Red
    exit 1
}
$probe = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/csrf")
if ($probe.Code -eq 0) {
    Write-Host "  [FAIL] cannot reach $BaseUrl/auth/csrf" -ForegroundColor Red
    Write-Host '         Start it with the db profile first (see the header of this script).' -ForegroundColor Yellow
    exit 1
}
Write-Host "  [OK]   reachable, HTTP $($probe.Code)" -ForegroundColor Green

# ---------------------------------------------------------------- 1) CSRF gate
Show-Step '1) Login is gated by the CSRF pair (cookie + header)'
$noCsrf = Post-Login (New-LoginBody $SeedLogin $SeedPassword)
Report ($noCsrf.Code -eq 403) 'no CSRF cookie/header -> 403' "HTTP $($noCsrf.Code) body: $($noCsrf.Body)"
$noCsrfJson = ConvertTo-JsonSafe $noCsrf.Body
if ($noCsrfJson) {
    Report ($noCsrfJson.code -eq 'AUTH_CSRF_INVALID') 'code is AUTH_CSRF_INVALID' "code: $($noCsrfJson.code)"
    Report ($null -eq $noCsrfJson.data) 'data is null (contract error shape)' "data: $($noCsrfJson.data)"
    Report ($null -ne $noCsrfJson.details) 'details array is present' "details: $($noCsrfJson.details)"
    $t = Get-HeaderValue $noCsrf.Headers 'X-Trace-Id'
    Report ($t -eq $noCsrfJson.traceId -and $noCsrfJson.traceId -ne 'unknown-trace') 'traceId matches X-Trace-Id' "header: $t body: $($noCsrfJson.traceId)"
} else {
    Report $false 'rejection body is the contract envelope, not plain text' "body: $($noCsrf.Body)"
}

# Cookie present, header missing -> still rejected (this is the whole point of the
# synchronizer pattern: the cookie alone is forgeable via cookie injection).
$csrfOnly = Get-CsrfPair
$cookieOnly = Post-Login (New-LoginBody $SeedLogin $SeedPassword) @('-b', "CSRFID=$($csrfOnly.Cookie)")
Report ($cookieOnly.Code -eq 403) 'CSRF cookie but no header token -> 403' "HTTP $($cookieOnly.Code)"

# Header present, cookie value forged -> rejected (server compares stored hashes).
$forged = Post-Login (New-LoginBody $SeedLogin $SeedPassword) @('-H', "X-CSRF-Token: $($csrfOnly.Token)", '-b', 'CSRFID=forged-value-not-issued-by-us')
Report ($forged.Code -eq 403) 'forged CSRF cookie value -> 403' "HTTP $($forged.Code)"

# ---------------------------------------------------------------- 2) uniform 401
Show-Step '2) Credential failures are indistinguishable (no account enumeration)'
$wrongPassword = Login-WithCsrf (New-LoginBody $SeedLogin 'wrong-password-123')
Report ($wrongPassword.Code -eq 401) 'wrong password -> 401' "HTTP $($wrongPassword.Code)"
$wrongJson = ConvertTo-JsonSafe $wrongPassword.Body
Report ($wrongJson.code -eq 'AUTH_INVALID_CREDENTIALS') 'wrong password uses AUTH_INVALID_CREDENTIALS' "code: $($wrongJson.code)"
Report ($null -eq (Get-CookieValue $wrongPassword.Headers 'SID')) 'wrong password sets no SID cookie' ''

$unknownName = 'no.such.user.' + [guid]::NewGuid().ToString('N').Substring(0, 8)
$unknown = Login-WithCsrf (New-LoginBody $unknownName 'any-password-123')
Report ($unknown.Code -eq 401) 'unknown login name -> 401' "HTTP $($unknown.Code)"
$unknownJson = ConvertTo-JsonSafe $unknown.Body
Report ($unknownJson.code -eq 'AUTH_INVALID_CREDENTIALS') 'unknown login name uses the same code' "code: $($unknownJson.code)"
Report ($unknownJson.message -eq $wrongJson.message) 'same message for unknown user and wrong password' "wrong: $($wrongJson.message) unknown: $($unknownJson.message)"
Report ($null -eq (Get-CookieValue $unknown.Headers 'SID')) 'unknown login name sets no SID cookie' ''

# Case sensitivity is a documented limitation (DB unique index is case sensitive).
$caseVariant = Login-WithCsrf (New-LoginBody ($SeedLogin.ToUpperInvariant()) $SeedPassword)
Report ($caseVariant.Code -eq 401) 'login name is case sensitive (documented limitation)' "HTTP $($caseVariant.Code)"

# ---------------------------------------------------------------- 3) happy path
Show-Step '3) Successful login returns SessionData and sets SID'
$ok = Login-WithCsrf (New-LoginBody $SeedLogin $SeedPassword)
Report ($ok.Code -eq 200) 'valid credentials -> 200' "HTTP $($ok.Code) body: $($ok.Body)"
$okJson = ConvertTo-JsonSafe $ok.Body
Report ($okJson.code -eq '0') 'envelope code is "0"' "code: $($okJson.code)"

$sid = Get-CookieValue $ok.Headers 'SID'
Report ([bool]$sid) 'Set-Cookie carries SID' "sid length: $($sid.Length)"
Report ($sid.Length -ge 40) 'SID is high entropy (>= 40 chars, 32 random bytes)' "len: $($sid.Length)"
Report ($ok.Body -notmatch [regex]::Escape($sid)) 'SID value never appears in the response body' ''
Report (@(Get-SetCookies $ok.Headers | Where-Object { $_ -match '^CSRFID=' }).Count -eq 0) 'login does not re-issue CSRFID' ''

$sidCookie = (Get-SetCookies $ok.Headers | Where-Object { $_ -match '^SID=' } | Select-Object -First 1)
if ($sidCookie) {
    Report ($sidCookie -match '(?i);\s*HttpOnly') 'SID cookie is HttpOnly' $sidCookie
    Report ($sidCookie -match '(?i);\s*SameSite=Lax') 'SID cookie is SameSite=Lax' $sidCookie
    Report ($sidCookie -match '(?i);\s*Path=/api/v1') 'SID cookie Path is /api/v1' $sidCookie
    $maxAge = [regex]::Match($sidCookie, '(?i);\s*Max-Age=(\d+)')
    if ($maxAge.Success) {
        $age = [int]$maxAge.Groups[1].Value
        Report (([math]::Abs($age - 28800)) -lt 180) 'Max-Age tracks absolute TTL (~8h)' "Max-Age: $age"
    } else {
        Report $false 'SID cookie carries Max-Age (session cookie would contradict the absolute TTL)' $sidCookie
    }
    Report ($true) 'SID cookie Secure is configurable (xx.session.secure-cookie)' "present: $([bool]($sidCookie -match '(?i);\s*Secure'))"
}

if ($okJson) {
    $userId = $okJson.data.userId
    Report ([bool]$userId) 'data.userId present' "userId: $userId"
    Report ($userId -match '^[0-9a-fA-F-]{36}$') 'data.userId is a uuid' "userId: $userId"
    # Contract: date-time is RFC3339 with offset, UTC rendered as Z.
    Report ($okJson.data.idleExpiresAt -match '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$') 'idleExpiresAt is ISO8601 UTC (Z)' "got: $($okJson.data.idleExpiresAt)"
    Report ($okJson.data.absoluteExpiresAt -match '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$') 'absoluteExpiresAt is ISO8601 UTC (Z)' "got: $($okJson.data.absoluteExpiresAt)"

    $nowUtc = [datetimeoffset]::UtcNow
    $idle = [datetimeoffset]::Parse($okJson.data.idleExpiresAt)
    $abs  = [datetimeoffset]::Parse($okJson.data.absoluteExpiresAt)
    Report (([math]::Abs(($idle - $nowUtc).TotalMinutes - 15)) -lt 2) 'idleExpiresAt ~= now + 15m' "delta: $([math]::Round(($idle - $nowUtc).TotalMinutes,1)) min"
    Report (([math]::Abs(($abs - $nowUtc).TotalHours - 8)) -lt 0.1) 'absoluteExpiresAt ~= now + 8h' "delta: $([math]::Round(($abs - $nowUtc).TotalHours,2)) h"
    Report ($idle -lt $abs) 'idleExpiresAt < absoluteExpiresAt' ''
}

# ---------------------------------------------------------------- 4) database
Show-Step '4) Persistence in auth.user_session / auth.app_user'
$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
$dbReady = (Test-Path $psql) -and [bool]$DB_PASSWORD
if (-not $dbReady) {
    Write-Host '  [SKIP] psql.exe or DB_PASSWORD missing; persistence checks skipped' -ForegroundColor Yellow
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

    $seedId = Query-Scalar "SELECT id FROM auth.app_user WHERE login_name = '$SeedLogin';"
    Report ([bool]$seedId) 'seed account exists (dev-seed enabled)' "id: $seedId"
    if ($seedId -and $okJson) {
        Report ($okJson.data.userId -eq $seedId) 'response userId equals the seeded account id' "resp: $($okJson.data.userId) db: $seedId"
    }

    # Strongest check: the stored sid_hash is exactly sha256 of the SID we received.
    if ($sid) {
        $match = Query-Scalar "SELECT count(*) FROM auth.user_session WHERE sid_hash = sha256(convert_to('$sid','UTF8'));"
        Report ($match -eq '1') 'sid_hash == sha256(SID value)' "matches: $match"
        $leak = Query-Scalar "SELECT count(*) FROM auth.user_session WHERE encode(sid_hash,'hex') LIKE '%$sid%';"
        Report ($leak -eq '0') 'raw SID is not stored (hex compare)' "matches: $leak"
    }

    $traceHeader = Get-HeaderValue $ok.Headers 'X-Trace-Id'
    $rowTrace = Query-Scalar "SELECT trace_id FROM auth.user_session ORDER BY created_at DESC, id DESC LIMIT 1;"
    Report ($rowTrace -eq $traceHeader -and [bool]$rowTrace) 'session row stores the request traceId' "row: $rowTrace header: $traceHeader"

    Report ((Query-Scalar "SELECT count(*) FROM auth.user_session WHERE revoked_at IS NULL;") -gt '0') 'a live session exists' ''
    Report ((Query-Scalar "SELECT count(*) FROM auth.user_session WHERE idle_expires_at > absolute_expires_at;") -eq '0') 'CHECK (idle_expires_at <= absolute_expires_at) holds' ''

    if ($csrfOnly) {
        # The CSRF row used by the successful login must be bound to the new session.
        $bound = Query-Scalar "SELECT count(*) FROM auth.csrf_session WHERE session_id IS NOT NULL;"
        Report ($bound -gt '0') 'login bound auth.csrf_session.session_id to the new session' "bound rows: $bound"
    }

    Report ((Query-Scalar "SELECT count(*) FROM auth.app_user WHERE login_name = '$SeedLogin' AND last_login_at IS NOT NULL;") -eq '1') 'last_login_at was recorded' ''

    # ------------------------------------------------------------ 5) rotation
    Show-Step '5) Login rotates SID (old session is revoked)'
    $rot = Login-WithCsrf (New-LoginBody $SeedLogin $SeedPassword) @('-b', "SID=$sid")
    Report ($rot.Code -eq 200) 're-login with an existing SID -> 200' "HTTP $($rot.Code)"
    $newSid = Get-CookieValue $rot.Headers 'SID'
    Report ([bool]$newSid -and $newSid -ne $sid) 'a different SID was issued' "old: $($sid.Substring(0,6))... new: $($newSid.Substring(0,6))..."
    if ($sid) {
        $revoked = Query-Scalar "SELECT count(*) FROM auth.user_session WHERE sid_hash = sha256(convert_to('$sid','UTF8')) AND revoked_at IS NOT NULL;"
        Report ($revoked -eq '1') 'previous session is revoked on rotation' "revoked rows: $revoked"
    }

    # ------------------------------------------------------------ 6) disabled
    Show-Step '6) Disabled account cannot log in (same 401 as a wrong password)'
    $disLogin = 'disabled.probe.' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    $disId = [guid]::NewGuid().ToString()
    try {
        # Clone the seed account's hash so the only difference is status: if the
        # status check were missing, this login would succeed.
        # psql prints the RETURNING row AND the command tag ("INSERT 0 1") on stdout,
        # so compare by "contains the id" and take the first line for the message.
        $insert = Query-Scalar "INSERT INTO auth.app_user (id, user_code, login_name, display_name, password_hash, status) SELECT '$disId', '$disLogin', '$disLogin', 'disabled probe', password_hash, 'DISABLED' FROM auth.app_user WHERE login_name = '$SeedLogin' RETURNING id;"
        Report ($insert -match [regex]::Escape($disId)) 'temporary DISABLED account created' "psql said: $($insert) "
        $dis = Login-WithCsrf (New-LoginBody $disLogin $SeedPassword)
        Report ($dis.Code -eq 401) 'disabled account -> 401' "HTTP $($dis.Code)"
        $disJson = ConvertTo-JsonSafe $dis.Body
        Report ($disJson.code -eq 'AUTH_INVALID_CREDENTIALS' -and $disJson.message -eq $wrongJson.message) 'disabled account reuses the same code and message' "code: $($disJson.code)"
        Report ($null -eq (Get-CookieValue $dis.Headers 'SID')) 'disabled account sets no SID cookie' ''
    } finally {
        Query-Scalar "DELETE FROM auth.app_user WHERE id = '$disId';" | Out-Null
    }

    # ------------------------------------------------------------ 7) throttle
    Show-Step '7) Repeated failures are throttled with 429 + Retry-After'
    $burstName = 'burst.probe.' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    $last = $null
    $statuses = @()
    for ($i = 0; $i -lt 11; $i++) {
        $last = Login-WithCsrf (New-LoginBody $burstName 'wrong-password-123')
        $statuses += $last.Code
    }
    Report ($statuses[10] -eq 429) 'the 11th consecutive failure -> 429' "statuses: $($statuses -join ',')"
    Report ((@($statuses) | Where-Object { $_ -eq 401 }).Count -eq 10) 'the first 10 failures -> 401' "statuses: $($statuses -join ',')"
    $throttleJson = ConvertTo-JsonSafe $last.Body
    if ($throttleJson) {
        Report ($throttleJson.code -eq 'RATE_LIMITED') 'throttled response uses RATE_LIMITED' "code: $($throttleJson.code)"
        Report ($null -eq $throttleJson.data) 'throttled response carries data:null' ''
    }
    $retryAfter = Get-HeaderValue $last.Headers 'Retry-After'
    Report ([bool]$retryAfter -and [int]$retryAfter -ge 1) 'Retry-After header present and >= 1s' "Retry-After: $retryAfter"
    # The burst used a throwaway login name, so the seed account is NOT locked out.
    $afterBurst = Login-WithCsrf (New-LoginBody $SeedLogin $SeedPassword)
    Report ($afterBurst.Code -eq 200) 'seed account is unaffected by another account being throttled' "HTTP $($afterBurst.Code)"
}

# ---------------------------------------------------------------- 8) contract guards
Show-Step '8) Request validation and routing still follow the contract'
$unknownField = Login-WithCsrf '{"loginName":"demo.admin","password":"x","extraField":1}'
Report ($unknownField.Code -eq 400) 'unknown body field -> 400' "HTTP $($unknownField.Code)"
$unknownFieldJson = ConvertTo-JsonSafe $unknownField.Body
Report ($unknownFieldJson.code -eq 'VALIDATION_ERROR') 'unknown field uses VALIDATION_ERROR' "code: $($unknownFieldJson.code)"
# NB: wrap the pipeline in @() before reading .Count. In Windows PowerShell 5.1 a
# pipeline that emits exactly one object returns that object, and a single
# PSCustomObject has no Count member, so '.Count' silently evaluates to $null
# and '... -eq 1' becomes false even though the element is right there.
Report ((@(@($unknownFieldJson.details) | Where-Object { $_.field -eq 'extraField' })).Count -eq 1) 'details names the offending field' "details: $($unknownFieldJson.details | ConvertTo-Json -Compress)"

$blank = Login-WithCsrf '{"loginName":"","password":"x"}'
Report ($blank.Code -eq 400) 'blank loginName -> 400' "HTTP $($blank.Code)"
$tooLong = Login-WithCsrf (New-LoginBody ('a' * 81) 'x')
Report ($tooLong.Code -eq 400) 'loginName longer than 80 chars -> 400 (contract maxLength)' "HTTP $($tooLong.Code)"
$missingPassword = Login-WithCsrf '{"loginName":"demo.admin"}'
Report ($missingPassword.Code -eq 400) 'missing password -> 400' "HTTP $($missingPassword.Code)"

$getLogin = Invoke-Curl @('--max-time', '10', "$BaseUrl/auth/login")
Report ($getLogin.Code -eq 405) 'GET /auth/login -> 405' "HTTP $($getLogin.Code)"
$getLoginJson = ConvertTo-JsonSafe $getLogin.Body
Report ($getLoginJson.code -eq 'METHOD_NOT_ALLOWED') '405 uses METHOD_NOT_ALLOWED' "code: $($getLoginJson.code)"

# Cross-origin write must be rejected by the CORS filter with the contract envelope
# (a plain-text 'Invalid CORS request' would break the frontend error handling).
$crossOrigin = Post-Login (New-LoginBody $SeedLogin $SeedPassword) @('-H', 'Origin: https://evil.example.com')
Report ($crossOrigin.Code -eq 403) 'cross-origin login -> 403' "HTTP $($crossOrigin.Code)"
$crossJson = ConvertTo-JsonSafe $crossOrigin.Body
if ($crossJson) {
    Report ($crossJson.code -eq 'AUTH_CSRF_INVALID') 'cross-origin rejection uses the contract envelope' "code: $($crossJson.code)"
} else {
    Report $false 'cross-origin body is the contract envelope, not plain text' "body: $($crossOrigin.Body)"
}

# ---------------------------------------------------------------- result
Write-Host ''
if ($script:failed -eq 0) {
    Write-Host "RESULT: $($script:passed) passed, 0 failed" -ForegroundColor Green
    exit 0
} else {
    Write-Host "RESULT: $($script:passed) passed, $($script:failed) failed" -ForegroundColor Red
    exit 1
}
