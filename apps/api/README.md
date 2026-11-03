# xx-platform 后端

本轮把 #5 的领域代码与已修复 #3 `7e5f131704c784fc61657d8c2f5999bc83e44d44` 集成。规则唯一基线为远程 main `aca438ebfb218d40350b60ff933d2251bed50500` 的原件，不以本页或分支报告替代开发规则。

规则入口：[主分支开发入口](https://github.com/WangLiu-Hades/xx-platform/blob/aca438ebfb218d40350b60ff933d2251bed50500/docs/START_HERE.md)、[OpenAPI](../../contracts/api/openapi.yaml)、[API约定](../../contracts/api/api-conventions.md)、[双人分工](../../docs/project-governance/BACKEND_TWO_DEV_SPLIT.md)、[内部任务协议](../../docs/project-governance/INTERNAL_TASK_PROTOCOL.md)、[审批协议](../../docs/project-governance/SENSITIVE_ACTION_PROTOCOL.md)、[数据库定义与字典](../../contracts/database/README.md)。本轮不改写这些规则原件。

## 构建与完整测试

使用 Java17、Maven3.9.x。生成器固定为主分支约定的 Spring Generator7.25.0，生成目录在 `target/generated-sources/openapi`，不提交生成文件。`generate-api` 默认激活；验收仍显式指定，避免其他Maven profile影响默认激活。

```powershell
cd apps/api
mvn -B -Pgenerate-api compile
```

编译不连接数据库。首次构建需要取得生成器及依赖；缓存齐全后才可加 `-o`，不承诺首次离线可用。

完整测试包括真实数据库、真实HTTP登录、权限、CSRF、任务、审批和文件检查，必须指定**专用、可丢弃的 PostgreSQL/PostGIS 测试库**。测试会写入合成数据，并清理测试队列等表，不能指向开发共享库或生产库。

```powershell
$env:XX_TASK_IT_DB_URL='jdbc:postgresql://127.0.0.1:55433/pr5_it'
$env:XX_TASK_IT_DB_USER='你的隔离测试账号'
$env:XX_TASK_IT_DB_PASSWORD='该测试账号的口令'
mvn -B -Pgenerate-api clean verify
```

`scripts/dev-check.ps1` 使用同一完整入口，未配置测试库时会先报错。CI使用一次性PostgreSQL/PostGIS服务并提供上述环境变量，保留Surefire原始结果；镜像来源为[PostGIS维护仓库](https://github.com/postgis/docker-postgis)。仓库内CI口令只用于这一个临时测试容器，不是部署凭据。

生成、编译成功不能代替完整测试；只跑领域单测也不能代替 #3 与 #5 的组合回归。当前验证结果和本机Windows编译限制的实际处理见[本轮检查报告](../../reports/pr5-fix/verification.md)。

## 启动与联调

先按 `scripts/db-provision.ps1` 及数据库说明建立开发库和账号，由迁移账号应用原始迁移。应用启动使用运行账号，保持原始迁移校验。真实连接口令只从环境注入，不写入仓库。

```powershell
$env:SPRING_DATASOURCE_URL='jdbc:postgresql://127.0.0.1:5432/xxplatform_dev'
$env:SPRING_DATASOURCE_USERNAME='xxplatform_app'
$env:SPRING_DATASOURCE_PASSWORD='该开发运行账号的口令'
mvn -B -Pgenerate-api spring-boot:run "-Dspring-boot.run.profiles=dev,db"
```

接口前缀 `/api/v1`。用 `/health` 和 `/ready` 检查；旧 `/smoke` 控制器已删除，不恢复演示端点。就绪检查依赖真实配置和依赖状态，不能把health成功当作所有业务已就绪。

前端使用现有Vite同源代理转发 `/api/v1`，沿用SID Cookie、绑定CSRF、可信Origin和固定权限码。生产部署按主分支要求使用HTTPS及Secure Cookie；本轮测试中的HTTP回环设置不改变生产配置。

## 已实现代码及尚待接入的能力

本分支包含 #3 的会话、当前权限/范围、Context、统一任务、审批及受控文件底座，以及 #5 的接入批次、回放、补数、质量查询、车牌查询和地图编排。路由存在不代表真实数据或外部业务已经接通。

| 生产接入端口 | 当前状态与接入要求 |
| --- | --- |
| `DataServiceBindings.SharedAccess` | 尚未绑定。需调用 #3 的真实SID、当前操作权限及授权元组；不可采用固定身份 |
| `IngestionDomainGateway.AtomicSubmission` | 尚未绑定。批次、公共任务、幂等回执、审批消费及审计必须同事务提交 |
| `IngestionQueryAdapter.Client` | 尚未绑定真实M03读取服务 |
| `IngestionQueryAdapter.WindowPolicy` | 已有窗口工具类；仍需绑定当前留存边界与主分支查询预算 |
| `PlateQueryAdapter.Client`、`TargetLookup` | 尚未绑定真实采集/地图查询及目标LOOKUP，不编造传输地址或目标ID |
| `CaptureExportSourceAdapter.ContextLookup`及导出执行连接 | 尚未绑定。需接现有完整Context、公共导出/发布/下载链路 |

未绑定时返回实际不可用原因，不制造成功回执。`client-config.features`仍是唯一能力目录，RAW_INGESTION等未就绪能力保持禁用。回放/补数还需要接入 #3 的真实资源冻结、版本重校与同事务审批消费；不能仅检查 `X-Approval-Id` 的UUID形状。

`dev,ingestion-mock`只允许回环演示读取，普通启动不启用；演示写入仍不可用。不能把这类测试或测试替身结果当作真实浏览器→API→数据库业务验收。

## 必须保留的安全与文件规则

- 审批人名单、审批快照密钥 `XX_APPROVAL_PAYLOAD_KEY` 及真实设备Profile由部署绑定；申请人/执行人不得自批，配置缺失保持动作不可用。
- 下载许可密钥 `XX_FILE_DOWNLOAD_PERMIT_KEY` 为至少32字节随机值的Base64，多实例共享。前端先取元数据获取5分钟许可，再下载；当前权限、来源范围及文件到期都需复核。
- ACTIVE文件缺少到期时间不能引用、访问或发布。#5接入和回放也执行相同要求，不能把空到期时间当成永久有效。
- 新会话完整绑定UA/IP；旧的不完整绑定需重新登录。缩权后不能通过旧审批、旧幂等响应或下载许可继续访问。
- 批次详情及错误行路径拒绝缩写UUID，隐藏/不存在对象仍按404处理。

## 历史记录

此前阶段说明已保存在[历史README](../../reports/pr5-fix/history/api-readme-before-compatibility-fix.md)。历史副本仅统一了行尾空白，原件可按修复后 #3 的提交追溯。其中旧测试数量、仅骨架描述和无数据库全量测试等内容不再作为当前使用说明；历史记录不替代主分支规则。本轮修复及可重复检查记录见[验证报告](../../reports/pr5-fix/verification.md)，#3原修复证据仍在[原复核报告](../../reports/pr3-fix/final-main-rule-verification.md)。
