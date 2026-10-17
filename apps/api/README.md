# xx-platform 后端服务（`apps/api`）

多维数据分析平台的**正式 HTTP API**。前端（Vue 原型）已经按契约写好，这里要把 193 个操作真正实现出来。

- 契约唯一来源：[openapi.yaml](../../contracts/api/openapi.yaml)（OpenAPI 3.0.3，版本 `1.0.0-dev.2`，193 操作 / 581 Schema，Base URL `/api/v1`）
- 接口约定：[api-conventions.md](../../contracts/api/api-conventions.md) ｜ 错误码：[error-codes.md](../../contracts/api/error-codes.md)
- 前端对接：[FRONTEND_INTEGRATION.md](../../contracts/api/FRONTEND_INTEGRATION.md)
- 契约速读（新人先看这个）：[API速读指南.md](../../docs/API速读指南.md)
- **两人分工与边界**：[BACKEND_TWO_DEV_SPLIT.md](../../docs/project-governance/BACKEND_TWO_DEV_SPLIT.md)

> 当前状态：**只有骨架，没有任何业务接口**。18 个源文件，其中 5 个是分层说明（`package-info.java`）。
> 骨架已完成并经过实跑验证的部分见 §4，未做的见 §5。

---

---

## 1. 五分钟跑起来


环境要求：**JDK 17** + Maven（本机实测：Java 17.0.12 + Maven 3.9.16，装在 `C:\Tools\apache-maven-3.9.16`）。

**两个构建入口，二选一**：

```powershell
# 入口 A：用本机 Maven（本机已装，最省事）
mvn -o -B compile

# 入口 B：用 Maven Wrapper（团队成员不用各自装 Maven，版本由 .mvn/wrapper 固定）
.\mvnw.cmd -o -B compile        # cmd/PowerShell
./mvnw -o -B compile            # Git Bash / Linux / macOS
```

Wrapper 的行为：**优先复用本机已装的 Maven**；本机没装时，按 `.mvn/wrapper/maven-wrapper.properties` 里的 `distributionUrl` 指引使用缓存发行版。说明：本目录的 `mvnw`/`mvnw.cmd` 是**轻量等价实现**（官方 `mvn wrapper:wrapper` 会额外生成一个二进制 `maven-wrapper.jar`，当前离线环境不便引入，详见 properties 文件里的注释）。需要换成官方 wrapper 时执行 `mvn -N wrapper:wrapper -Dmaven=3.9.16` 即可，properties 文件可继续沿用。

完整跑起来：

```powershell
cd apps/api

# 1) 编译（离线可用）
mvn -o -B compile

# 2) 带自检端点启动（dev profile 才会注册 /smoke）
mvn -o -B spring-boot:run "-Dspring-boot.run.profiles=dev"

# 3) 另开一个终端验证
curl.exe http://127.0.0.1:8080/api/v1/smoke
```

要连数据库（`GET /auth/csrf` 等端点需要），加上 `db` profile 与连接信息：

```powershell
# 口令不要写进仓库；本地真实值放 apps/api/.env.local（已被 .gitignore 排除）
$env:SPRING_PROFILES_ACTIVE      = 'dev,db'
$env:SPRING_DATASOURCE_URL       = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
$env:SPRING_DATASOURCE_USERNAME  = 'xxplatform_owner'
$env:SPRING_DATASOURCE_PASSWORD  = '***'
mvn -o -B spring-boot:run "-Dspring-boot.run.profiles=dev,db"
```

启动日志里应能看到 `Successfully validated 1 migration` 与
`Schema "public" is up to date`——说明 Flyway 已接上，库结构与契约一致。

**不带 `db` profile 时应用完全不连库**：没有 PostgreSQL 的机器照样能
`mvn compile` / 起服务 / 跑 `/smoke`。这是刻意的（原因见 `application-db.yml` 头部注释）。

端口 **8080**、上下文路径 **`/api/v1`**（在 `application.yml` 里配死），与前端 Vite 开发代理的默认目标一致。

一键自检脚本（编译 + 提示下一步）：

```powershell
# 用 powershell 或 pwsh 都可以；pwsh 不在 PATH 时用前者
powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/dev-check.ps1
```

### 前端联调怎么接

前端跑在 5180，通过 Vite 代理把 `/api/v1/**` 转发到 `http://127.0.0.1:8080`：

```powershell
cd apps/web
# 复制 .env.example 为 .env.local，设置 VITE_API_MODE=http、VITE_API_BASE_URL=/api/v1
$env:API_PROXY_TARGET='http://127.0.0.1:8080'
npm run dev -- --port 5180 --strictPort
```

代理**保留路径**，后端必须自己带 `/api/v1` 前缀（已配好）。注意：`API_PROXY_TARGET` 必须作为环境变量传入，只写进 `.env.local` 当前配置读不到。

---

## 2. 目录结构与分层


