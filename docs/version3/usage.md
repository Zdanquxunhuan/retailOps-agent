# Version 3 配置与使用

## 1. 先准备现有基础配置

沿用 [V2使用说明](../version2/usage.md) 配置美团SDK、DeepSeek、用户密码哈希、用户门店范围和思迅只读数据源。思迅账号只授予所需映射/流水表的SELECT；查询不会写思迅，但应用层 `setReadOnly(true)` 不能代替数据库授权。

复制 `config/application-local.example.yml` 为 `config/application-local.yml`，真实配置不提交Git。V3默认启用工具规划，远程日志默认关闭。只有本地完成编译和实际只读联调后，才能确认可运行。

## 2. 由管理员配置远程日志读取

先阅读 [权限与风险](security.md)。物理目录固定为业务日志目录 `/data/service/b-push-center-server/logs`，推荐只读挂载至chroot内 `/logs`。按示例创建专用SFTP只读账号，准备私钥和已独立核对指纹的known_hosts。

在现有 `retailops:` 下合并配置，不要重复创建同名YAML根节点：

```yaml
retailops:
  agent:
    enabled: true
    planner-enabled: true
    remote-logs:
      enabled: true
      host: '${RETAILOPS_LOG_HOST}'
      port: 22
      username: 'retailops_logs'
      private-key: '${RETAILOPS_LOG_PRIVATE_KEY}'
      known-hosts: '${RETAILOPS_LOG_KNOWN_HOSTS}'
      directory: '/logs'
      zone-id: 'Asia/Shanghai'
      charset: 'UTF-8'
      callback-received-marker: ''
```

主机不能由请求或模型指定。若服务器确实用其他时区/编码，应改为实际值。`callback-received-marker` 只能填真实回调接收位置打印的固定文字；目前用户提供了文件名和查找命令，但未提供接收日志标记，故保持空值并报告 `UNKNOWN`，不可用订单号代替接收标记。

Windows PowerShell 会话级环境变量示例：

```powershell
$env:JAVA_HOME = 'F:\ayd\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:RETAILOPS_LOG_HOST = '替换为实际日志服务器主机名或IP'
$env:RETAILOPS_LOG_PRIVATE_KEY = 'F:\retailops-secrets\retailops_logs_key'
$env:RETAILOPS_LOG_KNOWN_HOSTS = 'F:\retailops-secrets\known_hosts'
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dmaven.test.skip=true' package
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\RetailOps-Agent-0.0.1-SNAPSHOT.jar
```

以上启动/调用命令供你本地操作；本次开发未执行启动或业务调用。无需修改全局JAVA_HOME，不影响其他JDK8项目。密钥路径示例须改成实际文件，别将密钥内容贴进对话。

## 3. 发起单订单诊断

```powershell
.\scripts\Invoke-AgentDiagnosis.ps1 `
  -OrderId '500540116926156' `
  -OrderDate '2026-11-08' `
  -OutputFile '.\data\reports\agent-order-20261108.json'
```

脚本会交互输入操作员密码，默认只连接本机服务。远程BaseUrl必须HTTPS，不跟随重定向；输出文件已有时拒绝覆盖。此脚本会发起真实只读美团/思迅/SFTP请求并消耗模型用量，不是自动化测试。

对应HTTP请求：

```http
POST /api/v1/diagnoses/agent/meituan-sales
Content-Type: application/json
Authorization: Basic <操作员认证值>
```

```json
{
  "message": "帮我检查美团销售订单500540116926156为什么没同步到思迅",
  "orderDate": "2026-11-08"
}
```

使用字符串订单号；完整日期放在字段里。只在message里写日期不能代替 `orderDate`。缺少字段返回 `NEED_INPUT/ORDER_DATE_REQUIRED`，不请求模型、SDK、数据库或SFTP。格式错误日期返回400，未来日期/2000年以前日期返回NEED_INPUT。

## 4. 查哪些日志

对该例按下列优先级扫描：

1. `b-push-center-server.log.2026-11-08.*.gz`
2. `b-push-center-server.log.2026-11-07.*.gz`
3. `b-push-center-server.log.2026-11-09.*.gz`

实际归档后缀支持字母、数字、下划线和短横线，不接受路径或任意通配符输入。如果实际后缀使用额外句点，需基于实际轮转规则调整匹配器。扫描不会包含活动的 `b-push-center-server.log`，当天尚未归档时会存在覆盖缺口。

实现保留精确订单匹配和后200行检查窗口的排查意图，但不在服务器上执行以下人工命令：

```bash
zcat b-push-center-server.log.2026-11-08.*.gz | grep -A 200 '500540116926156'
```

邻近行可能是其他订单。返回值只有经过身份规则筛选的证据ID、类别、事件时间、行号和哈希；不会展示整段200行，也不会交给DeepSeek。

## 5. 查看结果与任务

优先阅读 `report.summary`、`report.outcome`、`remoteLogs.callbackReceipt`、`remoteLogs.search.limitations` 和 `repairPlan.blockers`。查看 `steps[].decisionSource` 区分 `JAVA_IDENTITY_GATE`、`DEEPSEEK_TOOL_CALL` 与 `RULE_FALLBACK`。

任务读取：`GET /api/v1/diagnoses/agent/{taskId}`，使用同一操作员认证。重启前未完成任务显示INTERRUPTED，不自动续跑；如需再次诊断，重新POST会产生新任务。旧V2任务仍通过旧GET地址读取。

## 6. 常见结果

- 806/超窗且不能取得可信门店：停止，不因为日期明确就允许搜索任意门店日志。
- SFTP主机指纹不匹配：UNAVAILABLE并带安全错误码，管理员核验后更新known_hosts。
- 远程日志关闭：DISABLED，流水查询和SOP仍可完成。
- 只出现订单号：提供线索但接收状态仍UNKNOWN。
- 压缩文件损坏、大小超限、日期无归档：PARTIAL/UNAVAILABLE，查看limitations。
- 模型未按单工具约束返回：记录拒绝码，剩余步骤由规则流程完成。
- 任意一类流水存在：不得把旧业务的 `ok` 当完整成功，不自动整单补推。

可将 `retailops.agent.planner-enabled=false` 关闭V3规划调用，使用规则顺序；现有初始意图识别仍需要DeepSeek。`retailops.agent.enabled=false` 关闭V3新诊断入口，V2入口及历史读取保留。
