# 前端接口适配层

本目录把页面动作转换为OpenAPI请求并映射响应，不包含真实服务。来源为[当前机器契约](<../../../../contracts/api/openapi.yaml>)。

## 文件与目录

| 文件 | 功能与维护方式 |
| --- | --- |
| [client.ts](<client.ts>) | Axios本站入口、强类型request、CSRF/幂等/If-Match、会话失效、取消、错误及二进制下载 |
| [gateway.ts](<gateway.ts>) | 远端状态、分页/游标、编码/视图映射、页面查询、Context及ETag记录 |
| [mutations.ts](<mutations.ts>) | Context、模块来源、目标解析及新增/更新/删除/撤销适配 |
| [httpForms.ts](<httpForms.ts>) | 从字典/资源/配置构造HTTP表单、初值和编辑意图 |
| [resources.ts](<resources.ts>) | 上传策略、受控上传、导入任务/模板、证据与档案筛选 |
| [tasks.ts](<tasks.ts>) | awaitTask轮询统一任务，处理成功/失败/取消与AbortSignal |
| [validation.ts](<validation.ts>) | AJV校验参数、正文和响应，按需加载校验器 |
| [schema.ts](<schema.ts>) | **生成文件**：OpenAPI DTO及operation TypeScript类型 |
| [operations.ts](<operations.ts>) | **生成文件**：operationId到方法/路径/权限/pending轻量目录 |
| [contract.json](<contract.json>) | **生成文件**：完整Schema、参数、样例和操作描述 |
| [validation-contract.json](<validation-contract.json>) | **生成文件**：去说明/样例的运行时校验Schema |

## 怎么使用

从client导入request，按operationId传path/query/body，适用时传etag、signal或upload。查询在gateway或Remote组件加载，写操作经mutations/store编排。文件先读取策略/上传再提交导入任务；异步响应只表示已提交，成功后才能取结果或下载。

契约改变从工程根执行`npm run generate:api`，覆盖上述四个生成文件和生成记录。手工维护client/gateway等适配文件，不手工改生成DTO，也不使用历史YAML副本。

## 维护与检查

执行typecheck及contracts/http-forms/http-resources/session相关测试，按变动跑HTTP样例回归，再接真实后端。错误须保留traceId，HTTP不以合成数据兜底。静态引用不是成功调用覆盖率。返回[源码入口](<../README.md>)。
