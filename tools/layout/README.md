# 目录检查工具

在仓库根目录执行 `python tools/layout/verify_layout.py`；Python 3.10+，只用标准库。检查关键入口、角色链接、活动Markdown链接、契约生成路径与逐文件映射，并输出 reports/path-optimization/layout-checks.json。原始文件完整性用 `--integrity` 做SHA256核对，同时核对原始OpenAPI/DDL未变；需要本机保留的local-only/依赖/缓存，克隆共享仓库后不具备这些本地历史材料，故不默认执行。迁移清单与旧业务哈希是2026-10-13固定证据；后续正式修改契约时继续用默认导航检查，不把历史完整性检查作为永久冻结契约的CI门禁。检查不会安装依赖、联网或替代前端测试。
