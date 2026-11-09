# 18 美团销售只读诊断：实现、配置和本地验证思路

当前交付为真实集成代码的第一阶段，不包含 Mock；按用户指示，不新增/执行测试，不启动应用，不调用美团、DeepSeek 或思迅。仅进行静态代码检查与跳过测试的编译打包。

构建记录：使用 `F:\ayd\jdk-17` 执行 Maven `-Dmaven.test.skip=true package`，20 个 Java 源文件编译并打包成功，测试源码未编译、测试未执行。产物为 `target/RetailOps-Agent-0.0.1-SNAPSHOT.jar`。这不代表真实 SDK 请求、数据库连接或端到端流程已实测通过。

## 代码入口

| 位置 | 实现 |
|---|---|
| `api/DiagnosisController` | POST 创建诊断、GET 查看自己的任务 |
| `agent/OrderIntentExtractor` | Spring AI/DeepSeek 结构化提取；订单号必须原样来自输入；缺号、多号及不支持场景不发业务请求 |
| `workflow/DiagnosisWorkflow` | 有界并发、阶段顺序、失败分支和确定性结果 |
| `integration/meituan/MeituanSdkOrderAdapter` | 从本地真实 SDK 加载请求类，仅调用订单详情接口 |
| `integration/meituan/MeituanResponseParser` | 成功对象、5天窗口、806、业务失败及响应异常的独立处理 |
| `integration/pos/JdbcPosFlowAdapter` | 门店路由、门店映射、两表计数，固定参数化 SQL |
| `security` | HTTP Basic、BCrypt、用户与 app_poi_code 权限 |
| `workflow/DiagnosisStore` | 本地 JSON 任务记录，临时文件+原子替换，禁止跨用户读取 |

包路径均在 `src/main/java/com/yu/retailops` 下。对外报告只保留必要字段，不保存原始聊天、客户姓名地址、SDK 签名或完整订单响应。

## 版本及 SDK 隔离

保持 Boot 4.1.1 / Spring AI 2.0.2 / Java 17。SDK 不作为 Maven 主运行类路径依赖，通过专用 URLClassLoader 加载指定 JAR 与 SDK 配置目录，父加载器为 Java 平台加载器。避免 JAR 内附带的 Fastjson、HTTP 客户端、MapStruct 编译处理器和 ServiceLoader 文件干扰新应用。

这个边界只传 String 和由新应用解析的最小 DTO，不向业务层暴露 SDK 类型。请求使用 `new SystemParam(appId, appSecret)` 的语义；不要求用户提供门店才能查询详情。当前固定签名模式，不开启 Token 自动刷新分支。关闭应用时释放类加载器；这不等于已经验证旧 SDK 内部线程/连接池的全部生命周期。

SDK 配置模板中的 `env=1` 和固定 HTTPS 地址来自对本地 URLFactory 字节码的检查，`isSig=1` 为签名模式；超时来自 PropertiesUtil 的配置键。SDK 初始化和网络兼容仍由本地实测确认。

## 配置步骤

在项目根目录运行以下文件复制命令；它们仅准备配置，不启动服务：

```powershell
if (Test-Path -LiteralPath .\config\application-local.yml) { throw '本地配置已存在，请手动合并，不要覆盖。' }
Copy-Item -LiteralPath .\config\application-local.example.yml -Destination .\config\application-local.yml
New-Item -ItemType Directory -Path .\config\meituan-sdk -Force
if (Test-Path -LiteralPath .\config\meituan-sdk\environment.properties) { throw 'SDK配置已存在，请手动合并。' }
Copy-Item -LiteralPath .\config\meituan-sdk.example\environment.properties -Destination .\config\meituan-sdk\environment.properties
```

如果目标配置已存在，不覆盖，应手动合并。编辑 application-local.yml 的用户、stores 和 pos-routes：

- `users.operator.stores`：允许查询的实际美团 `app_poi_code`；没有 `*` 全门店默认授权。
- `pos-routes` 的键：同一个实际 `app_poi_code`。值为已核实的思迅数据库连接、只读账号和 schema。
- JDBC URL 示例结构：`jdbc:sqlserver://数据库主机:1433;databaseName=目标数据库;encrypt=true;trustServerCertificate=false`。请使用受信任证书，不为了连通直接关闭验证。
- 当前在该数据库的指定 schema 内查询 `t_bd_mtbranch_info`，按 `mt_storeid` 获取唯一非空 `siss_storeid`；随后按 `mail_orderid` 和 `branch_no` 查两表。用户原 SQL 存在显式 `isszmv9.dbo` 与默认库差异，必须确认该路由数据库中三表位置正确。
- `schema` 仅允许简单 SQL 标识符。客户端、模型无法指定数据库、schema、SQL 或 SDK 地址。

运行环境需要这些变量：

