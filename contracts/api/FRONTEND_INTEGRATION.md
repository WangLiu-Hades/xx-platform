> 2026-10-18修订：当前暂定开发规则及分工以[PD-2026-10-19.v1](../../docs/project-governance/暂定方案与修改记录.md)为准。下文旧pending/NO/签认门禁和人员编号保留作历史追踪；本轮不声称后端或生产验收通过。

# 当前 Vue 前端与后端对接说明

核对日期：2026-10-09。范围：现有前端原型与公共 Web API 的联调，不包含真实后端、算法或设备/消息服务的实现。

## 1. 当前保留情况

- 唯一机器契约为本目录 `openapi.yaml`，版本 `1.0.0-dev.2`，Base URL 为 `/api/v1`。
- YAML 与前端生成目录均包含 193 个操作、581 个 Schema。193 是契约目录数量，不代表 193 个操作均有独立界面入口或已通过真实后端联调。
- 其中 65 个操作在操作级标记 `x-design-status: pending`；字段级及项目级未决事项还须查 `OPENAPI_PENDING.md`。其余操作未带该标记，也不等于已完成生产验收。
- 当前页面已有 HTTP 适配：认证/权限、首页、检索、人像任务、库与档案、地图、分析、数据集、布控、模型、场所/设备、配置、报表、通知、日志与任务中心。
- 当前 5182 演示和已交付的 `apps/web/dist`（路径为仓库根相对路径） 为 Mock 构建。Mock 使用合成数据；切到 HTTP 后不回退为演示数据。
- 现有 16 组 HTTP 浏览器回归使用 OpenAPI 样例拦截，属于前端契约适配验证，尚无真实后端端到端联调结论。

## 2. 后端接收哪些资料

| 资料 | 用途 |
| --- | --- |
| `openapi.yaml` | 路径、方法、operationId、请求/响应、状态码、权限与安全定义 |
| `api-conventions.md` / `error-codes.md` | 会话、分页、时间、坐标、版本、幂等、任务和错误处理 |
| `api-coverage-matrix.md` / `OPENAPI_COVERAGE.md` | 页面动作与业务模块对应的操作 |
| `OPENAPI_PENDING.md` | 未决规则和启用条件；Mock 示例不得作为真实业务规则 |
| `../../docs/system-design/10_开发联调与集成方案.md` | T01–T27 联调与验收用例 |
| `../../apps/web` | 当前前端源码、运行说明、测试和审计证据 |
| `../database/api-storage-map.md` | 实现 API 时的数据库字段来源与投影边界 |

传交源码时保留仓库根下 `contracts/api/` 与 `apps/web/` 的目录关系。`apps/web/scripts/generate-api.mjs` 从前端目录相对 `../../contracts/api/openapi.yaml` 读取契约。历史 `openapi - 副本.yaml` 不能用于实现当前接口。

## 3. 开发环境怎样连接

后端先提供本机或测试环境的 API 地址。例如后端监听 `http://127.0.0.1:8080`，业务路由包含 `/api/v1` 前缀。

在仓库根下 `apps/web` 目录复制 `.env.example` 为 `.env.local`，设置：

```dotenv
VITE_API_MODE=http
VITE_API_BASE_URL=/api/v1
```

在同一目录打开 PowerShell，使用项目兼容的 Node.js（当前基线为 Node.js 24）：

```powershell
npm ci
$env:API_PROXY_TARGET='http://127.0.0.1:8080'
npm run dev -- --port 5180 --strictPort
```

已有依赖时不必再次执行 `npm ci`。5180 被占用时选择其他空闲端口，并同步后端的可信 Origin 配置。

浏览器访问 `http://127.0.0.1:5180`。请求流向为：

```text
浏览器 /api/v1/...
  → Vite 开发代理
  → http://127.0.0.1:8080/api/v1/...
```

当前 `vite.config.ts` 通过 `process.env.API_PROXY_TARGET` 读取代理目标，默认值为 `http://127.0.0.1:8080`；因此按上述 PowerShell 环境变量设置目标，仅在 `.env.local` 填写 `API_PROXY_TARGET` 不能保证被当前配置读取。代理保留路径，不会自动去掉 `/api/v1`。`VITE_API_BASE_URL` 当前只接受本站绝对路径，不能填完整的跨域 URL。

修改 `.env.local` 后重启开发服务。HTTP 模式使用后端提供的真实测试账号；`demo.admin/demo123` 仅是 Mock 内的账号，后端未创建时不能用来登录。

## 4. 第一批必须打通的请求

当前前端登录按以下顺序执行：

1. `GET /api/v1/auth/csrf`：获取 CSRF token，并由后端设置相应 Cookie。
2. `POST /api/v1/auth/login`：JSON 为 `loginName/password`，携带 `X-CSRF-Token`；后端创建或轮换 SID Cookie。
3. 再次 `GET /api/v1/auth/csrf`：更新登录后的 CSRF token。
4. `GET /api/v1/me`：返回当前用户、菜单、权限码和数据/场所范围。
5. `GET /api/v1/client-config`：返回能力开关、字典绑定、上传策略、查询限制等完整配置。
6. 登录成功后读取首页聚合接口。

