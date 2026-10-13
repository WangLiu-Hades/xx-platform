# 多维数据分析平台 · Vue 前端原型

本目录是原始 PDF 对应的 28 页前端展示原型，技术栈为 Vue 3、TypeScript、Vite、Element Plus、Pinia、Axios、ECharts 与 OpenLayers。只实现界面、浏览器交互、合成数据和接口适配；真实后端、算法、设备命令、短信/邮件发送均不在本目录实现。旧版静态原型保存在`../../archive/ui/prototype-v1-baseline/`。

## 文件与目录

| 文件或目录 | 功能 |
| --- | --- |
| [package.json](<package.json>) | 固定依赖版本及dev/build/test/generate/audit命令 |
| [package-lock.json](<package-lock.json>) | 完整依赖锁定，用npm ci可重复安装，不手工改版本树 |
| [index.html](<index.html>) | Vite源码入口，挂载src/main.ts，不能直接双击运行 |
| [vite.config.ts](<vite.config.ts>) | Vue插件、相对资源路径、开发代理、ES2022构建与分包 |
| [tsconfig.json](<tsconfig.json>) | 严格TypeScript、Vue/测试检查和Bundler解析 |
| [.env.example](<.env.example>) | Mock/HTTP及本站API路径配置样例 |
| [.gitignore](<.gitignore>) | 排除依赖、缓存、个人环境及临时测试文件 |
| [src/](<src/README.md>) | 应用壳、页面、组件、状态、接口适配、Mock及工具 |
| [scripts/](<scripts/README.md>) | OpenAPI生成和已有审计证据汇总 |
| [tests/](<tests/README.md>) | 单元、浏览器、HTTP样例与地图框选回归 |
| [audit/](<audit/README.md>) | 需求追踪、生成记录、测试报告及历史迁移证据 |
| `dist/`（构建产物；旧版说明保存在本机归档） | 构建后的静态展示包，必须整个目录一起分发 |
| [vendor/](<vendor/README.md>) | 旧静态版JSZip与许可；当前Vue从npm包导入 |
| [CHANGES.md](<CHANGES.md>) | 已实施修改和验证记录 |
| [MIGRATION_PLAN.md](<MIGRATION_PLAN.md>) | 迁移范围、过程和边界；Vue迁移已经完成 |
| `node_modules/` | npm ci生成的第三方依赖，不是需要发送的源码 |
| `.npm-cache/` | 本机npm缓存，不是运行产物 |

## 怎么使用：运行

需要 Node.js 24 或兼容当前 Vite 版本的 Node.js。PowerShell 中从本目录运行：

```powershell
npm ci
npm run dev -- --port 5180 --strictPort
```

访问 Vite 输出的本机地址。默认 `VITE_API_MODE=mock`，演示账号 `demo.admin`、`demo.analyst`、`demo.secret`、`demo.test`，密码均为 `demo123`。仅使用合成数据；浏览器本地存储可恢复 Mock 操作记录。

HTTP接口模式：首次复制`.env.example`为个人`.env.local`（已有文件时直接编辑），设置`VITE_API_MODE=http`、`VITE_API_BASE_URL=/api/v1`。当前vite.config.ts从进程环境读取代理目标，仅把API_PROXY_TARGET写进.env.local不会自动用于该配置。在启动服务的同一PowerShell中执行：

```powershell
$env:API_PROXY_TARGET='http://127.0.0.1:8080'
npm run dev -- --port 5181 --strictPort
```

浏览器API地址须为本站绝对路径；使用Cookie、CSRF/Origin和OpenAPI请求头，服务失败不回退为Mock成功。HTTP模式在构建时确定，生产部署另需同源API代理及真实后端。

```powershell
npm run generate:api
npm run typecheck
npm test
npm run build
```

浏览器回归需要另开终端保留前端服务，命令、变量和输出见[tests说明](<tests/README.md>)。`npm run audit`只汇总已有报告，不重跑测试、构建或联网漏洞检查，输出时间不能证明所有输入刚执行过。

`generate:api` 只读取 `../../contracts/api/openapi.yaml`，生成类型、操作目录和运行时契约；契约变化后先重新生成、再检查与构建。`test:e2e` 使用本机 Edge，默认访问正在运行的 `http://127.0.0.1:5180`，可设置 `PROTOTYPE_URL` 和 `AUDIT_BROWSER`。HTTP 样例回归脚本为 `node tests/vue-http.e2e.cjs`，访问独立启动的 HTTP 模式端口 5181。生产构建位于 `dist/`，应通过 HTTP 服务而非直接双击文件访问。

