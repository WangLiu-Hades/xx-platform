# GitHub 上传、分角色拉取与开发操作指南

适用仓库：<https://github.com/WangLiu-Hades/xx-platform>。编写日期：2026-10-13。主要操作环境：Windows PowerShell。本指南提供操作步骤，没有替你提交或推送代码。

本地待上传源目录：`C:\Users\C-Linglong\Desktop\Claude杂项\平台 - 路径优化`。成员电脑无需建立相同的桌面路径，下面统一以 `C:\Work` 为示例工作区。

## 1. 先选择你的操作路线

| 操作者 | 顺序 | 完成标志 |
| --- | --- | --- |
| 管理员，首次把优化后的项目放到仓库 | 第2节准备 → 第3节上传 → 第4节配置协作 | 导入PR合并，main含apps、contracts、docs等目录 |
| 后端 DEV-01 | 第2节 → 第5节拉取 → 第6节后端任务 → 第7～9节开发提交 | 本机有后端目录、共用契约与需求，个人任务分支已建立 |
| 前端 DEV-02 | 第2节 → 第5节拉取 → 第6节前端启动 → 第7～9节开发提交 | 本机前端可运行，修改进入自己的PR |
| 数据/算法 DEV-03 | 第2节 → 第5节拉取 → 第6节数据任务 → 第7～9节开发提交 | 本机有Worker、数据库设计与任务输入 |
| 测试/部署 DEV-04 | 第2节 → 第5节拉取 → 第6节质量任务 → 第7～9节开发提交 | 本机有完整项目，可运行现有门禁并建立后续验收 |

建议首次加入的人完整克隆，再只编辑自己的目录。确实需要工作区只显示部分目录时，用第5节的稀疏检出路线。两种路线二选一；不要在同一个非空目录再clone一次。

### 1.1 仓库、工作区、分支分别是什么

- GitHub仓库是团队共同的版本库。
- 工作区是你自己电脑上clone出来的文件夹；每个人有自己的副本。
- `main`是团队合并后的主线；个人任务在自己的分支修改，通过PR交回主线。
- 拉取文件不是领取任务。任务范围仍需按角色文档和负责人安排确认；PR注明任务、需求或operationId。
- 稀疏检出控制本机显示哪些文件，不是目录访问权限。能读取仓库的人仍可读取其他目录和版本历史。

### 1.2 “所有项目文件上传”的范围

上传当前源码、锁文件、配置样例、需求、契约、分工、工具及需要共享的历史资料。`node_modules`、缓存、构建dist、个人环境文件、合同、预算和原Git元数据不上传，由当前 `.gitignore` 排除；这些原文件继续保留在源目录。`local-only/README.md`作为归档说明可以上传，目录内实际材料不上传。

网页的“Upload files”和ZIP下载不适合这套日常流程：网页上传不会替你按本机.gitignore筛选；ZIP没有可继续提交、更新的Git工作区。下面统一使用Git命令。

## 2. 每台电脑先准备一次

### 2.1 安装并检查工具

