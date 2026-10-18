# PLATE固定合成样本

38行原始JSONL通过4个批次形成72条处理结果：接纳16、重复17、拒绝39；最终16条事实、43条质量记录、8组查询预期。来源、设备、目标和图片引用均为合成。

- raw/：初始34行、增量3行、另一来源1行。
- protocol.json、reference-data.json：协议与参照目录。
- batches.json、cases.json：批次顺序与具名案例；caseId只用于测试。
- expected/：逐行结果、标准DTO、批次计数和查询预期。
- manifest.json：固定种子与11个生成数据文件的SHA-256。

原始字节沿用已验收版本；运行模块不得从expected生成结果。仓库根运行python workers/data/run_stage1_local.py验证本地结果，run_pg_lab.py在独立PG中显式创建测试引用、任务和合成文件。该样本不代表厂商协议、生产任务或授权已签认。
