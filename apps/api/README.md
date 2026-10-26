> 当前基线：PD-2026-10-19.v1；后端个人模块按[当前分工](../../docs/project-governance/BACKEND_TWO_DEV_SPLIT.md)。前端DEV-03、数据DEV-04、测试DEV-05。

# xx-platform 后端服务（`apps/api`）

多维数据分析平台的**正式 HTTP API**。前端（Vue 原型）已经按契约写好，这里要把 198 个操作真正实现出来。

- 契约唯一来源：[openapi.yaml](../../contracts/api/openapi.yaml)（OpenAPI 3.0.3，版本 `1.0.0-dev.4-provisional`，198 操作 / 623 Schema，Base URL `/api/v1`）
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

**构建入口**：

```powershell
# 入口 A：用本机 Maven（本机已装，最省事）
mvn -B compile

# 入口 B：用 轻量Maven启动脚本（复用已有安装或缓存，不自动下载）
.\mvnw.cmd -B compile        # cmd/PowerShell
sh mvnw -B compile            # Git Bash / Linux / macOS
```

Wrapper 的行为：**优先复用本机已装的 Maven**；本机没装时，按 `.mvn/wrapper/maven-wrapper.properties` 里的 `distributionUrl` 指引使用缓存发行版。说明：本目录的 `mvnw`/`mvnw.cmd` 是**轻量等价实现**（官方 `mvn wrapper:wrapper` 会额外生成一个二进制 `maven-wrapper.jar`，当前离线环境不便引入，详见 properties 文件里的注释）。需要换成官方 wrapper 时执行 `mvn -N wrapper:wrapper -Dmaven=3.9.16` 即可，properties 文件可继续沿用。

完整跑起来：

```powershell
cd apps/api

# 1) 编译（离线可用，不需要数据库）
mvn -o -B compile

# 2) 启动（需要数据库；带 dev 时会按下面的配置补种开发账号）
#    口令不要写进仓库；本地真实值放 local-only/apps-api.env.local（已被 .gitignore 排除）
$env:SPRING_DATASOURCE_URL      = 'jdbc:postgresql://127.0.0.1:5432/xxplatform'
$env:SPRING_DATASOURCE_USERNAME = 'xxplatform_owner'
$env:SPRING_DATASOURCE_PASSWORD = '***'
mvn -o -B spring-boot:run "-Dspring-boot.run.profiles=dev,db"

# 3) 另开一个终端验证
curl.exe http://127.0.0.1:8080/api/v1/health
curl.exe http://127.0.0.1:8080/api/v1/ready
```

启动日志里应能看到 `Successfully validated 1 migration` 与
`Schema "public" is up to date`——说明 Flyway 已接上，库结构与契约一致。

**应用启动需要数据库**（从任务 4 起）：仓储层用 `JdbcTemplate`，没有数据源时 Spring 会在启动阶段直接报
`Failed to configure a DataSource: 'url' attribute is not specified`。这是刻意的——用 H2 之类的内存库兜底
只会让"没装 PostgreSQL 也能起"变成"起来之后每条查询都错在别的地方"。

没有 PostgreSQL 的机器仍然可以 `mvn -o -B compile` 与 `mvn -o -B test`：测试用占位 URL +
`initialization-fail-timeout=-1`，不会真的去连库。

「数据源存在但连不上」的情况由 `GET /ready` 如实报告（503，见下文探针一节）。

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
├── scripts/
│   ├── dev-check.ps1          编译自检
│   ├── db-provision.ps1       建角色/建库/装 PostGIS（幂等，从不 DROP，见 §8.2）
│   ├── db-migrate.ps1         跑 Flyway 迁移（见 §8.3）
│   ├── verify-skeleton.ps1    共享层验收 25 条
│   ├── verify-csrf.ps1        CSRF 验收 34 条
│   ├── verify-login.ps1       登录验收 65 条
│   ├── verify-me.ps1          /me 验收 94 条
│   ├── verify-scope-tuples.ps1 数据范围元组验收 15 条（A01 交叉扩权反例）
│   ├── verify-client-config.ps1  前端配置验收 53 条
│   ├── verify-health.ps1      探针验收 47 条
│   └── verify-logout.ps1      退出登录验收 41 条
└── src/
    ├── main/
    │   ├── java/com/xxplatform/api/
    │   │   ├── ApiApplication.java        启动类（@SpringBootApplication）
    │   │   ├── api/                       ① 接口层：Controller + 请求 DTO
    │   │   │   ├── AuthController.java      GET /auth/csrf、POST /auth/login、POST /auth/logout、GET /me
    │   │   │   ├── ClientConfigController.java  GET /client-config
    │   │   │   └── SystemController.java    GET /health、GET /ready
    │   │   ├── service/                   ② 业务层：领域规则、状态机、事务边界
    │   │   │   ├── CsrfTokenService.java
    │   │   │   ├── LoginService.java
    │   │   │   ├── SessionService.java
    │   │   │   ├── SessionAuthenticator.java
    │   │   │   ├── MeService.java
    │   │   │   ├── MeView.java
    │   │   │   ├── AuthenticatedSession.java
    │   │   │   ├── ScopeCalculator.java
    │   │   │   ├── ClientConfigService.java / ClientConfigScalars.java / ClientConfigView.java
    │   │   │   └── HealthService.java / HealthView.java
    │   │   ├── infra/                     ③ 基础设施：持久化、口令哈希、频控、种子数据
    │   │   │   ├── UserAccountRepository.java
    │   │   │   ├── UserSessionRepository.java
    │   │   │   ├── CsrfSessionRepository.java
    │   │   │   ├── RbacRepository.java
    │   │   │   ├── PasswordHasher.java
    │   │   │   ├── LoginThrottle.java
    │   │   │   ├── DevUserSeeder.java
    │   │   │   └── ClientConfigRepository.java
    │   │   ├── client/                    ④ 外部依赖：DEV-04 查询计算、通知通道、设备协议（空）
    │   │   ├── common/                    ⑤ 跨层共享：响应壳、错误码、异常兜底、traceId、时间与摘要
    │   │   │   ├── ApiResponses.java / ErrorResponse.java / ErrorCode.java / ApiException.java
    │   │   │   ├── ApiExceptionHandler.java / RequestIdFilter.java
    │   │   │   ├── Instants.java（UTC 秒级时间与 ISO 输出）/ RequestCookies.java
    │   │   │   └── Sha256.java（摘要与常量时间比较，**不用于口令**）
    │   │   ├── config/                    ⑥ 配置：JSON 口径、开发期 CORS、各层可调参数
    │   │   │   ├── WebConfiguration.java / ContractCorsProcessor.java
    │   │   │   ├── CsrfProperties.java / SessionProperties.java
    │   │   │   └── LoginThrottleProperties.java / DevSeedProperties.java
    │   │   └── generated/                 ⑦ 契约生成物（构建时生成，不入库）
    │   └── resources/
    │       ├── application.yml            默认配置（不含数据源；启动须带 db profile）
    │       ├── application-db.yml          db profile：数据源 + 启动时 Flyway 迁移
    │       └── db/migration/          V1 基线 DDL（逐字节副本）+ V2 部署默认值种子（见 §8）
    └── test/java/com/xxplatform/api/
        ├── ApiApplicationTests.java        骨架冒烟（手工起应用，不用 @SpringBootTest）
        ├── infra/LoginThrottleTests.java
        ├── infra/PasswordHasherTests.java
        ├── service/ScopeCalculatorTests.java
        └── migration/MigrationBaselineTests.java
