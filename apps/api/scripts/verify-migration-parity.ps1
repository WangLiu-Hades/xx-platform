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

    Exit code 0 = parity, 1 = drift, 2 = environment problem.

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

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File apps/api/scripts/verify-migration-parity.ps1 -Port 55433
#>
param(
    [string]$PgBin = '',
    [int]$Port = 55433,
    [string]$HostName = '127.0.0.1',
    [string]$User = 'postgres',
    [switch]$Keep,
    [string]$RunTag = ''
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
function Invoke-Tool([string]$exe, [string[]]$arguments) {
    $out = & $exe @arguments 2>&1
    return @{ Code = $LASTEXITCODE; Output = @($out) }
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

function Test-DatabaseExists([string]$database) {
    $out = & $psql ($conn + @('-d', 'postgres', '-tA', '-c', "SELECT 1 FROM pg_database WHERE datname = '$database'")) 2>&1
    $lines = @($out | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
    return ($lines -contains '1')
}

# A name collision means "somebody else owns that database": refuse instead of dropping it. This is
# the whole point of A05 -- the script used to DROP DATABASE IF EXISTS on two fixed names.
foreach ($database in @($dbMig, $dbRef)) {
    if (Test-DatabaseExists $database) {
        Fail "refusing to run: database '$database' already exists (concurrent run, or created by hand). This script never drops a database it did not create; wait for the other run to finish or point -Port at an isolated instance."
    }
}

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
        $null = Invoke-Tool $psql ($conn + $dropArgs)
        Write-Host "cleaned up $($createdDbs -join ', ') (only databases this run created)"
    } else {
        Write-Host "nothing to clean up: no throwaway database was created by this run"
    }
    if ($Keep) {
        Write-Host "Work directory kept at $workDir"
    } else {
        Remove-Item -Recurse -Force $workDir -ErrorAction SilentlyContinue
    }
}

exit $exitCode
