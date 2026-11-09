# Version 2 使用文档

适用环境：Windows 10、项目专用 JDK 17、Maven Wrapper。保持全局 JDK 8 不变。V2 没有新增外部服务依赖，仍需要第一阶段配置的真实 DeepSeek、美团 SDK 与思迅只读账号。

## 1. 获取代码与配置

先切换到包含 Version 2 的开发分支，或在合并后更新 master。已有工作区请先确认 `git status`，不要覆盖未提交修改。

已有 `config/application-local.yml` 时保留原文件，手动合并下方 diagnostics 配置。首次使用按[第一阶段使用说明](../18-meituan-sales-implementation.md)准备本地配置、SDK环境文件、凭证和 BCrypt 操作员密码哈希；不要把真实文件提交到 Git。

V2 的基础默认值已在 `application.properties` 中，所以旧配置无需增加字段即可继续加载：日志关闭，SOP 开启，二次 DeepSeek 解释关闭。新功能不替代原有 DeepSeek 意图识别，`DEEPSEEK_API_KEY` 仍必需。

## 2. 只为当前终端设置 JDK 17

在项目根目录 PowerShell 中执行：

```powershell
$env:JAVA_HOME = 'F:\ayd\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dmaven.test.skip=true' package
```

编译成功后才启动：

```powershell
& "$env:JAVA_HOME\bin\java.exe" -jar .\target\RetailOps-Agent-0.0.1-SNAPSHOT.jar
```

命令跳过测试编译和执行。应用启动会校验真实配置、初始化 SDK/适配器；请在你授权的环境中操作。当前交付环境未完成完整 Maven 编译，因此本地 `package` 是使用前必做的确认步骤，不能直接假设新版本已构建通过。

IDEA 中：Project SDK 和模块 SDK 设为 JDK 17，Maven Runner JRE 也选择项目 JDK 17；运行配置的 JRE 选17、工作目录选项目根目录，便于读取 `./config` 和 `./data`。这些是项目设置，不需要改全局 JAVA_HOME。

## 3. 按需启用日志证据

在原 `retailops:` 下合并以下节点，不要新建第二个 retailops 根键：

```yaml
retailops:
  # 保留已有 meituan、users、pos-routes 等节点
  diagnostics:
    logs:
      enabled: true
      directory: 'F:/retailops-readonly-logs/meituan'
      file-glob: '*.log'
      charset: 'UTF-8'
      max-files: 20
      max-bytes: 20971520
      max-line-bytes: 16384
      max-matches: 20
      timeout-millis: 3000
    knowledge:
      enabled: true
      location: 'classpath:knowledge/meituan-sales.json'
      max-entries: 100
      top-k: 3
    explanation-enabled: false
```

日志来源由管理员准备为本地只读副本或只读挂载。程序不提供远程采集、SSH、下载、解压或日志改写。不要为了制造诊断场景修改生产数据；只读取现有且获准访问的文件。原日志若是 GBK 等编码，按实际编码设置 charset，不能靠乱码内容推断异常。

### 当前支持的行格式

同一完整日志行必须同时含有明确的两个键值：

| 身份 | 可识别字段名 | 约束 |
|---|---|---|
| 订单 | orderId、order_id、mailOrderId、mail_orderid | 完整字符串等于请求订单号 |
| 美团门店 | app_poi_code、appPoiCode | 完整字符串等于已授权详情的门店号 |

键值可使用 `=` 或 `:`，支持常见 JSON 的单双引号。只接受一行内识别到的所有订单值/所有门店值分别与当前身份一致；多个不同订单或门店的聚合行会被排除。日志行必须以 LF 结束，Windows CRLF 可读取。

不支持仅含订单号而没有门店字段的行、只有 `branch_no` 的行、跨行 JSON/堆栈以及 `.gz` 文件。本版不会猜测这些行属于哪个门店。真实日志格式尚未提供完整核验，所以启用后可能 NO_MATCH；这表示格式/扫描范围内未关联到证据，不代表业务没有接收订单。

目录包含轮转文件时，可按实际文件名调整 glob，但必须是未压缩的文本文件。不建议直接指向巨大活跃日志目录；优先提供有界的只读诊断副本。无论结果如何，系统都不宣称覆盖完整30天。

## 4. 按需启用 DeepSeek 二次解释

审核 SOP 内容和对外模型数据范围后，将：

```yaml
retailops:
  diagnostics:
    explanation-enabled: true
```

合并进同一配置树。启用后，成功取得门店授权且检索到知识时，会额外进行一次应用层模型调用。仅发送 outcome、日志状态/类别和候选 SOP，不发送订单号、门店号、原始日志；原先的意图提取仍会发送用户输入。

不启用时也能查看所有检索出的 SOP，`details.explanation.status=DISABLED`。模型失败时为 RULE_FALLBACK，不会丢失已查到的流水事实。

## 5. 发起单订单诊断

在另一个 PowerShell 终端、项目根目录执行。输入你自己已获授权的真实美团销售订单号：

