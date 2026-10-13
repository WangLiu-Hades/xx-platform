# 合成数据与本地模拟

此目录只用于Mock展示，不真实采集、计算身份或执行外部命令；运行模式由构建配置决定。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [fixture.json](<fixture.json>) | 初始虚构账号、场所、设备、采集、人员、任务、布控、报表和日志等 |
| [repository.ts](<repository.ts>) | 数据校验/加载、reactive数据库、本地持久化/重置、模拟审计/统计/分析/报表 |
| [imports.ts](<imports.ts>) | stageImport在副本中准备/校验演示导入，由调用者成功提交 |

## 怎么使用

按[工程说明](<../../README.md>)运行Mock。LocalStorage键为vue-prototype-mock-v2，清除当前站点该键并刷新恢复fixture。修改fixture不会自动替换已有浏览器存储，测试使用隔离上下文或明确重置。

## 维护与检查

数据结构与domain及validDatabase一致，只用合成资料。模拟命中/模型/通知不证明正式算法有效；规则看pending和签认。导入改动跑platform/files相关测试，错误不能部分落库。返回[源码入口](<../README.md>)。
