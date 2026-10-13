# ADR 004 GIS 引擎

**状态：拟议。** Context：S1/S4 聚焦 2D 地图、框选、轨迹、热力、设备点。Decision：前端 OpenLayers，后端 PostGIS，交换 GeoJSON WGS84，bbox/zoom 聚合。Alternatives：MapLibre、Cesium。Consequences：2D 交互集中；底图样式/授权、离线地图与坐标源仍需 ISSUE-010/Q-003 确认。
