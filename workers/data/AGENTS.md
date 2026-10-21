> 2026-10-19当前分工：DEV-04数据、DEV-03前端、DEV-05独立测试；DEV-01维护共享TaskStore/授权/迁移，DEV-02负责M03等领域公开API与查询编排。下文D3编号和v0协议为交付历史；业务与任务参数以[主分支PD方案](../../docs/project-governance/暂定方案与修改记录.md)及[内部协议](../../docs/project-governance/INTERNAL_TASK_PROTOCOL.md)为准，旧pending停做/实验租约数值不覆盖现行规则。

# DEV-04 数据模块交接

先读README.md、docs/DELIVERY_GUIDE.md、docs/d3-12r-current-contract.md及validation/README.md。中文说明真实结果和未完成范围。

- 权威数据库/API契约在仓库contracts；不擅自新增公开API、关闭P清单或改DEV-01状态机。
- 样本在fixtures，代码在data_worker，实验夹具在lab，草案在docs；不依赖个人workspace。
- 输出默认.artifacts，D3_ARTIFACT_ROOT可指定独立目录。不要提交虚拟环境、数据库data、SQLite状态、个人环境或临时日志。
- fixtures保持冻结字节；Git属性禁止换行转换。不能改预期让测试通过。
- 仓库根复验：python workers/data/run_stage1_local.py；真实PG：python workers/data/run_pg_lab.py。
- 当前只验证合成数据；正式H04、DEV-05独立复验、真实协议和业务指标仍未完成。
- D3-09～12已补有界准备/短事务发布、control检查、四项M03读取和故障用例，详见docs/m03-execution-and-reads-v0.md。当前实现仍使用lab控制/发布/恢复替身，正式H04未交付。
- ID 修复套件含103项本地、30项PG用例。2026-10-17历史验收基于163表；validation/identity-v2-*原始报告保留，不能证明本次主线整合包或165表已通过。当前源码与报告必须重新核对，见docs/identity-v2.md和validation/README.md。
- 当前先交付ID边界修复，再完成D3-12R新契约/165表复验；之后与DEV-01/02对齐授权、质量口径和游标会话，接入正式H04及同事务publisher，交DEV-05独立复验。不得绕过有界预算或在外层事务包装ingest。
- DEV-04负责数据模块及自测；DEV-01提供共享TaskStore/授权/迁移及同事务发布底座，DEV-02提供M03公开API与查询编排，DEV-05负责共享环境及独立复验。后端骨架已合入，正式TaskStore和业务API仍待实现。
- D3-12R当前套件为128项本地/34项PG；统一入口run_d3_12r.py。旧fixtures/contract-baseline.json保持冻结，新基线为fixtures/contract-pd-2026-10-19-v1.json。只有真实PG、当前契约和当前源码同时匹配才能报告新版本PASS。
- 每轮更新真实检查及缺口，不把历史PG结果当作新代码已通过。
- ID修复保留无冒号键的原ID；含冒号键使用带版本的JSON数组编码。旧事实回放返回持久化ID，不自动重编号；不要混用旧Worker处理含冒号输入。
