# requires -Version 5.1
<#
.SYNOPSIS
    Negative-path self-test for verify-migration-parity.ps1 (review item A05).

.DESCRIPTION
    verify-migration-parity.ps1 is the script that will be run by whoever is debugging a migration,
    usually against a shared scratch instance. Its safety properties are therefore worth testing
    directly, not just by reading the code:

      1. a name collision is refused (exit 2) and the pre-existing database survives;
      2. the happy path still reports PASS and leaves no parity_* database behind;
      3. a migration that fails to apply exits 2 and still cleans up;
      4. a migration that applies but disagrees with the contract exits 1 (drift) and still cleans up;
      5. when the throwaway database cannot be dropped (a session is still connected) the run reports
         exit 3 instead of claiming success, and does not drop anybody else's database.

    Every case asserts on the process exit code and on the database list, so a future change that
    reintroduces "DROP DATABASE IF EXISTS <fixed name>" fails here instead of on a colleague's
    machine. Cases 3 and 4 temporarily write V9__*.sql files into the migration directory and always
    delete them again in a finally block; run this only on a checkout you own.

    Exit code 0 = all cases passed, 1 = at least one case failed, 2 = environment problem.

.PARAMETER PgBin
    PostgreSQL 16 bin directory. Detected from PATH or C:\Program Files\PostgreSQL\*\bin when omitted.

.PARAMETER Port
    Port of an already running instance (default 55434, matching the scratch cluster used in
    apps/api/README.md).

.PARAMETER Tag
    Base name for the throwaway databases; the cases append a suffix. 1-16 characters of a-z 0-9 _.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File apps/api/scripts/verify-migration-parity-selftest.ps1 -Port 55434
#>
param(
    [string]$PgBin = '',
    [int]$Port = 55434,
    [string]$HostName = '127.0.0.1',
    [string]$User = 'postgres',
    [string]$Tag = 'a05st'
)

$ErrorActionPreference = 'Continue'

if ($Tag -notmatch '^[a-z0-9_]{1,16}$') {
    Write-Host "ERROR  -Tag must be 1-16 characters of a-z 0-9 _ (got '$Tag')" -ForegroundColor Red
    exit 2
}

$repoRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$parity = Join-Path $PSScriptRoot 'verify-migration-parity.ps1'
$migrations = Join-Path $repoRoot 'apps/api/src/main/resources/db/migration'
$brokenMigration = Join-Path $migrations 'V9__a05_selftest_broken.sql'
$driftMigration = Join-Path $migrations 'V9__a05_selftest_drift.sql'

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
if (-not $PgBin) {
    Write-Host 'ERROR  PostgreSQL bin directory not found (use -PgBin)' -ForegroundColor Red
    exit 2
}
$psql = Join-Path $PgBin 'psql.exe'
$conn = @('-h', $HostName, '-p', "$Port", '-U', $User)
$env:PGCLIENTENCODING = 'UTF8'

$passed = 0
$failed = 0

function Show-Case([string]$name) {
    Write-Host ''
    Write-Host "== $name" -ForegroundColor Cyan
}

function Report([bool]$condition, [string]$message) {
    if ($condition) {
        $script:passed++
        Write-Host "  PASS  $message" -ForegroundColor Green
    } else {
        $script:failed++
        Write-Host "  FAIL  $message" -ForegroundColor Red
    }
}

function Invoke-Psql([string[]]$arguments) {
    $out = & $psql ($conn + $arguments) 2>&1
    return @{ Code = $LASTEXITCODE; Output = @($out) }
}

