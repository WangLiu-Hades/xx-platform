# 界面设计与原型目录

本目录保存界面规则和线框；当前Vue工程已移到仓库根的`apps/web/`，历史原型在`archive/ui/`。

## 文件与目录

| 文件或目录 | 功能与使用方法 |
| --- | --- |
| [apps/web](<../../../apps/web/README.md>) | 当前Vue 3 + TypeScript + Vite前端，包含代码、Mock/HTTP适配、测试及dist |
| [design-system.md](<design-system.md>) | 配色、间距、字体、表单、表格、图表/GIS和状态呈现规范 |
| [information-architecture.md](<information-architecture.md>) | 页面树、菜单命名、共享任务中心及Context跳转关系 |
| [wireframes/](<wireframes/README.md>) | 关键页面的低保真结构与异常状态说明 |
| [prototype-v1-baseline/](<../../../archive/ui/prototype-v1-baseline/README.md>) | 旧HTML/JavaScript/CSS源码快照，供迁移对比；归档依赖不完整 |
| [prototype-migration-assessment/](<../../../archive/ui/prototype-migration-assessment/README.md>) | 迁移前AST盘点、估算和历史验证；不是当前工程 |
| [prototype-audit-baseline.html](<../../../archive/ui/prototype-audit-baseline.html>) | 更早的单文件原型审计备份，供查看原始布局/行为，不作为当前交付入口 |

## 怎么使用

产品评审读页面树和线框；前端开发先读设计系统，再到当前工程启动。给他人演示发送仓库根下`apps/web/dist/`整体，给开发者发送仓库根下`apps/web/`源码并附API/数据库契约，不需要把历史基线或测试截图打进运行包。

## 维护与检查

业务冲突以原始PDF为准，原型中的合成数据和模拟成功不能定义正式算法、权限或设备协议。设计变动检查页面树、组件、契约和测试是否同步。原始PDF的64张内嵌图为2×2占位图，现有文字验收不能证明缺失截图的像素一致。返回[系统设计入口](<../README.md>)。
