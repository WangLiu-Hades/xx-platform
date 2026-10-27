# 后端骨架Git操作

按[团队GitHub操作指南](GitHub上传与分角色开发操作指南.md)从最新main创建个人任务分支，先读[当前后端分工](../project-governance/BACKEND_TWO_DEV_SPLIT.md)并在PR中注明实际作者、DEV编号、operationId、验证证据及未完成范围。

DEV-01负责共享底座与平台运维后端，DEV-02负责数据研判领域；DEV-03前端，DEV-04数据，DEV-05测试。以当前main契约和PD暂定方案为准，不能把旧分支整份覆盖main或提交target、node_modules、运行数据及个人凭据。

PR合并前同步最新main，人工整合文档冲突并运行前端生成/类型/单测/构建、后端Maven验证、目录链接及契约检查。真实联调、PG和生产验收与静态检查分开记录；只提交明确选择的文件。
