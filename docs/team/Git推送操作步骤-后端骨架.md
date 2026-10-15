# 本次改动推送步骤（DEV-01 后端骨架）

适用：把本机 `apps/api` 后端骨架、API 速读指南、后端双人分工方案推到 GitHub。
流程依据 [GitHub上传与分角色开发操作指南](<GitHub上传与分角色开发操作指南.md>) 第 7～9 节；本文件只补齐"这一次"的具体分支名、提交信息和要暂存的文件清单。

**当前状态（执行前已核对）**：分支 `main`（`486a5b4 Initial project structure upload`），远程 `origin = https://github.com/WangLiu-Hades/xx-platform.git`，Git 作者已配置，共 13 项未提交改动（12 项改动 + 本次新增的 `.gitattributes` 规则）。

> ⚠️ **不要直接在 main 上提交推送**。团队约定：`main` 只通过 PR 合并，改动先进个人任务分支。

---

## 0. 这次改了哪些文件

| 类别 | 文件 |
| --- | --- |
| 后端骨架（新增） | `apps/api/pom.xml`、`mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`、`.gitignore`、`scripts/dev-check.ps1`、`src/**` |
| 文档（新增） | `docs/API速读指南.md`、`docs/project-governance/BACKEND_TWO_DEV_SPLIT.md` |
| 文档（修改） | `apps/api/README.md`（骨架说明）、`README.md`（apps/api 状态）、`docs/project-governance/README.md`（登记分工方案） |
| 行尾规则（修改） | `.gitattributes`（新增 `*.cmd`/`*.bat` 强制 CRLF） |

**不该出现在提交里的**：`apps/api/target/`（编译产物与临时文件）。它已被 `apps/api/.gitignore` 忽略，无需手工删除，但下面的暂存必须用明确路径，不要用 `git add .`。

---

## 1. 确认工作区与改动

```powershell
cd C:\Users\dev02\workspace\xx-platform
git branch --show-current          # 应为 main
git status --short
git diff --stat
```

## 2. 同步主线（你已在 main，且本地无分叉）

```powershell
git fetch origin
if ($LASTEXITCODE -ne 0) { throw 'fetch 失败，先处理网络或权限。' }
git pull --ff-only origin main
if ($LASTEXITCODE -ne 0) { throw '主线无法快进，先处理分叉，不要强推。' }
```

如果 `git pull` 带回别人的新提交，先确认没有和你改到同一个文件（`git status --short` 会提示冲突）。

## 3. 建立个人任务分支

分支名规则：`codex/角色-人员-任务`，全小写、无中文空格。下面这条可直接用，也可把 `heyifan` 换成你的英文名：

```powershell
git switch -c codex/dev01-heyifan-api-skeleton
if ($LASTEXITCODE -ne 0) { throw '分支名已存在或创建失败。' }
git branch --show-current
```

## 4. 只暂存本次任务的文件（明确路径，不用 `git add .`）

```powershell
git add -- .gitattributes
git add -- README.md apps/api/README.md
git add -- docs/API速读指南.md
git add -- docs/project-governance/BACKEND_TWO_DEV_SPLIT.md docs/project-governance/README.md
git add -- apps/api/pom.xml apps/api/mvnw apps/api/mvnw.cmd
git add -- apps/api/.gitignore apps/api/.mvn apps/api/scripts apps/api/src
```

## 5. 提交前逐项核对（这一步不要跳过）

> 本文件编写时已经替你执行过第 4 步，27 个文件**已暂存**。你只需要重新核对下面几条，再继续第 6 步。

```powershell
# 清单概览
git diff --cached --stat

# 1) 应为 27 个文件，且逐条都属于第 0 节清单
git diff --cached --name-only

# 2) 必须为空：确认没有"本应被忽略却被强行加入"的文件
git ls-files -ci --exclude-standard

# 3) 必须看不到 target/ 与 cp.txt
git diff --cached --name-only | Select-String -Pattern 'target/|cp\.txt'

# 4) 两个 wrapper 入口的行尾属性（关键）
git ls-files --eol -- apps/api/mvnw apps/api/mvnw.cmd
```

第 4 条预期（`i/` 是索引、`w/` 是工作区）：

```
i/lf    w/lf    attr/text eol=lf      	apps/api/mvnw
i/lf    w/crlf  attr/text eol=crlf    	apps/api/mvnw.cmd
```