```
apps/api/
├── pom.xml                    Java 17 / Spring Boot 3.5.16；契约生成器在 generate-api profile 里
├── mvnw / mvnw.cmd            Maven Wrapper 入口（轻量等价实现，见 §1）
├── .mvn/wrapper/              固定 Maven 发行版与版本
├── README.md                  本文件
├── .gitignore                 target/ 与 IDE、本机配置
├── scripts/dev-check.ps1      编译自检
└── src/
    ├── main/
    │   ├── java/com/xxplatform/api/
    │   │   ├── ApiApplication.java        启动类（@SpringBootApplication）
    │   │   ├── api/                       ① 接口层：Controller（当前只有 dev 自检端点）
    │   │   ├── service/                   ② 业务层：领域规则、状态机、事务边界（空）
    │   │   ├── infra/                     ③ 基础设施：持久化/幂等/游标/文件/任务表/迁移（空）
    │   │   ├── client/                    ④ 外部依赖：DEV-03 查询计算、通知通道、设备协议（空）
    │   │   ├── common/                    ⑤ 跨层共享：响应壳、错误码、异常兜底、traceId
    │   │   ├── config/                    ⑥ 配置：JSON 口径、开发期 CORS
    │   │   └── generated/                 ⑦ 契约生成物（构建时生成，不入库，目前尚未生成）
    │   └── resources/application.yml
    └── test/java/com/xxplatform/api/ApiApplicationTests.java   骨架冒烟测试（2 个用例）
```

### 分层规则（写代码前请先看 `package-info.java`，每个包都写了边界）

| 层 | 只做 | 不做 |
| --- | --- | --- |
| `api` | 参数绑定与校验、调 service、返回响应壳 | 业务规则、直接访问数据库、自己拼错误壳 |
| `service` | 领域规则、状态机、`@Transactional` | 重写 DEV-03 的查询/算法 |
| `infra` | 持久化、幂等键、游标签名、文件存储、迁移 | 业务判断 |
| `client` | 调 DEV-03、通知通道、设备协议；超时/重试/错误翻译 | 把厂商协议暴露到 `/api/v1` |
| `common` | 响应壳、错误码、异常兜底、traceId | ——（**只有平台侧能改**，见 §6） |

`generated/` 里的东西**永远不要手改**：契约变了重新生成，编译器会告诉你哪个签名变了。

---

## 3. 契约生成器（重要，别配错）


契约不靠人手抄成 Java，用 OpenAPI Generator 生成 **接口 + DTO + 枚举**，保证 Java 签名和契约一字不差。

```powershell
# 需要联网下载生成器（首次），之后会缓存
mvn -Pgenerate-api generate-sources
```

生成到 `target/generated-sources/openapi`（在 `target` 下，`mvn clean` 会清掉，**不提交进仓库**）：

| 包 | 内容 |
| --- | --- |
| `com.xxplatform.api.generated.api` | 每个 operationId 一个 Java 接口方法 |
| `com.xxplatform.api.generated.model` | 581 个 Schema 对应的 DTO 与枚举（`TaskStatus`、`ErrorCode`…） |

配置（在 `pom.xml` 的 `generate-api` profile 内，必须与契约 `info.x-codegen.spring` 一致）：

```
generatorName          = spring
interfaceOnly          = true     ← 只生成接口，业务写在 service，契约更新不会覆盖你的代码
useSpringBoot3         = true     ← 否则生成 javax.*，Boot 3 编译不过
useOneOfInterfaces     = true     ← 契约有 21 个 discriminator / 22 个 oneOf（如 GeoJSON）
annotationLibrary      = none     ← 见下，让生成物不依赖 io.swagger 注解
documentationProvider  = none     ← 同上，不生成 @Operation/@ApiResponse
nameMappings           = setTaskId=setOperationId   ← 注意：是插件 mojo 参数，不是 configOptions
```

**关于 `annotationLibrary=none` + `documentationProvider=none`（重要）**：`spring` generator 默认会在每个接口方法上生成 `@Operation`/`@ApiResponse`，这些注解来自 `io.swagger.core.v3:swagger-annotations-jakarta`。**项目默认不引入该依赖**，所以不关掉这两项就会"生成成功、编译失败"。

**关于 `nameMappings` 的位置（这是个坑，已实测）**：它必须写成**插件 mojo 级参数**：

```xml
<nameMappings>
  <param>setTaskId=setOperationId</param>
</nameMappings>
```

**放进 `<configOptions>` 会被静默忽略**——不报错、不告警，生成物完全不改名。实测对比：放 configOptions 里时 `SetTaskCreated` 仍生成重复的 `setTaskId(...)` 方法导致编译失败；移到 mojo 级后正确生成 `setOperationId(...)` / `setSetOperationId(...)`。

为什么必须改名：契约 `SetTaskCreated` 同时含 `setTaskId` 和 `taskId` 两个属性，默认生成会给 `setTaskId` 属性造出 setter `setTaskId(...)`，与 `taskId` 的 setter **同名同参 → 重复方法 → 编译失败**。这是综合测试报告 2026-10-16 "生成后编译失败"的另一半原因（不只是缺 swagger 依赖）。改名只动 Java 方法名，**JSON 报文两侧仍是 `setTaskId` / `taskId`**。

