# 前端测试与执行说明

测试从**前端工程根目录**执行，先npm ci。Vitest不要求开发服务器，Playwright脚本要求单独运行对应模式的服务。HTTP套件通过浏览器拦截返回契约样例，不能证明真实后端完成。

## 文件与目录

| 文件或目录 | 覆盖内容与输出 |
| --- | --- |
| [contracts.test.ts](<contracts.test.ts>) | OpenAPI响应样例及路径/正文/未知字段反例 |
| [files.test.ts](<files.test.ts>) | 文件大小/解压限额、CSV引号等输入边界 |
| [sets.test.ts](<sets.test.ts>) | 集合表达式、括号、交并差和非法输入 |
| [platform.test.ts](<platform.test.ts>) | Mock状态、业务动作、导入与权限等 |
| [session.test.ts](<session.test.ts>) | 会话切换/失效、旧请求与跨会话状态隔离 |
| [http-forms.test.ts](<http-forms.test.ts>) | HTTP表单字段、上下文模块来源、更新及未决能力边界 |
| [http-resources.test.ts](<http-resources.test.ts>) | 受控上传、文件/资源、任务与响应处理边界 |
| [vue.e2e.cjs](<vue.e2e.cjs>) | 13组Mock浏览器操作，输出audit/vue-migration/vue-e2e-results.json |
| [vue-smoke.cjs](<vue-smoke.cjs>) | 28页冒烟，输出smoke-final.json和dashboard-smoke.png |
| [vue-flows.e2e.cjs](<vue-flows.e2e.cjs>) | 22组扩展Mock业务流程，输出audit/vue-migration/flows/ |
| [vue-map-selection.e2e.cjs](<vue-map-selection.e2e.cjs>) | 11项合成多点框选/反向/替换/空白/单选/重置，输出map-selection-results.json |
| [vue-http.e2e.cjs](<vue-http.e2e.cjs>) | 5组HTTP契约样例浏览器检查，输出http-e2e-results.json |
| [vue-http-analysis.e2e.cjs](<vue-http-analysis.e2e.cjs>) | 3组HTTP研判表单，输出http-analysis-results.json |
| [vue-http-resources.e2e.cjs](<vue-http-resources.e2e.cjs>) | 8组HTTP上传/资源/人像/地图/模型/任务，输出http-resources-results.json |
| [audit.e2e.cjs](<audit.e2e.cjs>) | **历史静态版**测试，内置8790普通文件服务器和旧DOM选择器；不支持直接测试当前Vite源码 |
| [evidence/](<evidence/README.md>) | 历史静态版截图、下载样本和69项报告，不是当前Vue验收入口 |

## 怎么使用

单测执行`npm test`；定向示例：`npm test -- tests/session.test.ts`。在第一个终端运行Mock服务`npm run dev -- --port 5180 --strictPort`，第二个终端执行：

```powershell
npm run test:e2e
node tests/vue-smoke.cjs
node tests/vue-flows.e2e.cjs
```

Mock脚本使用PROTOTYPE_URL，默认5180；地图专项默认5182，需先npm run build并另起`npm run preview -- --port 5182 --strictPort`，再执行`node tests/vue-map-selection.e2e.cjs`。若在其他Mock端口运行，在测试终端设置`$env:PROTOTYPE_URL='http://127.0.0.1:5182'`。FLOW_FILTER用`|`分隔流程名片段，定向结果不能充当22组完整回归。

HTTP套件需按[工程README](<../README.md>)单独启动HTTP模式5181，在测试终端设置`$env:PROTOTYPE_HTTP_URL='http://127.0.0.1:5181'`，执行：

```powershell
node tests/vue-http.e2e.cjs
node tests/vue-http-analysis.e2e.cjs
node tests/vue-http-resources.e2e.cjs
```

除vue-smoke.cjs外，当前Vue浏览器脚本可用AUDIT_BROWSER指定Chromium可执行文件；默认本机Edge。vue-smoke.cjs当前硬编码Windows Edge路径，其他机器需调整脚本路径后才能用；仅npm ci不会安装系统Edge。脚本使用隔离上下文，但会覆盖同名结果文件。

## 维护与检查

历史audit.e2e.cjs仍指向当前prototype目录的旧结构，不能直接运行并覆盖旧证据；需专门恢复完整静态快照和调整路径再复现。检查报告时看实际time/timestamp、模式、URL、checks/errors及退出码，旧failure截图不自动说明本次失败。本轮README工作不重跑业务测试，保留此前真实日期。返回[工程入口](<../README.md>)。
