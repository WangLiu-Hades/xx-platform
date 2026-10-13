# GitHub操作指南检查结果（2026-10-13）

交付入口：[GitHub上传与分角色开发操作指南](../../docs/team/GitHub上传与分角色开发操作指南.md)；辅助工具：[prepare_upload.py](../../tools/git/prepare_upload.py)。

## 实际检查

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 远程只读状态 | 默认main，核对时HEAD为6a9142911f40f6ec1ba60d55fc3f39b33b583bf1；根含README和两份需求资料 | git ls-remote --symref 与只读ls-tree；因此指南保留远程历史、通过导入分支和PR操作 |
| PowerShell命令块 | 31块，语法错误0；仅解析，不执行Read-Host或远程push | [语法记录](powershell-syntax.json) |
| Git本地演练 | 23项通过，失败0 | [演练记录](rehearsal.json) |
| 导入预览与原件 | 默认只预览；未确认覆盖被阻止；确认后复制、旧README原字节备份；源依赖/缓存/合同不进入发布副本 | 演练upload_*、old_readme_exact_backup、runtime_private_excluded |
| 远程历史保留 | 用本地模拟仓库先归档两份根资料，再导入，已有提交和原件保留 | remote_archive_preserved |
| 个人任务 | 识别默认分支、建立任务分支、提交推送到本地模拟远程、并入队友主线更新 | default_branch_detected、task_branch_sync_and_push |
| 四角色拉取 | DEV-01/02/03稀疏选择、所需需求/契约可见；前端生成源路径可见；add补充和disable恢复完整检查；DEV-04完整检查通过 | 各角色sparse/full记录 |
| 误操作防护 | 有改动目标被阻止；另一套根应用被阻止；无自动删除、强推或实际GitHub提交 | dirty_destination_rejected、other_root_application_rejected |

演练模拟远程为临时本地bare仓库，原始PDF/Word使用合成占位内容模拟归档操作；平台源码则按实际共享筛选结果复制。演练没有连接GitHub执行push、创建PR或修改仓库设置。本地演练不代表GitHub账号认证、远程Actions和PR审批已经完成，操作者仍须按指南执行并查看结果。

## 现有说明同步

检查时contracts/api/README仍链接一个当前不存在的历史YAML副本，docs/requirements/source/README仍链接三个当前不存在的早期资料。已按实际清单移除失效链接并注明缺失现状，没有恢复或删除这些历史文件；修改前README保存到local-only/github-guide-2026-10-13。原始业务PDF、当前OpenAPI、数据库SQL、前端业务代码未因本任务修改。

新增手册、上传辅助工具/说明和检查证据；在根README、docs/START_HERE、docs/team/README、tools/README补上入口。修改列表见 [changes.json](changes.json)。此前路径优化报告保留其原日期与快照，不伪装为本轮新执行结果。

最终文档链接与目录检查见 [document-checks.json](document-checks.json)。官方认证/评审说明已通过公开网页读取核对，Git命令另对照本机Git2.52的官方手册并进行本地演练；未检查每个成员的账号权限或个人机器环境。
