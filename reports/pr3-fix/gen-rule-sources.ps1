# 生成 reports/pr3-fix/rule-sources.json
# 规则唯一来源：远程 origin/main 的 git 对象（git show <M>:<path>）。
# 本脚本从 git 原文逐行提取条款文本，并记录 blob ID 与固定提交链接，避免手工誊抄改变字节。
#
# 用法：pwsh -File reports/pr3-fix/gen-rule-sources.ps1 [-RuleCommit <sha>] [-RepoRoot <path>]

param(
    [string]$RuleCommit = 'aca438ebfb218d40350b60ff933d2251bed50500',
    [string]$RepoRoot = (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
)

$ErrorActionPreference = 'Stop'
$RepoRoot = (Resolve-Path $RepoRoot).Path
$repoUrl = 'https://github.com/WangLiu-Hades/xx-platform'
$reviewHead = (& git -C $RepoRoot rev-parse 'origin/codex/dev01-heyifan-api-skeleton').Trim()
$branch = 'codex/dev01-heyifan-api-skeleton'

# 每项：id、标题、类别、主分支来源（path + 行区间 + 条款标签）
$items = @(
    @{ id='M01'; title='后台轮询仍然延长登录时间'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/暂定规则明细.md'; from=14; to=14; clause='P-001-02 会话续期/绝对期限/轮换/60秒节流' },
         @{ path='contracts/api/api-conventions.md'; from=125; to=125; clause='会话：主动请求才滑动空闲期，探针/轮询不续期' }
       ) },
    @{ id='M02'; title='登录来源约束缺失'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/暂定规则明细.md'; from=15; to=15; clause='P-001-03 登录来源内网CIDR/IP、UA变化重新登录' }
       ) },
    @{ id='M03'; title='登录后旧CSRF凭据仍有效，取令牌响应可被缓存'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/暂定规则明细.md'; from=14; to=14; clause='P-001-02 登录/改密/提权轮换并撤销旧值' },
         @{ path='contracts/api/openapi.yaml'; from=158; to=161; clause='GET /auth/csrf：同源校验、SID轮换后重新获取、禁止缓存' }
       ) },
    @{ id='M04'; title='初始密码可直接办理业务，本人改密入口缺失'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/暂定规则明细.md'; from=67; to=67; clause='P-021-01 密码策略/初始密码首次登录只放行改密' },
         @{ path='contracts/api/api-conventions.md'; from=51; to=51; clause='本人改密须currentPassword并撤销其他会话' }
       ) },
    @{ id='M05'; title='过期或已经请求取消的任务仍能提交成功'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/INTERNAL_TASK_PROTOCOL.md'; from=16; to=16; clause='heartbeat：每10秒，延长至now+60秒，0行为LEASE_LOST' },
         @{ path='docs/project-governance/INTERNAL_TASK_PROTOCOL.md'; from=18; to=18; clause='finish：同事务校验租约/取消/当前授权/结果引用' },
         @{ path='docs/project-governance/INTERNAL_TASK_PROTOCOL.md'; from=23; to=23; clause='Lease固定字段与写操作条件（taskId/owner/generation/leaseUntil>now）' }
       ) },
    @{ id='M06'; title='任务分配、预算和时限没有按协议统一'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/INTERNAL_TASK_PROTOCOL.md'; from=15; to=15; clause='claim：priority高值优先、submittedAt/id升序、SKIP LOCKED、supportedTypes/limit' },
         @{ path='docs/project-governance/暂定规则明细.md'; from=22; to=22; clause='P-003-01 排队5/15分钟与执行时限从RUNNING开始' },
         @{ path='docs/project-governance/暂定规则明细.md'; from=23; to=23; clause='P-003-02 每用户2重任务/5排队、重计算2并发、I/O2并发（1槽保留）' }
       ) },
    @{ id='M07'; title='外部动作失去租约后会被直接重排'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/INTERNAL_TASK_PROTOCOL.md'; from=20; to=20; clause='retry：仅FAILED且同输入快照；UNKNOWN副作用先对账' },
         @{ path='docs/project-governance/INTERNAL_TASK_PROTOCOL.md'; from=27; to=27; clause='租约失效由reaper递增generation；外部动作UNKNOWN不自动重发' },
         @{ path='docs/project-governance/暂定规则明细.md'; from=60; to=60; clause='P-018-02 RESET不自动重试、命令TTL120秒转UNKNOWN' }
       ) },
    @{ id='M08'; title='通用任务结果引用不完整'; category='明确规则下的实现问题'
       sources=@(
         @{ path='contracts/api/openapi.yaml'; from=19020; to=19036; clause='TaskResultReference schema（resultType枚举与各类型引用字段）' },
         @{ path='contracts/api/api-conventions.md'; from=82; to=82; clause='按任务类型的 resultReference 对照表' }
       ) },
    @{ id='M09'; title='审批资格和人数不能只按操作权限判断'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md'; from=8; to=8; clause='审批人=部署配置+动作权限+当前范围；申请/执行人不得审批；1名/2名' },
         @{ path='docs/project-governance/暂定规则明细.md'; from=63; to=63; clause='P-019-02 审批人数、自审限制、24小时/10分钟/5分钟时限' }
       ) },
    @{ id='M10'; title='审批提案缺少严格校验和服务器冻结'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md'; from=9; to=9; clause='提案仅收operationId/路径/body/If-Match；服务端冻结并对快照算SHA-256' },
         @{ path='docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md'; from=15; to=15; clause='requestDecryption冻结context/采集集合/recipient' },
         @{ path='docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md'; from=17; to=17; clause='createActionApproval复用有效审批，不制造多份许可' },
         @{ path='contracts/api/openapi.yaml'; from=38330; to=38350; clause='UpdateDeviceParametersApprovalProposal：严格Schema/pattern/additionalProperties=false' }
       ) },
    @{ id='M11'; title='审批清单泄露嵌套敏感值并允许截断'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md'; from=34; to=34; clause='reviewItems完整显示目标/版本/范围/用途；敏感值掩码；不允许截断后批准' },
         @{ path='contracts/api/openapi.yaml'; from=39326; to=39348; clause='ActionApproval 列表/详情分页 schema（items/nextCursor/pageSize）' }
       ) },
    @{ id='M12'; title='通用数据库配置会自动启用仓库固定审批密钥'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md'; from=21; to=21; clause='action_approval：部署密钥认证加密、随机nonce、禁止明文落JSONB' },
         @{ path='docs/project-governance/暂定规则明细.md'; from=36; to=36; clause='P-008-02 密钥由部署方注入，禁止仓库存储' }
       ) },
    @{ id='M13'; title='敏感导出没有验证和消费审批许可'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md'; from=7; to=7; clause='需审批的动作清单（含敏感批量导出定义）' },
         @{ path='docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md'; from=11; to=11; clause='X-Approval-Id：同事务锁审批/复核/消费/写领域动作；过期410、冲突409' },
         @{ path='contracts/api/openapi.yaml'; from=843; to=860; clause='createExport：请求头与响应/错误码' }
       ) },
    @{ id='M14'; title='导出、发布和下载没有完整复核当前数据范围'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/暂定规则明细.md'; from=20; to=20; clause='P-002-05 缩权立即生效、发布与下载复核当前权限' },
         @{ path='docs/project-governance/暂定规则明细.md'; from=38; to=38; clause='P-009-01 Context TTL30分钟、每次消费重校权限' },
         @{ path='docs/project-governance/暂定规则明细.md'; from=40; to=40; clause='P-009-03 导出异步上限与短时下载5分钟每次重鉴权' },
         @{ path='contracts/api/api-conventions.md'; from=50; to=50; clause='列表/详情/Context/任务/导出/文件每次重校范围' }
       ) },
    @{ id='M15'; title='多个写接口丢弃幂等键'; category='明确规则下的实现问题'
       sources=@(
         @{ path='contracts/api/api-conventions.md'; from=57; to=57; clause='Idempotency-Key 16–160字符、作用域账号+operationId+键、同输入返原响应' },
         @{ path='docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md'; from=10; to=10; clause='审批时限与重复票：相同Idempotency-Key返回原决定' },
         @{ path='contracts/api/openapi.yaml'; from=17294; to=17302; clause='IdempotencyKey 头定义（16–160）' }
       ) },
    @{ id='M16'; title='同时创建同键任务时一个请求失败'; category='明确规则下的实现问题'
       sources=@(
         @{ path='contracts/api/api-conventions.md'; from=57; to=57; clause='相同输入返回原响应、不同输入409' },
         @{ path='contracts/database/data-dictionary.md'; from=829; to=829; clause='唯一约束 (owner_user_id,task_type,idempotency_key)' }
       ) },
    @{ id='M17'; title='上传策略与下发规则不一致，普通导入被限成10MiB'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/暂定规则明细.md'; from=23; to=23; clause='P-003-02 图片10MiB、普通导入100MiB/10万行' },
         @{ path='contracts/api/api-conventions.md'; from=53; to=53; clause='uploadPolicies按category返回限制，实际取全局与分类较小值' },
         @{ path='contracts/api/api-conventions.md'; from=100; to=100; clause='multipart 必填category、读uploadPolicies、服务端不信扩展名' },
         @{ path='contracts/database/data-dictionary.md'; from=334; to=336; clause='core.upload_policy 上传白名单' }
       ) },
    @{ id='M18'; title='无效内容被当成合格文件开放'; category='明确规则下的实现问题'
       sources=@(
         @{ path='contracts/api/api-conventions.md'; from=100; to=100; clause='实际内容校验（非扩展名）' },
         @{ path='contracts/api/api-conventions.md'; from=101; to=101; clause='413/415；PROCESSING→ACTIVE/QUARANTINED；只有ACTIVE可引用' }
       ) },
    @{ id='M19'; title='文件未设置到期时间，详情还漏返必填字段'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/project-governance/RETENTION_POLICY.md'; from=7; to=7; clause='12个自然月，目标月无同日取月末，不按365天' },
         @{ path='docs/project-governance/RETENTION_POLICY.md'; from=11; to=11; clause='文件按自身ACTIVE时间计算12个自然月' },
         @{ path='contracts/api/openapi.yaml'; from=18707; to=18760; clause='FileAsset schema（expiresAt 必填/可空）' },
         @{ path='contracts/database/data-dictionary.md'; from=604; to=606; clause='core.file_asset 受控文件元数据' }
       ) },
    @{ id='M20'; title='关键业务操作没有正式审计记录'; category='明确规则下的实现问题'
       sources=@(
         @{ path='docs/requirements/source/平台功能设计（原始）.pdf'; pdfPage=25; clause='原始功能要求：操作审计（PDF第25页）' },
         @{ path='docs/project-governance/暂定规则明细.md'; from=70; to=70; clause='P-021-04 必须服务端审计的事件清单与拒绝/失败' },
         @{ path='contracts/database/data-dictionary.md'; from=3265; to=3269; clause='audit.audit_event 审计表' }
       ) },
    @{ id='M21'; title='开发跨源配置不允许审批请求头'; category='开发联调方式的待完善事项'
       sources=@(
         @{ path='contracts/api/openapi.yaml'; from=17321; to=17325; clause='X-Approval-Id 请求头定义' },
         @{ path='contracts/api/FRONTEND_INTEGRATION.md'; from=1; to=12; clause='默认前端代理联调路径与端口' }
       ) },
    @{ id='E01'; title='迁移验证脚本会把查询失败报告成清理成功'; category='已复现的工程脚本错误'
       sources=@(
         @{ path='docs/system-design/10_开发联调与集成方案.md'; from=50; to=50; clause='合并门禁：迁移空库/升级库必须可验证' }
       ) }
)