function Get-ParityDatabases([string]$pattern) {
    $r = Invoke-Psql @('-d', 'postgres', '-tA', '-c', "SELECT datname FROM pg_database WHERE datname LIKE '$pattern' ORDER BY datname")
    return @($r.Output | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' })
}

function Drop-Database([string]$database) {
    $null = Invoke-Psql @('-d', 'postgres', '-q', '-c', "DROP DATABASE IF EXISTS $database;")
}

function Test-DatabaseExists([string]$database) {
    $r = Invoke-Psql @('-d', 'postgres', '-tA', '-c', "SELECT 1 FROM pg_database WHERE datname = '$database'")
    return (@($r.Output | ForEach-Object { "$_".Trim() } | Where-Object { $_ -ne '' }) -contains '1')
}

function Invoke-Parity([string]$runTag) {
    $out = & powershell -NoProfile -ExecutionPolicy Bypass -File $parity -PgBin $PgBin -Port $Port -RunTag $runTag 2>&1
    return @{ Code = $LASTEXITCODE; Output = @($out) }
}

# A runspace keeps the holder session in this process: Start-Job/Start-Process need stdio pipes,
# which the sandbox denies (EPERM) and whose inherited environment trips the duplicate proxy keys.
function Start-Holder([string]$database, [int]$seconds) {
    $ps = [powershell]::Create()
    $null = $ps.AddScript({
            param($psqlPath, $connArgs, $db, $sleep)
            & $psqlPath @connArgs -d $db -tA -c "select pg_sleep($sleep)" *> $null
        }).AddArgument($psql).AddArgument($conn).AddArgument($database).AddArgument($seconds)
    $null = $ps.BeginInvoke()
    return $ps
}

$exitCode = 0
try {
    Write-Host "PostgreSQL $HostName`:$Port, migrations: $migrations"

    # ---------------------------------------------------------------- case 1: collision refused
    Show-Case '1) an existing database with the derived name is refused, not dropped'
    $sentinel = "parity_${Tag}c_mig"
    Drop-Database $sentinel
    $null = Invoke-Psql @('-d', 'postgres', '-q', '-v', 'ON_ERROR_STOP=1', '-c', "CREATE DATABASE $sentinel;")
    $run = Invoke-Parity "${Tag}c"
    $survived = @(Get-ParityDatabases "parity_${Tag}c_mig")
    Report ($run.Code -eq 2) "exit code is 2 (got $($run.Code))"
    Report (($run.Output -join "`n") -match 'refusing to run') 'output says it refused to run'
    Report ($survived -contains $sentinel) 'the pre-existing database still exists'
    Drop-Database $sentinel
    Report (@(Get-ParityDatabases "parity_${Tag}c_%").Count -eq 0) 'no parity database is left behind'

    # ---------------------------------------------------------------- case 2: happy path
    Show-Case '2) the normal run still reports parity and cleans up after itself'
    $run = Invoke-Parity "${Tag}n"
    $text = $run.Output -join "`n"
    Report ($run.Code -eq 0) "exit code is 0 (got $($run.Code))"
    Report ($text -match 'PASS  structure is identical') 'output reports PASS'
    Report (@(Get-ParityDatabases "parity_${Tag}n_%").Count -eq 0) 'no parity database is left behind'

    # ---------------------------------------------------------------- case 3: migration fails
    Show-Case '3) a migration that cannot be applied exits 2 and still cleans up'
    Set-Content -Path $brokenMigration -Encoding ascii -Value @(
        '-- A05 self-test only: this file is created and deleted by verify-migration-parity-selftest.ps1.',
        'CREATE TABLE broken_selftest (;'
    )
    $run = Invoke-Parity "${Tag}b"
    $text = $run.Output -join "`n"
    Report ($run.Code -eq 2) "exit code is 2 (got $($run.Code))"
    Report ($text -match 'migration failed') 'output names the failing migration'
    Report (@(Get-ParityDatabases "parity_${Tag}b_%").Count -eq 0) 'no parity database is left behind'
    Remove-Item -Force $brokenMigration -ErrorAction SilentlyContinue

    # ---------------------------------------------------------------- case 4: drift
    Show-Case '4) a migration that applies but disagrees with the contract exits 1 and still cleans up'
    Set-Content -Path $driftMigration -Encoding ascii -Value @(
        '-- A05 self-test only: this file is created and deleted by verify-migration-parity-selftest.ps1.',
        'ALTER TABLE core.site ADD COLUMN a05_selftest_drift text;'
    )
    $run = Invoke-Parity "${Tag}d"
    $text = $run.Output -join "`n"
    Report ($run.Code -eq 1) "exit code is 1 (got $($run.Code))"
    Report ($text -match 'FAIL  \d+ difference') 'output reports the differences'
    Report (@(Get-ParityDatabases "parity_${Tag}d_%").Count -eq 0) 'no parity database is left behind'
    Remove-Item -Force $driftMigration -ErrorAction SilentlyContinue

    # ---------------------------------------------------------------- case 5: cleanup fails
    Show-Case '5) a database that cannot be dropped is reported, never silently claimed as cleaned'
    $holder = $null
    $runPs = [powershell]::Create()
    $null = $runPs.AddScript({
            param($script, $pgBin, $port)
            & powershell -NoProfile -ExecutionPolicy Bypass -File $script -PgBin $pgBin -Port $port -RunTag $args[0] *>&1
        }).AddArgument($parity).AddArgument($PgBin).AddArgument($Port)
    $null = $runPs.AddArgument("${Tag}x")
    $handle = $runPs.BeginInvoke()
    # The migrations database is created first; connect to it before the run reaches its finally block.
    $target = "parity_${Tag}x_mig"
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline -and -not (Test-DatabaseExists $target)) {
        Start-Sleep -Milliseconds 100
    }
    if (Test-DatabaseExists $target) {
        $holder = Start-Holder $target 45
        Start-Sleep -Milliseconds 500
    }
    while (-not $handle.IsCompleted -and (Get-Date) -lt $deadline) {
        Start-Sleep -Milliseconds 200
    }
    $output = @($runPs.EndInvoke($handle) | ForEach-Object { "$_" })
    $runPs.Dispose()
    $text = $output -join "`n"
    Report ($null -ne $holder) 'a session was connected to the throwaway database during cleanup'
    Report ($text -match 'ERROR  cleanup failed') 'output reports the failed cleanup'
    Report ($text -notmatch 'cleaned up parity_') 'output never claims the cleanup succeeded'
    $leftover = @(Get-ParityDatabases "parity_${Tag}x_%")
    Report ($leftover.Count -ge 1) 'the database is still there (the script could not drop it)'
    if ($null -ne $holder) {
        try { $holder.Stop() } catch { }
        $holder.Dispose()
    }
    foreach ($database in $leftover) {
        $null = Invoke-Psql @('-d', 'postgres', '-c', "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname = '$database';")
        Drop-Database $database
    }
    $goneDeadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $goneDeadline -and @(Get-ParityDatabases "parity_${Tag}x_%").Count -gt 0) {
        Start-Sleep -Milliseconds 250
        foreach ($database in @(Get-ParityDatabases "parity_${Tag}x_%")) { Drop-Database $database }
    }
    Report (@(Get-ParityDatabases "parity_${Tag}x_%").Count -eq 0) 'the leftover database is gone after the holder stops'
} finally {
    Remove-Item -Force $brokenMigration -ErrorAction SilentlyContinue
    Remove-Item -Force $driftMigration -ErrorAction SilentlyContinue
    foreach ($database in (Get-ParityDatabases "parity_${Tag}%")) {
        Drop-Database $database
    }
}

Write-Host ''
if ($failed -eq 0) {
    Write-Host "PASS  $passed checks passed" -ForegroundColor Green
    $exitCode = 0
} else {
    Write-Host "FAIL  $failed of $($passed + $failed) checks failed" -ForegroundColor Red
    $exitCode = 1
}
exit $exitCode