| 方案 | 问题 |
| --- | --- |
| 补 `swagger-annotations` 依赖 | 默认构建（含离线环境）从此依赖它；一旦版本/仓库不可用，`mvn compile` 连骨架都编不了 |
| 提供一个 stub jar | 运行期若有代码反射注解会踩空，且不知注解版本，属于隐藏地雷 |
| **关闭注解生成（本方案）** | 生成物只依赖 `spring-web` + `validation`，离线可编译；签名与校验注解不受影响 |

代价是接口上没有 OpenAPI 注解。这可以接受：**契约文档的唯一来源是 `contracts/api/openapi.yaml` 本身**，不靠 Java 注解二次维护（`generateApiDocumentation` 本来也关着）。如果将来确实需要注解（例如接 springdoc 自动生成文档），再补那条依赖并去掉这两项即可——那是一次独立决策，不要顺手改。

**为什么放在 profile 里**：生成器插件需要联网下载，放进默认 `build` 会让离线环境连 `mvn compile` 都跑不了。所以默认构建不碰它，需要时显式加 `-Pgenerate-api`。**注意**：生成器本身（以及首次的插件依赖）必须联网拉取一次；之后走本地缓存。

---

## 4. 骨架已经做完并验证过的部分


| 能力 | 位置 | 说明 |
| --- | --- | --- |
| 启动与配置 | `ApiApplication`、`application.yml` | 8080 端口、`/api/v1` 上下文路径、UTF-8、关闭 RFC 7807（避免两套错误格式）、日志含 traceId |
| 统一成功壳 | `common/ApiResponses#success` | `{code:"0",message:"success",data,traceId}`，**不含 details** |
| 统一失败壳 | `common/ErrorResponse` | `{code,message,data:null,traceId,details:[]}`，**data 必须出现且为 null** |
| 错误码全集 | `common/ErrorCode` | 契约 34 个错误码 + 每个的 HTTP 状态映射；**另加 `METHOD_NOT_ALLOWED`(405)**，见下 |
| traceId 链路 | `common/RequestIdFilter` | 写 MDC、回写 `X-Trace-Id` 响应头；**默认由服务端生成**，保证头与体内一致 |
| 异常兜底 | `common/ApiExceptionHandler` | 参数校验/报文解析/未知字段/**方法不支持**/缺参/类型错/未知路由/未捕获，全部转成契约错误壳 |
| JSON 口径 | `config/WebConfiguration` | **未知字段拒绝**（`failOnUnknownProperties=true`，对齐 `additionalProperties:false`）；**故意不设全局 NON_NULL**（会吃掉错误体的 `data: null`） |
| 开发期 CORS | `config/WebConfiguration`、`config/ContractCorsProcessor` | 自定义 `CorsFilter`（映射必须写 `/**`，见下）；仅 `127.0.0.1:5180`/`localhost:5180`，带凭据；**拒绝时返回契约错误壳**而不是纯文本；生产必须换真实 Origin 白名单 |
| 数据库接入 | `application-db.yml` | 数据源**只在 `db` profile 下启用**；默认启动不连库，没装 PostgreSQL 的机器也能编译/起服务 |
| Flyway 启动迁移 | `application-db.yml`（`spring.flyway`） | `db` profile 启动时先 `validate` 再 `migrate`；校验和漂移会让进程**启动失败**而不是带病服务 |
| CSRF 令牌 | `auth/AuthController`、`auth/CsrfTokenService`、`auth/CsrfSessionRepository` | `GET /auth/csrf`：签发 `CSRFID` Cookie + 同步器 token，落 `auth.csrf_session`（只存 SHA-256） |
| 骨架冒烟测试 | `ApiApplicationTests` | 上下文能起来；未知路由返回契约错误壳且头=体（**沙箱内因 Mockito 自附加受限跑不了，见 §9**） |
| dev 自检端点 | `api/SmokeController` | 仅 `dev` profile 注册；`GET /api/v1/smoke` 验证成功壳/traceId/UTF-8/时间格式；`POST /api/v1/smoke/echo` 验证未知字段拒绝 |

### 骨架修复记录（综合测试报告 2026-10-16，已实测复现并修复）

