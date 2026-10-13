# GitHub上传辅助工具

先读 [逐步操作指南](../../docs/team/GitHub上传与分角色开发操作指南.md)。prepare_upload.py用Git真实忽略规则预览可共享文件，并可复制到另一个干净clone；保留被覆盖原件，不提交、不推送、不删除目标多余文件。要求Python3.10+及Git，源必须为优化项目根，目标必须为独立干净Git克隆，报告必须放在两个项目之外（建议系统TEMP）。

默认只预览。先查看报告中的overwrites和destinationOnlyTrackedFiles；确认后添加--apply，允许内容覆盖还需--allow-overwrite。源代码和备份位置都有路径边界检查；链接路径、100MiB超限文件、依赖/本机材料误入及其他根应用会被阻止。该工具不代替业务差异评审；已有远程改动必须先检查，不能每次日常开发都重新导入整套项目。

```powershell
python tools/git/prepare_upload.py --help
```