function Get-BlobId([string]$path) {
    $id = & git -C $RepoRoot rev-parse "${RuleCommit}:${path}"
    if ($LASTEXITCODE -ne 0) { throw "git rev-parse 失败：${RuleCommit}:${path}" }
    return $id.Trim()
}

$out = [System.Collections.Generic.List[object]]::new()
foreach ($item in $items) {
    $ruleSources = [System.Collections.Generic.List[object]]::new()
    foreach ($src in $item.sources) {
        $blob = Get-BlobId $src.path
        $encoded = ($src.path -split '/' | ForEach-Object { [System.Uri]::EscapeDataString($_) }) -join '/'
        $permalink = "$repoUrl/blob/$RuleCommit/$encoded"
        $excerpt = $null
        if ($src.ContainsKey('pdfPage')) {
            # PDF 是二进制：只记录文件级 blob 与页码，文本以 PDF 阅读结果为准（见 issue-matrix 说明）
            $size = (& git -C $RepoRoot cat-file -s "${RuleCommit}:$($src.path)").Trim()
            $permalink = "$permalink#page=$($src.pdfPage)"
            $excerpt = "（PDF 二进制，blob 大小 ${size} 字节；条款位于第 $($src.pdfPage) 页，本轮操作审计以 P-021-04 与 audit.audit_event 结构为可逐字核对的补充来源）"
        } else {
            $lines = & git -C $RepoRoot show "${RuleCommit}:$($src.path)" | Select-Object -Skip ($src.from - 1) -First ($src.to - $src.from + 1)
            if ($null -eq $lines -or @($lines).Count -eq 0) { throw "提取为空：$($src.path):$($src.from)" }
            $excerpt = (@($lines) | ForEach-Object { $_.TrimEnd() }) -join "`n"
            if ($excerpt.Trim().Length -eq 0) { throw "取到的行为空：$($src.path):$($src.from)" }
            if ($src.to -ne $src.from) { $permalink = "$permalink#L$($src.from)-L$($src.to)" } else { $permalink = "$permalink#L$($src.from)" }
        }
        $ruleSources.Add([ordered]@{
            path      = $src.path
            blobId    = $blob
            permalink = $permalink
            clause    = $src.clause
            lineRange = if ($src.ContainsKey('pdfPage')) { "p.$($src.pdfPage)" } else { "L$($src.from)-L$($src.to)" }
            status    = '已确认规则（主分支原文；暂定条目保留其 USER_CONFIRMED/暂定状态）'
            excerpt   = $excerpt
        })
    }
    $out.Add([ordered]@{
        item         = $item.id
        title        = $item.title
        category     = $item.category
        ruleSources  = $ruleSources
    })
}

