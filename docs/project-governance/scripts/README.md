# 工程审查与文档检查脚本

脚本以其位置推导项目根目录；下面命令从**项目根目录**执行。Node任务需前端npm ci，Python任务按具体脚本需要python-docx、pypdf及Pillow；Word渲染另依赖当前电脑的Word、文档工具和Poppler。移到其他电脑先核对硬编码外部资料/运行时路径。

## 文件与目录

| 文件 | 输入、输出与覆盖行为 |
| --- | --- |
| [inspect_workspace.py](<inspect_workspace.py>) | 枚举全项目并读取/哈希、提取自有文本和外部原始DOCX/PDF信息，覆盖audit/workspace-*及first-party-text.txt；含第三方/缓存，不是逐行安全证明 |
| [inspect_api.cjs](<inspect_api.cjs>) | 读OpenAPI、生成契约和TS/Vue调用，覆盖api-inventory.csv/json及contract-only-operations.md；静态引用不代表按钮可达或后端可用 |
| [build_governance.py](<build_governance.py>) | 读盘点/原分工，生成交接Markdown和工作包；加--docx生成Word，覆盖相应文件，不是验证命令 |
| [render_word_review.py](<render_word_review.py>) | 读取已用Word导出的assignment-reviewed.pdf及修订版DOCX，生成word-render逐页PNG；检查PDF不早于DOCX，清理同目录过期高页码图 |
| [verify_deliverables.py](<verify_deliverables.py>) | 当前兼容入口，转发tools/layout/verify_layout.py；旧的固定快照检查原件已保存到local-only，不覆盖历史checks/output-manifest |
| [audit_dependencies.cjs](<audit_dependencies.cjs>) | 发送依赖元数据至公共npm registry，保存dependency-current.json、执行记录/哈希和stderr；须已有明确联网发送授权，不升级依赖 |
| [verify_readmes.py](<verify_readmes.py>) | 当前兼容入口，转发tools/layout/verify_layout.py；旧版原件在local-only备份，历史readme-checks保留 |

## 怎么使用

本轮只读核对文档可执行：

```powershell
python docs/project-governance/scripts/verify_readmes.py
python docs/project-governance/scripts/verify_deliverables.py
```

第一次建立工程盘点才按inspect_workspace → inspect_api → 人工复核 → build_governance顺序操作；会覆盖初始快照或人工文档，不能在历史报告上随意重跑。API清点会覆盖历史盘点；新结果建议先设置`$env:LAYOUT_API_OUT="reports/path-optimization/api-inventory"`，再执行`node docs/project-governance/scripts/inspect_api.cjs`。依赖审计示例为`node docs/project-governance/scripts/audit_dependencies.cjs 'C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js'`，npm路径必须实际存在；本项目2026-10-10已获用户明确授权，其他场景先确认相应授权。

## 维护与检查

更新Word需重新导出、逐页视觉审查并刷新哈希，脚本通过不能代替人工版面检查。基线盘点、业务回归和联网审计有不同时间/范围，不以当前汇总时间替代历史执行日期。新增源码README不属于Vite构建输入，交付检查据实际运行文件判断构建新旧。返回[治理入口](<../README.md>)。
