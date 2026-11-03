# apps/api database provisioning (one-time, superuser required)
#
# Usage (superuser credentials required; never commit them):
#   $env:PGSUPERUSER_PASSWORD = '...'
#   powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/db-provision.ps1 `
#       -Database xxplatform -Owner xxplatform_owner -OwnerPassword '...'
#
# Creates, idempotently:
#   1. the migration owner role (LOGIN, NOSUPERUSER, NOCREATEDB, NOCREATEROLE)
#   2. a UTF8 database owned by that role
#   3. the PostGIS extension INSIDE that database
#
# Why step 3 cannot be part of the Flyway migration:
#   `CREATE EXTENSION postgis` requires superuser in a stock PostgreSQL install, and it
#   also emits "cannot run inside a transaction block" for parts of its install script.
#   Flyway runs each migration inside a transaction, so even with superuser it would warn.
#   The pristine, repeatable shape is therefore: provision the extension as superuser once,
#   then let the migration owner own everything else (163 tables, indexes, triggers, seeds).
#
#   V1 still contains `CREATE EXTENSION IF NOT EXISTS postgis;` (it is a byte-for-byte copy
#   of contracts/database/schema.sql, guarded by MigrationBaselineTests). With the extension
#   already present that statement becomes a warning, not an error - the migration succeeds.
#
# Safety: this script never drops anything. It refuses to touch an existing database that
# already has project schemas without an explicit -Force.
#
# NOTE: keep this file ASCII-only (Windows PowerShell 5.1 reads .ps1 as ANSI without a BOM).

param(
    [string]$Database = 'xxplatform',
    [string]$Owner    = 'xxplatform_owner',
    [Parameter(Mandatory = $true)][string]$OwnerPassword,

    [string]$PgHost   = '127.0.0.1',
    [int]   $PgPort   = 5432,
    [string]$SuperUser = 'postgres',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$psql = 'C:\Program Files\PostgreSQL\16\bin\psql.exe'
if (-not (Test-Path $psql)) {
    $cmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($cmd) { $psql = $cmd.Source } else { Write-Host '  [FAIL] psql not found' -ForegroundColor Red; exit 1 }
}
if (-not $env:PGSUPERUSER_PASSWORD) {
    Write-Host '  [FAIL] PGSUPERUSER_PASSWORD is not set (password of the superuser)' -ForegroundColor Red
    exit 1
}

$env:PGPASSWORD = $env:PGSUPERUSER_PASSWORD
$env:PGCONNECT_TIMEOUT = '10'
$base = @('-w', '-h', $PgHost, '-p', "$PgPort", '-U', $SuperUser)

function Invoke-Psql([string]$db, [string]$sql) {
    $out = & $psql @base -d $db -v ON_ERROR_STOP=1 -tAc $sql 2>&1
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $($out -join ' ')" }
    return ($out | Out-String).Trim()
}

Write-Host "== Provisioning database '$Database' (owner '$Owner')" -ForegroundColor Cyan

# ---------------------------------------------------------------- 1) role
$roleExists = Invoke-Psql 'postgres' "select 1 from pg_roles where rolname = '$Owner';"
if ($roleExists -eq '1') {
    Write-Host "  [SKIP] role '$Owner' already exists" -ForegroundColor Yellow
    Invoke-Psql 'postgres' "ALTER ROLE $Owner WITH LOGIN PASSWORD '$OwnerPassword';" | Out-Null
    Write-Host "  [OK]   role password reset" -ForegroundColor Green
} else {
    Invoke-Psql 'postgres' "CREATE ROLE $Owner LOGIN PASSWORD '$OwnerPassword' NOSUPERUSER NOCREATEDB NOCREATEROLE;" | Out-Null
    Write-Host "  [OK]   role created" -ForegroundColor Green
}

# ---------------------------------------------------------------- 2) database
$dbExists = Invoke-Psql 'postgres' "select 1 from pg_database where datname = '$Database';"
if ($dbExists -eq '1') {
    if (-not $Force) {
        $hasProject = Invoke-Psql $Database "select count(*) from information_schema.tables where table_schema in ('auth','core','capture','analysis','operation','audit');"
        if ($hasProject -ne '0') {
            Write-Host "  [FAIL] database '$Database' already contains $hasProject project tables." -ForegroundColor Red
            Write-Host '         Refusing to touch it. Use a fresh database name, or pass -Force if you really mean it.' -ForegroundColor Yellow
            exit 1
        }
    }
    Write-Host "  [SKIP] database '$Database' already exists" -ForegroundColor Yellow
} else {
    Invoke-Psql 'postgres' "CREATE DATABASE $Database OWNER $Owner ENCODING 'UTF8';" | Out-Null
    Write-Host "  [OK]   database created (UTF8)" -ForegroundColor Green
}

# ---------------------------------------------------------------- 3) extension
# Detect via pg_extension, NOT via postgis_lib_version(): that function does not exist
# before the extension is installed, so calling it first raises instead of returning empty.
# Always provision the extension as superuser: a plain DB owner cannot create it at all,
# and even a superuser gets a warning when doing it inside Flyway's transaction.
$extInstalled = Invoke-Psql $Database "select count(*) from pg_extension where extname = 'postgis';"
if ($extInstalled -ne '0') {
    Write-Host "  [SKIP] PostGIS extension already installed" -ForegroundColor Yellow
} else {
    Invoke-Psql $Database 'CREATE EXTENSION IF NOT EXISTS postgis;' | Out-Null
    Write-Host "  [OK]   PostGIS extension installed" -ForegroundColor Green
}

# Read the version only when the extension is guaranteed present
$extVersion = Invoke-Psql $Database 'select postgis_lib_version();'
Write-Host "  [OK]   PostGIS $extVersion" -ForegroundColor Green

# ---------------------------------------------------------------- 4) sanity
$pgVersion = Invoke-Psql $Database "show server_version_num;"
if ([int]$pgVersion -lt 140000) {
    Write-Host "  [FAIL] PostgreSQL 14+ required (server_version_num=$pgVersion)" -ForegroundColor Red
    exit 1
}
$parts = $extVersion.Split('.')
$major = [int]$parts[0]; $minor = [int]$parts[1]
if ($major -lt 3 -or ($major -eq 3 -and $minor -eq 0)) {
    Write-Host "  [FAIL] PostGIS 3.0.x is not compatible with PG14+ (found $extVersion)" -ForegroundColor Red
    exit 1
}
Write-Host "  [OK]   preconditions met (PG $pgVersion, PostGIS $extVersion)" -ForegroundColor Green

# ---------------------------------------------------------------- 5) schema ownership
# The database owner may create schemas, but the six project schemas are created here
# explicitly so the invariant is visible and re-runnable, not implicit.
foreach ($s in @('auth','core','capture','analysis','operation','audit')) {
    Invoke-Psql $Database "CREATE SCHEMA IF NOT EXISTS $s AUTHORIZATION $Owner;" | Out-Null
}
Write-Host "  [OK]   six project schemas exist, owned by $Owner" -ForegroundColor Green

# ---------------------------------------------------------------- next
Write-Host "`nNext: grant schema ownership so the migration owner can create tables" -ForegroundColor Cyan
Write-Host "  (the migration itself runs as '$Owner', not as superuser)"
Write-Host @"

  `$env:DB_URL      = 'jdbc:postgresql://${PgHost}:${PgPort}/${Database}'
  `$env:DB_USER     = '$Owner'
  `$env:DB_PASSWORD = '<owner password>'
  cd apps/api
  mvn -Pdb-migrate flyway:migrate
"@ -ForegroundColor Gray
exit 0
