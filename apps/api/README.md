> 当前基线：PD-2026-10-18.v1；后端个人模块按[当前分工](../../docs/project-governance/BACKEND_TWO_DEV_SPLIT.md)。前端DEV-03、数据DEV-04、测试DEV-05。

# xx-platform 后端服务（`apps/api`）

多维数据分析平台的**正式 HTTP API**。前端（Vue 原型）已经按契约写好，这里要把 198 个操作真正实现出来。

- 契约唯一来源：[openapi.yaml](../../contracts/api/openapi.yaml)（OpenAPI 3.0.3，版本 `1.0.0-dev.3-provisional`，198 操作 / 623 Schema，Base URL `/api/v1`）
- 接口约定：[api-conventions.md](../../contracts/api/api-conventions.md) ｜ 错误码：[error-codes.md](../../contracts/api/error-codes.md)
- 前端对接：[FRONTEND_INTEGRATION.md](../../contracts/api/FRONTEND_INTEGRATION.md)
- 契约速读（新人先看这个）：[API速读指南.md](../../docs/API速读指南.md)
- **两人分工与边界**：[BACKEND_TWO_DEV_SPLIT.md](../../docs/project-governance/BACKEND_TWO_DEV_SPLIT.md)

> 当前状态：**只有骨架，没有任何业务接口**。18 个源文件，其中 5 个是分层说明（`package-info.java`）。
> 骨架已完成并经过实跑验证的部分见 §4，未做的见 §5。

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

# 1) 编译（首次需要解析依赖；仅缓存完整时可加-o）
mvn -B compile

# 2) 带自检端点启动（dev profile 才会注册 /smoke）
mvn -B spring-boot:run "-Dspring-boot.run.profiles=dev"

# 3) 另开一个终端验证
curl.exe http://127.0.0.1:8080/api/v1/smoke
```

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
    │   │   ├── client/                    ④ 外部依赖：DEV-04 查询计算、通知通道、设备协议（空）
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
| `service` | 领域规则、状态机、`@Transactional` | 重写 DEV-04 的查询/算法 |
| `infra` | 持久化、幂等键、游标签名、文件存储、迁移 | 业务判断 |
| `client` | 调 DEV-04、通知通道、设备协议；超时/重试/错误翻译 | 把厂商协议暴露到 `/api/v1` |
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
| `com.xxplatform.api.generated.model` | 623 个 Schema 对应的 DTO 与枚举（`TaskStatus`、`ErrorCode`…） |

配置（在 `pom.xml` 的 `generate-api` profile 内，必须与契约 `info.x-codegen.spring` 一致）：

```
generatorName          = spring
interfaceOnly          = true     ← 只生成接口，业务写在 service，契约更新不会覆盖你的代码
useSpringBoot3         = true     ← 否则生成 javax.*，Boot 3 编译不过
useOneOfInterfaces     = true     ← 契约有 21 个 discriminator / 22 个 oneOf（如 GeoJSON）
nameMappings           = setTaskId=setOperationId
```

**关于 `setTaskId=setOperationId`**：契约里 `Task` 的 JSON 字段是 `taskId`，Java 侧 setter 改名以避免与"执行 taskId"重名。**JSON 报文不变**。这是契约指定的唯一改名，不要自己加映射，更不要为了让编译通过去改契约字段名。

**生成与编译分开**：默认构建不运行生成器，需要时加`-Pgenerate-api`。默认骨架编译不证明生成的198操作接口/DTO可以编译，业务实现前必须单独验证。

---

## 4. 骨架已经做完并验证过的部分

| 能力 | 位置 | 说明 |
| --- | --- | --- |
| 启动与配置 | `ApiApplication`、`application.yml` | 8080 端口、`/api/v1` 上下文路径、UTF-8、关闭 RFC 7807（避免两套错误格式）、日志含 traceId |
| 统一成功壳 | `common/ApiResponses#success` | `{code:"0",message:"success",data,traceId}`，**不含 details** |
| 统一失败壳 | `common/ErrorResponse` | `{code,message,data:null,traceId,details:[]}`，**data 必须出现且为 null** |
| 错误码全集 | `common/ErrorCode` | 契约 34 个错误码 + 每个的 HTTP 状态映射，字面量已与契约对齐 |
| traceId 链路 | `common/RequestIdFilter` | 写 MDC、回写 `X-Trace-Id` 响应头；客户端传了就透传，保证**头与体内一致** |
| 异常兜底 | `common/ApiExceptionHandler` | 参数校验/报文解析/缺参/类型错/未知路由/未捕获，全部转成契约错误壳 |
| JSON 口径 | `config/WebConfiguration` | 当前仍忽略未知字段，与主分支additionalProperties:false要求存在待修差异；**故意不设全局 NON_NULL**（会吃掉错误体的 `data: null`） |
| 开发期 CORS | `config/WebConfiguration` | 配置列出localhost Origin，但当前未限定dev且路径匹配需复核；正式同源HTTPS规则不变，见本轮检查报告 |
| 骨架冒烟测试 | `ApiApplicationTests` | 上下文能起来；未知路由返回契约错误壳且头=体 |
| dev 自检端点 | `api/SmokeController` | 仅 `dev` profile 注册；`GET /api/v1/smoke` 验证成功壳/traceId/UTF-8/时间格式 |