| 问题 | 修复前实测 | 修复 |
| --- | --- | --- |
| CORS 预检 403 | 允许的 Origin 预检 → 403，无任何 `Access-Control-*` 头 | **根因是路径映射**：`addCorsMappings` 的模式相对 servlet context，而 `context-path=/api/v1`，所以 `/api/v1/**` 永远匹配不上。改为 `/**` |
| 方法不支持 → 500 | `POST /smoke`（GET-only）→ 500 `SYSTEM_INTERNAL` | 加 `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` → 405 `METHOD_NOT_ALLOWED` |
| traceId 客户端可决定 | 传 `X-Trace-Id: audit-client-controlled-123` 原样回显 | 改为服务端生成；保留透传能力但**默认关闭**，只在 `xx.trace.accept-client-trace-id=true` 且值符合形状时采用 |
| 未知字段被静默吞掉 | `failOnUnknownProperties=false` | 改为 `true`；`HttpMessageNotReadableException` 里还原字段名到 `details.field` |
| 生成后编译失败 | 生成出了 Java，但编译报两类错：① `io.swagger.v3.oas.annotations` 不存在；② `SetTaskCreated` 里 `setTaskId` 重复定义 | ① 加 `annotationLibrary=none`/`documentationProvider=none`，生成物自包含；② 把 `nameMappings` 从 `configOptions` 移到 **mojo 级参数**（原先被静默忽略） |

**关于 `METHOD_NOT_ALLOWED`(405)**：契约当前每个操作只声明了 400/401/403/500，**没有 405**。但把客户端错误报成 500 会污染服务故障日志与告警，因此骨架按 HTTP 标准语义返回 405，并已提报在契约通用响应中补 405 声明。在契约补声明前，这是"实现比契约多一个状态码"的唯一例外，**不要据此再扩展其他码**。

**关于 CORS 的 `/**`**：这是最容易改回去的坑。写成 `/api/v1/**` 时配置一条都不匹配，现象是"允许的 Origin 也被 403 且没有 `Access-Control-*` 响应头"。排查时可把 URL 改成 `/api/v1/api/v1/smoke` 自证——如果那样反而 200，就是路径映射写错了。

### 实跑验证记录（本机，2026-10-17 复核）

```
$ mvn -o -B compile
[INFO] BUILD SUCCESS

$ GET http://127.0.0.1:8080/api/v1/smoke          （dev profile）
{"code":"0","message":"success","data":{"status":"UP","profile":"dev",
 "checkedAt":"2026-10-17T11:28:46.379887+08:00","note":"骨架自检：统一成功壳 / traceId / UTF-8"},
 "traceId":"61ca8601-..."}

$ GET http://127.0.0.1:8080/api/v1/no-such-route
HTTP 404   X-Trace-Id: d7039b3b-...
{"code":"DATA_NOT_FOUND","message":"资源不存在","data":null,
 "traceId":"d7039b3b-...","details":[]}

$ GET /api/v1/smoke 带 X-Trace-Id: audit-client-controlled-123
X-Trace-Id: 61ca8601-...          ← 服务端重新生成，不再回显客户端值

$ OPTIONS /api/v1/smoke  Origin: http://127.0.0.1:5180, ACRM: POST
HTTP 200   Access-Control-Allow-Origin: http://127.0.0.1:5180
           Access-Control-Allow-Credentials: true

$ OPTIONS /api/v1/smoke  Origin: http://evil.example.com
HTTP 403                          ← 非白名单 Origin 仍然拒绝

$ POST /api/v1/smoke               ← GET-only 路由
HTTP 405   {"code":"METHOD_NOT_ALLOWED","message":"请求方法不被支持",...}

$ POST /api/v1/smoke/echo  {"name":"abc","count":3,"typoField":true}
HTTP 400   {"code":"VALIDATION_ERROR","message":"请求包含未知字段",
            "details":[{"field":"typoField","reason":"该字段不在请求 Schema 中（additionalProperties:false）"}],...}

$ POST /api/v1/smoke/echo  {"name":"abc","count":3}
HTTP 200   {"code":"0",...,"data":{"name":"abc","count":3}}
```

回归脚本：[`scripts/verify-skeleton.ps1`](scripts/verify-skeleton.ps1)（需服务已在 dev profile 启动）。

### `GET /auth/csrf`（任务 4，2026-10-17 实现）

契约出处：`openapi.yaml` → `/auth/csrf`（`x-permission: PUBLIC`，匿名可取）。

**请求**：`GET /api/v1/auth/csrf`，无参数、无请求体。

**响应**：

```
HTTP 200
Set-Cookie: CSRFID=<43 字符 base64url>; Path=/api/v1; HttpOnly; SameSite=Lax
X-Trace-Id: ea0abf6d-1120-494d-9455-42e7ad7299ac

{"code":"0","message":"success",
 "data":{"token":"<43 字符 base64url>","headerName":"X-CSRF-Token"},
 "traceId":"ea0abf6d-1120-494d-9455-42e7ad7299ac"}
```

**四个设计决定（都有取舍，改动前先读）**

1. **Cookie 值与 body token 是两个不同的随机值，不是同一个。**
   只发同一个值就是"双重提交 Cookie"，而双重提交挡不住 Cookie 注入攻击（攻击者能往
   受害者域写同名 Cookie）。服务端把两者的 SHA-256 都存进 `auth.csrf_session` 并按
   Cookie 关联，于是伪造的 Cookie 找不到匹配的 token。代价是每个浏览器会话多一行数据、
   写请求要查一次库（走 `cookie_hash` 唯一索引，代价可忽略）。

