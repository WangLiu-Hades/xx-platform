# apps/api database migration helper (Flyway)
#
# Usage (from repo root, or anywhere):
#   # 1) set connection info as environment variables (never commit passwords)
#   $env:DB_URL      = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
#   $env:DB_USER     = 'xxplatform_owner'
#   $env:DB_PASSWORD = '...'
#
#   # 2) run a flyway command
#   powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/db-migrate.ps1 -Command info
#   powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/db-migrate.ps1 -Command migrate
#
# Supported -Command values map 1:1 to flyway Maven goals:
#   info | migrate | validate | repair | baseline | clean-check
#
# Why a wrapper instead of calling mvn directly:
#   - fails fast with a readable message when DB_URL/DB_USER/DB_PASSWORD are missing,
#     instead of a Flyway stack trace that buries the real cause;
#   - refuses obviously wrong PostgreSQL versions before touching anything;
#   - keeps the password out of shell history and out of any committed file.
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 as ANSI unless the
# file has a UTF-8 BOM, so non-ASCII text here can break parsing on that host.

param(
    [ValidateSet('info', 'migrate', 'validate', 'repair', 'baseline', 'clean-check')]
    [string]$Command = 'info',

    # Optional: override the local Maven repository (useful on machines where
    # ~/.m2 is read-only, e.g. a confined sandbox).
    [string]$LocalRepo = ''
)

$ErrorActionPreference = 'Stop'
$apiDir = Split-Path -Parent $PSScriptRoot   # apps/api

function Fail($msg) {
    Write-Host "  [FAIL] $msg" -ForegroundColor Red
    exit 1
}

# ---------------------------------------------------------------- preflight
if (-not $env:DB_URL)      { Fail 'DB_URL is not set (e.g. jdbc:postgresql://127.0.0.1:5432/xxplatform)' }
if (-not $env:DB_USER)     { Fail 'DB_USER is not set (use the migration owner account, not the app account)' }
if (-not $env:DB_PASSWORD) { Fail 'DB_PASSWORD is not set' }

if ($env:DB_URL -notmatch '^jdbc:postgresql://') {
    Fail "DB_URL must start with jdbc:postgresql:// (got: $env:DB_URL)"
}

Write-Host "== Flyway: $Command" -ForegroundColor Cyan
Write-Host "   url  : $env:DB_URL"
Write-Host "   user : $env:DB_USER"
Write-Host "   pass : ***(hidden)"

# ---------------------------------------------------------------- guard: never clean
# The pom sets cleanDisabled=true; refuse anyway so a typo cannot drop a schema.
if ($Command -eq 'clean-check') {
    Write-Host "`n  [INFO] Flyway 'clean' is intentionally disabled (cleanDisabled=true)." -ForegroundColor Yellow
    Write-Host "         Dropping all objects is never a routine step here; use a fresh database instead." -ForegroundColor Yellow
    exit 0
}

# ---------------------------------------------------------------- run
Push-Location $apiDir
try {
    $args = @('-B')
    if ($LocalRepo) { $args += "-Dmaven.repo.local=$LocalRepo" }
    $args += @('-Pdb-migrate', "flyway:$Command")

    & mvn @args
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

Write-Host ''
if ($code -eq 0) {
    Write-Host "  [OK] flyway:$Command succeeded" -ForegroundColor Green
    exit 0
}

Write-Host "  [FAIL] flyway:$Command failed (exit $code)" -ForegroundColor Red
Write-Host '         Common causes:' -ForegroundColor Yellow
Write-Host '           28P01  wrong user/password' -ForegroundColor Yellow
Write-Host '           3D000  database does not exist yet (create it first)' -ForegroundColor Yellow
Write-Host '           0A000  PostGIS missing or PostGIS 3.0.x on PG14 (V1 guard rejects it)' -ForegroundColor Yellow
Write-Host '           checksum mismatch on V1  an already-applied migration was edited' -ForegroundColor Yellow
exit 1