**索引里统一是 LF 是正常的**：本机 `core.autocrlf=true`，仓库统一以 LF 存储，检出时按 `.gitattributes` 的 `eol` 属性还原成 CRLF。真正要确认的是 `attr/` 那两列分别是 `eol=lf` 和 `eol=crlf`——这才决定别人克隆后拿到什么行尾。

如果你看到的 `attr` 不是上面这样，说明属性没生效，**先停下来排查，不要提交**。

## 6. 提交

```powershell
git commit -m '[DEV-01] Add Spring Boot API skeleton, contract envelope and backend split guide'
if ($LASTEXITCODE -ne 0) { throw '提交失败：检查作者配置或是否确有暂存改动。' }
git log -3 --oneline
```

可选：正文里补一句改动范围与验证结论，例如

```powershell
git commit -m '[DEV-01] Add Spring Boot API skeleton, contract envelope and backend split guide' -m '骨架通过 mvn -o -B compile，并实跑验证成功/失败响应壳、traceId 头体一致、UTF-8。契约生成器放在 generate-api profile。193 个操作未实现。'
```

## 7. 推送个人分支

```powershell
git push -u origin codex/dev01-heyifan-api-skeleton
if ($LASTEXITCODE -ne 0) { throw '推送失败：先处理登录、权限或远程变化。' }
```

**只推送自己的任务分支，不要 `git push origin main`。** 首次推送时 Git Credential Manager 会弹浏览器登录有写权限的账号。

## 8. 在 GitHub 建 PR

1. 打开 <https://github.com/WangLiu-Hades/xx-platform> → 点推送后出现的 `Compare & pull request`，或 `Pull requests` → `New pull request`。
2. `base` 选 `main`，`compare` 选 `codex/dev01-heyifan-api-skeleton`。
3. 标题建议：`新增 DEV-01 后端 Spring Boot 骨架与契约基础配置`。
4. 正文按仓库 PR 模板填写，至少写清：
   - 实际作者与角色：何伊凡 / DEV-01
   - 改动范围：`apps/api` 骨架（pom + wrapper + 分层目录 + 统一响应壳/错误码/traceId）；文档：API 速读指南、后端双人分工方案
   - **验证证据**：`mvn -o -B compile` 通过；实跑 `GET /api/v1/smoke` 与未知路由的响应体、`X-Trace-Id` 与体内 traceId 一致
   - **明确未做**：会话/CSRF、权限与数据范围、幂等、ETag、游标、任务与文件底座；193 个业务接口均未实现
   - 需要评审的点：分层与包结构、`apps/api/README.md` 的共享层约定、分工方案的模块归属
5. 在 `Files changed` 确认**没有** `target/`、`cp.txt`、依赖或凭据。
6. 等 `Checks`/`Actions` 结果；有失败项就地修，继续在同一分支 commit + push，PR 会自动更新。
7. 通过后由有权限的人合并（共享账号时，"自己点 Approve"不算独立评审）。

## 9. 合并后回到主线

```powershell
git switch main
git pull --ff-only origin main
git log -3 --oneline
```

下一项任务再按指南第 7 节从最新 main 开新分支；旧任务分支不必删除，但不要在已合并的旧分支继续塞新任务。

---

## 附：本次顺手修掉的一个坑

`.gitattributes` 里原来只有 `* text=auto`。本机 `core.autocrlf=true` 时它能让 `.cmd` 在 Windows 检出为 CRLF，但**在 `core.autocrlf=false` 的机器或 Linux CI 上会被检出成 LF**；而 cmd.exe 是逐行解析批处理的，LF 会让它错乱（本机已实际撞到，报 `'rofiles' is not recognized`）。所以本次显式固定了两个入口的行尾：

```gitattributes
# Maven Wrapper：mvnw 是 shell 脚本必须 LF，mvnw.cmd 是批处理必须 CRLF
apps/api/mvnw text eol=lf
apps/api/mvnw.cmd text eol=crlf
# Windows 批处理必须保持 CRLF
*.cmd text eol=crlf
*.bat text eol=crlf
```

注意**顺序**：具体路径规则写在通配规则之前（同长度规则后者优先，先精确后通配最稳）。这条规则对以后任何 `.cmd`/`.bat` 都生效，不只是 wrapper。