2. **只存哈希，不存原文。** 表里 `cookie_hash`/`token_hash` 都是 32 字节 SHA-256。
   即使库被读，也拿不到可用的 Cookie/Token。验收脚本会断言
   `cookie_hash == sha256(收到的 Cookie 值)`，这是"确实在存哈希"的直接证据。

3. **`Secure` 是可配置的，默认 `false`。** 契约写死 `Secure`，但本地
   `http://127.0.0.1` 调试时浏览器不会回传 Secure Cookie，链路直接断。
   因此 `xx.csrf.secure-cookie` 控制它，非 HTTPS 时**打 WARN 日志**。
   **生产必须设为 `true` 并强制 TLS**（SD06）。这是"契约 vs 可调试性"的取舍，
   不是疏漏——`OPENAPI_PENDING.md` 的 P-001 里也还留着跨域部署边界待签认。

4. **同源校验做两次。** CORS 过滤器先挡一道（浏览器发起的跨源请求根本到不了控制器），
   控制器里的 `Origin`/`Referer` 校验是第二道，用于 CORS 被关闭或非浏览器场景。
   两者都失败为 `403 AUTH_CSRF_INVALID`——**不要**改成 `PERMISSION_DENIED`：
   来源校验失败时我们连调用者是谁都不知道，与"已认证用户越权"是两回事，
   混用会让安全审计无法区分跨站伪造和越权。

**`session_id` 现在是 `NULL`。** CSRF 必须在登录前就能拿到（登录请求本身要带 token），
所以此刻还没有会话可绑；登录成功后由会话服务回填（任务 5）。表上该列本身就允许 NULL。

**验收**：

```
# 服务需带 db profile 启动（本端点要写库）
$env:SPRING_PROFILES_ACTIVE='db'
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/xxplatform'
$env:SPRING_DATASOURCE_USERNAME='xxplatform_owner'
$env:SPRING_DATASOURCE_PASSWORD='...'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=db"

# 另开终端
$env:DB_PASSWORD='...'
powershell -ExecutionPolicy Bypass -File scripts\verify-csrf.ps1
# => RESULT: 34 passed, 0 failed
```

脚本覆盖 34 条：信封与 `traceId` 一致性、token 长度与 `headerName` 枚举、
Cookie 四属性、Cookie≠token、两次调用不复用、同源 200 / 异源 403 且**响应体是契约壳**
（不是纯文本 `Invalid CORS request`）、无 Origin 的非浏览器请求放行、
以及 8 条数据库断言（每次签发插一行、哈希 32 字节、`sha256` 可复算、
原文不入库、`session_id` 为 NULL、`created_at < expires_at`）。

### CORS 拒绝响应为什么要自定义（2026-10-17）

Spring 默认在 Origin 不在白名单时返回 **纯文本** `Invalid CORS request`。这违反
`api-conventions.md` 的统一响应壳，前端统一按 `code` 分支时会直接抛 JSON 解析异常，
用户看到"网络错误"而不是"请求来源不可信"。

因此 CORS 从 `addCorsMappings` 改成自定义 `CorsFilter` +
[`config/ContractCorsProcessor`](src/main/java/com/xxplatform/api/config/ContractCorsProcessor.java)，
把拒绝响应改写成 `403 AUTH_CSRF_INVALID` 的标准错误壳。

**两个容易踩的点**：

- Spring 6.2 的 `DefaultCorsProcessor.rejectRequest` 是**单参数**
  `rejectRequest(ServerHttpResponse)`（旧版本是双参数带 message）。签名写错**不会编译失败**，
  只是静默不生效、继续返回纯文本——所以 `verify-csrf.ps1` 专门有一条断言检查"体是 JSON"。
- `RequestIdFilter` 必须排在 CORS 过滤器**之前**（`HIGHEST_PRECEDENCE` vs
  `HIGHEST_PRECEDENCE + 10`），否则拒绝响应里 `traceId` 拿不到，会出现
  "响应头有值、体内是 `unknown-trace`"的不一致。

### 契约生成器验证记录（2026-10-17，实测）

```
$ mvn -B -Pgenerate-api compile
[INFO] Generator 'spring' is considered stable.
[INFO] writing file .../generated/model/SetTaskCreated.java
[INFO] Compiling 612 source files with javac [debug parameters release 17]
[INFO] BUILD SUCCESS
```

- 生成 **600 个 Java 文件**（193 个 Api 接口 + 模型/枚举），编译通过。
- 生成物 **0 处** `io.swagger` 引用 —— 全局离线可用。
- `SetTaskCreated` 正确生成 `setOperationId(...)`（JSON 仍是 `setTaskId`）+ `setTaskId(...)`，不再重复。
- **默认构建（无 profile）仍可完全离线**：`mvn -o -B compile` 通过。

