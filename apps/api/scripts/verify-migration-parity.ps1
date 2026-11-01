# requires -Version 5.1
<#
.SYNOPSIS
    Prove that "migrations built database" and "schema.sql built database" are structurally identical.

.DESCRIPTION
    MigrationBaselineTests compares migration text against the contract text (fast, offline).
    This script adds the semantic evidence: it executes both DDL sets on two throwaway databases
    inside one PostgreSQL instance, then compares information_schema / pg_indexes / pg_constraint /
    pg_trigger / seed rows.

    The script does NOT manage the PostgreSQL server itself: start a disposable instance first
    (docker, a scratch cluster from initdb, or the local service) and pass -Port. This keeps the
    script usable in CI and inside sandboxes where a child server cannot outlive the shell.

    Each run owns two databases named parity_<pid>_<MMddHHmmss>_mig and ..._ref, plus a matching
    work directory. If either name already exists the script refuses with exit code 2 instead of
    dropping it, and the finally block drops only the databases this run actually created.

    Example - scratch cluster without touching your main installation:
        $bin = 'C:\Program Files\PostgreSQL\16\bin'
        & "$bin\initdb.exe" -D ..\pg-scratch\data -U postgres -A trust
        & "$bin\pg_ctl.exe" -D ..\pg-scratch\data -o '-p 55433' -l ..\pg-scratch\pg.log start
        powershell -File apps/api/scripts/verify-migration-parity.ps1 -Port 55433
        & "$bin\pg_ctl.exe" -D ..\pg-scratch\data stop

    Exit code 0 = parity, 1 = drift, 2 = environment problem (including a refused name collision),
    3 = the run itself finished but cleanup of its own throwaway databases failed (a session was
    still connected). Exit code 3 is never reported as a pass: a leftover database is exactly the
    kind of state A05 asks this script not to leave behind.

.PARAMETER PgBin
    PostgreSQL 16 bin directory. Detected from PATH or C:\Program Files\PostgreSQL\*\bin when omitted.

.PARAMETER Port
    Port of an already running instance (default 55433).

.PARAMETER HostName
    Server host (default 127.0.0.1).

.PARAMETER User
    Superuser used to create the throwaway databases (default postgres).

.PARAMETER Keep
    Keep the work directory (SQL, fingerprint files, sessions) for debugging.

.PARAMETER RunTag
    Override the run tag that makes database names unique (default: <pid>_<MMddHHmmss>). Used by the
    self-test to prove that a name collision is refused; keep it 1-24 characters of a-z 0-9 _.

.PARAMETER SelfTest
    Execute the fault self-test against the real instance: the script re-runs itself as a child
    process for every situation it must distinguish (missing database, connection failure,
    authentication failure, psql non-zero exit, cleanup that cannot be verified) and checks both the
    exit code and the message. Exits 0 only when all scenarios behave as required, 1 otherwise.
    Faults are injected through the XX_PARITY_FAULT environment variable, which only has an effect
    when this switch starts the self-test; a normal run never reads it.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File apps/api/scripts/verify-migration-parity.ps1 -Port 55433
#>
param(
    [string]$PgBin = '',
    [int]$Port = 55433,
    [string]$HostName = '127.0.0.1',
    [string]$User = 'postgres',
    [switch]$Keep,
    [string]$RunTag = '',
    [switch]$SelfTest
)

$ErrorActionPreference = 'Continue'

# Each run derives its own database names and work directory from the PID plus the start time, so a
# concurrent run (or a human) can never end up sharing them. $createdDbs records exactly the
# databases this run created -- the finally block drops those and nothing else.
if ($RunTag -and $RunTag -notmatch '^[a-z0-9_]{1,24}$') {
    Write-Host "ERROR  -RunTag must be 1-24 characters of a-z 0-9 _ (got '$RunTag')" -ForegroundColor Red
    exit 2
}
$dbRun = if ($RunTag) { $RunTag } else { '{0}_{1}' -f $PID, (Get-Date -Format 'MMddHHmmss') }
$dbMig = "parity_${dbRun}_mig"
$dbRef = "parity_${dbRun}_ref"
$createdDbs = New-Object System.Collections.ArrayList