先按契约实现完整的响应结构，再逐步接入实际数据源。必填字段不能因为暂未实现而省略；无数据使用契约规定的空列表或空状态。未开放能力在 `features` 中明确禁用并说明原因。

## 5. 后端必须遵守的约定

| 项目 | 当前前端的要求 |
| --- | --- |
| 认证 | SID Cookie Session，Axios 已开启 `withCredentials`；写请求使用 CSRF token。当前客户端没有 Bearer Token 注入或自动续期流程，不能直接换成 JWT 登录响应。会话政策仍按 P-001 完成签认 |
| 同源与部署 | 浏览器请求本站 `/api/v1`；后端按实际前端 Origin 校验。生产采用 HTTPS 和契约要求的 Cookie 属性，本地调试由后端明确开发环境策略 |
| 成功 JSON | `{ "code": "0", "message": "success", "data": <操作定义的结构>, "traceId": "..." }`；`code` 是字符串 |
| 错误 | 使用操作定义的 HTTP 状态与 ErrorCode，返回可展示消息、traceId 和规定的错误结构；401 会让前端清理身份 |
| 结构校验 | 前端有请求/成功响应运行时校验，字段名、必填性、枚举、null 和额外字段必须符合 Schema；不要统一改成 `code: 200`、`result` 或自定义分页格式 |
| 分页 | 管理列表用 `page/pageSize/total`；大结果用 `cursor/nextCursor`。游标由服务端生成，不把当前页当全量 |
| 标识与敏感字段 | UUID 代理 ID 与展示号码分离；二次检索使用授权 targetId，不能把脱敏号码当真实条件。服务端仍须逐次鉴权和限制数据范围 |
| 时间与地图 | 时间带时区，查询范围为 `[from,to)`；地图使用 WGS84，坐标顺序为经度、纬度 |
| 并发更新 | 返回资源 `version` 与约定的 ETag，接受 `If-Match`，冲突按契约报错 |
| 重复提交 | 对契约要求幂等的操作按 `Idempotency-Key` 执行去重，不仅返回成功外壳 |
| 异步任务 | 导入、导出、分析等按任务状态机返回 taskId、进度、错误和结果引用；202 表示已接受，不表示执行成功 |
| 文件 | 上传使用 multipart 分类策略，文件达到 ACTIVE 后才进入业务流程；受控下载成功返回二进制流，不包成功 JSON |
| 未签认能力 | 依照 `OPENAPI_PENDING.md` 保持关闭，必要时返回 `REQUIREMENT_PENDING`；不得复制 Mock 规则作为真实算法或安全规则 |

精确字段和各操作差异以 YAML 与 `api-conventions.md` 为准，本表不覆盖完整契约。

## 6. 联调顺序与后端需要提供的东西

建议顺序：会话/权限/配置 → 场所/设备与检索 → Context/地图/档案 → 统一任务与文件 → 分析/集合/布控 → 模型/配置/报表/通知。字典、模板、参数目录按模块一并提供。

后端提供：可访问的测试地址和启动方法、数据库初始化方法、至少管理员/受限用户两个测试账号、合成或经批准脱敏的样本、各模块实现清单、未决能力状态、可查询的 traceId 日志，以及文件/任务服务的运行方式。未完成模块在配置中保持关闭。

每模块至少验收正常、空结果、无权、失败/超时与分页边界；写操作追加重复提交、版本冲突和跨账号访问；任务/文件追加处理中、失败、取消和过期。按 T01–T27 留存真实联调记录，不能只用现有拦截脚本替代。

## 7. 正式打包与契约变化

先在 HTTP 模式配置下执行 `npm run build`，再部署新生成的 `dist`。在正式 Web 服务上将 `/api/` 转发至后端并保留 `/api/v1` 路径，使页面和接口对浏览器同源。开发代理配置不会被打包进静态文件；Python 静态服务也不会自动代理业务 API。

`VITE_API_MODE` 在启动/构建时确定；给现有 Mock `dist` 添加 `.env.local` 不会把它变成 HTTP 版本。不同后端地址可由正式反向代理调整，前端保持 `/api/v1`。部署后仍须核验 Cookie、Origin、任务、文件和权限。

契约变化时先共同修改唯一 YAML 并记录兼容影响，再在前端依次执行：

```powershell
npm run generate:api
npm run typecheck
npm test
npm run build
```

生成类型后还要检查页面适配代码和真实联调，不代表自动完成业务变更。主要适配文件是 `src/api/client.ts`、`gateway.ts`、`mutations.ts`、`httpForms.ts`、`resources.ts` 与相关 `Remote*.vue` 组件。

参考：[Vite 环境变量与构建模式](https://vite.dev/guide/env-and-mode)、[Vite 开发代理](https://vite.dev/config/server-options#server-proxy)。