```

### 分层规则（写代码前请先看 `package-info.java`，每个包都写了边界）

| 层 | 只做 | 不做 |
| --- | --- | --- |
| `api` | 参数绑定与校验、调 service、返回响应壳 | 业务规则、直接访问数据库、自己拼错误壳 |
| `service` | 领域规则、状态机、`@Transactional` | 重写 DEV-04 的查询/算法 |
| `infra` | 持久化、幂等键、游标签名、文件存储、迁移、口令哈希、频控 | 业务判断 |
| `client` | 调 DEV-04、通知通道、设备协议；超时/重试/错误翻译 | 把厂商协议暴露到 `/api/v1` |
| `common` | 响应壳、错误码、异常兜底、traceId、时间/摘要工具 | 业务规则（**只有平台侧能改**，见 §6） |
| `config` | 配置类、`@ConfigurationProperties`、开发期 CORS | 业务规则 |

`generated/` 里的东西**永远不要手改**：契约变了重新生成，编译器会告诉你哪个签名变了。

### 为什么按"层"分目录，而不是按"模块"分

任务 4–6 的 auth 相关代码**没有**挤在一个 `auth/` 子包里，而是按职责散到 `api`/`service`/`infra`/`config` 四层。原因是这两条约束：

1. **团队约定优先**。`api/package-info.java` 里写的命名约定是「领域 + Controller」（`SiteController implements SitesApi`），一个生成的 Api 接口只允许一个实现类；`service`/`infra` 的边界也是这样划的。再按模块套一层 `auth/`，就变成 `com.xxplatform.api.auth.AuthController` 和 `com.xxplatform.api.api.SiteController` 两套写法并存，后来的人不知道该跟哪套。
2. **按模块分包会让层级边界变模糊**。一个 `auth/` 包既放 Controller 又放 SQL 和事务，半年后没人能保证"Controller 里不写 SQL"这条规则。按层分包，`import` 一眼就能看出跨层调用。

代价是**层内的类会变多**，用**类名前缀**区分：`AuthController`、`LoginService`、`MeService`、`UserAccountRepository`、`RbacRepository`、`SessionProperties`、`DevSeedProperties`。看类名前缀就知道属于哪个领域，看包名就知道在哪一层。**新增领域（如 `sites`、`devices`）时沿用同一套前缀，不要再新建 `sites/` 这样的子包。**

### 新加一个接口时，怎么判断该放哪一层

| 你要写的代码 | 落位 | 判断标准 |
| --- | --- | --- |
| `@GetMapping`/`@PostMapping`、`@Valid @RequestBody` | `api` | 只在"HTTP 长什么样"这一层 |
| 请求/响应体的手写 record | `api` | 契约生成物取代它之前，它只服务 HTTP 绑定 |
| 状态机、范围合成、认证判定、事务编排 | `service` | 答"业务上该怎么算/该不该允许" |
| SQL、`JdbcTemplate`、`FOR UPDATE SKIP LOCKED` | `infra` | 答"怎么存怎么取"，不判断对错 |
| 加密/摘要/滑动窗口/游标编码 | `infra` | 纯技术机制，不含策略 |
| 响应壳、错误码、traceId | `common` | 跨层都要用，且不属于任何领域 |
| `@ConfigurationProperties`、过滤器、CORS | `config` | 影响"整个应用怎么跑" |

一条硬规则：**依赖只能向下**（`api → service → infra → common/config`）。`service` 里出现 `import ...api.` 就是放错了——`MeView` 之所以在 `service` 而不是 `api`，正是因为它被 `MeService` 返回，放 `api` 会形成反向依赖。

### 这一层划分是怎么验证的

重构后按"编译 → 单测 → 真实 HTTP"三段验过：

| 验证 | 结果 |
| --- | --- |
| `mvn -o -B clean compile` | BUILD SUCCESS |
| `mvn -o -B test` | 53 例全绿（`ApiApplicationTests` 1、`infra.*` 14、`service.*` 33、`migration.*` 5；2026-10-26 A01 把 `ScopeCalculatorTests` 由 8 条扩到 13 条） |
| `verify-skeleton.ps1` | 25 passed, 0 failed |
| `verify-csrf.ps1` | 34 passed, 0 failed |
| `verify-login.ps1` | 65 passed, 0 failed |
| `verify-me.ps1` | 94 passed, 0 failed |
| `verify-scope-tuples.ps1` | 15 passed, 0 failed（A01：造 PLATE@A + FACE@B 两个角色，断言 `/me` 无交叉） |
| `verify-client-config.ps1` | 53 passed, 0 failed |
| `verify-health.ps1` | 47 passed, 0 failed |
| `verify-logout.ps1` | 41 passed, 0 failed |

注意第三段是必须的：**重构只过编译和单测不够**。这次就踩到过一次——`MeService` 少一行 `import ...infra.UserAccountRepository`，`mvn compile` 报了 `BUILD SUCCESS`（增量编译下 `javac` 把一个 `Unresolved compilation problem` 的桩类写进了 `target/classes`），单测也照样全绿，但真实请求 `/me` 直接 500。只有 `verify-me.ps1` 能抓到它。**改包结构后一定要重跑 HTTP 验收脚本，并且先 `mvn clean`。**

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
| `com.xxplatform.api.generated.model` | 623 个 Schema 对应的 DTO 与枚举（`TaskStatus`、`ErrorCode`…） |

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
| 数据库接入 | `application-db.yml` | 数据源配置**只在 `db` profile 下**；但仓储层从任务 4 起就依赖 `JdbcTemplate`，所以**启动必须带数据库**（`mvn compile`/`mvn test` 仍可离线无库运行，见 §1） |
| Flyway 启动迁移 | `application-db.yml`（`spring.flyway`） | `db` profile 启动时先 `validate` 再 `migrate`；校验和漂移会让进程**启动失败**而不是带病服务 |
| CSRF 令牌 | `api/AuthController`、`service/CsrfTokenService`、`infra/CsrfSessionRepository` | `GET /auth/csrf`：签发 `CSRFID` Cookie + 同步器 token，落 `auth.csrf_session`（只存 SHA-256） |
| 登录会话 | `api/AuthController#login`、`service/LoginService`、`service/SessionService` | `POST /auth/login`：校验 CSRF → 口令 → **轮换 SID**（旧会话 `revoked_at`）→ 写 `auth.user_session`（只存 SID 的 SHA-256）→ 回填 `csrf_session.session_id` |
| 口令哈希 | `infra/PasswordHasher` | **PBKDF2-HMAC-SHA256，600000 迭代**，自描述前缀 `pbkdf2-sha256$迭代$盐$摘要`；零第三方依赖（见下"为什么不用 BCrypt"） |
| 登录频控 | `infra/LoginThrottle` | 进程内滑动窗口，键 = `登录名|来源IP`，默认 10 次/15 分钟 → 429 `RATE_LIMITED` + `Retry-After`；多实例时阈值按实例数放大 |
| 开发种子账号 | `infra/DevUserSeeder` | **仅 `db` profile + 双闸门**（`xx.auth.dev-seed.enabled=true` 且给了口令）：创建 `demo.admin`/`demo.operator`/`demo.classified` 三个账号与三个角色；口令只从环境变量读；`reset-password` 可显式重置 |
| 当前用户 | `api/AuthController#getMe`、`service/MeService`、`service/SessionAuthenticator`、`infra/RbacRepository`、`service/ScopeCalculator` | `GET /me`：会话校验 → 角色/权限/菜单/数据范围/场所范围/会话七块；**数据范围按 `(类型, 场所, ownOnly)` 元组求并集**，`scopeGrants` 是权威集合、旧五字段只是 UI 投影（见下） |
| 退出登录 | `api/AuthController#logout`、`service/SessionService#end`、`infra/UserSessionRepository#revokeById` | `POST /auth/logout`：先认证（401）→ 校验 CSRF（403）→ 撤销当前会话行（**只写 `revoked_at`，不删行**）→ 用 `Max-Age=0` 清掉 `SID` 与 `CSRFID` → 200 `{accepted:true}`（见下） |
| 前端配置 | `api/ClientConfigController`、`service/ClientConfigService`、`service/ClientConfigScalars`、`infra/ClientConfigRepository` | `GET /client-config`：12 个 required 字段；数值读 `core.config_item`（**改阈值不用发版**），能力开关读 `core.feature_config`；两个 deprecated 字段按"投影/全局上限"规则下发（见下） |
| 运维探针 | `api/SystemController`、`service/HealthService`、`service/HealthView` | `GET /health`（存活）永远 200 + `UP`，不查依赖；`GET /ready`（就绪）查 `database`/`schema`/`deployment-config` 三项，任一不就绪 → **503 + `Retry-After: 5`**、`details` 指出是哪个依赖（见下） |
| 骨架冒烟测试 | `ApiApplicationTests` | 上下文能起来；未知路由返回契约错误壳且头=体（**手工起应用、不用 `@SpringBootTest`**，见 §9 Mockito 那条） |

### 骨架修复记录（综合测试报告 2026-10-16，已实测复现并修复）

| 问题 | 修复前实测 | 修复 |
| --- | --- | --- |
| CORS 预检 403 | 允许的 Origin 预检 → 403，无任何 `Access-Control-*` 头 | **根因是路径映射**：`addCorsMappings` 的模式相对 servlet context，而 `context-path=/api/v1`，所以 `/api/v1/**` 永远匹配不上。改为 `/**` |
| 方法不支持 → 500 | `POST /health`（GET-only）→ 500 `SYSTEM_INTERNAL` | 加 `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` → 405 `METHOD_NOT_ALLOWED`，并从 `ex.getSupportedHttpMethods()` 补 `Allow` 头 |
| traceId 客户端可决定 | 传 `X-Trace-Id: audit-client-controlled-123` 原样回显 | 改为服务端生成；保留透传能力但**默认关闭**，只在 `xx.trace.accept-client-trace-id=true` 且值符合形状时采用 |
| 未知字段被静默吞掉 | `failOnUnknownProperties=false` | 改为 `true`；`HttpMessageNotReadableException` 里还原字段名到 `details.field` |
| 生成后编译失败 | 生成出了 Java，但编译报两类错：① `io.swagger.v3.oas.annotations` 不存在；② `SetTaskCreated` 里 `setTaskId` 重复定义 | ① 加 `annotationLibrary=none`/`documentationProvider=none`，生成物自包含；② 把 `nameMappings` 从 `configOptions` 移到 **mojo 级参数**（原先被静默忽略） |

**关于 `METHOD_NOT_ALLOWED`(405)**：契约当前每个操作只声明了 400/401/403/500，**没有 405**。但把客户端错误报成 500 会污染服务故障日志与告警，因此骨架按 HTTP 标准语义返回 405，并已提报在契约通用响应中补 405 声明。在契约补声明前，这是"实现比契约多一个状态码"的唯一例外，**不要据此再扩展其他码**。

405 **必须带 `Allow` 头**（RFC 9110）：否则客户端只知道"方法不对"，不知道怎么改。`@ExceptionHandler` 自己写响应体就绕过了 Spring 默认补 `Allow` 的逻辑，所以要在 handler 里显式从 `HttpRequestMethodNotSupportedException#getSupportedHttpMethods()` 取回来写进响应——这条在 2026-10-17 的 `verify-skeleton.ps1` 里是硬断言（`POST /health` → `Allow: GET`）。

**关于 CORS 的 `/**`**：这是最容易改回去的坑。写成 `/api/v1/**` 时配置一条都不匹配，现象是"允许的 Origin 也被 403 且没有 `Access-Control-*` 响应头"。排查时可把 URL 改成 `/api/v1/api/v1/health` 自证——如果那样反而 200，就是路径映射写错了。

### 实跑验证记录（本机，2026-10-17 复核）

```
$ mvn -o -B compile
[INFO] BUILD SUCCESS

$ GET http://127.0.0.1:8080/api/v1/health
{"code":"0","message":"success","data":{"status":"UP",
 "checkedAt":"2026-10-17T11:28:46.379887+08:00"},
 "traceId":"61ca8601-..."}

$ GET http://127.0.0.1:8080/api/v1/ready
{"code":"0","message":"success","data":{"status":"READY","checkedAt":"2026-10-17T11:28:46.4+08:00",
 "dependencies":[{"name":"database","status":"READY"},{"name":"schema","status":"READY"},
                 {"name":"deployment-config","status":"READY"}]},"traceId":"..."}

$ GET http://127.0.0.1:8080/api/v1/no-such-route
HTTP 404   X-Trace-Id: d7039b3b-...
{"code":"DATA_NOT_FOUND","message":"资源不存在","data":null,
 "traceId":"d7039b3b-...","details":[]}

$ GET /api/v1/health 带 X-Trace-Id: audit-client-controlled-123
X-Trace-Id: 61ca8601-...          ← 服务端重新生成，不再回显客户端值

$ OPTIONS /api/v1/health  Origin: http://127.0.0.1:5180, ACRM: POST
HTTP 200   Access-Control-Allow-Origin: http://127.0.0.1:5180
           Access-Control-Allow-Credentials: true

$ OPTIONS /api/v1/health  Origin: http://evil.example.com
HTTP 403                          ← 非白名单 Origin 仍然拒绝

$ POST /api/v1/health              ← GET-only 路由
HTTP 405   {"code":"METHOD_NOT_ALLOWED","message":"请求方法不被支持",...}

$ POST /api/v1/auth/login  {"loginName":"demo.admin","password":"x","extraField":1}
HTTP 400   {"code":"VALIDATION_ERROR","message":"请求包含未知字段",
            "details":[{"field":"extraField","reason":"该字段不在请求 Schema 中（additionalProperties:false）"}],...}

$ POST /api/v1/auth/login  {"loginName":"","password":"x"}
HTTP 400   {"code":"VALIDATION_ERROR",...}   ← 已声明字段照常校验，不是静默忽略
```

