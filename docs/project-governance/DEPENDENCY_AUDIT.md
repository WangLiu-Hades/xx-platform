# 依赖漏洞联网补检

2026-10-10收到用户明确回复“授权发送并补做检查”后，已将前端依赖元数据提交公共npm registry进行审计。检查于2026-10-10 09:28:01 UTC（北京时间17:28:01）完成，npm正常退出，退出码0。

**结果：0项已知漏洞**。info、low、moderate、high、critical均为0。npm报告的依赖总数为245；检查包含生产依赖、开发依赖、可选依赖和peer依赖，未使用`--omit=dev`。该结果是检查时npm公告库对当前锁文件依赖树的匹配结果，不代表应用代码或未来版本不存在安全问题。

| 证据 | 用途 |
| --- | --- |
| [原始结果](<audit/dependency-current.json>) | npm返回的漏洞及依赖统计 |
| [执行记录](<audit/dependency-current-run.json>) | 授权来源、UTC时间、完整参数、Node/npm版本、退出码、输入/结果SHA256 |
| [标准错误输出](<audit/dependency-current.stderr.txt>) | 本次为空，无执行错误 |
| [先前失败记录](<audit/dependency-pre-authorization.json>) | 保留原DNS失败；不是本次漏洞结论 |
| [运行脚本](<scripts/audit_dependencies.cjs>) | 只进行审计并保存证据，不自动升级依赖 |
| [交付检查](<audit/checks.json>) | 核对报告完整性、当前锁文件、时间、哈希与文档一致性 |

本次使用Node v24.16.0、npm 11.13.0，在`apps/web`目录执行等价命令：

```text
npm audit --json --registry=https://registry.npmjs.org/ --include=dev --include=optional --include=peer --audit-level=low --ignore-scripts --fund=false --update-notifier=false --fetch-timeout=30000 --fetch-retries=1
```

依赖输入未变：

| 文件 | 执行前后相同的SHA256 |
| --- | --- |
| package.json | 5bf317bd9bbf932290eac397faf110142e1b4baf5b5343c19c17d42b082aa412 |
| package-lock.json | b02eb84cdf38dec3ba87846e6bfa747362a006402171dac1197d3fdb0be4fdc7 |

此次无漏洞修复或依赖升级，因此不重复运行已通过的前端回归，不将历史测试标记为本次重跑。早先的自动审批拒绝在收到用户明确授权后已解除；旧迁移审计保留原日期，当前补检以本页及上述原始证据为准。
