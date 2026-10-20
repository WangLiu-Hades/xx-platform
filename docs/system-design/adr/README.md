> 2026-10-18当前开发基线：[PD-2026-10-19.v1](../../project-governance/暂定方案与修改记录.md)。本文件原有未决值、四人编号及“未签认统一422/禁用”说明由本版暂定规则覆盖；甲方仍待确认，真实资源/实现就绪仍独立校验。原文保留作追踪。

# 架构决策记录（ADR）

本目录记录选型背景、决定、替代方案与代价，供架构评审和实施时解释“为什么这样设计”。Markdown文件直接阅读，无需运行程序。**每份文件的状态分别生效，拟议不等于已签认。**

## 文件与目录

| 文件 | 决策内容与当前状态 |
| --- | --- |
| [ADR-001-architecture.md](<ADR-001-architecture.md>) | 模块化单体API与独立Python Worker；拟议 |
| [ADR-002-backend.md](<ADR-002-backend.md>) | Spring Boot唯一业务API、Python接入/分析计算；拟议 |
| [ADR-003-database.md](<ADR-003-database.md>) | PostgreSQL/PostGIS及DB-DESIGN-2结构已采用，规则和生产容量仍待确认 |
| [ADR-004-gis.md](<ADR-004-gis.md>) | OpenLayers、PostGIS、WGS84及聚合边界；拟议，底图许可等仍待确认 |
| [ADR-005-async-task.md](<ADR-005-async-task.md>) | 数据库任务、尝试、租约及成功发布边界采用，限额与外部协议仍pending |
| [ADR-006-api.md](<ADR-006-api.md>) | REST /api/v1、OpenAPI、统一响应及Context；拟议 |
| [ADR-007-authentication.md](<ADR-007-authentication.md>) | Cookie SID会话、CSRF/Origin与撤销；拟议，最终生命周期见P-001 |
| [ADR-008-deployment.md](<ADR-008-deployment.md>) | 环境隔离、Compose、迁移和备份演练；拟议 |

## 怎么使用

先对照[技术栈专题](<../02_技术栈选型与ADR.md>)，再打开相关ADR和其引用的API/数据库设计。DEV-01负责公开API与认证事务，DEV-03负责数据计算，具体交接以[现行实现分工](<../../project-governance/ENGINEERING_ASSIGNMENTS.md>)为准。

## 维护与检查

决定改变时记录日期、状态、理由、被替代方案和影响范围，保留历史理由；同步相关专题、契约、部署与测试。检查是否误把拟议方案、选型采用或结构验证写成业务规则签认/生产完成。返回[系统设计入口](<../README.md>)。
