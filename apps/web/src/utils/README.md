# 浏览器文件与集合工具

工具供页面/store导入，不提供后端API，测试从工程根目录执行。

## 文件与目录

| 文件 | 功能 |
| --- | --- |
| [files.ts](<files.ts>) | JSZip生成XLSX、下载文件名处理、行导出、JSON/CSV/XLSX演示解析 |
| [sets.ts](<sets.ts>) | evaluateSets解析A–Z、交并差及括号，同层从左到右，不使用eval |

## 怎么使用

演示导入先parseImport后stageImport，正式HTTP使用受控上传和异步服务。当前解析限制为5MB文件、20MB解压、5000条ZIP项、1–1000行对象；XLSX读sheet1，不承诺复杂公式/样式保留。集合用括号明确分组，例如`(A ∪ B) ∩ C`，本地ID运算不承担P-016身份拟合。

## 维护与检查

运行`npm test -- tests/files.test.ts tests/sets.test.ts`，核非法表达式、缺操作数、解压限额、CSV引号与无效输入。正式CAPTURE/TARGET等价及异型拒绝遵循P-012契约。返回[源码入口](<../README.md>)。
