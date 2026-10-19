> 2026-10-18当前版本：[PD-2026-10-18.v1](docs/project-governance/暂定方案与修改记录.md)。当前分工01/02后端、03前端、04数据、05测试；下文旧数量/编号/NO及签认门禁保留作历史追踪，不覆盖本轮暂定设计，也不证明当前实现。旧治理生成脚本不得覆盖本轮policy/暂定文档。

# 多维数据分析平台

团队仓库：<https://github.com/WangLiu-Hades/xx-platform>。本次整理日期：2026-10-13；本地目录整理不会自动上传、提交或改变远程仓库。

首次上传与成员拉取按 [GitHub逐步操作指南](docs/team/GitHub上传与分角色开发操作指南.md)。先读 [开发入口](docs/START_HERE.md)，再进入自己的角色任务。当前已有Vue前端展示原型、Spring Boot API骨架和合成PLATE数据处理/查询模块；业务API、真实源/任务联调、外部投递与生产部署仍需开发。目录中的接口契约、SQL 和设计文档不能代表这些服务已经实现。

| 目录 | 放什么 | 主责 / 使用入口 |
| --- | --- | --- |
| [apps/web](apps/web/README.md) | 当前 Vue 3 / TypeScript / Vite 前端源码 | [DEV-03 前端](docs/team/DEV-03-frontend.md) |
| [apps/api](apps/api/README.md) | Java 17 / Spring Boot骨架；业务接口待实现 | [DEV-01/02模块分工](docs/project-governance/BACKEND_TWO_DEV_SPLIT.md) |
| [workers/data](workers/data/README.md) | 合成PLATE接入、PG写入/查询、工程指标与复验脚本 | [DEV-04 数据/算法](docs/team/DEV-04-data.md) |
| [workers/delivery](workers/delivery/README.md) | 消息、设备命令等外部动作的预留位置 | DEV-01 |
| [contracts/api](contracts/api/README.md) | 当前 OpenAPI、接口约定、错误码和待确认项 | DEV-01/02按模块主责，全员共同使用 |
| [contracts/database](contracts/database/README.md) | DDL、字典、索引与迁移设计 | DEV-01合并迁移，DEV-02领域片段，DEV-04数据映射/查询 |
| [docs/requirements/source](docs/requirements/source/README.md) | 原始 PDF、原分工及人员要求等原件 | 所有人，业务冲突以原始 PDF 为准 |
| [docs/system-design](docs/system-design/README.md) | 架构、安全、容量、交互、ADR与图示 | 按专题查阅 |
| [docs/project-governance](docs/project-governance/README.md) | 明确分工、接口交接、未决事项、开发计划及历史审查 | 所有人 |
| [infra](infra/README.md)、[tests](tests/README.md) | 部署与跨服务验收的预留位置 | [DEV-05 测试](docs/team/DEV-05-quality.md)；部署实际执行人另登记 |
| [tools/layout](tools/layout/README.md) | 本次目录和文件完整性检查工具 | DEV-05验证，修改者同步维护 |
| [reports/path-optimization](reports/path-optimization/README.md) | 旧→新逐文件映射、校验和整理报告 | 查询历史路径、复核整理结果 |
| [archive](archive/README.md) | 旧 React/Vinext 项目、旧静态原型与历史入口 | 只供对照，当前开发从 apps/web 开始 |
| [local-only](local-only/README.md) | 原 Git 历史、合同、预算、原件备份等本地保留材料 | 除入口说明外已排除 Git；需要时单独交接 |

## 启动前端

安装 Node.js 24，在仓库根目录执行：

```powershell
npm --prefix apps/web ci
npm --prefix apps/web run dev -- --port 5180 --strictPort
```

按终端地址访问。默认 Mock 模式，演示账号与后端接入方式见 [前端说明](apps/web/README.md)。原始业务需求最高优先级；按PD-2026-10-18.v1暂定规则开发，甲方确认与真实资源就绪分别校验。后端个人模块按[双人分工](docs/project-governance/BACKEND_TWO_DEV_SPLIT.md)。

```powershell
npm --prefix apps/web run generate:api
npm --prefix apps/web run typecheck
npm --prefix apps/web test
npm --prefix apps/web run build
python tools/layout/verify_layout.py
```

开发交接使用本仓库的源码、契约、需求和角色说明；仅展示时发送构建后的整个 `apps/web/dist/`。`node_modules/`、缓存、个人环境和 `local-only/` 保留在本机，不随 Git 分享。首次上传按 [协作说明](docs/team/README.md)检查待提交清单。

后端运行与当前未实现项见[apps/api说明](apps/api/README.md)。在apps/api执行`mvn -B test`；dev探针为`GET /api/v1/smoke`，不代替正式健康/就绪与业务验收。