回归脚本：[`scripts/verify-skeleton.ps1`](scripts/verify-skeleton.ps1)（需服务已带 `dev,db` 启动）。
"未知字段"那条断言现在由 [`scripts/verify-login.ps1`](scripts/verify-login.ps1) 用真实 `LoginRequest` 覆盖：
dev-only 的 `/smoke` 探针已在任务 8 之后删除，避免与 `/health` 长期并存两套探针。

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

### `POST /auth/login`（任务 5，2026-10-17 实现）

契约出处：`openapi.yaml` → `/auth/login`（`x-permission: PUBLIC`，但仍**强制**带 `X-CSRF-Token`）。

**请求**：`POST /api/v1/auth/login`，Cookie 带 `CSRFID`，头带 `X-CSRF-Token`（两者必须配对）。

```json
{"loginName": "demo.admin", "password": "..."}
```

`loginName` 契约限 `maxLength: 80`；`password` 契约未限长度，服务端也不截断
（见下"为什么不用 BCrypt"）。未知字段一律 400（`additionalProperties:false`）。

**成功响应**：

```
HTTP 200  X-Trace-Id: 6d34...  Set-Cookie: SID=<43字符>; Path=/api/v1; Max-Age=28800; HttpOnly; SameSite=Lax
{"code":"0","message":"success","data":{
  "userId":"45845f1c-...",
  "idleExpiresAt":"2026-10-17T05:42:04Z",
  "absoluteExpiresAt":"2026-10-17T13:27:04Z"},
 "traceId":"6d34..."}
```

时间一律 UTC + `Z`（契约 §3）。`SessionData` 在契约里带 `x-design-status: provisional`（取值待甲方确认），
所以 **TTL 是配置项**（`xx.session.idle-ttl`=15m、`absolute-ttl`=8h），不是硬编码常量；
两个默认值与 `docs/project-governance/provisional-policy.json` 的
`settings.auth.idleSeconds=900`、`absoluteSeconds=28800` 一致（PD-2026-10-19.v1）。

**Cookie 的四个属性**：`HttpOnly`、`SameSite=Lax`、`Path=/api/v1`（契约 `api-conventions.md` §4 写死）、
`Max-Age` 取到**绝对超时**的剩余秒数——若用会话 Cookie，关掉浏览器就失效，与"绝对超时 8 小时"的语义不符。
`Secure` 默认 `false` 但可配（`xx.session.secure-cookie`）：本地 `http://127.0.0.1` 调试时浏览器**不会回传** Secure Cookie，
链路会直接断；**生产必须置 `true` 并强制 TLS**（挂在 P-001 名下）。

**失败一律同一条消息**：账号不存在、口令错误、账号 `status='DISABLED'` 全部返回
`401 AUTH_INVALID_CREDENTIALS` + `"登录名或口令不正确"`，真实原因只进服务端日志（带 traceId）。
契约原文要求"失败统一提示，避免账号枚举"。未知账号也会跑一次等价耗时的哈希（`burnEquivalentTime`），
否则响应时间差本身就是一个账号枚举信道。

**CSRF 缺失/不匹配 → 403 而不是 400**：403 对前端是**可操作**的信号（"重新取一次 csrf"），
400 会被当成"我的请求写错了"。来源校验（Origin）由 CORS 过滤器在进控制器之前完成，控制器不重复实现。

**已知限制（有意为之，不要"顺手修"）**：`login_name` **大小写精确匹配**。数据库唯一索引是大小写敏感的，
若服务层容忍大小写，就会出现两个仅大小写不同的账号互相歧义的情况。

#### 为什么用 PBKDF2 而不是 BCrypt

契约里 `auth.app_user.password_hash` 只有 `varchar(255)`，**没有规定算法**（全 `contracts` 目录搜
`bcrypt|argon|PBKDF2|scrypt` 无任何约定）。选型依据：

1. **零依赖**：`spring-security-crypto` 不在本机 Maven 仓库里，而 `~/.m2` 只读；加任何依赖都会让
   默认离线 `mvn -o compile` 失败。PBKDF2 用 JDK 自带 `PBKDF2WithHmacSHA256`。
2. **BCrypt 只取前 72 字节**，超长口令被**静默截断**——前 72 字节相同的两个不同口令会互相通过。
   要规避就得拒绝超长口令（与契约未声明 `maxLength` 冲突）或预做 SHA-256，都更绕。
3. **迭代数可对齐 OWASP 2023 下限**：本机实测 600000 次 ≈ 619 ms（Java 17 单核），选 **600000**。
   写死不做配置项——把它做成配置就等于提供了一个"为了性能调低迭代数"的评审入口。

未来升级 Argon2id 靠自描述前缀分派（`pbkdf2-sha256$...` → 新前缀），改密时按新算法重算，不需要重写全表。

#### 登录频控

`xx.auth.throttle`：`max-failures=10`、`window=15m`、`lockout=15m`。键是 `登录名|来源IP` 的组合：

- 只按登录名 → 别人可以故意刷爆某账号，把真实用户锁在门外（拒绝服务）；
- 只按 IP → 同一出口 NAT 后所有人共享配额，一人手误全公司被限流。

来源 IP **只用 `getRemoteAddr()`，不解析 `X-Forwarded-For`**——该头客户端可伪造，信任它等于让攻击者
换个头就绕过频控。上线到反代后必须由反代改写并只信任反代来源，届时再改。

命中后返回 `429 RATE_LIMITED` + `Retry-After`（秒，最小 1：`Retry-After: 0` 等于允许立刻重试）。
**多实例部署时各算各的**，实际阈值 = 实例数 × 10；进程内实现是新实例的已知取舍，后续换 Redis 计数。

#### 开发种子账号

前端联调需要一个能登录的账号（`contracts/api/FRONTEND_INTEGRATION.md:59` 明确 `demo.admin` 是 Mock 账号，
后端不建就连不上）。`DevUserSeeder` 只在 **`db` profile 且双闸门**都满足时创建：

```bash
$env:XX_AUTH_DEVSEED_ENABLED  = 'true'      # xx.auth.dev-seed.enabled
$env:XX_AUTH_DEVSEED_PASSWORD = '<口令>'    # 只从环境变量读，yml 里没有口令
```

`enabled=true` 但口令为空或短于 8 位 → **启动直接失败**（不留一个弱口令账号在库里）。

它建**三个账号 + 三个角色**，因为只有一种权限的账号测不出权限分支：

| 账号 | 角色 | 权限 | `ownOnly` | `dataTypes` |
| --- | --- | --- | --- | --- |
| `demo.admin` | `SUPER_ADMIN` | 库内**全部 58 个**权限码 | `false` | FENCE/MAC/PLATE/FACE |
| `demo.operator` | `OPERATOR` | 13 个只读子集，**无** `capture:decrypt` | `true` | FENCE/PLATE |
| `demo.classified` | `CLASSIFIED` | 15 个 = operator + `capture:decrypt` + `audit:read` | `false` | FENCE/MAC/PLATE/FACE |

管理员的 58 个码用 `INSERT ... SELECT code FROM auth.permission` 灌入，**不在代码里复述权限清单**——
权限表加了新码，种子账号自动跟上，不会出现"库里有权、种子漏配"。三个角色都 `data_all_sites=true`
（否则得先造 `core.site` 行），`management_all_sites=false`。

**刻意不种菜单**（`auth.menu`/`role_menu`）：仓库里没有真实导航目录，`apps/web` 只把
`menus` 的 label 平铺成一个列表。塞假菜单等于把未签认的导航结构固化成"事实"，
而空菜单是合法结果（前端已按空数组处理）。同理不种 `auth.user_scope`：没有行就等于"账号没有附加授权"，
全部范围都来自角色，这才是"管理员看全部"的正确表达（A01 之后 `user_scope` 是**加法**授权，
不是缩小角色的收窄规则；要造收窄场景只能靠角色本身没有该场所）。

##### `reset-password`：口令漂移的出口

账号已存在时**默认跳过**（不重置口令，避免每次重启冲掉手工改过的口令）。但这会导致一个真实事故：
任务 5 用旧口令建了 `demo.admin`，任务 6 换了新口令只影响新建的两个账号，于是 admin 一直登不上——
验收脚本报 `28 failed`，而 22 条失败全是"拿不到 SID"的级联，看起来像 `/me` 坏了。

换口令时显式打开一次：

```bash
$env:XX_AUTH_DEVSEED_RESETPASSWORD = 'true'   # xx.auth.dev-seed.reset-password
```

它会**重算口令哈希并撤销该用户全部未撤销会话**（对齐 `api-conventions.md` §4
"管理员重置口令须撤销该用户会话"——改了口令还留着旧会话，等于改了锁不换钥匙）。

#### 验收记录

```bash
# 服务需带 db profile + 种子开关启动（本端点要写库）
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-login.ps1 `
  -DB_PASSWORD '<db口令>' -SeedPassword '<种子口令>'
