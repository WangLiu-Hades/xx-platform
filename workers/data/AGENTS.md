# DEV-03 数据模块交接

先读README.md、docs/DELIVERY_GUIDE.md及validation/README.md。中文说明真实结果和未完成范围。

- 权威数据库/API契约在仓库contracts；不擅自新增公开API、关闭P清单或改DEV-01状态机。
- 样本在fixtures，代码在data_worker，实验夹具在lab，草案在docs；不依赖个人workspace。
- 输出默认.artifacts，D3_ARTIFACT_ROOT可指定独立目录。不要提交虚拟环境、数据库data、SQLite状态、个人环境或临时日志。
- fixtures保持冻结字节；Git属性禁止换行转换。不能改预期让测试通过。
- 仓库根复验：python workers/data/run_stage1_local.py；真实PG：python workers/data/run_pg_lab.py。
- 当前只验证合成数据；正式H04、DEV-04独立复验、真实协议和业务指标仍未完成。
- D3-09～12已补有界准备/短事务发布、control检查、四项M03读取和故障用例，详见docs/m03-execution-and-reads-v0.md。当前实现仍使用lab控制/发布/恢复替身，正式H04未交付。
- 本地套件93项，PG套件27项；是否通过以validation中的m03报告及当前sourceHashes为准。旧67/12项报告保留为历史，不用它证明新代码。
- 下一步先完成当前真实PG复验，再与DEV-01签认批次授权/质量口径/游标会话、接入H04及同事务publisher，交DEV-04独立复验。不得绕过有界预算或在外层事务包装ingest。
- DEV-03负责数据模块及自测，DEV-01负责公开API/授权/任务及统一迁移，DEV-04负责共享环境及独立复验。
- 每轮更新真实检查及缺口，不把历史PG结果当作新代码已通过。
