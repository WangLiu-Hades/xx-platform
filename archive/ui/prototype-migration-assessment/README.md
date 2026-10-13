# 迁移评估的证据与复现

完整结论见项目根目录 `prototype-vue-migration-assessment.md`。本目录是评估资料，不属于原型运行入口，也不是新 Vue 工程。

- `inventory.json`：AST 结构、函数依赖、页面/需求计数、既有测试摘要与源码哈希。
- `estimate.json`：人日、页面分组、范围、假设及增量场景的机器可读数据。
- `baseline-verification.json`：2026-10-08 的独立浏览器检查；28 页正常场景＋3 个已重现风险＋3 个集合解析断言。
- `assessment-verification.json`：评估完成后再检查覆盖、数字、范围、来源和证据的结果。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [inspect.cjs](<inspect.cjs>) | 解析旧app.js AST并输出inventory.json |
| [verify-baseline.cjs](<verify-baseline.cjs>) | 旧原型浏览器及集合解析验证，输出baseline-verification.json |
| [verify-assessment.cjs](<verify-assessment.cjs>) | 核评估覆盖、估算加总、来源哈希，输出assessment-verification.json |
| [inventory.json](<inventory.json>) | 旧函数、依赖、页面与来源哈希快照 |
| [estimate.json](<estimate.json>) | 迁移前工作量、假设及增量方案；不作为当前完整平台排期 |
| [baseline-verification.json](<baseline-verification.json>) | 2026-10-08旧原型检查结果 |
| [assessment-verification.json](<assessment-verification.json>) | 迁移评估文档的历史核对结果 |

## 怎么使用

当前以阅读JSON和根目录历史评估为主。三个脚本仍读取相邻prototype/app.js，但Vue迁移后该文件已移到prototype-v1-baseline；旧基线的vendor也不完整。**以下仅记录历史执行顺序，当前目录不能直接复跑成功**。如确需复现，应在隔离副本还原完整旧文件布局、调整脚本路径及依赖并另存结果。

历史上从项目根目录顺序执行：

```powershell
node apps/web-migration-assessment/inspect.cjs
node apps/web-migration-assessment/verify-baseline.cjs
node apps/web-migration-assessment/verify-assessment.cjs
```

脚本使用本机 Codex 附带的 Playwright/Babel 包路径，浏览器默认是本机 Edge；`AUDIT_BROWSER` 可指定其它 Chromium 可执行文件。在其它机器重跑需先调整依赖路径。浏览器检查通过随机本地端口启动只读服务，在新建隔离环境中使用虚构数据，完成后关闭服务与浏览器。不会操作用户正在使用的浏览器，也不修改原型文件或旧测试结果。

## 维护与检查

重跑会覆盖同名JSON；不能删除哈希断言来伪造历史基线仍有效。当前Vue运行看[当前工程](<../../../apps/web/README.md>)，后续平台计划看[DEVELOPMENT_PLAN](<../../../docs/project-governance/DEVELOPMENT_PLAN.md>)。返回[界面入口](<../../../docs/system-design/ui/README.md>)。