# => RESULT: 65 passed, 0 failed
```

脚本覆盖 8 组 65 条：CSRF 三态拒绝、失败信息不可区分（含停用账号、大小写）、成功壳与 Cookie 四属性、
时间戳格式与 15m/8h 偏移、数据库 8 条（`sid_hash = sha256(SID)`、原文不入库、traceId 落库、
`csrf_session.session_id` 已绑定、`last_login_at` 已写）、登录轮换 SID 且旧会话被撤销、
频控 10 次 401 后 429 且 `Retry-After >= 1`、以及未知字段/超长/缺字段/405/跨源。

---

### `GET /me`（任务 6，2026-10-17 实现）

```http
GET /api/v1/me
Cookie: SID=<登录拿到的会话 cookie>
# 无参数表 → 不需要 X-CSRF-Token（只读，不改状态）
```

```json
{"code":"0","message":"success","traceId":"...","data":{
  "user":{"userId":"...","loginName":"demo.admin","displayName":"演示管理员","status":"ACTIVE"},
  "roleIds":["..."],
  "permissionCodes":["analysis:create","..."],
  "menus":[],
  "dataScope":{"siteIds":[],"allSites":true,"dataTypes":["FENCE","MAC","PLATE","FACE"],
               "ownOnly":false,"scopeVersion":0},
  "siteScope":{"siteIds":[],"allSites":true},
  "session":{"userId":"...","idleExpiresAt":"2026-10-17T14:28:11Z","absoluteExpiresAt":"2026-10-18T01:58:11Z"}
}}
```

七个块**全部必现**（契约 `CurrentUserData` 七个 required）。`menus` 可以为空数组。
200 只有 `X-Trace-Id`，**没有 ETag**——契约未声明条件请求，而权限/范围会随管理员改角色随时变化。

`x-permission` 是 `AUTHENTICATED` 而不是某个权限码：`/me` 要告诉前端"你是谁、你能看什么"，
如果它自己还要一个权限码，前端就没有任何可靠依据决定首屏。

#### 认证失败的三种分法（对应三种前端动作）

| 情况 | 码 | 前端该做什么 |
| --- | --- | --- |
| 没带 SID / SID 查不到 | `AUTH_REQUIRED` 401 | 回登录页 |
| 会话已撤销、空闲/绝对超时 | `AUTH_SESSION_EXPIRED` 401 | 清身份回登录，**不自动重放写请求** |
| 账号被停用或不存在 | `AUTH_SESSION_EXPIRED` 401 | 同上 |

"查不到"用 `AUTH_REQUIRED` 而不是 `AUTH_SESSION_EXPIRED`：后者会诱导前端去调 renew，
而一个根本不存在的 SID 续不活，只会多一次必然失败的请求。真实原因（账号被停用 / 已软删）只进服务端日志。

#### 为什么数据范围按"元组"求并集，而不是把维度摊平

`dataScope`/`siteScope` 是 `x-design-status: provisional`（PD-2026-10-19.v1；P-002：角色默认授权与范围组合规则待签认）。
`scopeGrants` 是**唯一权威**授权集合：每条是一个 `(dataType, siteScope, ownOnly)` 三元组，判定时逐元组独立匹配再 OR。
规则在 `service/ScopeCalculator#resolve`，类注释里逐条写明：

1. 每个角色按 `数据类型 × 该角色的 DATA 场所` 展开成元组；`data_all_sites=true` 表示该类型对全部场所授权（`siteIds=[]`）；
2. 多角色之间、以及账号附加授权（`auth.user_scope`）之间，都对**元组**取并集——并集只加授权、不减授权；
3. 角色没有数据类型，或 `allSites=false` 又没有场所行 → 该角色不产生采集元组（空集是"无权限"，不是"不受限"）；
4. `ownOnly` 留在元组里，不跨元组取"或"：一个角色的"只看自己的"不会传染给另一个全量角色，也不会被它救活；
5. 旧的五个字段（`siteIds`/`allSites`/`dataTypes`/`ownOnly`/`scopeVersion`）只是元组的**投影**，仅供 UI 提示，
   **不得作为服务端鉴权依据**：`allSites` = 所有元组都是 `allSites`；`siteIds` = 非全量元组的场所并集；
   `dataTypes` = 类型并集按枚举序；`ownOnly` = 任一元组为真。

为什么不能摊平（这是 A01 指出的原始缺陷）：角色甲授 PLATE@A、角色乙授 FACE@B，若先把类型和场所各摊成一个集合，
得到 `dataTypes={PLATE,FACE}`、`sites={A,B}`，任何"类型 ∈ 集合 && 场所 ∈ 集合"的判定都会放行 **PLATE@B 与 FACE@A** ——
凭空多出两条没人授过的授权（交叉扩权）。反过来，把账号附加授权当成"收窄"（取交集）又会吞掉它本该获得的权限；
契约 `userScopeMode` 写的就是 `additive_union_of_typed_scope_tuples`，即"按类型化范围元组做加法并集"。

`ScopeCalculatorTests` 用 13 条单测钉这些规则（含交叉扩权反例、账号追加、`ownOnly` 不跨元组、全量场所强制空列表）；
HTTP 级的交叉用例在 `scripts/verify-scope-tuples.ps1`：它临时造两个"只授单类型单场所"的角色和一个同时持两角色的账号，
断言 `/me.scopeGrants` 恰好两条、且不存在 PLATE@B / FACE@A，跑完删掉夹具。

#### 菜单为什么要按权限过滤

`auth.menu.required_permission_code` 存在时，若当前用户没有该权限，**这条菜单不返回**。
理由：角色被收回权限后 `role_menu` 关联往往还留着，返回它等于给前端一个"点了必然 403"的入口。
过滤只减少可见项、不增加任何权限。

`requiredPermission` 是契约里的 `$ref: PermissionCode`（**非 nullable**），所以无该字段时必须
**省略整个属性**（`@JsonInclude(NON_NULL)`）而不是输出 `null` —— 输出 `null` 会让严格校验的前端类型报错。

#### 验收记录

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-me.ps1 `
  -DB_PASSWORD '<db口令>' -SeedPassword '<种子口令>'
# => passed: 94   failed: 0
```

覆盖 8 组 94 条：匿名/伪造 SID 401 且 `data:null`+`details` 为数组、admin 全量形状（七键恰好 7 个、
58 个权限码、四类型有序、`allSites` 时 `siteIds=[]`、无 ETag）、operator 收窄（13 码、两类型、`ownOnly=true`）、
classified（15 码含 `capture:decrypt`）、数据库侧会话生命周期（时间与库内 `epoch` 无漂移、
改过去→`AUTH_SESSION_EXPIRED`、撤销→401、停用→401）、菜单投影（三条临时菜单只有两条可见、
被过滤那条不出现、无权限码时省略 `requiredPermission`）、以及 405 与未知查询参数；
A01 之后新增第 8 组：`dataScope` 恰好六个属性且含 `scopeGrants`、admin 四条全 `allSites`、operator 两条全 `ownOnly`、
classified 四条非 `ownOnly`、每条元组恰好 `dataType/siteScope/ownOnly` 三键。

交叉扩权这条单独的用例需要"非全量场所"的角色，种子账号造不出来，所以拆到独立脚本：

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-scope-tuples.ps1 `
  -DB_PASSWORD '<db口令>' -SeedPassword '<种子口令>'
# => RESULT: 15 passed, 0 failed
```

它用 psql 建一套一次性夹具（一个区域、两个场所 A/B、两个只授单类型单场所的角色、一个同时持两角色的账号），
登录后断言 `/me.scopeGrants` 恰好两条且没有交叉，`finally` 里删干净；缺 psql 或口令时打印 `[SKIP]` 并以 0 退出。

---

### `POST /auth/logout`（任务 11，2026-10-17 实现）

让"登录 → 进入 → 退出"在**真实接口**下闭环的最后一块。契约 `x-module: M01`、`x-owner: DEV-01`、
`x-permission: AUTHENTICATED`、参数 `$ref CsrfToken`、响应 200/400/401/403/500，200 体
`ActionResult` 只有 `accepted`（`additionalProperties:false`）。

**执行顺序：先认证，再校验 CSRF，最后才撤销**

| 步骤 | 失败时 | 为什么是这个顺序 |
| --- | --- | --- |
| `SessionAuthenticator#require` | 401 `AUTH_REQUIRED` / `AUTH_SESSION_EXPIRED` | 没登录就该看到 401（前端据此清本地身份）。报 403 会让人以为 Origin 配错了 |
| `CsrfTokenService#resolve`（`CSRFID` Cookie + `X-CSRF-Token`） | 403 `AUTH_CSRF_INVALID` | 退出是写操作，缺 CSRF 就成"跨站强制下线"；CSRF 只对已认证的写请求有意义，所以放在认证之后 |
| `revokeById(sessionId)` | —— | 两个检查都必须在撤销之前完成，否则等于没有防线 |

**只写 `revoked_at`，不删行**：`auth.user_session` 兼作审计（谁、何时、从哪个 UA 登录过）。
撤销只加一列时间戳，`SessionAuthenticator` 看到即判 `AUTH_SESSION_EXPIRED`。

**为什么响应里还要清 Cookie**：SID 是 `HttpOnly`，前端 JS 删不掉。不清浏览器就会一直带着一个
已撤销的 SID，下一个请求得到 401 而不是"干净状态"。清 Cookie 的属性必须与签发时**逐项一致**
（同名、同 `Path=/api/v1`、同 `SameSite=Lax`、同 `Secure`），否则浏览器把它当另一个 Cookie，
原 Cookie 继续存在——这是清 Cookie 最常见的坑。CSRFID 一并清掉（它绑定的会话已终止）。

**不删 `auth.csrf_session` 行**：CSRF 行单独不构成身份（必须同时有 SID），到期由
`CsrfTokenService#issue` 顺带清理。若在退出时删掉，客户端重试退出（双击、网络重试）会从
"401 会话已终止"变成"403 来源不可信"——把一次正常退出变成让人怀疑被攻击的报错。

**重复退出的语义**：第二次请求带的 SID 已撤销 → 401 `AUTH_SESSION_EXPIRED`（不是 200）。
契约允许（401 在响应列表里），前端 `logout()` 对失败只提示不清状态，不影响"已经退出"的事实。

**已知未做**：契约没给这个操作声明 `Idempotency-Key` 参数，前端却会带一个（`client.ts` 对写请求
统一加）。后端目前直接忽略该头，幂等层整体还没实现（见 §5）。