扩展业务回归使用 `node tests/vue-flows.e2e.cjs`，涵盖检索、导入、布控、模型、配置、报表、权限及日志等 22 组流程。`FLOW_FILTER` 可用 `|` 分隔用例名片段做定向复测；定向结果不能代替最后一次完整回归。浏览器脚本需要上述开发服务器运行。

## 架构与边界

地图框选专项回归：启动 5182 生产预览后执行 `node tests/vue-map-selection.e2e.cjs`。脚本在独立浏览器上下文注入合成点位，验证框内多点、框外排除、反向拖拽、重新框选、空白清空、单选及重置，不修改日常预览的本地数据。

- `src/mock/` 只提供合成演示数据；`src/api/` 从指定 OpenAPI 生成强类型请求/响应并用 AJV 校验。`VITE_API_MODE` 在构建时确定。
- `src/stores/platform.ts` 管理页面、表单、选择、权限及 Mock/HTTP 分支；`src/pages/` 是 28 个菜单对应的 11 类页面组件。
- 采集搜索、日志与分析结果使用后端分页/游标；Context 保留完整查询快照。异步写入只提示“已提交”，任务成功后才可下载受控文件。
- 密文解密只保留申请接口；正式审批与明文展示未签认。地图 HTTP 模式只用服务端 WGS84 坐标，无授权底图时保留点位与列表后备。
- OpenAPI 的 `x-design-status: pending` 与 `../../contracts/api/OPENAPI_PENDING.md` 明确了尚不能上线的算法、文件导入、消息通道、设备厂商参数和地图许可。这里的 Mock 操作不会实际执行外部动作。

## 验收资料

`audit/vue-migration/` 保存 PDF 逐项追踪、契约生成记录、浏览器截图、测试结果和最终审计报告。审计基线只以原始 PDF 为业务优先级最高的依据，`docs/system-design` 为工程约束；原 PDF 内嵌的 64 张图像仅为 2×2 占位图，无法校验其原始像素排版。

前端原型迁移已完成。历史迁移报告为`audit/vue-migration/final-audit.md`及同名JSON，修改说明为CHANGES.md；各证据保留实际日期。2026-10-10交接与限定回归看[项目审查](<../../docs/project-governance/PROJECT_AUDIT.md>)，授权后联网依赖结果看[依赖补检](<../../docs/project-governance/DEPENDENCY_AUDIT.md>)。代表性用例通过、页面可见与真实业务验收分别记录；后端、算法、外部服务与生产安全仍须真实联调验收。

HTTP 分析表单回归为 `node tests/vue-http-analysis.e2e.cjs`（3 组）；受控上传、导入、人像、画像、地图、模型与任务详情回归为 `node tests/vue-http-resources.e2e.cjs`（8 组）。三套 HTTP 浏览器检查合计 16 组，使用契约样例拦截，不能替代后端联调。生产版本可执行 `npm run preview -- --port 5182` 后，设置 `PROTOTYPE_URL=http://127.0.0.1:5182` 运行 Mock 业务回归和 `tests/vue-smoke.cjs`。

## 打包与分发

只给别人演示：确认构建模式为Mock（核对.env.local及终端中的VITE_API_MODE覆盖值），执行npm run build，发送整个dist目录（含assets）并附本README。接收方在dist目录执行`python -m http.server 5182 --bind 127.0.0.1`后访问`http://127.0.0.1:5182/`，也可使用其他静态HTTP服务；接收方不需要Node依赖。不要只发送index.html或双击打开。

给开发者交接：发送本目录源码、锁文件、配置样例，排除node_modules、.npm-cache及个人.env.local，另附[API契约](<../../contracts/api/README.md>)、[数据库设计](<../../contracts/database/README.md>)和[工程分工](<../../docs/project-governance/ENGINEERING_ASSIGNMENTS.md>)。HTTP构建的运行模式已固化，接收方临时修改源码.env不会改变已构建文件；正式服务需配置同源代理，不能把Vite开发配置直接当生产配置。

## 维护与检查

修改界面按原始PDF核对，修改契约先生成类型，再执行类型/相关测试/构建，交互变化执行对应浏览器回归。源码分层说明见各子目录README。返回[仓库入口](../../README.md)。
