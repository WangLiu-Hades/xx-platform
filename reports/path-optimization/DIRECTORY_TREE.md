# 整理后的目录导航

```text
平台 - 路径优化/
├─ README.md                       所有人先读
├─ .github/                        CI / PR模板 / 待启用评审模板
├─ apps/
│  ├─ web/                         DEV-02，当前可运行Vue前端
│  │  ├─ src/                      页面/组件/状态/API/Mock/工具
│  │  ├─ scripts/                  当前OpenAPI生成与历史汇总
│  │  ├─ tests/                    实际前端单元/浏览器回归
│  │  ├─ audit/                    迁移与测试证据，保留日期
│  │  └─ dist/                     新构建展示包，Git排除
│  └─ api/                         DEV-01，正式API待实现
├─ workers/
│  ├─ data/                        DEV-03，数据/算法待实现
│  └─ delivery/                    DEV-01，外部动作待实现
├─ contracts/
│  ├─ api/                         当前OpenAPI/约定/待确认项
│  └─ database/                    DDL/字典/索引/SQL/迁移设计
├─ docs/
│  ├─ START_HERE.md                 按顺序开始开发
│  ├─ team/                        四角色入口与路径归属
│  ├─ requirements/source/         原始PDF、Word来源
│  ├─ system-design/               00–11专题/ADR/UI/工程图示
│  ├─ project-governance/          工程分工/计划/接口交接/历史审查
│  └─ reference/diagrams/          原PNG/SVG/XMind材料
├─ infra/                          DEV-04，部署待实现
├─ tests/                          DEV-04，真实跨服务验收待实现
├─ tools/layout/                   可复跑目录检查
├─ reports/path-optimization/      前后映射和本次实测结果
├─ archive/
│  ├─ legacy-sites/                原React/Vinext/Sites项目
│  ├─ ui/                          旧静态原型与评估
│  ├─ design-project-root/         原项目根报告/入口
│  └─ original-grouping/           原分工残余空层级
└─ local-only/                     仅入口README进入Git
   ├─ git-history/                 完整保留原嵌套.git
   ├─ contracts/                   合同原件
   ├─ contract-revisions/           合同修订及渲染
   ├─ planning/                    预算原件
   ├─ office-locks/                原Office锁文件
   └─ path-optimization/           原dist/缓存/被改写文件原件
```

目录归属表示主责；跨角色的输入输出仍以工程分工交接表为准。后端、Worker、部署和跨服务测试目录当前只有README，不能据目录存在推断开发完成。