| 环境变量 | 用途 |
|---|---|
| DEEPSEEK_API_KEY | 模型调用凭证 |
| DEEPSEEK_MODEL | 可选；默认 deepseek-chat，按账号支持情况设置 |
| MEITUAN_APP_ID / MEITUAN_APP_SECRET | 现有应用签名凭证 |
| POS_JDBC_URL | 思迅 SQL Server 路由地址 |
| POS_READ_USERNAME / POS_READ_PASSWORD | 只读数据库账号 |
| RETAILOPS_OPERATOR_PASSWORD_HASH | `{bcrypt}` 前缀的 BCrypt 哈希，不能填明文 |

本地操作员哈希可以在交互式 PowerShell 中运行 `./scripts/New-PasswordHash.ps1` 生成。该脚本编译并解析类路径后运行独立密码工具，不启动 Spring、不连接任何业务服务、不执行测试。输入不回显，输出哈希填到环境变量。请通过本机安全方式设置真实变量，勿将它们粘贴回聊天或提交到 Git。

配置和 SDK 文件由管理员维护。数据目录初始为 `./data/diagnoses`，需要应用账号读写权限且不对其他普通账号开放。文件中含内部订单号与门店范围，应按企业策略保留和清理。

## 接口用法（由用户自行实测）

仅在配置完成并启动服务后执行。调用会产生真实模型费用、美团订单详情查询及数据库 SELECT；不会补推。

PowerShell 请求示例，订单号由你填写，不内置任何真实或 Mock 订单：

```powershell
$credential = Get-Credential -UserName 'operator' -Message '输入本地运维账号密码'
$pair = '{0}:{1}' -f $credential.UserName, $credential.GetNetworkCredential().Password
$headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair)) }
$orderId = Read-Host '输入要诊断的美团销售订单号'
$body = @{ message = "帮我查美团销售订单 $orderId 为什么没有同步到思迅" } | ConvertTo-Json
$report = Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/v1/diagnoses/meituan-sales' -Method Post -Headers $headers -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
$report | ConvertTo-Json -Depth 10
Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/v1/diagnoses/$($report.taskId)" -Headers $headers
Remove-Variable pair,headers,credential
```

本地示例使用 loopback HTTP；跨机器部署需 TLS 反向代理，不能在公网裸露 Basic 认证。真实请求只发送用户输入给 DeepSeek，完整订单响应不发送给模型。

所有诊断报告 `replayExecuted=false`、`postingStatus=UNKNOWN`。已有流水不证明过账；只有一表存在时只报告不完整；两表为空不证明回调丢失或允许重推。记录返回 taskId、来源、观测时间和计数，但不是完整不可篡改审计平台。

## 本地验证思路（不包含自动测试代码）

| 场景 | 你本地观察的预期 | 前提/注意 |
|---|---|---|
| 缺订单号、多个订单号 | NEED_INPUT，不调用美团/思迅 | 检查输入边界 |
| 京东、退货或税率请求 | UNSUPPORTED_SCENARIO | 初版不跨业务执行 |
| 模型失败或结构化输出不匹配 | MODEL_UNAVAILABLE 或 NEED_INPUT | 不继续业务查询 |
| 近5天合法订单，两表都有 | BOTH_FLOWS_PRESENT，两项 count>0 | 使用已知订单和只读账号核对 |
| 只有一张表有记录 | PARTIAL_FLOWS_PRESENT | 只选已有样例，不人为修改生产数据制造异常 |
| 两表都为空 | NO_FLOWS_FOUND，仍不自动补推 | 不据此认定原因 |
| 5–14天接口返回窗口错误 | OUTSIDE_QUERY_WINDOW | 不自动重试 |
| 超过14天返回806 | ORDER_UNCONFIRMED | 不断言从未存在，不用报错推断精确订单年龄 |
| SDK异常、空响应、错误data结构 | 明确查询失败，禁止后续解析和POS查询 | 不把错误当零条流水 |
| 返回非本人授权门店 | HTTP 403，不返回订单详情、不查询思迅 | 禁止记录未授权订单事实 |
| 门店路由/映射缺失或不唯一 | POS_QUERY_FAILED | 不尝试猜库或扩大范围 |
| POS连接/SELECT失败 | POS_QUERY_FAILED，计数不是0 | 请勿中断生产库制造故障 |
| 已过账且两表仍有记录 | 仍只确认写入，过账UNKNOWN | 符合用户已确认规则 |
| 另一用户读取taskId | HTTP 403 | 当前任务所有权隔离 |
| 并发超过配置容量 | HTTP 429 | 不排无限请求队列 |
| 应用被中断后重启 | 旧文件可读；RUNNING需人工核实 | 当前无自动恢复或补推 |

以上是验证设计，不代表这些用例已经运行或通过。当前工程原有 contextLoads 测试保持未修改，但真实配置下不应随意运行；本轮构建使用 `-Dmaven.test.skip=true`。

## 待后续接入的能力

日志读取（已知单实例、保留30天）、本地备份查询、RAG、自主工具选择、数据库持久化任务、SSO、完整审计与审批补推均尚未完成。用户已经提供实际推送链路，但正式补推 API 与幂等/事务保障还需实现和确认。不能把本轮只读代码描述为可直接上线的完整受控自愈平台。