```powershell
$orderId = Read-Host '请输入美团销售订单号'
.\scripts\Invoke-Diagnosis.ps1 -OrderId $orderId
```

脚本提示输入本地操作员密码，调用现有真实诊断 API，显示 JSON。它不是测试脚本，也不会补推；会产生真实模型调用和只读查询。

需要保存报告时：

```powershell
$reportPath = '.\data\reports\diagnosis-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.json'
.\scripts\Invoke-Diagnosis.ps1 -OrderId $orderId -OutputFile $reportPath
```

脚本拒绝覆盖现有文件。默认使用本机 `http://127.0.0.1:8080`；跨主机 `-BaseUrl` 必须 HTTPS，禁止在 URL 中放用户名、密码、query 或 fragment，且不跟随重定向。客户端180秒超时不取消后端已运行的只读任务；遇到超时先核实任务状态，不把它当作业务未执行。

### 接口直接调用

请求格式保持不变：

```json
{"message":"帮我诊断美团销售订单 <你的真实订单号>"}
```

上面的 `<你的真实订单号>` 是输入占位说明，不是可直接执行的测试订单。路径仍为 `POST /api/v1/diagnoses/meituan-sales`，认证为 HTTP Basic，`Content-Type: application/json`。

查询已返回 taskId 的任务：

```powershell
$taskId = Read-Host '输入上一份报告的taskId'
$credential = Get-Credential -UserName 'operator' -Message '输入本地运维账号密码'
try {
    $pair = '{0}:{1}' -f $credential.UserName, $credential.GetNetworkCredential().Password
    $headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($pair)) }
    Invoke-RestMethod -Uri "http://127.0.0.1:8080/api/v1/diagnoses/$taskId" -Headers $headers |
        ConvertTo-Json -Depth 20
} finally {
    Remove-Variable pair,headers,credential -ErrorAction SilentlyContinue
}
```

GET 返回外层 StoredTask，其中 `report` 才是报告；POST 直接返回报告。不要把两种响应层级混淆。

## 6. 如何解读

先看 outcome/summary，再看 evidence 中 E3 的两类 count，然后看 details.logs 的 status 和 limitations，最后看 knowledge/explanation。`details=null` 说明未进入获授权的 V2 增强阶段，或读取的是旧任务。

例如，若真实查询结果是 PARTIAL_FLOWS_PRESENT，同时关联日志行出现数据库异常标记，系统会保留“仅一类流水存在”的结论，K05提示核对异常因果，K02解释旧防重规则和查询时序。它不会将关键词自动升级成“数据库异常已确认为根因”，不会补写缺失流水。

所有结果都应保持 `postingStatus=UNKNOWN`、`replayExecuted=false`。完整字段说明见[功能说明](overview.md)。

## 7. 自定义知识与回滚

将 `src/main/resources/knowledge/meituan-sales.json` 复制至受控的本地路径，配置 `knowledge.location` 为例如 `file:./data/knowledge/meituan-sales.json`。目录数组的每条都必须包含源码中的所有字段；不要改成包装对象。

通用知识：`shared=true`、`stores=[]`；门店私有知识：`shared=false`、stores 填实际 app_poi_code，禁止 `*`。outcomes 必须使用 DiagnosisReport.Outcome 枚举值；logCategories 可为空，或使用上文的三种类别。explanation 最多800字符，nextCheck最多500字符，整个文件不超过1MiB。新增ID不可重复，内容变更同步提升 version。

知识经管理员审核后重启生效。含已撤回、权限变化或内容变化引用的历史任务将返回403；这不是任务丢失，请重新诊断。不要把客户信息、密钥或原始日志放进 SOP，尤其是 shared 条目和启用模型解释的目录。

只关闭二次模型调用：`explanation-enabled=false`。只关闭日志：`logs.enabled=false`。关闭新知识：`knowledge.enabled=false`，注意已含知识引用的历史 V2 报告会被引用复核拒绝。若回滚整个应用至 V1，保留 V2 数据目录备份、改用独立任务目录；V1对新增字段的读取兼容未验证，不要假设一定可直接读取V2文件。

## 常见问题

| 现象 | 排查 |
|---|---|
| Maven下载失败 | 查看企业代理/仓库连通性；本次交付环境即被 Maven Central DNS 阻塞，不是业务测试失败 |
| 启动提示知识目录错误 | 检查location、文件上限、JSON数组、唯一ID、枚举、shared/stores关系 |
| 日志NO_MATCH | 先查原文件是否同行含明确order/store、编码与glob；不能据此认定漏单 |
| 日志PARTIAL | 查看limitations，按需准备更小的获准日志集合，不盲目提高全部限额 |
| 解释RULE_FALLBACK | 二次模型失败或输出不合法；现有规则说明仍可用 |
| GET历史报告403 | 核对任务所有者、当前门店权限、知识引用是否撤回/更新/禁用 |
| 两表都有却仍UNKNOWN过账 | 这是设计行为；两表在过账后保留，没有独立过账证据 |
