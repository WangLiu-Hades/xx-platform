# Pinia状态与用户动作

这里集中管理共享交互状态，页面调用usePlatform与useAgent；从[工程目录](<../../README.md>)启动。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [platform.ts](<platform.ts>) | 登录/退出、导航/详情、筛选/分页、选中、表单、Context、文件、任务、消息与业务动作；按apiMode切换Mock/HTTP |
| [agent.ts](<agent.ts>) | 智能体会话、流式回答、动作执行、模型配置/探活、本机持久化与对话导出 |

## 怎么使用

HTTP分支调用gateway、mutations、httpForms、resources和tasks，Mock调用repository及浏览器工具。修改动作同时核对两个分支，领域ID、taskId、ETag、contextId和展示号不能混用。

智能体会话先经`utils/agentNlu.ts`识别意图，再决定是否调用`api/llm.ts`；模型调用失败、超时或用户停止时回退规则应答。`runAction`执行前用`platform.allowed`校验菜单权限，创建类动作复用platform现有表单（如`formWatch`、`formAnalysis`），不新开一套业务提交逻辑。会话与模型配置只写浏览器localStorage，不进入平台数据库。

## 维护与检查

换身份/退出清理旧状态和请求，HTTP失败不能写Mock作为成功兜底。运行platform、session、agent及相关HTTP测试，导航/选择变动再做页面回归。智能体新增动作时同步`utils/agentNlu.ts`、本文件`runAction`和浏览器回归。后端仍需强制验证权限，隐藏按钮不是授权机制。返回[源码入口](<../README.md>)。
