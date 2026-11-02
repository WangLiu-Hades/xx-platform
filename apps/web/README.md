# 多维数据分析平台 · Vue 前端原型

本目录是原始 PDF 对应的 28 个业务菜单页，并新增 1 个"智能体对话"主界面（共 29 个菜单），技术栈为 Vue 3、TypeScript、Vite、Element Plus、Pinia、Axios、ECharts 与 OpenLayers。智能体以豆包式对话承载平台入口，可调用本机/内网大模型（OpenAI 兼容接口），模型不可用时由内置规则引擎完成识别与跳转。只实现界面、浏览器交互、合成数据和接口适配；真实后端、算法、设备命令、短信/邮件发送均不在本目录实现。旧版静态原型保存在`../../archive/ui/prototype-v1-baseline/`。

## 文件与目录

| 文件或目录 | 功能 |
| --- | --- |
| [package.json](<package.json>) | 固定依赖版本及dev/build/test/generate/audit命令 |
| [package-lock.json](<package-lock.json>) | 完整依赖锁定，用npm ci可重复安装，不手工改版本树 |
| [index.html](<index.html>) | Vite源码入口，挂载src/main.ts，不能直接双击运行 |
| [vite.config.ts](<vite.config.ts>) | Vue插件、相对资源路径、开发代理、ES2022构建与分包 |
| [tsconfig.json](<tsconfig.json>) | 严格TypeScript、Vue/测试检查和Bundler解析 |
| [.env.example](<.env.example>) | Mock/HTTP、本站API路径及本地大模型（VITE_LLM_*）配置样例 |
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

登录后默认进入**智能对话**主界面。智能体默认调用本机 Ollama（`http://127.0.0.1:11434/v1`，模型 `qwen2.5:7b-instruct`）：

```powershell
ollama pull qwen2.5:7b-instruct
ollama serve
# 页面跨端口访问时，在启动 Ollama 的终端设置：
$env:OLLAMA_ORIGINS='http://127.0.0.1:5180'
```

未安装 Ollama 时，可用本地模型桩演示流式对话（OpenAI 兼容，零外部依赖）：

```powershell
npm run llm          # 终端1，监听 127.0.0.1:11434；被占用时自动换 11435/11436
npm run dev -- --port 5180 --strictPort   # 终端2
```

模型地址、名称、温度、超时可在页面右上「模型设置」中修改，保存在浏览器本地；关闭"启用本地模型"即完全使用规则引擎。部署、显存建议、CORS 与生产代理见[本地大模型部署与接入](<../../docs/smart-agent/本地大模型部署与接入.md>)。

HTTP接口模式：本目录已提供`.env.http`（`VITE_API_MODE=http`），不需要改`.env.local`，直接：

```powershell
npm run dev:http -- --port 5181 --strictPort
```

它按Vite的mode机制加载`.env.http`，优先级高于`.env.local`，因此`npm run dev`仍是Mock演示，两种模式互不干扰。当前vite.config.ts从进程环境读取代理目标（默认`http://127.0.0.1:8080`），仅把API_PROXY_TARGET写进`.env.local`不会自动用于该配置，需要时在启动服务的同一PowerShell中设置`$env:API_PROXY_TARGET`。构建HTTP版本用`npm run build:http`。

浏览器API地址须为本站绝对路径；使用Cookie、CSRF/Origin和OpenAPI请求头，服务失败不回退为Mock成功。HTTP模式在构建时确定，生产部署另需同源API代理及真实后端。

```powershell
npm run generate:api
npm run typecheck
npm test
npm run build
npm run baseline
npm run stub
npm run llm
npm run evidence
```

`npm run baseline`把类型检查、312项单测、Mock构建、HTTP构建（产物在`dist-http/`）和29个菜单页浏览器冒烟汇总到`audit/vue-migration/baseline-<日期>/`，用于每周会前留一份带日期的现状证据；它只证明原型可运行，不是215项业务验收证据。

`npm run stub`启动**本地契约桩**（`scripts/contract-stub.mjs`，监听`127.0.0.1:8080`，路由挂`/api/v1`）：后端业务API未实现时，让前端走真实HTTP路径联调。响应结构取自OpenAPI强类型样例并由前端AJV校验，`npm run stub -- --selfcheck`可离线校验全部样例（当前24项）。桩支持`GET /_stub/scenario?s=ok|forbidden|conflict|expired|server|timeout|empty|pending|cancelled|unauthorized`切换场景、`/_stub/state`查看状态、`/_stub/reset`清空会话与任务、`/_stub/expire`把当前会话置为过期（业务端点401、续期接口可恢复，用于验证静默续期）；演示账号`stub.admin / stub123`（密码填`wrong`验证登录失败分支）。

配合HTTP模式使用：

```powershell
npm run stub                                              # 终端1
npm run dev:http -- --port 5181 --strictPort              # 终端2
npm run evidence                                          # 终端3：跑首链14个场景并出矩阵
```

