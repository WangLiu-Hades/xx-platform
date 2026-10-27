# apps/api skeleton environment self-check
#
# Usage (from repo root or apps/api):
#   powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/dev-check.ps1
#   pwsh -File apps/api/scripts/dev-check.ps1
#
# Does three things: check toolchain versions, generate and verify once, print next steps.
# It does not start the service and does not modify source files (output goes to target/).
#
# NOTE: keep this file ASCII-only. Windows PowerShell 5.1 reads .ps1 as ANSI unless the
# file has a UTF-8 BOM, so non-ASCII text here can break parsing on that host.

$ErrorActionPreference = 'Continue'
$apiDir = Split-Path -Parent $PSScriptRoot   # apps/api
$ok = $true

function Show-Step($text) { Write-Host "`n== $text" -ForegroundColor Cyan }

Show-Step 'Toolchain'

try {
    $javaRaw = (& java -version 2>&1 | Select-Object -First 1).ToString()
    if ($javaRaw -match '"(\d+)') {
        $major = [int]$Matches[1]
        if ($major -ge 17) {
            Write-Host "  [OK]   Java $major  ($javaRaw)" -ForegroundColor Green
        } else {
            Write-Host "  [FAIL] Java 17+ required, found $major" -ForegroundColor Red
            $ok = $false
        }
    } else {
        Write-Host "  [WARN] cannot parse java version: $javaRaw" -ForegroundColor Yellow
    }
} catch {
    Write-Host '  [FAIL] java not found; install JDK 17 and set JAVA_HOME' -ForegroundColor Red
    $ok = $false
}

try {
    $mvnRaw = (& mvn -v 2>&1 | Select-Object -First 1).ToString()
    if ($mvnRaw -match '(\d+\.\d+\.\d+)') {
        Write-Host "  [OK]   $mvnRaw" -ForegroundColor Green
    } else {
        Write-Host "  [WARN] unexpected mvn -v output: $mvnRaw" -ForegroundColor Yellow
    }
} catch {
    Write-Host '  [FAIL] mvn not found; install Maven 3.9+ and add it to PATH' -ForegroundColor Red
    $ok = $false
}

Write-Host "  [INFO] JAVA_HOME = $env:JAVA_HOME"

if (-not $ok) {
    Write-Host "`nToolchain requirements not met; fix them before continuing." -ForegroundColor Red
    exit 1
}

Show-Step 'Clean contract build (mvn -B -Pgenerate-api clean verify)'
Push-Location $apiDir
try {
    & mvn -B -Pgenerate-api clean verify
    if ($LASTEXITCODE -eq 0) {
        Write-Host '  [OK]   clean verify passed' -ForegroundColor Green
    } else {
        Write-Host '  [FAIL] clean verify failed; see Maven output above' -ForegroundColor Red
        Write-Host '         If it cannot resolve a plugin/dependency you may be offline:' -ForegroundColor Yellow
        Write-Host '         compile dependencies are usually cached, but plugins' -ForegroundColor Yellow
        Write-Host '         (clean/package/openapi-generator) need one online fetch.' -ForegroundColor Yellow
        $ok = $false
    }
} finally {
    Pop-Location
}

Show-Step 'Next steps'

if ($ok) {
    Write-Host @'
  1) Start with the self-check endpoint (the /smoke route exists only under the dev profile):
       cd apps/api
       mvn -B -Pgenerate-api spring-boot:run "-Dspring-boot.run.profiles=dev"

  2) In another terminal, verify the contract envelope:
       curl.exe http://127.0.0.1:8080/api/v1/smoke
       curl.exe http://127.0.0.1:8080/api/v1/no-such-route

     Expected: the first returns code="0" with checkedAt inside data; the second returns
     404 with data=null and details=[]; for both, the X-Trace-Id response header equals
     the traceId inside the body.

  3) Generate contract Java interfaces/DTOs (first run needs network):
       mvn -B -Pgenerate-api clean verify

  4) Read the module split before starting work:
       docs/project-governance/BACKEND_TWO_DEV_SPLIT.md
'@ -ForegroundColor Gray
    exit 0
} else {
    Write-Host '  Resolve the FAIL items above first.' -ForegroundColor Yellow
    exit 1
}
