# 旧静态原型源码快照

本目录仅保留旧版index.html、app.js、app.css，不是当前开发入口。原README的运行/验收路径对应迁移前布局，现已在下文纠正；当前Vue看[prototype入口](<../../../apps/web/README.md>)。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [index.html](<index.html>) | 旧登录、应用壳及脚本入口 |
| [app.js](<app.js>) | 旧版原生JavaScript业务、Mock与交互 |
| [app.css](<app.css>) | 旧版登录、导航、内容区、表格及弹窗样式 |

## 怎么使用

直接阅读三个文件以对比迁移前后。旧index.html引用本目录vendor/jszip.min.js，但vendor未随此快照保留；当前这里也没有tests或audit子目录。因此不能声称本目录可完整独立运行/验收。需要复跑时应在隔离副本中恢复完整历史结构，使用[保留库及许可](<../../../apps/web/vendor/README.md>)，并调整旧测试服务根目录，不改写本基线。

仅在依赖和历史结构补齐后的隔离副本中，可用以下本地服务查看：

```powershell
python -m http.server 8080 --bind 127.0.0.1
```

然后访问 `http://127.0.0.1:8080/`。演示账号为 `demo.admin`、`demo.analyst`、`demo.secret`、`demo.test`，初始密码均为 `demo123`。顶栏“演示身份”可以切换权限场景。

所有身份、点位、人像、分析结果、短信及设备操作均为 Mock。表单变更保存在浏览器 LocalStorage；清除此站点的本地存储即可恢复初始数据。定时通知只在页面运行期间检查，超过通知时间且当天尚未发送时产生一次模拟记录。不会发出真实短信、邮件或设备命令。

检索行上右键打开九项菜单；未勾选时仅开放区域统计和添加数据集。人像操作在图片悬停时显示。地图框选需先选框选模式，再按下鼠标拖动；数据运算需从左侧拖动集合到右侧运算框。文件导入在确认后更新数据，支持 `.xlsx`、CSV、JSON；导出为真实 `.xlsx` 文件，多报表为包含工作簿的 ZIP。

## 验收资料

- 项目根目录 `prototype-audit-report.md`：修复清单、页面覆盖、限制和自检。
- [需求映射](<../../../apps/web/audit/requirements.json>)：214项PDF文字要求。
- [旧来源清单](<../../../apps/web/audit/manifest.json>)：原PDF哈希、页数、图像与摘要。
- [旧浏览器结果](<../../../apps/web/tests/evidence/results.json>)：历史69项测试。
- [旧工作簿验证](<../../../apps/web/tests/evidence/workbook-validation.json>)：下载XLSX/ZIP的读取检查。
- UI 目录中的 `prototype-audit-baseline.html`：修改前的完整原型备份，不属于运行入口。

## 维护与检查

旧浏览器脚本实际在[当前工程tests/audit.e2e.cjs](<../../../apps/web/tests/audit.e2e.cjs>)，仍假设迁移前目录和DOM，不能直接用于当前Vue。需完整历史副本、恢复vendor并重定向服务根目录后再运行，结果另存，避免覆盖历史证据。后续开发和回归都进入当前Vue工程。

原始 PDF 的 64 个内嵌图片均为 2×2 像素，原截图位置为空白。文字规定的位置和操作已核对；无法从该 PDF 验证缺失原图的像素布局。该限制在报告中单独列出。
