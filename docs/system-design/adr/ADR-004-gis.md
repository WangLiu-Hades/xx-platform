> 2026-10-18当前开发基线：[PD-2026-10-19.v1](../../project-governance/暂定方案与修改记录.md)。本文件原有未决值、四人编号及“未签认统一422/禁用”说明由本版暂定规则覆盖；甲方仍待确认，真实资源/实现就绪仍独立校验。原文保留作追踪。

# ADR 004 GIS 引擎

**状态：拟议。** Context：S1/S4 聚焦 2D 地图、框选、轨迹、热力、设备点。Decision：前端 OpenLayers，后端 PostGIS，交换 GeoJSON WGS84，bbox/zoom 聚合。Alternatives：MapLibre、Cesium。Consequences：2D 交互集中；底图样式/授权、离线地图与坐标源仍需 ISSUE-010/Q-003 确认。