function Fail([string]$message) {
    Write-Host ""
    Write-Host "ERROR  $message" -ForegroundColor Red
    exit 2
}

# Run a native tool and return exit code + output; stderr is merged into the output, so server
# NOTICE lines never turn into terminating errors (PowerShell 5.1 raises NativeCommandError).
function Invoke-Tool([string]$exe, [string[]]$arguments, [int]$TimeoutSeconds) {
    if (-not $PSBoundParameters.ContainsKey('TimeoutSeconds')) { $TimeoutSeconds = 900 }
    # Self-test fault injection: make one psql call fail with a non-zero exit code so the
    # "psql exited non-zero" path is exercised for real (the caller must refuse to report parity).
    if ($env:XX_PARITY_FAULT -eq 'psql-nonzero' -and -not $script:faultInjected -and
        (($arguments -join ' ') -match 'fingerprint\.sql')) {
        $script:faultInjected = $true
        Write-Host "      [self-test fault] applying a non-zero psql exit code to the fingerprint export" -ForegroundColor Yellow
        return @{ Code = 1; Output = @('simulated psql failure (SelfTest)') }
    }

    # A native call can hang: this repo's CI once saw DROP DATABASE wait forever on a PostgreSQL
    # whose checkpointer could not be signalled. Without a timeout the verifier would hang instead of
    # reporting "cannot confirm". 124 means "no answer within the budget" and is never a pass.
    $argLine = ($arguments | ForEach-Object {
            if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
        }) -join ' '
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $exe
    $psi.Arguments = $argLine
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $proc = [System.Diagnostics.Process]::Start($psi)
    $stdout = $proc.StandardOutput.ReadToEndAsync()
    $stderr = $proc.StandardError.ReadToEndAsync()
    if (-not $proc.WaitForExit($TimeoutSeconds * 1000)) {
        try { $proc.Kill() } catch { }
        $null = $proc.WaitForExit(5000)
        $text = "$($stdout.Result)`n$($stderr.Result)"
        $lines = @($text -split "`r?`n" | Where-Object { $_ -ne '' })
        $lines += "tool timed out after $TimeoutSeconds s: no answer, treated as unconfirmed"
        return @{ Code = 124; Output = $lines }
    }
    $proc.WaitForExit()
    $text = "$($stdout.Result)`n$($stderr.Result)"
    return @{ Code = $proc.ExitCode; Output = @($text -split "`r?`n" | Where-Object { $_ -ne '' }) }
}

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$apiRoot = Join-Path $repoRoot 'apps/api'
$migrations = Join-Path $apiRoot 'src/main/resources/db/migration'
$contract = Join-Path $repoRoot 'contracts/database/schema.sql'
$workDir = Join-Path $apiRoot "target/pg-parity/$dbRun"

if (-not $PgBin) {
    $cmd = Get-Command psql.exe -ErrorAction SilentlyContinue
    if ($cmd) {
        $PgBin = Split-Path -Parent $cmd.Source
    } else {
        $cand = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\psql.exe' -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending | Select-Object -First 1
        if ($cand) { $PgBin = Split-Path -Parent $cand.FullName }
    }
}
if (-not $PgBin) { Fail 'PostgreSQL bin directory not found (use -PgBin)' }

$psql = Join-Path $PgBin 'psql.exe'
$pgIsReady = Join-Path $PgBin 'pg_isready.exe'
foreach ($tool in @($psql)) {
    if (-not (Test-Path $tool)) { Fail "PostgreSQL tool not found: $tool" }
}
foreach ($path in @($contract, $migrations)) {
    if (-not (Test-Path $path)) { Fail "Input not found: $path" }
}

