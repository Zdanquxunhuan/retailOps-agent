# 09 MCP 采用决策

MVP 不依赖 MCP。普通 Java Tool 是本进程的能力封装；HTTP Adapter 是企业系统集成边界；MCP 解决工具描述、发现及调用协议的互操作问题，不提供业务幂等、审批或门店隔离的自动保证。

## 触发条件与范围

当第二种客户端需要共享日志/订单/SOP 查询能力，或跨团队工具协议维护成本显著增加时，增设只读 MCP Server。候选能力是 LogSearch、LocalOrderQuery、POSStatusQuery、KnowledgeSearch。生产补推仍走后端审批命令，首个 MCP 试点不暴露副作用操作。

适配路线：MCP Client → MCP Server 的身份和 Schema 校验 → 同一个 ToolGateway → HTTP Adapter → 原业务系统。不要为 MCP 重写业务逻辑，也不把老项目升级到新 JDK。工具名称/描述/输入输出 Schema 均版本化并经过代码评审，远端工具描述视为不可信内容，禁止自动信任未知服务器。

## 身份与边界

远程访问采用内网 TLS、固定服务发现白名单，使用具备 audience 和 scope 的短期凭证；校验 issuer、audience、过期时间与用户委托范围。不能透传任意客户端 Token 到下游，不能让服务器凭借广泛服务账号绕过用户门店范围。用户权限和服务能力取交集。

HTTP MCP 授权协议有独立规范，传输层授权不能代替每个工具的业务鉴权。实现时固定采用的协议/SDK 版本并做兼容验证，不假定所有客户端支持同一机制。[MCP 官方授权规范](https://modelcontextprotocol.io/specification/2026-07-28/basic/authorization)

MVP 本机演示可不启动 MCP；若后续使用 STDIO，仅用于开发环境本地工具，不因协议提供进程能力就开放任意命令执行。生产只读 MCP 也应有速率、响应长度、超时、审计及网络出口限制。

## 测试和停用策略

验证合法工具调用、无 Token、错误 audience、过期 Token、跨门店、未知工具、非法参数、恶意描述、工具返回 Prompt Injection、服务断开与重连。与直接 HTTP/Java Tool 调用比较输出契约及授权结果，必须一致。协议不可用时可以关闭 MCP，不影响平台内部 Workflow。