#### 验收记录

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-logout.ps1 `
  -DB_PASSWORD '<db口令>' -SeedPassword '<种子口令>'
# => RESULT: 41 passed, 0 failed
```

覆盖 8 组 41 条：登录建立活会话、CSRF 双要素（无头 403 / 伪造 Cookie 403 / 被拒的退出**不会**
撤销会话）、无会话 401 且不写 Cookie、正常退出（`code:"0"`、`data` 恰好一个键 `accepted:true`、
响应体不含 SID、`traceId` 与 `X-Trace-Id` 一致、两个 `Set-Cookie` 都是 `Max-Age=0` 且保留
`HttpOnly`/`SameSite=Lax`/`Path`）、退出后 `/me` 401 且 code 为 `AUTH_SESSION_EXPIRED`、
重复退出 401、数据库侧（`revoked_at` 已写、行仍在、账号仍 ACTIVE）、退出后可立刻重新登录并拿到
新 SID、以及 405 `Allow: POST` 与跨源退出 403（且未撤销会话）。

---

### `GET /client-config`（任务 7，2026-10-17 实现）

前端引导接口：登录后读一次，拿到"这个部署允许什么、上限多少、能力开没开"。

```
GET /api/v1/client-config
Cookie: SID=<opaque>
```

12 个字段**全部 required**（契约 `ClientConfig`），一个都不能少：

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| `version` | `core.config_item.value.version` | 配置版本，前端据此判断本地缓存要不要刷 |
| `mapCoordinateSystem` | `…value.mapCoordinateSystem` | 契约枚举只有 `EPSG:4326`，写别的值**启动即报错**（见下"部署故障"） |
| `institutionTimeZone` | `…value.institutionTimeZone` | 契约枚举只有 `Asia/Shanghai` |
| `taskPollIntervalSeconds` | `…value.taskPollIntervalSeconds` | 异步任务轮询间隔（秒），≥1 |
| `maxUploadBytes` | `…value.maxUploadBytes` | **deprecated**：旧全局上传上限，实际单类上限 = `min(本值, uploadPolicies[].maxBytes)` |
| `contextTtlSeconds` | `…value.contextTtlSeconds` | Context 存活时间（秒），≥1 |
| `maxOnlineQuerySeconds` | `…value.maxOnlineQuerySeconds` | 在线查询服务端时间预算（秒），≥1 |
| `maxOnlineWindowDays` | `…value.maxOnlineWindowDays` | 在线查询允许回溯的最大天数，≥1；**部署默认 31**（PD-2026-10-19.v1），与"留存 12 个月"是两个口径 |
| `features` | `core.feature_config` | 12 个能力开关，含 disabled 的与 `pendingReason` |
| `featureAvailability` | 由 `features` 投影 | **deprecated**：`{code,enabled}`，同序同长，**不做任何独立计算** |
| `uploadPolicies` | `core.upload_policy` | 分类上传限制；**空数组是合法结果** |
| `catalogBindings` | `core.dictionary_binding` join `core.dictionary` | 字段→字典绑定；**空数组是合法结果** |

#### 为什么这些数字放数据库，不放代码/yml

契约 `ClientConfig` 的注释原文是"**数值由部署配置提供，不在契约中捏造默认值**"。这些数字
（轮询间隔、上传上限、Context TTL、查询预算）随部署规模和安全口径变化，写进常量或
`application.yml` 就是"改一个阈值要重新发版"。所以落到 `core.config_item` 的
`config_key='client-config'` 一行，运维改法：

```sql
UPDATE core.config_item
   SET value = '{"version":"2", …}'::jsonb,
       config_version = config_version + 1
 WHERE config_key = 'client-config';
```

不需要改代码、不需要重启，下一次请求就按新值返回。

种子值在 [`db/migration/V2__seed_deployment_client_config.sql`](src/main/resources/db/migration/V2__seed_deployment_client_config.sql)，
用 `INSERT … ON CONFLICT (config_key) DO NOTHING` 保证**不覆盖运维已经改过的值**。
其中 `contextTtlSeconds`/`maxOnlineQuerySeconds`/`maxOnlineWindowDays` 属 **P-009**（查询取消、
限流、Context TTL 与导出限值，仍未签认）范围，`mapCoordinateSystem`/`institutionTimeZone` 属
**P-010**。种子里的数字是**可运行的部署默认值，不是已签认的业务口径**，不得据此对外承诺配额。

V2 播的是 90 天，与 PD-2026-10-19.v1 的 31 天不符；**已执行 V2 的库由
[`V4__policy_parameters_20261019.sql`](src/main/resources/db/migration/V4__policy_parameters_20261019.sql)
纠正**（改 V2 本身会让 Flyway 报 checksum mismatch）。V4 只动 `maxOnlineWindowDays` 与
`value.version`，并按 `config_version + 1` 递增，运维已自定义过的版本号不会被覆盖。

#### 部署故障优先于可用性

`core.config_item` 里那行缺失、被停用、`public_readable=false`、JSON 不合法、缺键、枚举不符
→ 全部 **500 `SYSTEM_INTERNAL`**，而不是编一套默认值继续跑。理由：前端拿到"看起来正常但
阈值是假的"配置，会用它去放行上传、去截断查询窗口，后果比一个明确的 500 严重得多。

反过来，**单行数据**不符合契约枚举时采取"跳过 + warn"而不是整体 500：上传分类不在 9 值内、
字典绑定字段不在 4 值内，这些行在界面上没有落点，丢掉不隐藏任何可见能力；若因此让整个接口
挂掉，前端连首页都打不开。

唯一的例外是 `core.feature_config.feature_code` 不在契约 12 值内 → **直接 500**。
`features` 是契约规定的唯一能力目录，多出一个无法表达的 code 说明有人写入了契约之外的数据；
静默丢弃会让一个可能已签认的能力凭空消失，而这种错误上线后极难发现（前端只是"少了个按钮"）。

#### 两个 deprecated 字段怎么处理

契约把 `maxUploadBytes` 与 `featureAvailability` 标了 deprecated，但**仍然 required**，所以必须返回：

- `featureAvailability` 只做 `features` 的投影（同序、同长、只取 `{code,enabled}`）。契约原文禁止
  "单独计算或覆写"——一旦它和 `features` 出现分歧，前端两个开关会互相矛盾。
- `maxUploadBytes` 是**全局**上限，不能拿它当某个分类的限制。分类限制只看
  `uploadPolicies[].maxBytes`，实际可用值取两者较小者。

#### 为什么 admin 和 operator 拿到的是同一份

契约把它定义为"仅前端需要读取的非机密配置"。同一部署里所有登录用户必须拿到同一份，
否则前端按角色分支读不同的值，行为差异会变得不可复现。真正的权限判断在各接口的权限码里做，
不在这里。验收脚本专门有一条断言把这点钉住（admin 与 operator 的 `data` 逐字节相等）。

#### 两处契约-DDL 不一致（已按契约保守实现）

| 编号 | 不一致 | 处理 |
| --- | --- | --- |
| D7 | `core.upload_policy.max_bytes` 的 CHECK 允许 `0`，且后面还有一条更宽的 `>= 0`；但契约 `maxBytes` 是 `minimum: 1` | 出参前丢弃 `max_bytes<1` 的行并 warn——发出去前端会当成"该分类不能上传"的合法策略 |
| D8 | `core.dictionary_binding.field_key` **没有 CHECK**，可存契约 4 值之外的字段名 | 出参前按 4 值枚举过滤并 warn |

#### 验收记录

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-client-config.ps1 `
  -DB_PASSWORD '<db口令>' -SeedPassword '<种子口令>'
# => passed: 53   failed: 0
```

覆盖 7 组 53 条：匿名/伪造 SID 401 且 `data:null`+`details` 为数组、12 个 required 键**恰好 12 个**、
两个单值枚举钉死、五个 `>=1` 数值、`features` 12 条且顺序稳定、每个 disabled 能力都带
`pendingReason`（属性必须存在，可为 null）、投影与 `features` 逐项相等、上传/绑定都落在契约枚举内、
admin 与 operator 拿到逐字节相同的 `data`、数据库侧六个标量与 `core.config_item.value` 逐一相等、
能力码集合与启用计数与库内一致、`config_version` 起始为 1、无 ETag、`POST` 405、未知查询参数被忽略。

---

### `GET /health` 与 `GET /ready`（任务 8，2026-10-17 实现）

两个端点都是 **PUBLIC**（`x-permission: PUBLIC`、`security: []`），不校验会话、不需要 CSRF 头，也不查权限码。
它们是给运维系统用的，不是给前端用的。

| 端点 | 语义 | 契约 `operationId` | 正常返回 |
| --- | --- | --- | --- |
| `GET /health` | 存活：进程还能不能回包 | `getHealth` | `200 {status:"UP", checkedAt}` |
| `GET /ready` | 就绪：能不能对外提供正确结果 | `getReadiness` | `200 {status:"READY", checkedAt, dependencies:[…]}` |

#### 为什么 `/health` 永远返回 `UP`

契约的枚举里有 `DOWN`，但**我们永不产出这个值**。存活探针（k8s liveness、Docker `HEALTHCHECK`、
云平台健康检查）只在进程活着时才能收到回包——进程死了回包自然失败，不需要我们写代码表达。
反过来，如果把"数据库连不上"映射成 liveness 失败，编排工具会不停重启容器：**重启修不好数据库**，
只是把仅剩的可用实例反复踢下线，故障被放大。所以数据库故障由 `/ready` 表达，那才是它的职责。

`checkedAt` 是服务端当前时间（RFC 3339 带偏移，复用 `common/Instants#iso`），不是"上次检查时间"。

#### `/ready` 查哪三个依赖

顺序固定，每项**独立**判定（一项炸了不影响其他项的结论，诊断信息才准确）：

