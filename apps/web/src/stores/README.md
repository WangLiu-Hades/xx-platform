# Pinia状态与用户动作

这里集中管理共享交互状态，页面调用usePlatform；从[工程目录](<../../README.md>)启动。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [platform.ts](<platform.ts>) | 登录/退出、导航/详情、筛选/分页、选中、表单、Context、文件、任务、消息与业务动作；按apiMode切换Mock/HTTP |

## 怎么使用

HTTP分支调用gateway、mutations、httpForms、resources和tasks，Mock调用repository及浏览器工具。修改动作同时核对两个分支，领域ID、taskId、ETag、contextId和展示号不能混用。

## 维护与检查

换身份/退出清理旧状态和请求，HTTP失败不能写Mock作为成功兜底。运行platform、session及相关HTTP测试，导航/选择变动再做页面回归。后端仍需强制验证权限，隐藏按钮不是授权机制。返回[源码入口](<../README.md>)。
