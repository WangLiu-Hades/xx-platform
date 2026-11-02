# 首链就绪度矩阵（契约桩）· 2026-10-25

运行时间：2026-10-25 14:34:55（Asia/Shanghai）  
前端：http://127.0.0.1:5181 　契约桩：http://127.0.0.1:8080

> **这是契约桩验证，不是真实后端联调。** 每个"通过"都只说明前端在真实 HTTP 路径下的请求、校验、状态处理正确；后端实现后须按同一矩阵重跑。

**结果：14/14 通过**

| 场景 | 说明 | 结果 | 关键观测 | 截图 |
| --- | --- | --- | --- | --- |
| login-ok | 登录成功并进入系统首页 | ✅ 通过 | 菜单 28 个；落地页：系统首页 | `login-ok.png` |
| login-fail | 密码错误被拒绝（401） | ✅ 通过 | 提示：账号或密码错误 | `login-fail.png` |
| search-ok | 检索成功（20 条 + 游标翻页） | ✅ 通过 | 首页 20 条；场所列：中心广场；设备列：设备 A-01；第二页 5 条 | `search-ok.png` |
| search-empty | 空结果 | ✅ 通过 | 显示空态文案 | `search-empty.png` |
| search-forbidden | 无权访问（403） | ✅ 通过 | 提示：「接口加载失败：无操作权限：当前账号无此操作权限（桩场景 forbidden） · Trace ID：stub-PERMI」 | `search-forbidden.png` |
| search-server | 服务异常（500） | ✅ 通过 | 提示：「接口加载失败：服务内部错误（桩场景 server） · Trace ID：stub-INTERNAL_ERROR-mvn」 | `search-server.png` |
| search-timeout | 请求超时（20s 客户端超时） | ✅ 通过 | 等待 23s 观察超时提示；提示：「接口加载失败：请求超时，请重试」 | `search-timeout.png` |
| map-ok | 地图研判加载（5 台设备 + 8 点轨迹） | ✅ 通过 | 轨迹行 8 条；在线 3 | `map-ok.png` |
| map-forbidden | 地图无权（403） | ✅ 通过 | 提示：「接口加载失败：无操作权限：当前账号无此操作权限（桩场景 forbidden） · Trace ID：stub-PERMI」 | `map-forbidden.png` |
| session-renew | 401 静默续期并重放（不跳登录页） | ✅ 通过 | 续期后检索 20 条；未跳登录页；已调用 renewSession | `session-renew.png` |
| search-expired-410 | 上下文过期 410 与「重新查询」 | ✅ 通过 | 出现「重新查询」按钮；重查后 20 条 | `search-expired-410.png` |
| export-ok | 导出 202 → 任务 → 下载 | ✅ 通过 | 任务状态列：成功（中文映射）；下载文件名：检索结果导出.xlsx | `export-ok.png` |
| export-cancel | 任务取消（RUNNING → 已取消） | ✅ 通过 | 详情状态：CANCELLED | `export-cancel.png` |
| export-conflict | 导出冲突（409） | ✅ 通过 | 提示：「接口加载失败：数据已被他人修改，请刷新后重试（桩场景 conflict） · Trace ID：stub-VERSION」 | `export-conflict.png` |

## 请求序列（成功路径）

```
200 GET /api/v1/auth/csrf
200 POST /api/v1/auth/login
200 GET /api/v1/auth/csrf
200 GET /api/v1/me
200 GET /api/v1/client-config
200 GET /api/v1/dashboard/summary
200 GET /api/v1/sites
200 POST /api/v1/captures/search
200 GET /api/v1/devices
200 POST /api/v1/captures/search
```

## 说明

- 所有请求都经过 Vite 代理（5181 → 8080），带 Cookie、X-CSRF-Token、Idempotency-Key，与接真实后端时的路径一致。
- 响应全部通过前端 AJV 契约校验；任一字段不合契约会抛"接口响应不符合约定"，矩阵里会显示为异常。
- 本目录不包含真实业务数据，不能作为 215 项 PDF 需求的验收证据。
