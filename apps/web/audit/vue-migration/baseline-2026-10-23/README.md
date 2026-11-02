# 前端基线 2026-10-23

生成时间：2026-10-23 21:08:03（Asia/Shanghai）
Node v24.22.1 / npm 11.19.1

| 项目 | 结果 |
| --- | --- |
| 类型检查 | 通过 |
| 单元/契约测试 | 294 项通过（8 个文件） |
| Mock 构建 | 通过，dist 3.28 MB |
| HTTP 构建 | 通过，dist-http 3.32 MB |
| 页面冒烟 | 28 个菜单，页面错误 0，控制台错误 0 |

## 契约指纹

- package-lock.json：`c836ec1b59e65f322328e991fe9a1f723e5e82f7b478c10139b0a3e5b07f7ae1`
- openapi.yaml：`340e95f1c58d6215a6434ca3f822856648a12a862c501048fc5e1d37a8ec601f`

## 复现

```powershell
cd apps/web
npm run baseline
```

## 边界

本目录只能证明"原型可运行、可构建、28 页无脚本错误"，**不能**作为 215 项 PDF 需求的业务验收证据，也不代表真实后端已联调。
