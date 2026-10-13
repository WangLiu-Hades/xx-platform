# 历史静态库与许可

保留旧静态原型JSZip。当前Vue从npm包导入，不加载本目录min.js。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [jszip.min.js](<jszip.min.js>) | 旧HTML的浏览器压缩/解压库，保留作历史对照 |
| [JSZip-LICENSE.md](<JSZip-LICENSE.md>) | 第三方许可，随库分发时保留 |

## 怎么使用

当前Vue使用npm ci安装锁定依赖。本文件不会自动补齐旧基线index.html引用的自身vendor相对路径；旧快照需按其README处理依赖缺失。

## 维护与检查

不直接在压缩第三方文件中开发业务。Vue升级应改依赖/锁文件并验证文件功能，历史归档是否同步需另作说明，避免改写历史证据。返回[工程入口](<../README.md>)。