| `name` | 查什么 | 不就绪时的 `reason` | 为什么这算"没就绪" |
| --- | --- | --- | --- |
| `database` | `SELECT 1` | 数据库连接异常 | 最小连通性。连不上时后面两项根本无意义 |
| `schema` | `flyway_schema_history` 里没有 `success=false`，且总行数 > 0 | 存在失败的迁移 / 数据库结构未迁移 | 结构没迁移到位时，所有写库端点都会 500，对外报"就绪"是撒谎 |
| `deployment-config` | `core.config_item` 中 `client-config` 行存在且 `enabled` 且 `public_readable` | 缺少 client-config 配置行 | 任务 7 之后 `/client-config` 就读这一行，缺行必然 500；而前端拿不到配置会白屏，等于没就绪 |

三项都直接跑 SQL，不依赖 Flyway 的 Java bean（测试里 `spring.flyway.enabled=false` 时没有 bean，
但探针仍然要能报"结构未迁移"）。每个查询都是常数级，探针每几秒一次也无压力。

#### 为什么不全就绪时返回 503，而不是 200 + `NOT_READY`

探针只看**HTTP 状态码**，不看 body。返回 `200 {status:"NOT_READY"}` 会让负载均衡/网关把这个实例当健康，
继续把用户请求打进来，前端于是拿到一片 500——比明确告诉它"这个实例现在不能服务，5 秒后再试"差得多。
契约本身也给了 503（`Http503` 组件声明了 `X-Trace-Id` 与 `Retry-After` 两个头），
`contracts/api/error-codes.md:3` 还明确要求「429/503 带 `Retry-After` 秒数」。

所以未就绪时的响应是：

```json
{
  "code": "SYSTEM_UNAVAILABLE",
  "message": "服务尚未就绪",
  "data": null,
  "traceId": "…",
  "details": [{ "field": "deployment-config", "reason": "缺少 client-config 配置行" }]
}
```

响应头带 `Retry-After: 5`。这条头靠 `common/ApiExceptionHandler` 的 `retryable` 判定顺延——
原来只给 `RATE_LIMITED` 加，任务 8 把 `SYSTEM_UNAVAILABLE` 一并纳入，否则 503 不带 `Retry-After`。

错误壳的 `data` 按契约必须是 `null`，于是"哪个依赖坏了"就只剩 `details` 能承载：
`field` = 依赖名，`reason` = 固定文案。这是 `details` 的合理用法（它对前端是诊断信息，对运维是定位线索）。

#### 为什么 `reason` 里绝不能出现异常信息

`/ready` 是 **PUBLIC** 的——任何人匿名就能请求。把 `SQLException`、JDBC URL、账号名塞进 `details`，
等于给匿名访问者一张内网拓扑图。所以每项失败时只产出**写死的文案**，真实异常只进服务端日志。
`HealthServiceTests` 里有一条专门断言：数据库异常时响应里不含 `password`、`xxplatform_owner`、`FATAL`。

#### 没有数据源时会怎样

`HealthService` 用 `ObjectProvider<JdbcTemplate>` 注入，缺数据源时不阻止上下文启动，
`database` 项报"未配置数据源"、`/ready` 返回 503。这是**防御性**分支：当前装配下应用没有数据源
根本起不来（见 §1），所以它在生产不可达，只由单元测试覆盖；留着的意义是——将来若把数据源做成可选，
`/ready` 会如实报未就绪，而不是抛 NPE 或给出错误的"READY"。

#### 还差一项：文件卷

任务单里这条的完成判定写的是"不得只返回静态 UP，要反映 DB / **文件卷**"。DB 侧做了两项，文件卷**故意没做**：
文件底座（上传、`PROCESSING→ACTIVE`、受权下载）还没开工，落盘目录、挂载点、剩余空间口径都还没定，
现在写一个"检查某目录可写"只会检查一个将来会被换掉的路径，反而制造假信号。
等文件底座落地时，在 `/ready` 加第四项 `storage`——契约里 `dependencies[].name` 是自由字符串，加项不破坏契约。

#### `x-owner: DEV-04` 与分工方案不一致

契约把 M15 标成 `x-owner: DEV-04`，而 `docs/project-governance/BACKEND_TWO_DEV_SPLIT.md` 把 M15 划给甲。
按"契约优先"实现即可（两个端点都是 PUBLIC、没有权限模型可分歧），但**归属冲突要人工裁决**，
不要据此去改契约的 `x-owner`。

#### 验收记录

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-health.ps1 `
  -DB_PASSWORD '<db口令>'
# => passed: 47   failed: 0
```

覆盖 6 组 47 条：匿名可访问（不带 Cookie/CSRF 头也 200）、`/health` 只回 `{status,checkedAt}` 且
`checkedAt` 带时区偏移、`/ready` 的 `dependencies` 恰好三项且顺序固定、三项全 `READY` 时 200、
`Retry-After` 只在未就绪时出现、无 ETag。其中两条是**破坏性负例**（都放 `finally` 里还原并复验回 200）：

1. `UPDATE core.config_item SET public_readable=false` → 503 且 `details` **只含** `deployment-config`
   （证明失败隔离：`database`/`schema` 没有被牵连）；
2. `ALTER TABLE flyway_schema_history RENAME TO flyway_schema_history_probe` → 503 且 `details` **只含** `schema`，
   而 `database` 仍是 `READY`。

| 日期 | 结论 |
| --- | --- |
| 2026-10-17 | 47 条全绿；`mvn -o -B clean test` 47 例全绿（含 `HealthServiceTests` 7 例、`SessionServiceLogoutTests` 4 例） |

---

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

- 生成 **600 个 Java 文件**（193 个 Api 接口 + 模型/枚举；此为契约 `1.0.0-dev.2` 的实测结果，`dev.4-provisional` 增到 198 操作 / 623 Schema），编译通过。
- 生成物 **0 处** `io.swagger` 引用 —— 全局离线可用。
- `SetTaskCreated` 正确生成 `setOperationId(...)`（JSON 仍是 `setTaskId`）+ `setTaskId(...)`，不再重复。
- **默认构建（无 profile）仍可完全离线**：`mvn -o -B compile` 通过。

> 若构建机没有可写的 `~/.m2`（例如受限沙箱），可临时把本地仓库指到工作区内：
> `mvn -Dmaven.repo.local=<path> -Pgenerate-api compile`。这只影响缓存位置，不改任何构建语义。

---

## 5. 还没做（依当前分工与主分支计划）


**基础设施（平台侧先做，另一侧只调用）**

- [x] CSRF 令牌签发：`GET /auth/csrf`、`CSRFID` Cookie 属性、同步器 token 落库（任务 4，2026-10-17）
- [x] 会话与登录：`POST /auth/login`（任务 5，2026-10-17：SID Cookie、轮换、频控、种子账号）
- [x] 当前用户：`GET /me`（任务 6，2026-10-17：会话校验、角色/权限/菜单/数据范围/场所范围）；**还差**：写请求统一校验 `X-CSRF-Token`+可信 Origin 的**拦截器**（现在只在 csrf/login/logout 三个端点各自校验），以及 `renewSession`/`changeMyPassword`
- [x] 退出登录：`POST /auth/logout`（任务 11，2026-10-17：认证 → CSRF → 撤销会话行 → 清 `SID`/`CSRFID` Cookie → `{accepted:true}`）
- [x] 前端配置：`GET /client-config`（任务 7，2026-10-17：12 个 required 字段、部署值读 `core.config_item`、12 个能力开关带 `pendingReason`）；**还差**：`uploadPolicies` 与 `catalogBindings` 的真数据（分别卡在 P-007 上传安全口径、P-006 字典目录签认，现在返回空数组）
- [x] 运维探针：`GET /health`（存活，永远 200 `UP`）与 `GET /ready`（就绪，三项依赖；不就绪 → 503 + `Retry-After: 5` + `details` 指出坏掉的依赖）（任务 8，2026-10-17）
- [ ] 权限与数据范围：`PermissionCode`（58 项）注解 + 数据范围切面（`/me` 已有 `ScopeCalculator`，但只覆盖"读自己"），列表/详情/导出/文件每次重校
- [ ] 幂等：`Idempotency-Key`（16–160 字符），同键同 payload 返原响应，异 payload 409
- [ ] 乐观锁：`If-Match` ↔ `version`，冲突 409 `RESOURCE_VERSION_CONFLICT`
- [ ] 签名游标：绑定账号/范围版本/查询快照/排序/页大小，只校验不解析，失败 400 `DATA_CURSOR_INVALID`
- [ ] 任务底座：`async_task` 表 + 状态机 + 领取/续租/取消/重试/发布协议（H04 交接物）
- [ ] 文件底座：multipart 分类策略、`PROCESSING→ACTIVE`、受权下载（不返回永久裸 URL）
- [ ] 审计埋点；未签认能力在 `features` 里禁用，资源缺失时按契约禁用能力或返回 503，`REQUIREMENT_PENDING` 只作兼容保留（能力开关本身已随 `GET /client-config` 下发，见任务 7）
- [x] 数据库迁移框架（DDL 版本化，只有平台侧统一合并）+ 启动时自动迁移（任务 3，2026-10-17）

**业务接口**：198 个操作全部未实现，按当前分工的模块归属和主分支60工作日计划推进。

**重要提醒**：原65操作已按主分支转为provisional，甲方确认仍pending；按暂定设计开发，不再仅因未签认统一返回422。资源缺失时按契约禁用能力或返回503，保留REQUIREMENT_PENDING兼容用途。动手前读当前[OPENAPI_PENDING.md](../../contracts/api/OPENAPI_PENDING.md)。

---

## 6. 归属与协作规则（两人并行，别改到对方的东西）


模块级不重叠，完整清单见 [BACKEND_TWO_DEV_SPLIT.md](../../docs/project-governance/BACKEND_TWO_DEV_SPLIT.md)：

| | DEV-01 平台与运维侧 | DEV-02 数据与研判侧 |
| --- | --- | --- |
| 模块 | M01、M06、M10、M12、M14、M15 | M03、M04、M05、M07、M08、M09、M11、M13 |
| 操作数 | 118 | 80 |
| 数据协作 | 统计、报表等消费DEV-04结果 | 查询、计算与结果消费DEV-04交付 |

**三条硬规则**

1. `common/`、`infra/`、`config/` 是**共享层**，由平台侧主写；另一侧只能调用，要新能力提需求，不要 fork 第二份响应壳/错误码。
2. **`async_task` 表与任务状态机只有平台侧能动**；另一侧创建任务只调已发布接口，不自己推进状态。
3. **数据库迁移由平台侧统一合并**；另一侧的迁移片段走 PR 提交，不各自改库。