### 实跑验证记录（本机）

```
$ mvn -B compile
[INFO] Compiling 12 source files with javac [debug parameters release 17]
[INFO] BUILD SUCCESS

$ GET http://127.0.0.1:8080/api/v1/smoke          （dev profile）
{"code":"0","message":"success","data":{"status":"UP","profile":"dev",
 "checkedAt":"2026-10-15T11:26:24.9473498+08:00","note":"骨架自检：统一成功壳 / traceId / UTF-8"},
 "traceId":"b931091e-..."}

$ GET http://127.0.0.1:8080/api/v1/no-such-route
HTTP 404   X-Trace-Id: d7039b3b-...
{"code":"DATA_NOT_FOUND","message":"资源不存在","data":null,
 "traceId":"d7039b3b-...","details":[]}

$ GET /api/v1/smoke 带 X-Trace-Id: front-end-abc-123
响应头与 body 的 traceId 都等于 front-end-abc-123   ← 透传成立
```

---

## 5. 还没做（依当前分工与主分支计划）

**基础设施（平台侧先做，另一侧只调用）**

- [ ] 会话与 CSRF：SID Cookie（HttpOnly/Secure/SameSite=Lax/Path=/api/v1）、`GET /auth/csrf`、login 轮换 SID、写请求校验 `X-CSRF-Token` + 可信 Origin
- [ ] 权限与数据范围：`PermissionCode`（58 项）注解 + 数据范围切面，列表/详情/导出/文件每次重校
- [ ] 幂等：`Idempotency-Key`（16–160 字符），同键同 payload 返原响应，异 payload 409
- [ ] 乐观锁：`If-Match` ↔ `version`，冲突 409 `RESOURCE_VERSION_CONFLICT`
- [ ] 签名游标：绑定账号/范围版本/查询快照/排序/页大小，只校验不解析，失败 400 `DATA_CURSOR_INVALID`
- [ ] 任务底座：`async_task` 表 + 状态机 + 领取/续租/取消/重试/发布协议（H04 交接物）
- [ ] 文件底座：multipart 分类策略、`PROCESSING→ACTIVE`、受权下载（不返回永久裸 URL）
- [ ] 审计埋点、`client-config.features` 能力开关（开发按暂定方案；真实资源与实现未就绪须明确不可用）
- [ ] 数据库迁移框架（DDL 版本化，只有平台侧统一合并）

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

---

## 8. 环境注意（踩过的坑）

| 现象 | 原因与做法 |
| --- | --- |
| `mvn package` / `mvn clean` 报无法解析插件 | 本机离线（Maven Central 不通）时，Maven **插件自身**的依赖可能没缓存。`mvn compile` 与 `mvn spring-boot:run` 不受影响；需要打包时先联网让 Maven 拉一次 |
| `mvn -Pgenerate-api generate-sources` 失败 | openapi-generator 未缓存，必须联网一次；之后本地缓存可复用 |
| 测试里出现 `Mockito ... Could not self-attach to current VM` | 沙箱环境禁止 Mockito 的自附加代理，**不是代码问题**；正常开发机/CI 上 `mvn test` 可正常跑 |
| 手工用 `.m2` 拼 classpath 启动会报 snakeyaml 找不到 | 别手工拼 classpath（会带上别的项目的旧版 slf4j/devtools），用 `mvn spring-boot:run` |
| 直接 `java -jar` 跑不了 | 还没执行过 `package`；先 `mvn package` 生成可执行 jar |
| `mvnw`/`mvnw.cmd` 报奇怪的 `'xx' is not recognized` | `.cmd` 必须是 **CRLF + ASCII**：cmd.exe 逐行解析批处理，LF 换行或 UTF-8 中文注释都会让它错乱。改这个文件时注意别被编辑器改成 LF |

---

## 9. 接下来各自的第一件事

1. **先跑 `mvn -B compile` 和 `mvn spring-boot:run -Dspring-boot.run.profiles=dev`**，用 §4 的两条响应确认骨架在你机器上也是通的。
2. **把 `scripts/dev-check.ps1` 跑一遍**，确认 JDK/Maven 版本对得上。
3. 按当前分工确认自己负责的模块，再按共享底座与领域依赖顺序开工。
4. 共享层接口在开工第一周内冻结并补进本 README 的 §4 表格；之后只在评审后变更。
5. 需要新增分层或改包结构，先在这里更新 §2，别让下一个人对着过时文档写代码。

本README保留PR的环境记录；其中编译/探针历史输出不是本轮测试结果。分支文档提及的离线环境、本机工具路径与旧脚本说明需按实际机器核验；本轮证据另存reports/pr1-integration/。
