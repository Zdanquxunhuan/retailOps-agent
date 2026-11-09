# Version 2 文档入口

开发基线：`master` 提交 `5a3fb0324ca3989ed1fdcd9c3b6d1a8b2cda7e5c`。版本名称：Version 2，只读证据增强。沿用原工程 Maven 产物版本 `0.0.1-SNAPSHOT`，新增报告 `details.version="2"`；不改动依赖版本。

| 你要了解什么 | 文档 |
|---|---|
| Version 2 开发了啥，哪些没做 | [版本变更](changes.md) |
| 功能、模块和报告字段如何理解 | [功能说明](overview.md) |
| 调用链、安全边界和取舍 | [设计文档](design.md) |
| Windows / IDEA 配置、启动、请求 | [使用文档](usage.md) |
| 当前验证记录及后续验收方法 | [验证与验收](validation.md) |

本版本以 [已确认业务事实](../17-meituan-sale-confirmed-facts.md) 和 [第一阶段实现](../18-meituan-sales-implementation.md) 为基础。早期路线图是长期目标，不能据此宣称已经支持五类订单、自动补推或分布式恢复。

交付状态：代码已实现，业务联调待完成。按用户既定要求未新增/执行测试、未启动应用、未请求真实 DeepSeek/美团/思迅。完整 Maven 编译受当前环境 Maven Central 域名解析失败阻塞；不能宣称构建成功。详见验证记录。
