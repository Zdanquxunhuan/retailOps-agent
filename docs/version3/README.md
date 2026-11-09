# Version 3 — 受控诊断 Agent 与远程压缩日志取证

基于 master 的 V2 实现，增加 Spring AI + DeepSeek 原生工具调用、有界诊断循环、任务上下文、逐步检查点、远程 SFTP gzip 读取和不可执行的处理计划。

交付状态：代码与文档已实现；Java 17 语法解析通过。完整 Maven 构建受 Maven Central DNS 解析失败阻塞，尚未完成类型/依赖编译与真实联调。未新增或执行测试、未启动应用、未调用模型或业务服务，没有使用 Mock。

- [开发了什么](changes.md)
- [功能与结果说明](overview.md)
- [设计与方法调用链](design.md)
- [配置与使用](usage.md)
- [远程服务器权限与风险](security.md)
- [验证记录及手工验收](validation.md)

先阅读 usage 与 security，再由管理员配置远程日志账号。默认远程日志关闭；没有主机、密钥、known_hosts 时不会尝试连接。

V2 API 保持可用；V3 API 为 `POST /api/v1/diagnoses/agent/meituan-sales`，增加显式 `orderDate`。日志日期由用户提供，不由模型从“昨天”“11月8日”等文本猜测。
