# 保存期限一年：变更与验证

2026-10-19用户明确要求将保存期限从一个月改为一年，以解决前次全面检查F08。策略升级PD-2026-10-19.v1，OpenAPI升级1.0.0-dev.4-provisional。

物理保存配置、业务/文件/任务/结果/审批审计及备份统一12个自然月；新增年报清理前冻结和在途输入保护的明确要求。详细定义见[保存策略](../../docs/project-governance/RETENTION_POLICY.md)。采集、任务、文件、日志、备份按各自起算事件计算；闰日按目标月最后一日钳制。既有缺失历史继续披露，延长期限不恢复已删除数据。

普通查询31天、Context/游标/下载许可、审批时限、RESIDENCE一个自然月统计窗口及标签7天访问期保持原值；其他运行配置、权限、接口报文结构与数据库DDL均未变。198操作、623 Schema和DEV-01/02的118/80分工保持不变；用户暂缓的API指南原句仍逐字保留。

本轮完成契约引用/示例校验、前端类型/294项单测/构建及目录检查，机器结果及实际日志保存在本目录。源码检查确认生成的运行时校验契约与改动前一致。F08在规则层已解决，正式清理、备份与年报调度服务仍需实现，未修改生产数据库。

## 上次Java/Maven与PostgreSQL验证受限的具体证据

本机命令可找到Java、Maven、pg_ctl、psql；Java/Maven已进入编译阶段，PG的initdb/pg_ctl版本检查均返回0。当前工具命令实际身份为CodexSandboxOffline，区别于桌面Administrator会话。观察到的失败为：

- Java编译过程中，JDK对依赖JAR执行真实路径解析/ZIP文件系统操作时抛出java.nio.file.AccessDeniedException，详见[Java诊断日志](../pr1-integration/backend-diagnostic.txt#L114)。Maven本身已经启动；该结果不能说明JDK或Maven未安装，也不能证明源码编译通过。
- 隔离PG实例启动时pg_ctl报could not create restricted token: error code 87，随后无法启动实例；[执行报告](../pr1-integration/worker-postgres.txt)标为NOT_RUN。没有改动已有PostgreSQL服务或数据库。

这些是实际执行身份下的路径访问/Windows安全令牌失败；更细的Windows策略或API根因仍需在正常桌面终端与受限进程之间对照定位，未断言安装损坏或测试已经通过。本轮保存策略修改无需变更Java/PG安装。