安装 [Git for Windows](https://git-scm.com/downloads/win)、[Node.js 24](https://nodejs.org/en/download)、[Python](https://www.python.org/downloads/windows/)，可选安装 [VS Code](https://code.visualstudio.com/download)。Python建议3.12或以上，用于目录检查和上传辅助工具。Node.js目前用于现有前端；后端/算法的运行时尚未确定，不因为装了Node就具备后端环境。

安装后重新打开PowerShell，逐行执行：

```powershell
git --version
node --version
npm.cmd --version
python --version
```

预期：各命令能打印版本；Node显示`v24...`。若python打开Microsoft Store或找不到命令，先完成Python安装及PATH配置，再继续。本文用`npm.cmd`避免Windows中npm.ps1被执行策略拦截；不需要关闭系统执行策略。

本文命令只复制代码框内内容，不复制终端提示符。命令报错时先处理错误，不继续执行后面的提交、推送步骤。所有示例路径都有引号，含中文和空格时也照样保留。

### 2.2 获得仓库权限及登录

推荐每人使用自己的GitHub账号，共同访问同一个仓库。管理员在仓库`Settings`的协作者/访问管理区域邀请成员，成员接受邀请后再开发。当前个人仓库的协作者管理说明见 [GitHub官方文档](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/repository-access-and-collaboration/inviting-collaborators-to-a-personal-repository)。

若暂时确实共用`WangLiu-Hades`登录，也必须使用不同本机工作区、不同任务分支，并在Git作者与PR正文写明实际人员。GitHub仍把这些操作视为同一账号，不能实现独立成员的权限和互相审批；例如同一个账号不能批准自己创建的PR。不要因此启用一个无人能满足的独立审批门禁。个人Git作者信息不会把一个共享登录变成多个GitHub身份。[认证说明](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/about-authentication-to-github)、[PR评审说明](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/reviewing-changes-in-pull-requests/about-pull-request-reviews)。

本文使用HTTPS地址。公开仓库clone可能无需登录；push需要写权限。Git for Windows安装时保留Git Credential Manager，首次需要认证时按浏览器提示登录有权限的账号。GitHub的Git操作不使用普通账号密码；不要把密码或token写进仓库URL、文档或命令记录。

## 3. 管理员：首次上传优化后的完整项目

这部分只由一人操作。成员等导入PR合并到main后再执行第5节。

### 3.1 核对当前仓库，建立独立发布工作区

2026-10-13只读核对时，远程默认分支为`main`，HEAD为`6a9142911f40f6ec1ba60d55fc3f39b33b583bf1`，顶层已有：`README.md`、`平台功能设计（原始）.pdf`、`多维数据分析平台开发模块与人员分工.docx`。仓库已有历史，下面采用clone后开分支导入，不在源目录重新建立一段无关历史。

打开PowerShell：

```powershell
$guideRepoUrl = 'https://github.com/WangLiu-Hades/xx-platform.git'
$guideSource = 'C:\Users\C-Linglong\Desktop\Claude杂项\平台 - 路径优化'
$guidePublish = 'C:\Work\xx-platform-upload'
New-Item -ItemType Directory -Path 'C:\Work' -Force | Out-Null
if (Test-Path -LiteralPath $guidePublish) { throw '发布目录已存在，请换一个新的目录名；不要覆盖已有工作区。' }
git clone $guideRepoUrl $guidePublish
if ($LASTEXITCODE -ne 0) { throw 'clone失败，先处理网络或权限。' }
Set-Location -LiteralPath $guidePublish
git remote -v
git branch --show-current
git -c core.quotePath=false ls-tree --name-only HEAD
```

检查origin地址必须是上述仓库，查看实际默认分支与根文件。如果远程已变成完整平台，先比对差异，不重复执行“首次导入”。不要覆盖其他人的新开发内容。若远程仍是上面的三个文件，可继续。

### 3.2 设置本机作者并创建导入分支

```powershell
$guideAuthor = Read-Host '输入你的真实姓名或团队可识别姓名'
$guideEmail = Read-Host '输入GitHub已验证邮箱或GitHub提供的noreply邮箱'
git config user.name $guideAuthor
git config user.email $guideEmail
$guideImportBranch = 'codex/project-import-' + (Get-Date -Format 'yyyyMMdd-HHmmss')
git switch -c $guideImportBranch
if ($LASTEXITCODE -ne 0) { throw '创建导入分支失败。' }
git status --short
```

作者配置只作用于本仓库。预期没有待提交变化，当前分支是`codex/project-import-...`，不是main。

### 3.3 保留远程已有的两份根目录资料

现在这两份资料在根目录，优化项目的正式来源入口在`docs/requirements/source/`。把远程原件归档到`archive/github-existing-materials/`，保留原文件及Git历史：

```powershell
if (Test-Path -LiteralPath 'archive/github-existing-materials') { throw '归档目录已有内容，先检查是否导入过。' }
if (-not (Test-Path -LiteralPath '平台功能设计（原始）.pdf') -or -not (Test-Path -LiteralPath '多维数据分析平台开发模块与人员分工.docx')) { throw '根资料与核对状态不同，请先检查远程最新目录。' }
New-Item -ItemType Directory -Path 'archive/github-existing-materials' -Force | Out-Null
git mv -- '平台功能设计（原始）.pdf' 'archive/github-existing-materials/平台功能设计（原始）.pdf'
if ($LASTEXITCODE -ne 0) { throw 'PDF归档失败。' }
git mv -- '多维数据分析平台开发模块与人员分工.docx' 'archive/github-existing-materials/多维数据分析平台开发模块与人员分工.docx'
if ($LASTEXITCODE -ne 0) { throw 'Word归档失败。' }
git diff --cached --stat
git commit -m '[docs] Preserve original repository requirement files in archive'
if ($LASTEXITCODE -ne 0) { throw '归档提交失败。' }
```

这次提交只存在于本机导入分支，尚未改变远程main。以后正式开发按优化项目的原始PDF和治理分工入口执行。

### 3.4 先预览需要导入的文件

本项目附带 [prepare_upload.py](../../tools/git/prepare_upload.py)，用于按源目录真实Git忽略规则筛选文件；默认只预览，正式复制也不commit/push。它不会把1.7GB左右的本机依赖与缓存全部复制过去。

```powershell
$guideUploadTool = Join-Path $guideSource 'tools/git/prepare_upload.py'
$guideUploadReport = Join-Path $env:TEMP 'xx-platform-upload-plan.json'
python $guideUploadTool --source $guideSource --destination $guidePublish --report $guideUploadReport
if ($LASTEXITCODE -ne 0) { throw '导入预览失败，请按错误信息处理。' }
notepad $guideUploadReport
```

检查JSON中的：

| 字段 | 应该检查什么 |
| --- | --- |
| `source`、`destination` | 来源是优化目录，目标是刚clone的独立发布工作区 |
| `fileList` | 包含apps、contracts、docs、workers、infra、tests、tools、.github和必要归档/报告 |
| `overwrites` | 新README等与远程已有文件内容不同；逐项确认后才能允许覆盖 |
| `destinationOnlyTrackedFiles` | 远程保留但来源中没有的文件；本工具不会删除这些文件 |
| `applied` | 预览阶段为false |

预览不应出现node_modules、缓存、个人.env、合同、预算或local-only实际材料。需要覆盖的旧文件会另存到发布工作区的`local-only/github-import-backup/`。如果出现另一套根package.json，工具会阻止应用，应先让项目负责人决定归档/整合方式，不自动覆盖另一个应用。

### 3.5 确认预览后复制

仅在已逐项确认预览后运行：

```powershell
python $guideUploadTool --source $guideSource --destination $guidePublish --report $guideUploadReport --apply --allow-overwrite
if ($LASTEXITCODE -ne 0) { throw '复制未完成，不要提交或推送；先检查报告和错误。' }
Set-Location -LiteralPath $guidePublish
git status --short
python tools/layout/verify_layout.py
if ($LASTEXITCODE -ne 0) { throw '布局检查未通过，先修正。' }
```

预期根目录已有当前Vue、契约和任务入口，目录检查通过。不要在这个发布副本运行`--integrity`：那是源目录的本机历史核对，发布副本故意不带依赖、旧缓存及local-only原件。

### 3.6 检查即将提交的清单，再创建提交

```powershell
git add .
if ($LASTEXITCODE -ne 0) { throw '暂存失败。' }
git diff --cached --stat
git diff --cached --name-only
git ls-files -ci --exclude-standard
```

逐项检查暂存清单。最后一条正常应没有输出；如果列出了原本应忽略的已跟踪文件，说明已有历史文件不受新.gitignore自动保护，先处理，不继续推送。不要用`git add -f`绕过忽略规则。

```powershell
git commit -m '[project] Import organized platform source, contracts and role guides'
if ($LASTEXITCODE -ne 0) { throw '导入提交失败。' }
git log -2 --oneline
git status --short
git push -u origin $guideImportBranch
if ($LASTEXITCODE -ne 0) { throw '推送失败，先处理登录、权限或分支问题。' }
```

预期push成功，status无待提交变化。此时远程有导入分支，main还没更新；不要强推main。

### 3.7 在GitHub创建和合并导入PR

1. 打开仓库，点击推送后出现的`Compare & pull request`；也可到`Pull requests` → `New pull request`。
2. `base`选main，`compare`选刚才的`codex/project-import-...`。
3. 标题写“导入优化后的平台开发目录”；正文写来源目录、两份旧资料已归档、当前只有前端原型实现、契约/后端预留边界，以及实际检查结果。
4. 在`Files changed`检查目录和内容，确认没有依赖、合同、预算、个人环境或凭据。
5. 查看`Checks`或`Actions`的前端/目录CI。首次CI会安装锁定依赖、生成接口、检查生成结果、类型、单元、构建及布局；本地验证通过不代表这次远程CI已经跑过。
6. 检查通过后由有权限的人按仓库规则合并。使用共享账号时不能把“自己点Approve”当独立评审。
7. 回到`Code`，切回main，确认能打开`docs/START_HERE.md`、`docs/team/`、`apps/web/`和`contracts/api/openapi.yaml`，再通知成员开始第5节。

## 4. 管理员：配置后续协作

| 事项 | 做法 |
| --- | --- |
| 成员身份 | 有独立账号时邀请为协作者；无需每人再建一个平台仓库 |
| 默认分支 | 保持团队统一主线，当前是main；后文同时给出自动识别命令 |
| 任务分支 | 采用`codex/角色-人员-任务`，一项任务一个分支和PR |
| CI | 在Actions确认前端流程实际能运行，再按仓库支持的功能配置主线检查要求 |
| 评审归属 | 现在是docs/team/ownership.json的角色归属；.github/CODEOWNERS.example尚未生效。提供个人账号后才能改成真实CODEOWNERS |
| 任务状态 | 使用GitHub Issues/Projects或团队现有任务表；至少记录角色、任务编号、交付、验收人和依赖 |

各角色读取同一份原始需求、契约和治理分工。契约变更由DEV-01统筹，DEV-03提供字段/SQL/算法协议，DEV-02同步客户端，DEV-04复核用例。角色目录归属是团队约定，不是GitHub自动禁止别人改目录的权限设置。

## 5. 开发者：拉到自己的工作区

先确认管理员已把项目导入并合并到main。若clone后仍只有README和两份根资料，说明首次导入还没合并，不能直接开始运行前端。

### 5.1 选择角色与本机路径

打开新的PowerShell，执行：

```powershell
$guideRepoUrl = 'https://github.com/WangLiu-Hades/xx-platform.git'
$guideRoleCode = (Read-Host '输入角色：DEV-01、DEV-02、DEV-03或DEV-04').ToUpperInvariant()
if ($guideRoleCode -notin @('DEV-01','DEV-02','DEV-03','DEV-04')) { throw '角色输入不正确。' }
$guideWorkRoot = 'C:\Work'
$guideWorkDir = Join-Path $guideWorkRoot ('xx-platform-' + $guideRoleCode.ToLowerInvariant())
New-Item -ItemType Directory -Path $guideWorkRoot -Force | Out-Null
if (Test-Path -LiteralPath $guideWorkDir) { throw '工作区已存在；已有克隆应进入它更新，不要重复clone。' }
```

例如DEV-02的路径是`C:\Work\xx-platform-dev-02`。不同人员即使角色相同，也在各自电脑操作；同一电脑多人使用时给目录再加人员标识，避免共用同一个工作区。

### 5.2 路线A：完整克隆（推荐）

```powershell
git clone $guideRepoUrl $guideWorkDir
if ($LASTEXITCODE -ne 0) { throw 'clone失败。' }
Set-Location -LiteralPath $guideWorkDir
git remote -v
git status --short
```

完整克隆只下载Git已经提交的内容，不会下载管理员本机被忽略的依赖/缓存/合同。先前迁移快照的76,211个原文件包含本机依赖和历史材料，不代表Git会提交或clone这么多文件。完成后继续5.4，不执行5.3。

### 5.3 路线B：只显示角色相关目录（稀疏检出）

若选择本路线，直接接着5.1执行；不能先在同一路径执行路线A再clone一次。

```powershell
git clone --filter=blob:none --sparse $guideRepoUrl $guideWorkDir
if ($LASTEXITCODE -ne 0) { throw '稀疏clone失败。' }
Set-Location -LiteralPath $guideWorkDir
```

接着只运行自己角色对应的一行：

```powershell
# DEV-01 后端
git sparse-checkout set --cone apps/api workers contracts docs tools .github
```

```powershell
# DEV-02 前端
git sparse-checkout set --cone apps/web contracts docs tools .github
```

```powershell
# DEV-03 数据/算法
git sparse-checkout set --cone workers/data apps/api contracts docs tools .github
```

```powershell
# DEV-04 测试/部署：需要全项目，恢复完整工作区
git sparse-checkout disable
```

```powershell
if ($LASTEXITCODE -ne 0) { throw '角色目录设置失败。' }
if ($guideRoleCode -ne 'DEV-04') { git sparse-checkout list }
Get-ChildItem
```

最后的`list`只用于DEV-01～03；DEV-04已关闭稀疏，不需要执行list。默认cone模式会保留根文件及所选目录祖先层的直属文件，因此看到README、.gitignore或apps/README.md是正常现象。`--filter=blob:none`按需取文件内容，不代表完全没有下载Git历史元数据；依赖源码、切换或合并仍可能需要联网。[Git稀疏检出文档](https://git-scm.com/docs/git-sparse-checkout)、[clone文档](https://git-scm.com/docs/git-clone)。

补充目录，例如前端需要看数据Worker：

```powershell
git sparse-checkout add workers/data
```

需要完整项目时，先提交/处理好本地修改，再执行：

```powershell
git sparse-checkout disable
```

稀疏模式的总导航仍列出全项目；跳到未展开目录前先用add补充，或在GitHub阅读对应文件。稀疏工作区未展开的目录不是被删除，不能为了让列表“干净”去执行git rm。默认布局检查要求完整目录，只在完整克隆或disable之后运行；前端本身的生成/类型/测试/构建在上述前端稀疏目录中可以运行。

### 5.4 设置作者、识别主线并检查工作区

路线A/B完成后都执行：

```powershell
git config user.name (Read-Host '输入你的真实姓名或团队可识别姓名')
git config user.email (Read-Host '输入GitHub已验证邮箱或noreply邮箱')
git remote set-head origin --auto
if ($LASTEXITCODE -ne 0) { throw '无法识别远程主线。' }
$guideBaseRef = git symbolic-ref --short refs/remotes/origin/HEAD
if ($LASTEXITCODE -ne 0) { throw '缺少origin/HEAD，请确认远程已初始化。' }
$guideBaseBranch = $guideBaseRef -replace '^origin/', ''
Write-Output ('当前团队主线：' + $guideBaseBranch)
git status --short
```

当前预期主线为main，工作区干净。以后另开终端，先进入自己的工作区，再重新运行本节识别主线的命令；PowerShell变量不会跨终端自动保留。

### 5.5 打开任务资料和编辑器

按顺序读根README、[开发入口](../START_HERE.md)、自己的[角色入口](README.md)、[任务边界](../project-governance/ENGINEERING_ASSIGNMENTS.md)、[开发计划](../project-governance/DEVELOPMENT_PLAN.md)及[未决清单](../project-governance/PENDING_REGISTER.md)。

若安装了code命令，在工作区根运行`code .`；否则在VS Code“打开文件夹”选择自己的`C:\Work\xx-platform-dev-xx`目录。不要打开管理员的原桌面目录、archive旧前端或只打开一个游离源码文件来开发。

## 6. 四个角色拿到代码后具体做什么

| 角色 | 主工作位置 | 必须一起读取 | 交付给谁 |
| --- | --- | --- | --- |
| DEV-01 | apps/api、workers/delivery | contracts/api、contracts/database、原始PDF、工程交接 | 给前端可用公共API，给数据工程师任务/内部协议，给测试可部署服务 |
| DEV-02 | apps/web | contracts/api、UI设计、原始PDF、API交接 | 界面、状态、适配、客户端校验、测试证据 |
| DEV-03 | workers/data、数据库查询/模型提案 | contracts、原始PDF、P清单、任务/版本/证据要求 | 给DEV-01可调用的查询/计算Worker和版本化结果 |
| DEV-04 | infra、根tests、.github/workflows、tools | 完整源码、需求追踪、架构和交接 | CI、环境、独立集成/容量/恢复验收与发布结论 |

### 6.1 DEV-01 后端

先读 [后端任务](DEV-01-backend.md)、[API约定](../../contracts/api/api-conventions.md)、[当前OpenAPI](../../contracts/api/openapi.yaml)、[数据库入口](../../contracts/database/README.md)。所有公共HTTP API由DEV-01负责，DEV-03的数据查询/算法通过约定的内部接口接入，不再单独建一套公共REST。

现在apps/api与workers/delivery只有预留README，尚无正式服务启动命令。先按开发计划建立运行骨架、会话/权限、任务/文件/Context底座，再逐域实现；选定实际后端运行时后，把安装、配置、启动和迁移命令写入apps/api/README.md。不要把空库schema.sql直接执行到有旧数据的生产库。

### 6.2 DEV-02 前端：从拉取到运行

先按第7节建立个人任务分支。然后在工作区根目录运行：

```powershell
npm.cmd --prefix apps/web ci
if ($LASTEXITCODE -ne 0) { throw '依赖安装失败。' }
npm.cmd --prefix apps/web run dev -- --port 5180 --strictPort
```

浏览器打开终端给出的`http://127.0.0.1:5180/`，使用Mock账号`demo.admin`、密码`demo123`。该终端需保持运行；Ctrl+C停止。端口被占用时换空闲端口并同步测试URL。

现在的实际编辑位置：页面在apps/web/src/pages，组件在src/components，状态在src/stores，HTTP适配在src/api；完整分层见 [前端README](../../apps/web/README.md)。

需要连真实后端时，在**另一个终端**进入同一工作区根目录，首次建立个人环境文件：

```powershell
if (-not (Test-Path -LiteralPath 'apps/web/.env.local')) { Copy-Item -LiteralPath 'apps/web/.env.example' -Destination 'apps/web/.env.local' }
notepad 'apps/web/.env.local'
```

设置`VITE_API_MODE=http`、`VITE_API_BASE_URL=/api/v1`，保存后在启动服务的那个终端执行：

```powershell
$env:API_PROXY_TARGET = 'http://127.0.0.1:8080'
npm.cmd --prefix apps/web run dev -- --port 5181 --strictPort
```

需要先停止同端口旧服务。真实后端必须确实在8080提供/api/v1；demo账号仅适用Mock。代理目标从进程环境读取，仅写进.env.local不够。恢复演示时把.env.local的模式改回mock并重启；不提交.env.local。详情见 [后端对接说明](../../contracts/api/FRONTEND_INTEGRATION.md)。

修改契约后从工作区根运行：

```powershell
npm.cmd --prefix apps/web run generate:api
if ($LASTEXITCODE -ne 0) { throw '接口生成失败。' }
npm.cmd --prefix apps/web run typecheck
if ($LASTEXITCODE -ne 0) { throw '类型检查失败。' }
npm.cmd --prefix apps/web test
if ($LASTEXITCODE -ne 0) { throw '单元测试失败。' }
npm.cmd --prefix apps/web run build
if ($LASTEXITCODE -ne 0) { throw '构建失败。' }
```

只有contracts/api/openapi.yaml是当前生成输入，4个客户端生成源码不要手改。相关浏览器回归按 [测试说明](../../apps/web/tests/README.md)运行；现有浏览器脚本还需相应浏览器可执行文件/服务，不能认为npm ci后已完成浏览器验收。

### 6.3 DEV-03 数据/算法

先读 [数据任务](DEV-03-data.md)、[字段字典](../../contracts/database/data-dictionary.md)、[数据库未决项](../../contracts/database/pending-design.md)和工程交接。workers/data目前只有预留说明；依赖、样本、运行时、算法模型需按任务选定和交接后实现，没有可以直接启动的真实算法服务。

首批产物是源协议/样本映射、标准事实、质量与去重、查询SQL/投影、Worker输入输出、可重放任务及失败语义。数据域迁移提案交DEV-01统一合并。P-016跨源拟合/合并拆分仍待签认，不因拿到文件就自行定阈值或无痕改原始事实。

### 6.4 DEV-04 测试/部署

使用完整工作区。先读 [质量任务](DEV-04-quality.md)、需求追踪和联调方案。在工作区根运行：

```powershell
python tools/layout/verify_layout.py
```

按6.2安装前端并跑生成、类型、单元、构建，再按受影响流程做浏览器回归。infra和根tests目前是预留说明，真实后端/数据库/Worker的环境、部署、容量、恢复与集成用例需继续建立；HTTP样例拦截不能当真实服务端到端验收。

## 7. 每个任务开始：更新主线，再开个人分支

先进入自己的工作区根目录。确认`git status --short`没有输出；有未提交修改时不要直接切主线，先按第8节提交当前任务，或先处理现有改动。

```powershell
git fetch origin
if ($LASTEXITCODE -ne 0) { throw '获取远程更新失败。' }
git remote set-head origin --auto
if ($LASTEXITCODE -ne 0) { throw '识别主线失败。' }
$guideBaseRef = git symbolic-ref --short refs/remotes/origin/HEAD
if ($LASTEXITCODE -ne 0) { throw '没有远程默认分支引用。' }
$guideBaseBranch = $guideBaseRef -replace '^origin/', ''
git switch $guideBaseBranch
if ($LASTEXITCODE -ne 0) { throw '切换主线失败。' }
git pull --ff-only origin $guideBaseBranch
if ($LASTEXITCODE -ne 0) { throw '主线无法快进，请先处理分叉，不要强推。' }
```

下一段请把示例角色/人员/任务换成自己本次任务的英文短名，保留codex/前缀，不输入中文、空格或尖括号：

```powershell
$guideTaskBranch = Read-Host '输入任务分支，例如 codex/dev02-lisi-map-selection'
if ($guideTaskBranch -notmatch '^codex/[a-z0-9][a-z0-9-]+$') { throw '分支名格式不正确。' }
git switch -c $guideTaskBranch
if ($LASTEXITCODE -ne 0) { throw '分支已存在或创建失败。' }
git branch --show-current
```

输出应是自己的任务分支。开始编码，按角色边界修改文件。已有任务第二天继续工作时，进入原分支即可，不要每天再创建同名分支：`git switch 'codex/dev02-lisi-map-selection'`，这里的分支名必须换成自己的。

## 8. 完成一轮修改：检查、提交、推送个人分支

### 8.1 先检查改动和验证结果

```powershell
git branch --show-current
git status --short
git diff --stat
git diff
```

确认当前不是main、改动属于本任务。前端至少按6.2跑相关检查；API/数据库/算法按工程交接执行对应验证。CI不代替必要的本地复验。

### 8.2 只暂存本任务的文件

下面为前端示例，按实际修改选择路径；不需要把没改动的目录全部加入：

```powershell
git add -- apps/web/src apps/web/tests
git diff --cached --stat
git diff --cached
```

后端通常暂存apps/api、workers/delivery及经过协商的契约；数据任务暂存workers/data和批准的数据库提案；质量任务暂存infra、tests、tools或.github对应文件。文档修改也要暂存相关docs文件。新增Worker协议文件按团队约定放置并交接，不擅自把所有contracts都包给自己。

若暂存错文件，用`git restore --staged -- '实际文件路径'`取消暂存；它保留工作区内容。不要用git reset --hard或git clean来处理普通暂存错误。

### 8.3 创建提交并推送

```powershell
git commit -m '[DEV-02] Fix map multi-point selection'
if ($LASTEXITCODE -ne 0) { throw '提交失败；检查作者配置或是否确有暂存改动。' }
$guideCurrentBranch = git branch --show-current
if ($LASTEXITCODE -ne 0 -or -not $guideCurrentBranch.StartsWith('codex/')) { throw '当前不是团队任务分支。' }
git push -u origin $guideCurrentBranch
if ($LASTEXITCODE -ne 0) { throw '推送失败，先处理权限或远程变化。' }
```

提交信息里的DEV角色和内容要改成自己的。第一次push设置追踪关系，以后该分支再提交可执行`git push`。commit只记录本机；push才把提交送到GitHub分支；main要等PR合并才会变。

### 8.4 创建PR并完成评审

1. 打开GitHub仓库 → Pull requests → New pull request。
2. base选择团队主线，compare选择自己的任务分支。
3. PR注明实际作者/DEV角色、任务或需求编号、operationId、行为变化、受影响契约/接收人、实际执行的检查及尚待签认规则。项目已有PR模板可填写。
4. 查看Files changed，确认只有本任务改动；查看Checks，处理失败项。
5. 有独立账号时请对应主责评审；跨角色API/数据库变更由DEV-01统筹，不能只由页面人员看界面就通过。
6. 有意见时继续在同一本机分支修正、commit、push，原PR会更新，不必新建一个PR。
7. 通过后由有权限的人合并。不要绕过失败CI、强推主线或把未完成算法标为已验收。

## 9. 开发中同步他人修改、处理冲突、合并后继续

### 9.1 任务未结束时把最新主线并入自己的分支

先把当前改动提交到自己的任务分支，确保status干净。在该分支运行：

```powershell
git fetch origin
if ($LASTEXITCODE -ne 0) { throw 'fetch失败。' }
git merge "origin/$guideBaseBranch"
```

`$guideBaseBranch`来自5.4/第7节，新终端先重新识别。这里用merge保留已推送分支的历史，不要求成员重写共享历史或force push。

若无冲突，重跑受影响检查并push。若有冲突：

1. `git status`看冲突文件；稀疏检出时冲突文件可能在原未展开目录出现，这不代表文件丢失。必要时先`git sparse-checkout add '相关目录'`。
2. 打开文件，理解两边改动，按需求/契约和代码主责决定最终内容；不要一律保留“我的”或“他们的”。生成源码冲突应先解决OpenAPI源契约，再生成并检查。
3. 删除冲突标记，保存，执行`git add -- '已解决的实际文件路径'`，全部解决后`git commit`，再检查并push。
4. 无法判断时联系对应代码负责人。若要取消这次尚未完成的merge，执行`git merge --abort`；前提是本节开始时工作区干净。

如果push报non-fast-forward，先`git fetch origin`，确认是否有人改了你的远程任务分支；在自己的干净任务分支合并`origin/自己的分支名`、解决并复验后再push。不要用force覆盖别人提交。

### 9.2 PR合并后更新主线并开始下一项任务

```powershell
git status --short
git switch $guideBaseBranch
if ($LASTEXITCODE -ne 0) { throw '切换主线失败，先处理本地改动。' }
git pull --ff-only origin $guideBaseBranch
if ($LASTEXITCODE -ne 0) { throw '更新主线失败。' }
git log -5 --oneline
```

确认能看到合并后的行为，再按第7节开下一项分支。旧任务分支可以暂留，不要在已合并的旧分支继续塞新任务；无需为每次更新重新clone仓库。

## 10. 常见问题按这个顺序处理

| 现象 | 操作 |
| --- | --- |
| Repository not found / 403 | 先核对URL和登录身份；私有仓库先接受邀请，push确认写权限。它不一定表示仓库真的不存在 |
| clone后只有3个根文件 | 管理员的导入PR尚未合并，或你在旧分支；确认main最新内容后再开发 |
| 目录已经存在，clone失败 | 已有Git工作区进入它fetch/pull；旧非Git目录先另存保留，换新工作区名，不递归删除 |
| 文件夹少于别人 | `git sparse-checkout list`查看选择范围；add补充，或在处理好改动后disable |
| 根目录npm运行失败 | 根目录没有package.json；按`npm.cmd --prefix apps/web ...`运行，或先进入apps/web |
| npm ci失败 | 核对Node24、锁文件、网络和npm报错；不先删除锁文件或改依赖来绕过 |
| 提示npm.ps1无权限 | 使用本文npm.cmd，不需要修改全局执行策略 |
| 前端HTTP登录失败 | 检查后端是否启动、API_PROXY_TARGET、/api/v1、Cookie/CSRF；demo账号仅用于Mock |
| 端口5180占用 | 停止自己的旧服务或换空闲端口，同步回归URL；不随意终止其他人的服务 |
| 本机检查提示缺apps/api/infra等 | 若用了稀疏检出，先disable再做全目录检查；不要补假的服务实现骗过检查 |
| 新仓库运行--integrity失败 | 这是本机保留历史核对，clone不包含这些材料；运行默认布局检查及实际代码检查 |
| git pull --ff-only失败 | 本地主线与远程分叉或有未处理状态；先保留自己的提交并请维护者处理，不强推main |
| LF/CRLF提示 | 先看是否只是行尾提示，项目已提供.gitattributes；不要因此全盘改换行或忽略真正冲突 |
| CI生成源码不一致 | 按当前OpenAPI重新generate、检查、把4个生成源码与对应契约一起提交 |
| 要中途换电脑 | 当前任务提交并push；新电脑clone后`git switch --track origin/实际任务分支`继续，个人.env和依赖需重建 |

## 11. 每个人第一次操作完成时的核对表

- [ ] origin是`WangLiu-Hades/xx-platform`，当前工作区是自己的本机目录。
- [ ] 能找到原始PDF、当前OpenAPI、角色任务、交接边界和开发计划。
- [ ] 正在自己的codex任务分支工作，Git作者姓名和邮箱已设置。
- [ ] 前端人员已npm ci并成功启动Mock；其他角色已确认预留服务未实现及首批交付物。
- [ ] 知道源目录、clone工作区、GitHub分支和PR的区别。
- [ ] 本次修改已按角色/契约协商，并跑过必要检查。
- [ ] 提交清单没有依赖、缓存、个人环境、合同预算或凭据。
- [ ] PR给出实际作者、任务、测试证据及未决项，合并后再更新本地主线。

## 12. 本指南如何核对

本地核对内容及结果见 [指南检查记录](../../reports/github-workflow-guide/REVIEW.md)。包括真实目录/角色链接、PowerShell语法、上传筛选/覆盖备份、完整克隆/四角色目录选择、生成路径和本地分支合并演练。演练使用临时Git仓库，没有向上述GitHub仓库推送，也没有更改其权限或主线。远程GitHub认证、CI运行和PR合并需要操作者按本文实际完成，不能由本地演练替代。

进一步查阅：[Git clone](https://git-scm.com/docs/git-clone)、[Git sparse-checkout](https://git-scm.com/docs/git-sparse-checkout)、[Git pull](https://git-scm.com/docs/git-pull)、[Git merge](https://git-scm.com/docs/git-merge)、[Git push](https://git-scm.com/docs/git-push)。