**已知的跨边界三处**（规则见当前分工“共享层与跨域边界”）：设备实体只有平台侧能改（另一侧报表只读引用）；通知投递/回执/频控的写路径只有平台侧能动（另一侧只读 `/deliveries`、`/messages`）；任务底座如上。

---

## 7. 契约红线（写完接口自检用）


1. 成功 `code` 是字符串 `"0"`，不是数字 200；失败 `data` 必须出现且为 `null`，`details` 是数组。
2. **按 `code` 判断，不按中文 message 判断**；每个响应都要有 `X-Trace-Id`，且与体内 `traceId` 相同。
3. 权限在后端：**单对象不可见返回 404 `DATA_NOT_FOUND`**（隐藏存在性），403 只用于"操作整体无权/范围收缩"。前端隐藏按钮不算授权。
4. 分页只有两套：管理资源用 `page/pageSize/total`，大结果用 `cursor/nextCursor`；游标服务端签名，**不要伪造 total**。
5. 时间是 RFC 3339 带偏移（UTC 存储/返回 Z），`TimeRange` 是 `[from,to)`；地图是 WGS84 且**经度在前**。
6. `targetId`（目标代理 ID）≠ `fileAssetId`（文件资产）；`analysisTaskId`（分析任务）≠ `taskId`（执行任务）。
7. 异步 202 只代表"已接受"，不代表执行成功；取消要先写 `cancelRequestedAt`，终态不可撤回（409）。
8. 按PD暂定设计开发；生产启用仍需真实资源与审批配置。缺资源明确不可用，不得用Mock结果或阈值冒充真实能力。
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
├── V1__baseline_design_2.sql              ← 2026-10-17 的契约快照（163 表 / 6 schema，已冻结）
├── V2__seed_deployment_client_config.sql  ← client-config 的部署默认值（任务 7）
├── V3__approval_aggregate_and_policy_v1.sql ← 审批聚合 + 解密许可列 + 策略文案（2026-10-25 补）
└── V4__policy_parameters_20261019.sql     ← 查询窗口 90→31 天（PD-2026-10-19.v1，2026-10-26 补）
```

- 命名必须 `V<序号>__<描述>.sql`（**双下划线**），否则 Flyway 会静默忽略。
- **V1 是 2026-10-17 那份 `contracts/database/schema.sql` 的逐字节副本**，SHA256 `7d3534b5…`（163 表 / 6 schema）。
  采取复制而非重写，是为了让"契约与实现漂移"变成一条红测试，而不是靠人记得同步。
  **V1 已冻结，不再代表最新契约**：2026-10-18 的契约提交把审批聚合写进了 `schema.sql`（165 表），
  但当时没交迁移，于是出现了 `V1 ≠ schema.sql` 的漂移；V3 就是补上的那个增量。
- **禁止编辑已发布的 V1**：Flyway 用 checksum 校验，改了会拒绝启动（这就是我们要的保护）。
- **V2 是数据种子，不是结构变更**：只往 `core.config_item` 插一行部署配置，用
  `ON CONFLICT DO NOTHING` 保证不覆盖运维改过的值。运维**临时调参**可以直接 `UPDATE` 那行；
  但**政策口径变更（例如查询窗口 90→31 天）必须再交一条增量迁移**，否则已经执行过 V2 的库
  永远停在旧值——V4 就是这么来的（只 UPDATE 数据、不动结构，见 §8.7）。
- **V4 是数据修正，不是结构变更**：把 `maxOnlineWindowDays` 从 90 改成 31，并把 `value.version`
  从 `'1'` 升到 `'2'`（运维已自定义过版本号时不覆盖）；`config_version` 同步 +1。
  它证明"数据类修正"同样要按 V3 的规矩走增量，而不是回头改 V2。
- 契约变更的正确顺序：改 `schema.sql` + 数据字典 + `api-storage-map` → **新增 `V<n>__xxx.sql` 增量迁移** → 同步更新 `MigrationBaselineTests` 里的 `EXPECTED_CONTRACT_SHA256`（细则见 §8.7）。

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
mvn -Pdb-migrate flyway:migrate      # 空库 → V1+V2+V3+V4，建 165 张表
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

- **空库**：直接 `flyway:migrate`，V1+V2+V3+V4 依次执行，建出与当前契约一致的 165 表（V1 163 表 + V3 的审批 2 表；V4 只改 `client-config` 的查询窗口，不动结构）。
- **升级旧库**：`schema.sql` **不是旧库升级脚本**（它假设空库）。旧库迁移必须按
  [migration-plan.md](../../contracts/database/migration-plan.md) 的 5 步生成**增量**迁移，
  其中大索引要 `CREATE INDEX CONCURRENTLY` 且在事务块外单独执行。
- `baselineOnMigrate=true` + `baselineVersion=0`：允许在已有内容的库上以基线方式接管，
  但**不代表旧库已迁移成功**——那是增量迁移的责任。

### 8.6 守卫测试

`src/test/java/.../migration/MigrationBaselineTests.java`（**5 个用例，不连数据库、不启动 Spring**，2026-10-25 由 4 个改为 5 个）：

| 用例 | 拦住什么 |
| --- | --- |
| `migrationsCoverContractStructure` | 有人改了 `schema.sql` 但没交迁移：逐项比对 `CREATE TABLE` 表体列名、`ALTER TABLE … ADD COLUMN` 累积列、索引名、具名约束名、触发器名（双向，多一个少一个都红），报错直接说缺哪张表/哪列/哪个索引 |
| `contractSchemaHashIsUnchanged` | 契约被静默改动：`schema.sql` 的 SHA256 必须是 `05a894a76…`（2026-10-20 核对） |
| `baselineStaysFrozen` | 有人回头去改 V1（会让已有库 `Migration checksum mismatch` 起不来）：V1 必须仍是 163 表、不得含 `operation.action_approval`；该表只能由 V3 之后提供 |
| `baselineCoversAllSchemasAndGuards` | 基线被"优化"掉关键部分：六个 schema + PG/PostGIS 守卫 + 事务包裹 |
| `migrationDirectoryFollowsNamingConvention` | 文件命名错导致 Flyway 忽略：版本号必须从 1 连续、`V<n>__<desc>.sql` |

> 该测试**只跑自身**可用 `mvn -Dtest=MigrationBaselineTests test`（不连库、不起 Spring）。

### 8.7 规矩：改契约必须同交增量迁移（2026-10-25 补充，来自一次真实漂移）

**背景**：2026-10-18 的契约提交 `b2f6374` 给 `schema.sql` 加了审批聚合（`operation.action_approval`、
`audit.action_approval_decision`、3 个索引、`tr_action_approval_decision_append_only` 触发器、
`decryption_request.approval_id` 等 4 列、`ck_decrypt_controlled_no_file`），**但没有交 Flyway 增量**。
结果：空库跑 V1+V2 只有 163 表，`MigrationBaselineTests` 两条红灯，且这个缺口会一直带到部署环境
（运行时 `relation "operation.action_approval" does not exist`，42P01）。

**规矩（评审时按这三条驳回）**：

1. **一次提交内同时包含四件套**：`contracts/database/schema.sql`（+ 数据字典 + `api-storage-map`）、
   `V<n>__*.sql` 增量、`MigrationBaselineTests.EXPECTED_CONTRACT_SHA256` 的新值、迁移用例的期望对象更新。
   只有第一个、没有第二个 = 漂移提交，不予合并。
2. **不改已发布的 V1…Vn，只追加 V(n+1)**。改老迁移会让所有已部署库 `checksum mismatch` 直接起不来；
   要"修"历史只能新增一个增量去纠正。
3. **增量里不写 `IF NOT EXISTS`**。迁移必须"要么整体成功、要么报错停下"，把 `IF NOT EXISTS` 塞进去
   只会把"库已经处于半迁移状态"变成静默继续。若某库手工跑过草案脚本（例如有人跑过
   `contracts/database/migrations/PD_20261018_approval.sql`），先确认
   `SELECT to_regclass('operation.action_approval')` 为空，再跑正式迁移。
4. **数据类修正也走增量**：改 V2 播的部署默认值（例如 `maxOnlineWindowDays` 90→31）时，
   回头改 V2 会让已执行库 `checksum mismatch`，正确做法是追加一条只含 `UPDATE` 的迁移（V4），
   并在 README/记录里写清"改的是部署默认值，不是已签认口径"。

**怎么验**（两道，第一道秒级，第二道分钟级）：

```powershell
cd apps/api
mvn -Dtest=MigrationBaselineTests test     # 文本层：迁移链的对象集合是否覆盖契约

