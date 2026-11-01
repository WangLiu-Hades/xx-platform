# 推送 PR #3 分支并用本文件同目录的 PR-description.md 更新 PR 说明。
# 用法（在仓库根执行，token 不落盘）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File reports\pr3-fix\push-and-update-pr.ps1 -Token <你的 GitHub PAT>
# PAT 需要该仓库的 contents:write（推送）与 pull_requests:write（改 PR 描述）权限。
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Token,
    [string]$Repo = 'WangLiu-Hades/xx-platform',
    [int]$PrNumber = 3,
    [string]$Branch = 'codex/dev01-heyifan-api-skeleton',
    [string]$BodyFile = 'reports/pr3-fix/PR-description.md'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$bodyPath = if ([System.IO.Path]::IsPathRooted($BodyFile)) { $BodyFile } else { Join-Path $repoRoot $BodyFile }
if (-not (Test-Path $bodyPath)) { throw "找不到 PR 描述文件：$bodyPath" }

$branchNow = (& git -C $repoRoot rev-parse --abbrev-ref HEAD).Trim()
if ($branchNow -ne $Branch) { throw "当前分支是 $branchNow，不是 $Branch；先切到目标分支再执行" }
$dirty = (& git -C $repoRoot status --porcelain | Out-String).Trim()
if ($dirty) { throw "工作区不干净，先提交或清理：`n$dirty" }

$localHead = (& git -C $repoRoot rev-parse HEAD).Trim()
Write-Output "本地 HEAD：$localHead"

# 用 http.extraHeader 传 token，避免写进 remote URL 或 git 配置。
# GitHub 的 git 传输只认 Basic（用户名 x-access-token、密码为 PAT），Bearer 仅适用于 REST API。
$basic = [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("x-access-token:$Token"))
Write-Output "推送 $Branch 到 origin ..."
& git -C $repoRoot -c "http.extraHeader=AUTHORIZATION: basic $basic" push origin $Branch
if ($LASTEXITCODE -ne 0) { throw "git push 失败，退出码 $LASTEXITCODE" }

$remoteLine = (& git -C $repoRoot -c "http.extraHeader=AUTHORIZATION: basic $basic" ls-remote origin "refs/heads/$Branch" | Out-String).Trim()
$remoteHead = ($remoteLine -split "`t")[0]
Write-Output "远端 HEAD：$remoteHead"
if ($remoteHead -ne $localHead) { throw "远端 HEAD 与本地不一致，PR 可能未指向新提交" }

$body = [System.IO.File]::ReadAllText($bodyPath, [System.Text.UTF8Encoding]::new($false))
$payload = @{ body = $body } | ConvertTo-Json -Compress -Depth 3
$bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)

$headers = @{
    Authorization          = "Bearer $Token"
    Accept                 = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
    'User-Agent'           = 'xx-platform-pr3-fix'
}

Write-Output "更新 PR #$PrNumber 描述（$($body.Length) 字符）..."
$response = Invoke-RestMethod -Method Patch -Uri "https://api.github.com/repos/$Repo/pulls/$PrNumber" -Headers $headers -ContentType 'application/json; charset=utf-8' -Body $bytes
Write-Output "完成：$($response.html_url)"
Write-Output "PR 当前 head sha：$($response.head.sha)"