$doc = [ordered]@{
    schemaVersion      = 1
    generatedAt        = (Get-Date).ToUniversalTime().ToString('yyyy-MM-ddTHH:mm:ssZ')
    generatedBy        = 'reports/pr3-fix/gen-rule-sources.ps1'
    repository         = $repoUrl
    ruleCommit         = $RuleCommit
    ruleCommitFull     = (& git -C $RepoRoot rev-parse $RuleCommit).Trim()
    reviewStartCommit  = $reviewHead
    targetBranch       = 'main'
    fixBranch          = $branch
    sourceDiscipline   = @(
        '规则文本一律由 git show <ruleCommit>:<path> 从远程主分支对象逐行取出，不使用工作区副本、PR 分支自己修改的契约或本机旧报告。',
        'blobId 由 git rev-parse <ruleCommit>:<path> 取得，permalink 指向该固定提交的 GitHub 原文，可直接复核字节。',
        '暂定规则（暂定规则明细.md 等）保留其"暂定/USER_CONFIRMED"状态，未冒充生产批准。'
    )
    entries            = $out
}

$target = Join-Path $RepoRoot 'reports/pr3-fix/rule-sources.json'
$json = $doc | ConvertTo-Json -Depth 8
[System.IO.File]::WriteAllText($target, $json + "`n", [System.Text.UTF8Encoding]::new($false))
"written: $target ($((Get-Item $target).Length) bytes, $($out.Count) items, $(($out | ForEach-Object { $_.ruleSources.Count } | Measure-Object -Sum).Sum) rule sources)"