# 语义层：真起两个库，跑完比对象指纹（表/列/索引/约束/触发器/种子/函数）
powershell -NoProfile -ExecutionPolicy Bypass -File apps/api/scripts/verify-migration-parity.ps1 -Port 55433
# → PASS  structure is identical: 3033 fingerprints match (165 tables)
#   退出码 0 = 一致，1 = 漂移（会列出前 40 条差异），2 = 环境问题
```

脚本需要**一个已经就绪的 PG 实例**（不自己起/停数据库，便于 CI 与沙箱复用）。每次运行按
`parity_<pid>_<时间戳>_mig`（跑 V1…Vn）与 `parity_<pid>_<时间戳>_ref`（跑 `schema.sql`）各建一库：
这两个名字**已存在就拒绝运行并退出 2**（宁可停下，也不删别人的库或别人的半成品），结束时只清理
本次自己建的库，中途失败同样清理；`-Keep` 保留 `apps/api/target/pg-parity/<run>` 下的 SQL 与指纹便于排查，
`-RunTag xxx` 可把库名固定成 `parity_xxx_mig/_ref`（自测“名字冲突即拒”时用）。临时实例可以这样起：

```powershell
$bin = 'C:\Program Files\PostgreSQL\16\bin'
& "$bin\initdb.exe" -D ..\pg-scratch\data -U postgres -A trust
& "$bin\pg_ctl.exe" -D ..\pg-scratch\data -o '-p 55433' -l ..\pg-scratch\pg.log start
# …跑上面的 verify-migration-parity.ps1 …
& "$bin\pg_ctl.exe" -D ..\pg-scratch\data stop
```

---

## 9. 环境注意（踩过的坑）


| 现象 | 原因与做法 |
| --- | --- |
| `mvn package` / `mvn clean` 报无法解析插件 | 本机离线（Maven Central 不通）时，Maven **插件自身**的依赖可能没缓存。`mvn compile` 与 `mvn spring-boot:run` 不受影响；需要打包时先联网让 Maven 拉一次 |
| `mvn -Pgenerate-api generate-sources` 失败 | openapi-generator 未缓存，必须联网一次；之后本地缓存可复用 |
| 测试里出现 `Mockito ... Could not self-attach to current VM` | 沙箱禁止 Mockito 的自附加代理（加 `-Dnet.bytebuddy.experimental=true` 也无效）。**本项目已把测试写成不依赖 Mockito**：`ApiApplicationTests` 改为手工 `SpringApplicationBuilder` 起应用 + JDK `HttpClient` 发请求（`@SpringBootTest` 会挂 `MockitoContextCustomizer`，一挂上就整个套件跑不完）。若你新增测试又要用 Mockito，请注意此限制 |
| 手工用 `.m2` 拼 classpath 启动会报 snakeyaml 找不到 | 别手工拼 classpath（会带上别的项目的旧版 slf4j/devtools），用 `mvn spring-boot:run` |
| 直接 `java -jar` 跑不了 | 还没执行过 `package`；先 `mvn package` 生成可执行 jar |
| `mvnw`/`mvnw.cmd` 报奇怪的 `'xx' is not recognized` | `.cmd` 必须是 **CRLF + ASCII**：cmd.exe 逐行解析批处理，LF 换行或 UTF-8 中文注释都会让它错乱。改这个文件时注意别被编辑器改成 LF |
| `Non-parseable POM ... in comment after two dashes (--)` | **XML 注释里不能出现 `--`**。写 Java 注释时随口写 `--spring.profiles.active=db` 就会炸，而且报错位置指向注释内部，不太好找 |
| `Cannot access central ... in offline mode` 却又明显联网可用 | 本机 `~/.m2` **只读**（沙箱策略），Maven 无法写入缓存 → 加 `-Dmaven.repo.local=<workspace 内可写目录>` 即可，网络本身是通的 |
| `spring-boot:run` 报插件依赖缺 `spring-boot-loader-tools` | 用了 `-o` 且本地仓库里只有编译期依赖。去掉 `-o` 让它拉一次插件依赖（`compile` 仍可离线） |
| `mvn test` 全量跑挂但单测本身没问题 | 先看是不是又引入了 `@SpringBootTest`/Mockito（受限环境里它们让**整个套件**终止）。当前套件 48 例全绿（`migration.*` 5 例） |
| 启动报 `Failed to instantiate [LoginThrottle]: No default constructor found` | 类里有**多个构造器**时 Spring 不会"选参数最多的那个"，而是报这个错——用 `@Autowired` 显式标出生产构造器 |
| `@SpringBootTest` 报 `Failed to determine a suitable driver class` | 加了数据源依赖后，全上下文启动就需要 `DataSource` bean。不连库的测试要给占位 url + `spring.datasource.hikari.initialization-fail-timeout=-1` + `spring.flyway.enabled=false`（见 `ApiApplicationTests` 注释） |
| 单测报端口 8080 被占用 | `SpringApplicationBuilder.properties(...)` 设的是 **default properties，优先级低于 `application.yml`**，`server.port=0` 会被 yml 的 `8080` 覆盖。改用 `.run("--server.port=0", ...)`（命令行参数优先级最高）。这个坑的失败信息看起来像环境问题，其实是测试写错——只在"本机没起服务"时才碰巧通过 |
| 种子账号改了口令却登不上 | `DevUserSeeder` 幂等且默认**不重置**已存在账号的口令，于是新口令只对新建账号生效。用 `XX_AUTH_DEVSEED_RESETPASSWORD=true` 重置一次（它会同时撤销该用户的旧会话） |
| CORS 拒绝返回纯文本 `Invalid CORS request` | `DefaultCorsProcessor` 默认行为，且 Spring 6.2 的 `rejectRequest` 是**单参数**——签名写错不编译失败、只是静默不生效。见 §4 `ContractCorsProcessor` |
| PS 5.1 校验脚本报 `The term 'if' is not recognized` | `(if (...) {...})` 这种"表达式化的 if"在 Windows PowerShell 5.1 里不是合法表达式（PS7 可以），要先赋值再放进 `@{...}` |
| PS 5.1 里 `(... | Where-Object {...}).Count` 拿到空值 | 管道只输出**一个**对象时它返回标量，而标量没有 `Count` 成员，`-eq 1` 就永远为假。用 `@(... \| Where-Object ...).Count` 包一层 |
| 频控类单测断言"键数归零"失败 | `pruneExpired` 清空桶后没把空桶从 map 摘掉 → 每个失败过的键永久占一条（内存泄漏）。清空后要 `map.remove(key, bucket)` |
| 改了包结构后 `mvn compile` 报 `BUILD SUCCESS`，但请求 500、日志是 `java.lang.Error: Unresolved compilation problem: XxxRepository cannot be resolved to a type` | 搬类时漏了一行 `import`。增量编译下 `javac` 会把**带 `Unresolved compilation problem` 的桩类**写进 `target/classes`，装进 Spring 上下文后第一次调用才炸，编译和单测都发现不了。做法：改包结构后先 `mvn -o -B clean compile`，**并重跑 HTTP 验收脚本**（见 §2 最后一节） |
| `mvn -q compile` 的 `$LASTEXITCODE` 看着是 0，实际编译失败 | 用 `-q` + `*>` 重定向时退出码不可靠。判断编译结果要看日志里的 `BUILD SUCCESS` / `ERROR`，或去掉 `-q` |
| PowerShell 里 `[System.IO.File]::ReadAllBytes('.\target\...')` 报找不到文件，但同路径 `Get-Item` 正常 | .NET 的静态方法用的是**进程工作目录**，不是 PowerShell 的当前位置（`cd` 只改后者）。脚本里给 .NET 方法传**绝对路径** |
| 命令行临时文件写到 `/tmp/xxx.json` 后 `curl --data-binary @/tmp/xxx.json` 报读不到 | Windows 上 `/tmp` 被解析成 `C:\tmp`（通常不存在）。用 `$env:TEMP` 或 workspace 内路径 |
| 运维在 `core.config_item.value` 里多留一个备注键，接口就 500 | 别用**全局** `ObjectMapper.treeToValue` 反序列化下发配置：它配了 `failOnUnknownProperties(true)`（那是给请求体用的）。下发配置要 `readTree` + 逐键显式读取，未知键忽略。见 `ClientConfigScalars` |
| 契约只有 12 个能力码、9 个上传分类、4 个绑定字段，但数据库表上**没有 CHECK** | `core.feature_config.feature_code`、`core.upload_policy.category`、`core.dictionary_binding.field_key` 都是自由文本列。出参前必须按契约枚举校验，否则会把契约表达不了的值发出去（D7/D8/D9，见 §4 `GET /client-config`） |
| 不带 `db` profile 启动 → `Failed to configure a DataSource: 'url' attribute is not specified` | 不是配置写错：仓储层从任务 4 起依赖 `JdbcTemplate`，**启动就必须有数据源**（见 §1）。`mvn compile`/`mvn test` 不需要库 |
| `mvn clean` 报 `Failed to delete ...\target` | `spring-boot:run` 起的 JVM 还占着 `target/classes`（Windows 文件锁）。先停掉服务再 `clean`——尤其改完类要重启时，顺序必须是「停 → clean → 起」 |
| `mvn test` 报 `TestEngine with ID 'junit-jupiter' failed to discover tests` 且 `Tests run: 0` | 增量编译留下的 `target/test-classes` 与源码不一致（改过测试、加过类之后常见）。`mvn -o -B clean test` 即恢复，**不是代码写错**；同理加类/改包后一律 `clean` 一次 |
| 退出登录后浏览器仍带着旧 `SID`，下一个请求 401 而不是"干净状态" | 清 Cookie 的 `Set-Cookie` 必须与签发时**逐项一致**（同名、同 `Path=/api/v1`、同 `SameSite=Lax`、同 `Secure`），只改 `Max-Age=0` 不够——属性不同的 `Set-Cookie` 会被浏览器当成**另一个** Cookie，原 Cookie 继续存在。见 §4 `POST /auth/logout` |
| 前端点退出后弹「退出失败」但仍认为已退出 | 退出两次的第二次必然 401（SID 已撤销），这是契约内的正常结果；前端 `logout()` 对失败只 `ElMessage.warning`，`finally` 清本地状态。**不要**为了"看起来成功"把重复退出改成 200 |

---

## 10. 接下来各自的第一件事


1. **先跑 `mvn -o -B compile`，再带着数据库启动 `mvn spring-boot:run "-Dspring-boot.run.profiles=dev,db"`**（见 §1），用 §4 的两条响应确认骨架在你机器上也是通的。
2. **把 `scripts/dev-check.ps1` 跑一遍**，确认 JDK/Maven 版本对得上。
3. 要动数据库的接口，带 `db` profile 启动（见 §1），跑一次 `scripts/verify-csrf.ps1`、`scripts/verify-login.ps1`、`scripts/verify-me.ps1`、`scripts/verify-client-config.ps1`、`scripts/verify-health.ps1` 与 `scripts/verify-logout.ps1` 确认库、迁移、登录、`/me`、前端配置、探针、退出都正常。
   前端联调用 `demo.admin` / `demo.operator` / `demo.classified`（三种权限档）：启动时给 `XX_AUTH_DEVSEED_ENABLED=true` + `XX_AUTH_DEVSEED_PASSWORD=<口令>`（见 §4 种子账号）。
4. 按分工方案 §2 确认自己负责的模块，再按 §4 的阶段顺序开工。
5. 共享层接口在开工第一周内冻结并补进本 README 的 §4 表格；之后只在评审后变更。
6. 需要新增分层或改包结构，先在这里更新 §2，别让下一个人对着过时文档写代码。
