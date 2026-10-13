# 关键目录README编写与核查

本次按用户要求为关键目录说明文件用途、使用方法、依赖、输入输出及维护检查方式。核对日期2026-10-10。

## 修改范围

项目/文档入口、系统设计及API/数据库/ADR/图示/UI、当前Vue工程各源码层/脚本/测试/构建/审计、历史原型/评估、工程治理、合同修订及逐页渲染目录。第三方node_modules、npm缓存、Git对象、渲染临时树和带哈希的构建assets由上级README说明，不逐个给外部包或生成资源添加文档。

具体路径及每份README哈希见[audit/readme-checks.json](<audit/readme-checks.json>)，逐目录文件登记见[audit/readme-catalog.json](<audit/readme-catalog.json>)。

## 已纠正的说明

- 旧静态基线缺vendor、tests和audit的问题明确说明，历史证据链接指向实际位置，不再声称可完整独立运行。
- 迁移评估脚本仍读prototype/app.js，当前Vue已无此文件；命令标为历史复现，要求隔离还原及路径调整。
- 前端说明核对开发代理从process.env取API_PROXY_TARGET、HTTP测试使用PROTOTYPE_HTTP_URL、页面冒烟硬编码Edge等实际行为。
- 区分Mock演示包、开发源码包、设计契约、历史证据和合同参考，写清打包对象与接收方运行步骤。
- 审计汇总只读取已有证据，不能用生成时间证明业务测试或联网检查重跑；当前结果入口指向治理报告。
- 系统设计逐文件列出00–11及总说明书，所有源码、测试和证据文件有用途说明，渲染目录按实际页码列图。

本次未修改运行前端、接口YAML、数据库SQL、Word/PDF或历史测试内容。除README外，新增文档核查脚本和证据；既有verify_deliverables.py只调整构建新旧判断，排除不参与Vite编译的README，避免新增说明被误判为业务源码变化。checks.json和output-manifest.json按最终交付重新生成。

## 检查结果

已完成检查并修正发现的缺项，结果如下：

| 项目 | 结果 |
| --- | --- |
| 关键目录/README | 38个，全部覆盖；新增32份、完善6份 |
| 直接文件用途 | 374个文件逐项说明；构建assets及第三方/临时树由上级说明 |
| README相对链接 | 506个，全部有效且指向项目内 |
| README检查门禁 | 13/13通过，问题0 |
| 原有交付门禁 | 39/39通过，文档坏链接0、警告0；114个历史图片/PDF/DOCX解析核对通过 |
| 原有文件范围 | 本轮修改前400个非README文件全部比对，仅交付检查脚本及其checks/manifest按上述说明更新 |

首次自动检查发现治理入口缺少统一文件目录标题、旧CSS用途说明不足，均已补齐并重跑通过。人工按实际代码复核页面归属，确认地图页包括设备分布、档案页包括数据态势，已纠正描述。检查记录含每份README的最终SHA256。

自动检查能验证覆盖/链接/路径及文件变化范围；文件作用、使用前提、历史/当前边界另按实际源码、配置和报告人工核对。本次文档任务不重新执行前端业务测试，不把此前测试日期改为本次。返回[治理入口](<README.md>)。

## 全部README入口

| 目录（相对项目根） | 本次处理 |
| --- | --- |
| [.](<../../archive/design-project-root/README.md>) | 新增 |
| [docs](<../../archive/design-project-root/docs/README.md>) | 新增 |
| [docs/project-governance](<README.md>) | 完善 |
| [docs/project-governance/audit](<audit/README.md>) | 新增 |
| [docs/project-governance/audit/word-render](<audit/word-render/README.md>) | 新增 |
| [docs/project-governance/scripts](<scripts/README.md>) | 新增 |
| [docs/system-design](<../system-design/README.md>) | 完善 |
| [docs/system-design/adr](<../system-design/adr/README.md>) | 新增 |
| [contracts/api](<../../contracts/api/README.md>) | 完善 |
| [contracts/database](<../../contracts/database/README.md>) | 新增 |
| [docs/system-design/diagrams](<../system-design/diagrams/README.md>) | 新增 |
| [docs/system-design/ui](<../system-design/ui/README.md>) | 新增 |
| [apps/web](<../../apps/web/README.md>) | 完善 |
| [apps/web/audit](<../../apps/web/audit/README.md>) | 新增 |
| [apps/web/audit/vue-migration](<../../apps/web/audit/vue-migration/README.md>) | 新增 |
| [apps/web/audit/vue-migration/flows](<../../apps/web/audit/vue-migration/flows/README.md>) | 新增 |
| `dist/`（构建产物；旧版说明保存在本机归档） | 新增 |
| [apps/web/scripts](<../../apps/web/scripts/README.md>) | 新增 |
| [apps/web/src](<../../apps/web/src/README.md>) | 新增 |
| [apps/web/src/api](<../../apps/web/src/api/README.md>) | 新增 |
| [apps/web/src/components](<../../apps/web/src/components/README.md>) | 新增 |
| [apps/web/src/mock](<../../apps/web/src/mock/README.md>) | 新增 |
| [apps/web/src/pages](<../../apps/web/src/pages/README.md>) | 新增 |
| [apps/web/src/stores](<../../apps/web/src/stores/README.md>) | 新增 |
| [apps/web/src/utils](<../../apps/web/src/utils/README.md>) | 新增 |
| [apps/web/tests](<../../apps/web/tests/README.md>) | 新增 |
| [apps/web/tests/evidence](<../../apps/web/tests/evidence/README.md>) | 新增 |
| [apps/web/vendor](<../../apps/web/vendor/README.md>) | 新增 |
| [archive/ui/prototype-migration-assessment](<../../archive/ui/prototype-migration-assessment/README.md>) | 完善 |
| [archive/ui/prototype-v1-baseline](<../../archive/ui/prototype-v1-baseline/README.md>) | 完善 |
| [docs/system-design/ui/wireframes](<../system-design/ui/wireframes/README.md>) | 新增 |
| [合同修订工作](../../local-only/README.md)（本机归档：`../../local-only/contract-revisions/README.md`） | 新增 |
| [合同修订工作/compact-render-v1](../../local-only/README.md)（本机归档：`../../local-only/contract-revisions/compact-render-v1/README.md`） | 新增 |
| [合同修订工作/compact-render-v2](../../local-only/README.md)（本机归档：`../../local-only/contract-revisions/compact-render-v2/README.md`） | 新增 |
| [合同修订工作/compact-render-v3](../../local-only/README.md)（本机归档：`../../local-only/contract-revisions/compact-render-v3/README.md`） | 新增 |
| [合同修订工作/final-render-v1](../../local-only/README.md)（本机归档：`../../local-only/contract-revisions/final-render-v1/README.md`） | 新增 |
| [合同修订工作/original-render](../../local-only/README.md)（本机归档：`../../local-only/contract-revisions/original-render/README.md`） | 新增 |
| [合同修订工作/reference-render](../../local-only/README.md)（本机归档：`../../local-only/contract-revisions/reference-render/README.md`） | 新增 |