> 若构建机没有可写的 `~/.m2`（例如受限沙箱），可临时把本地仓库指到工作区内：
> `mvn -Dmaven.repo.local=<path> -Pgenerate-api compile`。这只影响缓存位置，不改任何构建语义。

---

## 5. 还没做（按阶段，见分工方案 §4）


**基础设施（平台侧先做，另一侧只调用）**

- [x] CSRF 令牌签发：`GET /auth/csrf`、`CSRFID` Cookie 属性、同步器 token 落库（任务 4，2026-10-17）
- [ ] 会话与登录：SID Cookie（HttpOnly/Secure/SameSite=Lax/Path=/api/v1）、`POST /auth/login`、login 轮换 SID 并回填 `csrf_session.session_id`、写请求校验 `X-CSRF-Token`+可信 Origin 的**拦截器**（任务 5）
- [ ] 权限与数据范围：`PermissionCode`（58 项）注解 + 数据范围切面，列表/详情/导出/文件每次重校
- [ ] 幂等：`Idempotency-Key`（16–160 字符），同键同 payload 返原响应，异 payload 409
- [ ] 乐观锁：`If-Match` ↔ `version`，冲突 409 `RESOURCE_VERSION_CONFLICT`
- [ ] 签名游标：绑定账号/范围版本/查询快照/排序/页大小，只校验不解析，失败 400 `DATA_CURSOR_INVALID`
- [ ] 任务底座：`async_task` 表 + 状态机 + 领取/续租/取消/重试/发布协议（H04 交接物）
- [ ] 文件底座：multipart 分类策略、`PROCESSING→ACTIVE`、受权下载（不返回永久裸 URL）
- [ ] 审计埋点、`client-config.features` 能力开关（未签认能力返回 422 `REQUIREMENT_PENDING`）
- [x] 数据库迁移框架（DDL 版本化，只有平台侧统一合并）+ 启动时自动迁移（任务 3，2026-10-17）

**业务接口**：193 个操作全部未实现，按分工方案的模块归属与阶段推进。

**重要提醒**：65 个操作在契约里标了 `x-design-status: pending`（**Profile 领域 36 个里有 24 个**）。这些**不要猜规则、不要用 Mock 阈值实现**，必须返回 `422 REQUIREMENT_PENDING`。动手前先查 [OPENAPI_PENDING.md](../../contracts/api/OPENAPI_PENDING.md)。

---

## 6. 归属与协作规则（两人并行，别改到对方的东西）


模块级不重叠，完整清单见 [BACKEND_TWO_DEV_SPLIT.md](../../docs/project-governance/BACKEND_TWO_DEV_SPLIT.md)：

| | 平台与运维侧 | 数据与研判侧 |
| --- | --- | --- |
| 模块 | M01、M06、M10、M12、M14、M15 | M03、M04、M05、M07、M08、M09、M11、M13 |
| 操作数 | 113 | 80 |
| 依赖 DEV-03 | 0 | 72 |

**三条硬规则**

1. `common/`、`infra/`、`config/` 是**共享层**，由平台侧主写；另一侧只能调用，要新能力提需求，不要 fork 第二份响应壳/错误码。
2. **`async_task` 表与任务状态机只有平台侧能动**；另一侧创建任务只调已发布接口，不自己推进状态。
3. **数据库迁移由平台侧统一合并**；另一侧的迁移片段走 PR 提交，不各自改库。

**已知的跨边界三处**（规则见分工方案 §3）：设备实体只有平台侧能改（另一侧报表只读引用）；通知投递/回执/频控的写路径只有平台侧能动（另一侧只读 `/deliveries`、`/messages`）；任务底座如上。

---

## 7. 契约红线（写完接口自检用）


1. 成功 `code` 是字符串 `"0"`，不是数字 200；失败 `data` 必须出现且为 `null`，`details` 是数组。
2. **按 `code` 判断，不按中文 message 判断**；每个响应都要有 `X-Trace-Id`，且与体内 `traceId` 相同。
3. 权限在后端：**单对象不可见返回 404 `DATA_NOT_FOUND`**（隐藏存在性），403 只用于"操作整体无权/范围收缩"。前端隐藏按钮不算授权。
4. 分页只有两套：管理资源用 `page/pageSize/total`，大结果用 `cursor/nextCursor`；游标服务端签名，**不要伪造 total**。
5. 时间是 RFC 3339 带偏移（UTC 存储/返回 Z），`TimeRange` 是 `[from,to)`；地图是 WGS84 且**经度在前**。
6. `targetId`（目标代理 ID）≠ `fileAssetId`（文件资产）；`analysisTaskId`（分析任务）≠ `taskId`（执行任务）。
7. 异步 202 只代表"已接受"，不代表执行成功；取消要先写 `cancelRequestedAt`，终态不可撤回（409）。
8. 未签认能力在 `features` 里禁用并返回 `REQUIREMENT_PENDING`，**不得用 Mock 阈值上线**。
9. 文件上传要校验真实内容（不是扩展名），只有 `ACTIVE` 才可引用；下载返回二进制流、不套 JSON 壳。
10. `interfaceOnly=true` 不生成 Controller，**同一个生成的 Api 接口只允许一个实现类**，否则 Spring 启动报 `NoUniqueBeanDefinitionException`（建议后续加个"一接口一实现"的上下文测试）。
11. **未知字段必须拒绝**（`additionalProperties:false`）：多传/拼错字段要 400 并在 `details.field` 指出，不能静默忽略。
12. 框架异常要**逐个显式处理**，否则会掉进 `Exception` 兜底变成 500，把客户端错误伪装成服务故障。

