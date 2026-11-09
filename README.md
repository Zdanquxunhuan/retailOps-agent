# RetailOps Agent

当前版本：**Version 2 — 美团销售只读证据增强**。在真实 DeepSeek 意图识别、美团 SDK 和思迅 SQL Server 只读诊断基础上，增加受限日志扫描、带版本引用的本地 SOP 检索和可选 DeepSeek 解释选择。无 Mock 数据、无补推执行接口。

[Version 2 文档入口](docs/version2/README.md) · [开发了什么](docs/version2/changes.md) · [功能说明](docs/version2/overview.md) · [设计文档](docs/version2/design.md) · [怎么使用](docs/version2/usage.md)

交付状态：代码已实现，完整构建因当前环境无法解析 Maven Central 而受阻，业务联调待完成。按既定要求未新增/执行测试、未启动应用或请求真实业务服务。[验证记录](docs/version2/validation.md)区分静态检查与尚未完成的验证。

## 已实现链路

自然语言 → Spring AI 结构化订单识别 → 美团 SDK 详情查询 → 门店授权 → 思迅固定 SQL 查询 → 确定性报告 → 可选本地日志取证 → SOP 权限过滤与检索 → 可选 DeepSeek 选择解释 → 引用复核 → 按用户隔离的本地任务记录。

用户只需提供订单号，不需先给门店号。凭证由后端配置。两类流水均存在表示满足当前写入存在性核验条件；只存在一类时提示排查；两类都为空也不自动补推。过账状态始终标为 UNKNOWN，806 不被解释为历史订单肯定不存在。

## 开始使用

1. 阅读 [Version 2 使用文档](docs/version2/usage.md)；首次接入同时阅读 [基础配置说明](docs/18-meituan-sales-implementation.md)。
2. 将 `config/application-local.example.yml` 复制为 `config/application-local.yml`，配置用户授权的 `app_poi_code`、SDK 路径和对应思迅数据库。
3. 将 `config/meituan-sdk.example/environment.properties` 复制至 `config/meituan-sdk/environment.properties`。
4. 通过环境变量提供美团、DeepSeek、思迅只读账号及操作员 BCrypt 密码哈希，不提交真实配置。
5. 编译和启动：

```powershell
$env:JAVA_HOME = 'F:\ayd\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dmaven.test.skip=true' package
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\RetailOps-Agent-0.0.1-SNAPSHOT.jar
```

启动会校验配置，缺少密钥、用户授权或 SDK 文件时不会自动回退到演示数据。应用默认仅监听 127.0.0.1，远程部署需使用 TLS 入口；思迅账号必须是只读账号。

## 当前边界

- Spring AI 用于意图识别及可选解释 ID 选择；查询顺序由 Java 控制，尚未实现自主 Tool Calling 或完整根因诊断。当前知识检索基于 outcome/日志标记，不是向量 RAG。
- 日志只读取管理员配置的本地文本文件，严格要求同行订单和美团门店身份；不远程采集、不提供完整30天覆盖证明，异常关键词不等于根因。日志和二次模型解释默认关闭。
- 当前结果是“平台详情 + 思迅流水状态诊断”，不能用无日志证据的报告断言数据库故障或美团漏推。
- SDK 隔离为依赖隔离，不是恶意代码沙箱；仅使用可信的用户提供 SDK。旧 SDK 内部行为还需用户实测。
- 单实例本地文件保存任务结果，不是分布式 Workflow 引擎。中断任务可能保留 RUNNING，需人工识别和重新提交只读诊断；不自动续跑。
- 未提供生产补推 API，不运行旧项目中的临时 test，不写 BI/思迅、不自动执行审批动作。
- 两表查询假定所配置数据库内包含门店映射表与流水表。如实际跨库，需要先调整只读适配器配置/契约，不能盲目扩大数据库权限。

完整设计及已核验业务事实见 [文档入口](docs/README.md)。早期设计目标不代表当前已实现能力。
