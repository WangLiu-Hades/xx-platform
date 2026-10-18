> 2026-10-18修订：当前暂定开发规则及分工以[PD-2026-10-18.v1](../../docs/project-governance/暂定方案与修改记录.md)为准。下文旧pending/NO/签认门禁和人员编号保留作历史追踪；本轮不声称后端或生产验收通过。

# API交付入口

唯一当前机器契约是[openapi.yaml](<openapi.yaml>)，OpenAPI3.0.3、版本1.0.0-dev.2，共193个操作和581个Schema。当前前端有127项运行时静态引用、66项仅契约；这些数字不代表真实后端已实现。使用者为前端、后端、数据及测试工程师。

## 文件与目录

| 文件 | 功能与使用方法 |
| --- | --- |
| [openapi.yaml](<openapi.yaml>) | 当前路径、方法、参数、请求/响应、权限及pending的机器契约；供生成类型、实现与校验 |
| [FRONTEND_INTEGRATION.md](<FRONTEND_INTEGRATION.md>) | 前端HTTP模式、调用链、后端联调次序及交付要求 |
| [api-conventions.md](<api-conventions.md>) | 响应封装、分页、时间、会话、权限、状态及生成约定 |
| [error-codes.md](<error-codes.md>) | 错误码、HTTP状态及前端处理约定 |
| [api-coverage-matrix.md](<api-coverage-matrix.md>) | 214项PDF文字要求/28页面动作到接口、响应及后续动作的追踪 |
| [OPENAPI_COVERAGE.md](<OPENAPI_COVERAGE.md>) | M01–M15覆盖和无需新增API的能力 |
| [OPENAPI_PENDING.md](<OPENAPI_PENDING.md>) | P-001～P-021主清单，供规则确认与关闭；P-016在此登记 |
| [OPENAPI_CHANGELOG.md](<OPENAPI_CHANGELOG.md>) | 契约修改、兼容影响及版本记录 |
| [openapi-audit-report.md](<openapi-audit-report.md>) | 先前API审计问题、修订、验证方式及结论范围 |

## 怎么使用

先读约定、错误码及pending，再按operationId实现接口。公开HTTP API实现归DEV-01，数据查询/计算交接DEV-03，页面接入DEV-02；具体列表见[前端接口交接](<../../docs/project-governance/FRONTEND_API_HANDOFF.md>)。数据库字段与API投影看[存储映射](<../database/api-storage-map.md>)。不能用Mock数据决定未签认的阈值、权限或厂商协议。

当前Vue生成方式：先按[前端运行说明](<../../apps/web/README.md>)安装依赖，在`apps/web`目录执行：

```powershell
npm run generate:api
npm run typecheck
npm test
```

命令读取本目录`openapi.yaml`，更新`src/api/schema.ts`、`operations.ts`、`contract.json`、`validation-contract.json`及生成记录，不生成真实后端。当前Vue使用ESNext/Bundler和Axios封装。先前审计另使用Java17及OpenAPI Generator7.25.0验证TypeScript/Axios与Spring接口，JAR未随本目录提供；需要复现时参考审计报告，在隔离目录输出，不能覆盖当前客户端。

## 维护与检查

业务以原始PDF为准；变更契约时同步约定、覆盖、pending、变更记录、数据库映射、前端生成类型及测试。Java生成的名称映射可避免方法重名，不能自行改变JSON字段`setTaskId`。生成成功不等于运行接口可用；真实接入须验证会话、权限、任务与文件流。返回[系统设计入口](<../../docs/system-design/README.md>)。

本说明按2026-10-13本次操作指南检查时的实际文件清单同步。此前清单中的`openapi - 副本.yaml`当前不在本目录，不作为可直接打开的本地链接；本次未恢复或删除这些历史文件。