---

## 8. 数据库迁移（Flyway）


**技术选型**：Flyway 11.7.2（版本由 `spring-boot-starter-parent` 管理，不在 pom 里写死）。
理由：迁移是**有序递增的 SQL 文件**，与项目"DDL 版本化、只有平台侧统一合并"的约定最贴合；Liquibase 的 XML/YAML changelog 对本项目是多余抽象。

### 8.1 目录与命名

```
apps/api/src/main/resources/db/migration/
└── V1__baseline_design_2.sql      ← 契约 schema.sql 的逐字节副本（空库基线）
```

- 命名必须 `V<序号>__<描述>.sql`（**双下划线**），否则 Flyway 会静默忽略。
- **V1 是 `contracts/database/schema.sql` 的逐字节副本**，SHA256 `7d3534b5…`（163 表 / 6 schema）。
  采取复制而非重写，是为了让"契约与实现漂移"变成一条红测试，而不是靠人记得同步。
- **禁止编辑已发布的 V1**：Flyway 用 checksum 校验，改了会拒绝启动（这就是我们要的保护）。
- 契约变更的正确顺序：改 `schema.sql` + 数据字典 + `api-storage-map` → **新增 `V2__xxx.sql` 增量迁移** → 同步更新 `MigrationBaselineTests` 的基线哈希。

### 8.2 一次性 provisioning（超级用户）

Flyway 用**迁移 owner** 账号跑，但 `CREATE EXTENSION postgis` 需要**超级用户**，且它不能干净地放在 Flyway 的事务里（会报 `25001: cannot run inside a transaction block`）。所以把扩展的创建**前置**成一次性动作：

```powershell
# 超级用户口令只在 provisioning 时用，别写进仓库
$env:PGSUPERUSER_PASSWORD = '...'

powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/db-provision.ps1 `
    -Database xxplatform `
    -Owner xxplatform_owner `
    -OwnerPassword '<owner 口令>'
```

脚本幂等，做四件事：建 owner 角色（`NOSUPERUSER NOCREATEDB NOCREATEROLE`）→ 建 UTF8 库并指定 owner → 以超级用户建 PostGIS → 建六个项目 schema 并授权给 owner。**它从不 DROP 任何东西**；若目标库已存在项目表会直接拒绝退出（除非显式 `-Force`）。

### 8.3 执行迁移

`db-migrate` 是**独立 profile**，不进默认构建——迁移是部署动作，不该在 `mvn package` 时顺手改库，也要保证没数据库的机器能 `mvn compile`。

```powershell
# 口令走环境变量，不写进仓库
$env:DB_URL      = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
$env:DB_USER     = 'xxplatform_owner'      # 迁移专用账号，与 app/Worker 账号隔离
$env:DB_PASSWORD = '...'

cd apps/api
mvn -Pdb-migrate flyway:migrate      # 空库 → 建 163 张表
mvn -Pdb-migrate flyway:info         # 看已应用版本
mvn -Pdb-migrate flyway:validate     # 校验 checksum 未漂移
```

或用封装脚本（会先检查环境变量是否齐全）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/db-migrate.ps1 -Command info
```

`.env.example` 给了完整变量清单；复制成 `.env.local` 填真实值即可（`.env*` 已被 gitignore，只有 `.example` 进仓库）。

### 8.4 环境前置

| 项 | 要求 | 说明 |
| --- | --- | --- |
| PostgreSQL | **14+** | `V1` 内有 `server_version_num<140000` 的 RAISE 守卫，低于 14 直接失败 |
| PostGIS | **≥3.1.4，且不能是 3.0.x** | `V1` 显式拒绝 `^3[.]0[.]`；PG14 不兼容 PostGIS 3.0 |
| 账号 | **迁移 owner 与 app/Worker 账号隔离** | owner 建表/改 schema；运行账号只增删改数据，不能被授予 TRUNCATE/schema 变更 |
| 扩展权限 | **PostGIS 必须由超级用户预装** | 见 §8.2；普通 owner 无 `CREATE EXTENSION` 权限（`42501`） |

> `V1` 里仍保留 `CREATE EXTENSION IF NOT EXISTS postgis;`（因为它是 `schema.sql` 的逐字节副本，被守卫测试保护）。扩展预装后该语句退化为**警告**而非错误，迁移照常成功——本机实测就是这条路径。

缺少扩展时需先以超级用户执行 `CREATE EXTENSION postgis;`，否则 `V1` 会在第一步失败。