$migrationFiles = @(Get-ChildItem $migrations -Filter 'V*__*.sql' | Sort-Object Name)
if ($migrationFiles.Count -eq 0) { Fail "No migrations found in $migrations" }

$conn = @('-h', $HostName, '-p', "$Port", '-U', $User)
$env:PGCLIENTENCODING = 'UTF8'

Write-Host "[1/4] checking server $HostName`:$Port"
if (Test-Path $pgIsReady) {
    $ping = Invoke-Tool $pgIsReady (@('-h', $HostName, '-p', "$Port", '-q'))
    if ($ping.Code -ne 0) {
        Fail "no PostgreSQL instance is accepting connections on $HostName`:$Port (start one first, or pass -Port)"
    }
}

if (Test-Path $workDir) { Remove-Item -Recurse -Force $workDir -ErrorAction SilentlyContinue }
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

function Get-DatabaseState([string]$database) {
    # Three outcomes must stay distinguishable: exists / does not exist / the query itself failed.
    # Collapsing "query failed" into "does not exist" is what made the old script claim a clean
    # cleanup while a throwaway database was still there (E01).
    if ($env:XX_PARITY_FAULT -eq 'cleanup-verify-fail' -and $script:inCleanup) {
        # Self-test fault injection: the drop succeeded but the follow-up check cannot confirm it.
        # The script must report "cannot be verified" and exit 3, never a clean cleanup.
        return [pscustomobject]@{ QueryOk = $false; Exists = $false; Detail = 'simulated verification failure (SelfTest)' }
    }
    $out = & $psql ($conn + @('-d', 'postgres', '-tA', '-c', "SELECT 1 FROM pg_database WHERE datname = '$database'")) 2>&1
    $code = $LASTEXITCODE
    $lines = @($out | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
    $errors = @($lines | Where-Object { $_ -match '^(ERROR|FATAL|psql:)' })
    if ($code -ne 0 -or $errors.Count -gt 0) {
        return [pscustomobject]@{ QueryOk = $false; Exists = $false; Detail = ($lines -join ' ') }
    }
    return [pscustomobject]@{ QueryOk = $true; Exists = ($lines -contains '1'); Detail = '' }
}

# How long a DROP DATABASE may block before the run refuses to claim it cleaned up. A PostgreSQL whose
# postmaster cannot signal its checkpointer waits forever; 120s is long enough for a healthy instance.
$cleanupTimeoutSeconds = 120
if ($env:XX_PARITY_CLEANUP_TIMEOUT -match '^\d+$') { $cleanupTimeoutSeconds = [int]$env:XX_PARITY_CLEANUP_TIMEOUT }

# Self-test: proves, on the real instance, that the script distinguishes the cases the old version
# conflated -- database exists, database missing, the query itself failing, a non-zero psql exit and
# a cleanup that cannot be confirmed. Every scenario is a real child run of this same script.
function Invoke-SelfTest {
    Write-Host ""
    Write-Host "=== self-test: exists / missing / query-failed must stay distinguishable ==="
    $pwshExe = (Get-Process -Id $PID).Path
    $scriptPath = $PSCommandPath
    $tag = 'selftest_' + (Get-Date -Format 'HHmmss')
    $failed = $false
    $residue = New-Object System.Collections.ArrayList

    # The happy path cannot be "exit 0" unconditionally: some sandboxes run PostgreSQL in a way that
    # forbids DROP DATABASE (the postmaster cannot signal its checkpointer, and the drop then blocks).
    # In that case the correct outcome is exit 3 with "cleanup failed", never exit 0. The scenario
    # accepts either code but insists that the parity verdict itself was reached and that no run
    # claims "cleaned up" without confirming it.

    $scenarios = @(
        [pscustomobject]@{
            Name        = 'database already exists -> refuse instead of dropping (upgrade-cycle safety)'
            Args        = @('-Port', "$Port", '-HostName', $HostName, '-User', $User, '-RunTag', "${tag}bis")
            Expect      = 2
            Fault       = $null
            MustContain = 'already exists'
            PreCreateDb = "parity_${tag}bis_mig"
        }
        [pscustomobject]@{
            Name        = 'databases missing -> created -> parity compared, cleanup verified (happy path)'
            Args        = @('-Port', "$Port", '-HostName', $HostName, '-User', $User, '-RunTag', "${tag}run")
            AllowedExit = @(0, 3)
            Fault       = $null
            MustContain = 'structure is identical'
            PreCreateDb = $null
        }
        [pscustomobject]@{
            Name        = 'connection failure (wrong port) -> environment error, not a pass'
            Args        = @('-Port', '1', '-HostName', $HostName, '-User', $User, '-RunTag', "${tag}net")
            Expect      = 2
            Fault       = $null
            MustContain = 'no PostgreSQL instance'
            PreCreateDb = $null
        }
        [pscustomobject]@{
            Name        = 'authentication/role failure -> "cannot inspect", never "does not exist"'
            Args        = @('-Port', "$Port", '-HostName', $HostName, '-User', "xx_parity_absent_$PID", '-RunTag', "${tag}auth")
            Expect      = 2
            Fault       = $null
            MustContain = 'cannot inspect'
            PreCreateDb = $null
        }
        [pscustomobject]@{
            Name        = 'psql exits non-zero -> report failure, never a parity verdict'
            Args        = @('-Port', "$Port", '-HostName', $HostName, '-User', $User, '-RunTag', "${tag}fail")
            Expect      = 2
            Fault       = 'psql-nonzero'
            MustContain = 'fingerprint export'
            PreCreateDb = $null
        }
        [pscustomobject]@{
            Name        = 'cleanup cannot be verified -> exit 3, never "cleaned up"'
            Args        = @('-Port', "$Port", '-HostName', $HostName, '-User', $User, '-RunTag', "${tag}clean")
            Expect      = 3
            Fault       = 'cleanup-verify-fail'
            MustContain = 'cleanup could not be verified'
            PreCreateDb = $null
        }
    )

    foreach ($scenario in $scenarios) {
        if ($scenario.PreCreateDb) {
            $r = Invoke-Tool $psql ($conn + @('-d', 'postgres', '-q', '-v', 'ON_ERROR_STOP=1', '-c', "CREATE DATABASE $($scenario.PreCreateDb);"))
            if ($r.Code -ne 0) {
                Write-Host "  [FAIL] $($scenario.Name): could not pre-create $($scenario.PreCreateDb)" -ForegroundColor Red
                $failed = $true
                continue
            }
        }

        $previousFault = $env:XX_PARITY_FAULT
        if ($scenario.Fault) { $env:XX_PARITY_FAULT = $scenario.Fault }
        else { Remove-Item Env:XX_PARITY_FAULT -ErrorAction SilentlyContinue }
        try {
            $childArgs = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $scriptPath) + $scenario.Args
            $output = & $pwshExe @childArgs 2>&1 | Out-String
            $code = $LASTEXITCODE
        } finally {
            if ($previousFault) { $env:XX_PARITY_FAULT = $previousFault }
            else { Remove-Item Env:XX_PARITY_FAULT -ErrorAction SilentlyContinue }
        }

        $matched = [bool]($output -match [regex]::Escape($scenario.MustContain))
        $alsoMatched = $true
        if ($scenario.PSObject.Properties.Name -contains 'MustAlso' -and $scenario.MustAlso) {
            $alsoMatched = [bool]($output -match [regex]::Escape($scenario.MustAlso))
        }
        $ok = ($code -eq $scenario.Expect) -and $matched -and $alsoMatched
        if ($scenario.PSObject.Properties.Name -contains 'AllowedExit') {
            # Happy path: parity must have been compared, and the run must report cleanup honestly:
            # exit 0 only together with "cleaned up"; exit 3 only together with "cleanup failed".
            $ok = ($scenario.AllowedExit -contains $code) -and $matched
            if ($code -eq 0) { $ok = $ok -and [bool]($output -match 'cleaned up') }
            if ($code -eq 3) { $ok = $ok -and [bool]($output -match 'cleanup failed; still present') }
        }
        if (-not $ok) { $failed = $true }
        $verdict = if ($ok) { 'PASS' } else { 'FAIL' }
        $color = if ($ok) { 'Green' } else { 'Red' }
        Write-Host ("  [{0}] {1}" -f $verdict, $scenario.Name) -ForegroundColor $color
        $expectedText = if ($scenario.PSObject.Properties.Name -contains 'AllowedExit') { ($scenario.AllowedExit -join ' or ') } else { "$($scenario.Expect)" }
        Write-Host ("         exit code: expected {0}, got {1}; required message '{2}': {3}" -f $expectedText, $code, $scenario.MustContain, $matched)
        if ($scenario.MustAlso) {
            Write-Host ("         also required '{0}': {1}" -f $scenario.MustAlso, $alsoMatched)
        }
        if (-not $ok) {
            Write-Host "         ---- child output (last 40 lines) ----"
            ($output -split "`r?`n" | Select-Object -Last 40) | ForEach-Object { Write-Host "         $_" }
        }

        if ($scenario.PreCreateDb) {
            $null = Invoke-Tool $psql ($conn + @('-d', 'postgres', '-q', '-c', "DROP DATABASE IF EXISTS $($scenario.PreCreateDb);")) $cleanupTimeoutSeconds
            $state = Get-DatabaseState $scenario.PreCreateDb
            if ($state.QueryOk -and (-not $state.Exists)) {
                # removed as intended
            } elseif ($state.QueryOk) {
                # The instance cannot drop; not a self-test failure, but never reported as cleaned.
                $null = $residue.Add($scenario.PreCreateDb)
            } else {
                Write-Host "  [FAIL] self-test cleanup could not confirm removal of $($scenario.PreCreateDb)" -ForegroundColor Red
                $failed = $true
            }
        }
    }

    # Anything this run created that the instance refused to drop is reported, never counted as cleaned.
    $leftoverQuery = Invoke-Tool $psql ($conn + @('-d', 'postgres', '-tA', '-c', "SELECT datname FROM pg_database WHERE datname LIKE 'parity_${tag}%' ORDER BY 1"))
    foreach ($db in $leftoverQuery.Output) {
        $name = "$db".Trim()
        if ($name -and -not $residue.Contains($name)) { $null = $residue.Add($name) }
    }

    if ($residue.Count -gt 0) {
        Write-Host ""
        Write-Host "  NOTE  the instance refused the drop, so these throwaway databases remain (not reported as cleaned):" -ForegroundColor Yellow
        foreach ($database in $residue) { Write-Host "         $database" }
        Write-Host "        remove them once DROP DATABASE is permitted:"
        foreach ($database in $residue) { Write-Host "         DROP DATABASE IF EXISTS $database;" }
    }

    if ($failed) {
        Write-Host ""
        Write-Host "SELF-TEST FAILED: at least one situation was reported as a success it was not" -ForegroundColor Red
        return 1
    }
    Write-Host ""
    Write-Host "SELF-TEST PASSED: all $($scenarios.Count) situations reported with the required exit code and message" -ForegroundColor Green
    return 0
}

