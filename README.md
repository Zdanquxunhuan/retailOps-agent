# RetailOps Agent

当前实现：美团销售订单**只读诊断**，使用真实 DeepSeek、真实本地美团 SDK、真实思迅 SQL Server 查询适配器。无 Mock 数据、无补推执行接口。本轮按用户要求不新增或运行测试，手工验证方案见下方文档。

## 已实现链路

自然语言 → Spring AI 结构化订单识别 → 美团 SDK 详情查询 → 返回门店与用户权限校验 → 配置的数据源路由 → 固定 SQL 查询门店映射及两类流水 → 确定性报告 → 按用户隔离的本地任务记录。

用户只需提供订单号，不需先给门店号。凭证由后端配置。两类流水均存在表示满足当前写入存在性核验条件；只存在一类时提示排查；两类都为空也不自动补推。过账状态始终标为 UNKNOWN，806 不被解释为历史订单肯定不存在。

## 开始使用

1. 阅读 [本地配置与验证说明](docs/18-meituan-sales-implementation.md)。
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

- Spring AI 当前用于意图/结构化输出；查询顺序由 Java 控制，尚未实现自主 Tool Calling、日志搜索、RAG 或完整故障根因诊断。
- 当前结果是“平台详情 + 思迅流水状态诊断”，不能用无日志证据的报告断言数据库故障或美团漏推。
- SDK 隔离为依赖隔离，不是恶意代码沙箱；仅使用可信的用户提供 SDK。旧 SDK 内部行为还需用户实测。
- 单实例本地文件保存任务结果，不是分布式 Workflow 引擎。中断任务可能保留 RUNNING，需人工识别和重新提交只读诊断；不自动续跑。
- 未提供生产补推 API，不运行旧项目中的临时 test，不写 BI/思迅、不自动执行审批动作。
- 两表查询假定所配置数据库内包含门店映射表与流水表。如实际跨库，需要先调整只读适配器配置/契约，不能盲目扩大数据库权限。

完整设计及已核验业务事实见 [文档入口](docs/README.md)。早期设计目标不代表当前已实现能力。
