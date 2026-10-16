# DEV-03 数据模块交接

先读README.md、docs/DELIVERY_GUIDE.md及validation/README.md。中文说明真实结果和未完成范围。

- 权威数据库/API契约在仓库contracts；不擅自新增公开API、关闭P清单或改DEV-01状态机。
- 样本在fixtures，代码在data_worker，实验夹具在lab，草案在docs；不依赖个人workspace。
- 输出默认.artifacts，D3_ARTIFACT_ROOT可指定独立目录。不要提交虚拟环境、数据库data、SQLite状态、个人环境或临时日志。
- fixtures保持冻结字节；Git属性禁止换行转换。不能改预期让测试通过。
- 仓库根复验：python workers/data/run_stage1_local.py；真实PG：python workers/data/run_pg_lab.py。
- 当前只验证合成数据；正式H04、DEV-04独立复验、真实协议和业务指标仍未完成。
- 下一步优先改造PgIngestor整批锁async_task的问题，接入实际续租、运行中取消和短发布事务。lab publisher不是生产实现。
- DEV-03负责数据模块及自测，DEV-01负责公开API/授权/任务及统一迁移，DEV-04负责共享环境及独立复验。
- 每轮更新真实检查及缺口，不把历史PG结果当作新代码已通过。