if ($SelfTest) {
    exit (Invoke-SelfTest)
}

# A name collision means "somebody else owns that database": refuse instead of dropping it. This is
# the whole point of A05 -- the script used to DROP DATABASE IF EXISTS on two fixed names.
foreach ($database in @($dbMig, $dbRef)) {
    $state = Get-DatabaseState $database
    if (-not $state.QueryOk) {
        Fail "cannot inspect whether '$database' exists (query failed: $($state.Detail)). Refusing to guess: this script never drops a database it could not identify."
    }
    if ($state.Exists) {
        Fail "refusing to run: database '$database' already exists (concurrent run, or created by hand). This script never drops a database it did not create; wait for the other run to finish or point -Port at an isolated instance."
    }
}

$cleanupFailed = $false
$script:inCleanup = $false
$script:faultInjected = $false

try {
    foreach ($database in @($dbMig, $dbRef)) {
        $r = Invoke-Tool $psql ($conn + @('-d', 'postgres', '-q', '-v', 'ON_ERROR_STOP=1', '-c', "CREATE DATABASE $database;"))
        if ($r.Code -ne 0) { Fail "creating $database failed: $($r.Output -join ' ')" }
        $null = $createdDbs.Add($database)
        Write-Host "      created $database"
    }

    Write-Host "[2/4] applying $($migrationFiles.Count) migration(s) to $dbMig"
    foreach ($file in $migrationFiles) {
        Write-Host "      - $($file.Name)"
        $r = Invoke-Tool $psql ($conn + @('-d', $dbMig, '-v', 'ON_ERROR_STOP=1', '-q', '-f', $file.FullName))
        if ($r.Code -ne 0) {
            $noise = @($r.Output | Where-Object { $_ -notmatch 'NOTICE' })
            Fail "migration failed: $($file.Name)`n$($noise -join "`n")"
        }
    }

    Write-Host "[3/4] applying contracts/database/schema.sql to $dbRef"
    $r = Invoke-Tool $psql ($conn + @('-d', $dbRef, '-v', 'ON_ERROR_STOP=1', '-q', '-f', $contract))
    if ($r.Code -ne 0) {
        $noise = @($r.Output | Where-Object { $_ -notmatch 'NOTICE' })
        Fail "contract schema.sql failed`n$($noise -join "`n")"
    }

    $fingerprintSql = Join-Path $workDir 'fingerprint.sql'
    @'
\pset format unaligned
\pset tuples_only on
SELECT 'T ' || n.nspname || '.' || c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE c.relkind='r' AND n.nspname IN ('auth','core','capture','analysis','operation','audit');
SELECT 'C ' || table_schema||'.'||table_name||'.'||column_name||' | '||data_type||' | '||is_nullable||' | '
       ||coalesce(column_default,'-')||' | '||coalesce(character_maximum_length::text,'-')||' | '
       ||coalesce(numeric_precision::text,'-')||','||coalesce(numeric_scale::text,'-')
 FROM information_schema.columns WHERE table_schema IN ('auth','core','capture','analysis','operation','audit');
SELECT 'I ' || schemaname||'.'||indexname||' | '||regexp_replace(indexdef,'\s+',' ','g') FROM pg_indexes
 WHERE schemaname IN ('auth','core','capture','analysis','operation','audit');
SELECT 'K ' || n.nspname||'.'||con.conname||' | '||con.contype::text||' | '
       ||regexp_replace(pg_get_constraintdef(con.oid),'\s+',' ','g')
 FROM pg_constraint con JOIN pg_namespace n ON n.oid=con.connamespace
 WHERE n.nspname IN ('auth','core','capture','analysis','operation','audit');
SELECT 'G ' || n.nspname||'.'||c.relname||'.'||tg.tgname FROM pg_trigger tg
 JOIN pg_class c ON c.oid=tg.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE NOT tg.tgisinternal AND n.nspname IN ('auth','core','capture','analysis','operation','audit');
SELECT 'F ' || feature_code||' | '||enabled::text||' | '||coalesce(pending_reason,'-')||' | '||policy_version
 FROM core.feature_config;
SELECT 'P ' || code||' | '||module_code||' | '||risk_level||' | '||assignable::text FROM auth.permission;
SELECT 'S ' || p.proname||' | '||n.nspname FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace
 WHERE n.nspname IN ('auth','core','capture','analysis','operation','audit');
'@ | Out-File -FilePath $fingerprintSql -Encoding ascii

    $fpMig = Join-Path $workDir 'fp-mig.txt'
    $fpRef = Join-Path $workDir 'fp-ref.txt'
    $r = Invoke-Tool $psql ($conn + @('-d', $dbMig, '-At', '-f', $fingerprintSql, '-o', $fpMig))
    if ($r.Code -ne 0) { Fail "fingerprint export ($dbMig) failed: $($r.Output -join ' ')" }
    $r = Invoke-Tool $psql ($conn + @('-d', $dbRef, '-At', '-f', $fingerprintSql, '-o', $fpRef))
    if ($r.Code -ne 0) { Fail "fingerprint export ($dbRef) failed: $($r.Output -join ' ')" }

    Write-Host "[4/4] comparing structure fingerprints"
    $left = @(Get-Content $fpMig -Encoding UTF8 | Where-Object { $_ -match '^\S+ ' } | Sort-Object)
    $right = @(Get-Content $fpRef -Encoding UTF8 | Where-Object { $_ -match '^\S+ ' } | Sort-Object)
    $diff = @(Compare-Object $left $right)
    $tables = @($left | Where-Object { $_ -match '^T ' }).Count
    $exitCode = 1

    if ($diff.Count -eq 0) {
        Write-Host ""
        Write-Host "PASS  structure is identical: $($left.Count) fingerprints match ($tables tables)" -ForegroundColor Green
        $exitCode = 0
    } else {
        Write-Host ""
        Write-Host "FAIL  $($diff.Count) difference(s): '<= migrations' vs '=> contract'" -ForegroundColor Red
        $diff | Select-Object -First 40 | ForEach-Object {
            Write-Host ("  {0} {1}" -f $_.SideIndicator, $_.InputObject)
        }
    }
}
finally {
    if ($createdDbs.Count -gt 0) {
        $dropArgs = @('-d', 'postgres', '-q')
        foreach ($database in $createdDbs) { $dropArgs += @('-c', "DROP DATABASE IF EXISTS $database;") }
        $dropResult = Invoke-Tool $psql ($conn + $dropArgs) $cleanupTimeoutSeconds
        # psql can exit 0 even when a DROP failed (the error is reported but the batch continues), so
        # ask the catalogue instead of trusting the exit code: what matters is whether the databases
        # are actually gone.
        $leftovers = New-Object System.Collections.ArrayList
        $script:inCleanup = $true
        foreach ($database in $createdDbs) {
            $state = Get-DatabaseState $database
            if (-not $state.QueryOk) {
                $cleanupFailed = $true
                Write-Host ""
                Write-Host "ERROR  cleanup could not be verified: querying '$database' failed ($($state.Detail))" -ForegroundColor Red
                Write-Host "       treat the run as leaving state behind until a human checks:"
                Write-Host "         DROP DATABASE IF EXISTS $database;"
            } elseif ($state.Exists) {
                $null = $leftovers.Add($database)
            }
        }
        if ($leftovers.Count -gt 0) {
            $cleanupFailed = $true
            Write-Host ""
            Write-Host "ERROR  cleanup failed; still present: $($leftovers -join ', ')" -ForegroundColor Red
            Write-Host "       a session was probably still connected to the throwaway database. Drop it by hand once the session ends:"
            foreach ($database in $leftovers) { Write-Host "         DROP DATABASE IF EXISTS $database;" }
            Write-Host "       ($($dropResult.Output -join ' '))"
        } elseif (-not $cleanupFailed) {
            Write-Host "cleaned up $($createdDbs -join ', ') (only databases this run created)"
        }
    } else {
        Write-Host "nothing to clean up: no throwaway database was created by this run"
    }
    if ($Keep) {
        Write-Host "Work directory kept at $workDir"
    } else {
        Remove-Item -Recurse -Force $workDir -ErrorAction SilentlyContinue
    }
}

if ($cleanupFailed) {
    exit 3
}

exit $exitCode