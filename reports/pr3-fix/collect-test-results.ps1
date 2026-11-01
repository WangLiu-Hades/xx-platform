# 运行后端测试并把结果整理为 reports/pr3-fix/test-results.json
#
# 只做两件事：真实执行 `mvn -B clean test`（真库用例通过环境变量真正执行），
# 然后把 Surefire XML 汇总成机读结果，避免手工誊抄通过/失败数。
#
# 用法示例：
#   powershell -NoProfile -ExecutionPolicy Bypass -File reports/pr3-fix/collect-test-results.ps1 `
#       -DbUrl 'jdbc:postgresql://127.0.0.1:55433/xx_it' -Label 'full-with-real-db'

param(
    [string]$DbUrl = 'jdbc:postgresql://127.0.0.1:55433/xx_it',
    [string]$DbUser = 'postgres',
    [string]$DbPassword = '',
    [string]$Label = 'full-with-real-db',
    [string]$RepoRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)),
    [string]$OutFile = ''
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path $RepoRoot).Path
$apiDir = Join-Path $RepoRoot 'apps/api'
$reportDir = Join-Path $RepoRoot 'reports/pr3-fix'
$logDir = Join-Path $reportDir 'logs'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
if (-not $OutFile) { $OutFile = Join-Path $reportDir 'test-results.json' }
$logFile = Join-Path $logDir "$Label.log"

function Get-CommandOutput([string]$file, [string[]]$arguments) {
    # 原生命令把版本号写到 stderr（java -version），默认 $ErrorActionPreference=Stop 会把它当终止错误；
    # 这里临时放宽，只要拿到输出就返回，拿不到才写 unavailable。
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { (& $file @arguments 2>&1 | Out-String).Trim() } catch { "unavailable: $($_.Exception.Message)" }
    finally { $ErrorActionPreference = $previous }
}

$startedAt = (Get-Date).ToUniversalTime()
$previousUrl = $env:XX_TASK_IT_DB_URL
$previousUser = $env:XX_TASK_IT_DB_USER
$previousPassword = $env:XX_TASK_IT_DB_PASSWORD
$env:XX_TASK_IT_DB_URL = $DbUrl
$env:XX_TASK_IT_DB_USER = $DbUser
$env:XX_TASK_IT_DB_PASSWORD = $DbPassword

Push-Location $apiDir
try {
    $output = & mvn -B clean test 2>&1
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
$finishedAt = (Get-Date).ToUniversalTime()
$output | Set-Content -Path $logFile -Encoding UTF8

$suites = @()
$surefireDir = Join-Path $apiDir 'target/surefire-reports'
if (Test-Path $surefireDir) {
    foreach ($file in Get-ChildItem $surefireDir -Filter '*.xml' | Sort-Object Name) {
        [xml]$doc = Get-Content $file.FullName -Encoding UTF8
        $suite = $doc.testsuite
        if ($null -eq $suite) { continue }
        $cases = @()
        foreach ($case in $suite.testcase) {
            $status = 'passed'
            if ($case.failure -or $case.error) { $status = 'failed' }
            elseif ($case.skipped) { $status = 'skipped' }
            $cases += [pscustomobject]@{
                name     = [string]$case.name
                status   = $status
                duration = [double]$case.time
                message  = if ($case.failure) { ("" + $case.failure.message).Trim() } elseif ($case.error) { ("" + $case.error.message).Trim() } elseif ($case.skipped) { ("" + $case.skipped.message).Trim() } else { $null }
            }
        }
        $suites += [pscustomobject]@{
            className = $suite.name
            tests     = [int]$suite.tests
            failures  = [int]$suite.failures
            errors    = [int]$suite.errors
            skipped   = [int]$suite.skipped
            time      = [double]$suite.time
            cases     = $cases
        }
    }
}

$totalTests = ($suites | Measure-Object -Property tests -Sum).Sum
$totalFailures = ($suites | Measure-Object -Property failures -Sum).Sum
$totalErrors = ($suites | Measure-Object -Property errors -Sum).Sum
$totalSkipped = ($suites | Measure-Object -Property skipped -Sum).Sum

# 真库用例是否真跑：统计受 XX_TASK_IT_DB_URL 门控的类有没有被 skip
$dbGated = $suites | Where-Object { $_.cases | Where-Object { $_.message -match 'XX_TASK_IT_DB_URL' } }
$dbGatedSkipped = 0
foreach ($s in $suites) { $dbGatedSkipped += @($s.cases | Where-Object { $_.status -eq 'skipped' -and $_.message -match 'XX_TASK_IT_DB_URL' }).Count }

$mvnVersion = (Get-CommandOutput 'mvn' @('-v')) -split "`n" | Select-Object -First 1
$javaVersion = (Get-CommandOutput 'java' @('-version')) -split "`n" | Select-Object -First 1
$psqlVersion = Get-CommandOutput 'psql' @('--version')
$nodeVersion = Get-CommandOutput 'node' @('--version')
$gitHead = (& git -C $RepoRoot rev-parse HEAD).Trim()
$gitBranch = (& git -C $RepoRoot rev-parse --abbrev-ref HEAD).Trim()
$statusPorcelain = (& git -C $RepoRoot status --porcelain | Out-String).Trim()

$report = [ordered]@{
    schemaVersion   = 1
    label           = $Label
    startedAtUtc    = $startedAt.ToString('yyyy-MM-ddTHH:mm:ssZ')
    finishedAtUtc   = $finishedAt.ToString('yyyy-MM-ddTHH:mm:ssZ')
    durationSeconds = [math]::Round(($finishedAt - $startedAt).TotalSeconds, 1)
    command         = 'mvn -B clean test'
    workingDirectory = 'apps/api'
    exitCode        = $exitCode
    logFile         = "reports/pr3-fix/logs/$Label.log"
    database        = [ordered]@{
        url             = $DbUrl
        user            = $DbUser
        password        = '（空；本地信任认证）'
        realDbTestsRan  = ($dbGatedSkipped -eq 0)
        skippedDueToDb  = $dbGatedSkipped
    }
    environment     = [ordered]@{
        os        = $(try { (Get-CimInstance Win32_OperatingSystem).Caption } catch { [Environment]::OSVersion.VersionString })
        java      = $javaVersion
        maven     = $mvnVersion
        node      = $nodeVersion
        psql      = $psqlVersion
        gitHead   = $gitHead
        gitBranch = $gitBranch
    }
    totals          = [ordered]@{
        tests    = [int]$totalTests
        failures = [int]$totalFailures
        errors   = [int]$totalErrors
        skipped  = [int]$totalSkipped
        suites   = $suites.Count
    }
    workingTree     = $statusPorcelain
    suites          = $suites
}

$json = $report | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($OutFile, $json + "`n", [System.Text.UTF8Encoding]::new($false))

# 还原环境变量，避免影响调用方会话
$env:XX_TASK_IT_DB_URL = $previousUrl
$env:XX_TASK_IT_DB_USER = $previousUser
$env:XX_TASK_IT_DB_PASSWORD = $previousPassword

"exit=$exitCode tests=$totalTests failures=$totalFailures errors=$totalErrors skipped=$totalSkipped dbSkipped=$dbGatedSkipped suites=$($suites.Count) -> $OutFile"