`npm run evidence`把登录、检索、地图、导出/任务中心在正常、空结果、403、410、500、超时、取消、409、会话续期等场景下的截图与请求序列写入`audit/vue-migration/first-chain-<日期>/`（`README.md`为就绪度矩阵）。它验证的是前端在真实HTTP路径下的行为，**不是真实后端联调**，不能作为业务验收证据。

浏览器回归需要另开终端保留前端服务，命令、变量和输出见[tests说明](<tests/README.md>)。`npm run audit`只汇总已有报告，不重跑测试、构建或联网漏洞检查，输出时间不能证明所有输入刚执行过。

`generate:api` 只读取 `../../contracts/api/openapi.yaml`，生成类型、操作目录和运行时契约；契约变化后先重新生成、再检查与构建。`test:e2e` 使用本机 Edge，默认访问正在运行的 `http://127.0.0.1:5180`，可设置 `PROTOTYPE_URL` 和 `AUDIT_BROWSER`。HTTP 样例回归脚本为 `node tests/vue-http.e2e.cjs`，访问独立启动的 HTTP 模式端口 5181。生产构建位于 `dist/`，应通过 HTTP 服务而非直接双击文件访问。

扩展业务回归使用 `node tests/vue-flows.e2e.cjs`，涵盖检索、导入、布控、模型、配置、报表、权限及日志等 22 组流程。`FLOW_FILTER` 可用 `|` 分隔用例名片段做定向复测；定向结果不能代替最后一次完整回归。浏览器脚本需要上述开发服务器运行。

智能体专项回归使用 `node tests/vue-agent.e2e.cjs`：在 5180 端口和本地模型桩（或真实 Ollama）同时运行时，检查登录落地页、豆包式对话、布控三入口、新建布控表单、预警结果跳转、检索条件带入、会话切换和模型设置，截图与结果写入 `audit/smart-agent-<日期>/`。其余回归脚本默认在浏览器本地存储中关闭本地模型，避免未启动模型时产生网络错误噪音。

## 智能体主界面与本地大模型

需求（2026-11-02 甲方）：以智能体为载体，把豆包式"用户输入 + 智能体应答"作为主界面，其他功能不变；输入"布控"返回"查看布控任务 / 新建布控 / 查看布控预警结果"，点击可跳到布控操作界面；大模型部署在本地。

- 一次问答分两步：`src/utils/agentNlu.ts` 先用本地规则把问题映射到 15 类平台能力与可点击动作；`src/api/llm.ts` 再把识别结果交给本地大模型组织成自然语言并流式渲染。模型不可用、超时或输出为空时自动回退规则应答，跳转功能不受影响。
- 页面动作由本地动作目录决定，模型只能生成文字，不能直接跳转或提交；每条动作执行前校验当前角色菜单权限，无权限时在对话里说明原因。
- 提示词只包含能力目录和聚合数量，不发送采集明细、人员信息、身份证/手机号等敏感字段；默认只接受本机/内网模型地址，公网地址在设置页给出警告。
- 会话与模型配置保存在浏览器本地（`vue-agent-conversations-v1`、`vue-agent-llm-config-v1`），不写入平台数据库；Mock 演示数据的相对时间以数据最新时间为基准，保证"最近24小时"能命中演示记录。
- 本地模型接入、模型选型、显存要求、CORS 与生产部署见[本地大模型部署与接入](<../../docs/smart-agent/本地大模型部署与接入.md>)；界面与动作清单见[智能体模块说明](<../../docs/smart-agent/README.md>)；逐文件修改记录见[修改日志](<../../docs/smart-agent/修改日志.md>)。

## 架构与边界

地图框选专项回归：启动 5182 生产预览后执行 `node tests/vue-map-selection.e2e.cjs`。脚本在独立浏览器上下文注入合成点位，验证框内多点、框外排除、反向拖拽、重新框选、空白清空、单选及重置，不修改日常预览的本地数据。

- `src/mock/` 只提供合成演示数据；`src/api/` 从指定 OpenAPI 生成强类型请求/响应并用 AJV 校验。`VITE_API_MODE` 在构建时确定。
- `src/stores/platform.ts` 管理页面、表单、选择、权限及 Mock/HTTP 分支；`src/stores/agent.ts` 管理智能体会话、流式回答、动作执行与本地模型状态；`src/pages/` 是 29 个菜单对应的 12 类页面组件。
- 采集搜索、日志与分析结果使用后端分页/游标；Context 保留完整查询快照。异步写入只提示“已提交”，任务成功后才可下载受控文件。
- 当前契约已给出暂定审批及一次性受控结果协议，前端页面和真实服务仍待实现；按PD方案开发，甲方确认仍pending。地图 HTTP 模式只用服务端 WGS84 坐标，无授权底图时保留点位与列表后备。
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