### 8.5 空库 vs 升级库

- **空库**：直接 `flyway:migrate`，一条 `V1` 建出全部 163 表。
- **升级旧库**：`schema.sql` **不是旧库升级脚本**（它假设空库）。旧库迁移必须按
  [migration-plan.md](../../contracts/database/migration-plan.md) 的 5 步生成**增量**迁移，
  其中大索引要 `CREATE INDEX CONCURRENTLY` 且在事务块外单独执行。
- `baselineOnMigrate=true` + `baselineVersion=0`：允许在已有内容的库上以基线方式接管，
  但**不代表旧库已迁移成功**——那是增量迁移的责任。

### 8.6 守卫测试

`src/test/java/.../migration/MigrationBaselineTests.java`（4 个用例，不连数据库、不启动 Spring）：

| 用例 | 拦住什么 |
| --- | --- |
| V1 与 `schema.sql` 逐字节一致 | 有人改了契约但没同步迁移 |
| `schema.sql` 的 SHA256 未变 | 契约被静默改动 |
| 六个 schema + PG/PostGIS 守卫 + 事务包裹齐全 | 基线被"优化"掉关键部分 |
| 迁移命名符合 `V<n>__<desc>.sql` | 文件命名错导致 Flyway 忽略 |

> 该测试**只跑自身**可绕开沙箱限制：
> `mvn -Dtest=MigrationBaselineTests test`（`ApiApplicationTests` 需要 Mockito 自附加，受限环境会失败，见 §9）。

---

## 9. 环境注意（踩过的坑）


| 现象 | 原因与做法 |
| --- | --- |
| `mvn package` / `mvn clean` 报无法解析插件 | 本机离线（Maven Central 不通）时，Maven **插件自身**的依赖可能没缓存。`mvn compile` 与 `mvn spring-boot:run` 不受影响；需要打包时先联网让 Maven 拉一次 |
| `mvn -Pgenerate-api generate-sources` 失败 | openapi-generator 未缓存，必须联网一次；之后本地缓存可复用 |
| 测试里出现 `Mockito ... Could not self-attach to current VM` | 沙箱环境禁止 Mockito 的自附加代理，**不是代码问题**；正常开发机/CI 上 `mvn test` 可正常跑 |
| 手工用 `.m2` 拼 classpath 启动会报 snakeyaml 找不到 | 别手工拼 classpath（会带上别的项目的旧版 slf4j/devtools），用 `mvn spring-boot:run` |
| 直接 `java -jar` 跑不了 | 还没执行过 `package`；先 `mvn package` 生成可执行 jar |
| `mvnw`/`mvnw.cmd` 报奇怪的 `'xx' is not recognized` | `.cmd` 必须是 **CRLF + ASCII**：cmd.exe 逐行解析批处理，LF 换行或 UTF-8 中文注释都会让它错乱。改这个文件时注意别被编辑器改成 LF |
| `Non-parseable POM ... in comment after two dashes (--)` | **XML 注释里不能出现 `--`**。写 Java 注释时随口写 `--spring.profiles.active=db` 就会炸，而且报错位置指向注释内部，不太好找 |
| `Cannot access central ... in offline mode` 却又明显联网可用 | 本机 `~/.m2` **只读**（沙箱策略），Maven 无法写入缓存 → 加 `-Dmaven.repo.local=<workspace 内可写目录>` 即可，网络本身是通的 |
| `spring-boot:run` 报插件依赖缺 `spring-boot-loader-tools` | 用了 `-o` 且本地仓库里只有编译期依赖。去掉 `-o` 让它拉一次插件依赖（`compile` 仍可离线） |
| `mvn test` 全量跑挂但单测本身没问题 | `ApiApplicationTests` 需要 Mockito 自附加；只跑守卫测试用 `-Dtest=MigrationBaselineTests` |
| CORS 拒绝返回纯文本 `Invalid CORS request` | `DefaultCorsProcessor` 默认行为，且 Spring 6.2 的 `rejectRequest` 是**单参数**——签名写错不编译失败、只是静默不生效。见 §4 `ContractCorsProcessor` |

---

## 10. 接下来各自的第一件事


1. **先跑 `mvn -o -B compile` 和 `mvn spring-boot:run -Dspring-boot.run.profiles=dev`**，用 §4 的两条响应确认骨架在你机器上也是通的。
2. **把 `scripts/dev-check.ps1` 跑一遍**，确认 JDK/Maven 版本对得上。
3. 要动数据库的接口，带 `db` profile 启动（见 §1），跑一次 `scripts/verify-csrf.ps1` 确认库和迁移都正常。
4. 按分工方案 §2 确认自己负责的模块，再按 §4 的阶段顺序开工。
5. 共享层接口在开工第一周内冻结并补进本 README 的 §4 表格；之后只在评审后变更。
6. 需要新增分层或改包结构，先在这里更新 §2，别让下一个人对着过时文档写代码。
