# 17 美团销售：已确认业务事实与接入边界

更新日期：2026-11-09。依据为用户提供的代码提取文档、主动补推代码及对业务状态的直接确认。本文对早期方案中的假设作补充修正；不代表已经完成数据库或接口联调。

## 当前部署

美团项目为一台服务器、一个实例；日志保存在服务器，保留 30 天；没有测试环境。用户后续明确要求直接开发真实接入代码，不使用 Mock、不新增或执行测试，由用户自行本地实测。开发过程不启动应用、不连接生产执行补推，测试思路写入文档。通用架构保留多实例扩展能力，但当前美团接入按单实例处理。

## 优先研究主动补推链路

```text
getOrderIds()
  → getOrderMessage(orderId)
  → 解析 data 为 MeituanToTRm
  → pushToBi(meituan)
  → saveDataService.saveData(meituan)
  → meituanService.pushFinishedOrder(meituan)
```

用户指定当前重点是这条主动查询美团订单再推送思迅的链路。回调文档用于了解共用的服务和写库实现；不能把回调 Controller 的取消检查、签名和锁视为主动补推链路已有的保障。

从已提供的 Mapper/执行器代码可确认，相关思迅写入通过数据库执行，并非已证实存在一个 POS HTTP 推送或查询接口。销售服务还包含部分退款处理，其副作用范围需进一步核验。

## 用户已确认的结果判断

- 正常推送完成后，销售流水表 `t_rm_saleflow_frommail` 和支付流水表 `t_rm_payflow_frommail` 都有该订单记录。
- 门店过账后，两张表的记录仍保留。
- 当前通过各自 Service 按 `mailOrderId = orderId` 查询 count，判断是否已写入思迅。

现有防重条件为 `saleCount > 0 || payCount > 0`：任意表有记录就返回 `ok` 并跳过写入。这是既有代码行为，不是“两表都写入成功”的等价表达。

| saleCount | payCount | 诊断应报告的事实 | 后续处理边界 |
|---|---|---|---|
| 0 | 0 | 本次查询未发现两类流水 | 不能仅凭空结果自动补推；先核实数据源、订单身份、进行中任务及其余预检条件 |
| >0 | 0 | 销售流水存在，支付流水未查到；未满足正常完成条件 | 标记两表状态不完整，核查是否仍在处理、查询时序及异常记录；不自动重推或补写 |
| 0 | >0 | 支付流水存在，销售流水未查到；未满足正常完成条件 | 同上；不能把现有 `ok` 当作完整成功 |
| >0 | >0 | 两类流水均存在，满足当前业务约定的写入存在性核验 | 避免重复推送；不据此宣称全部明细/金额正确或已经过账 |

查询失败或数据源未确定时返回 UNKNOWN，不转成 count=0。两次 count 可能观察到不同时间的状态，只有一表有记录时需进一步核查，不能立即断言发生永久性部分写入。

**过账后记录保留意味着两表存在性不能区分已过账与未过账。** 尚无其他过账状态来源时，报告应为：“销售流水和支付流水均已写入思迅；过账状态暂未核实。”不能自动报告“门店尚未过账”。

## 对早期设计与示例的修正

早期文档中的 POS 查询工具是目标能力抽象，当前应优先评估通过受限、固定查询能力封装两表状态；不假设已经有 HTTP 状态接口，也不开放模型生成 SQL。适配器必须在正确的门店数据源中查询。

文档 16 的多实例、POS 接收回执、业务状态和明确未过账等内容是标明前提的假设演示，不是当前系统事实。当前可确认的结果是两表写入存在性；完整明细、事务一致性、并发幂等及过账识别能力尚未验证。

## 主动补推入口：用户后续确认

1. `getOrderIds()` 是人工把待补推单号写入 `Arrays.asList(...)`，不是扫描失败订单或数据库任务队列。Agent 可从用户自然语言提取订单号，替代这一步；初版仍限定单订单，提取成功不等于获得写操作授权。
2. `getOrderMessage(orderId)` 使用美团 SDK：创建 `OrderGetOrderDetailRequest(systemParam)`，设置字符串 `order_id`，执行 `doRequest()`，取得 `SgOpenResponse.getRequestResult()`。当前片段还会记录 `getRequestSig()` 供联系美团排查。
3. `pushUnfinishedOrder()` 写在临时 test 中，当前未提供正式补推 API 或调度任务入口。不能把运行这个 test 当作单元测试或安全演示，它会沿真实业务链路产生写入。

后续设计入口为：自然语言 → 订单号字符串提取及用户范围核验 → 受控订单详情查询 → 业务状态与门店核验 → 诊断/补推预检 → 审批后的正式业务调用边界。原测试方法仅作为现有行为参考，不由 Agent 直接运行。正式补推入口的建设及鉴权需另行设计，不在本轮修改旧项目。

订单号保持字符串，避免长整型订单号在前端/JSON 数值处理中精度丢失。门店信息可以来自可靠的详情响应，但在日志、POS 查询及写操作之前必须校验对应门店权限；不能仅因为用户知道订单号就允许访问该订单。详情查询本身也需授权边界，不能先向用户披露返回数据再做校验。

### 从当前片段可以确认的待处理点

- `doRequest()` 抛出被捕获的异常时，代码记录日志后继续执行，而 `sgOpenResponse` 仍为 null；随后调用 `getRequestSig()` 会触发空指针异常。未来适配器应保留原始查询失败类型并停止后续解析，不把它当作订单不存在。
- 当前展示的逻辑没有显式校验美团业务响应是否成功、`data` 是否有效以及订单是否符合销售补推条件。不能仅根据详情请求完成或没有匹配到“未获取有效门店id”就判定成功；SDK 是否有内部业务错误处理仍需核验。
- `OrderGetOrderDetailRequest` 是按订单号获取详情的调用。用户后续已提供该详情接口的 5 天/14 天窗口说明，见下节；不再将窗口规则整体列为未知，精确时间边界仍需确认。
- `requestSig` 不交给模型或普通报告；如需排障留存，先确认其敏感性和企业日志策略，并限制访问。

## SDK 与响应：用户后续提供的证据

SDK 由用户从美团官网下载到本地，不要求必须有公开 Maven 坐标。用户提供的失败请求文本中含 `sg_open_sdk-1.0.26-SNAPSHOT` 标识，这只是样本内的请求来源标识；本地实际包版本和内容仍需读取 SDK 文件确认。

官方入口：[SDK 下载](https://tscc.meituan.com/developerCenter/sdkDownload)、[订单详情接口](https://tscc.meituan.com/home/doc/market/134)。本次网页读取仍未取得接口正文；以下窗口规则依据用户明确摘录的接口说明，不声称已独立读取完整网页。

### 查询窗口及 806 错误

| 订单时间范围（按用户提供的接口说明） | 接口行为 | Agent 应如何解释 |
|---|---|---|
| 近 5 天 | 支持订单详情查询 | 仍需检查实际返回结果，不保证任意订单号都成功 |
| 超过近 5 天但未超过近 14 天 | 返回“只能查5天内订单” | 无法通过该详情接口取得订单数据；该分支具体错误码尚未提供 |
| 超过近 14 天 | 返回“不存在此订单” | 不能据此判断历史订单从未存在 |

用户提供的超过 14 天失败样本为：`result_code=3`、`data="ng"`，`error` 与 `error_list` 中均含 `code=806`、`msg="不存在此订单"`。这证明超窗订单可能返回 806，但不能把所有 806 都反向认定为超窗：订单时间未知时，应说明“接口返回不存在；可能受查询窗口影响，尚不能确认订单不存在”。

窗口涉及哪个订单时间字段、平台时区及精确临界点尚未完整核验，不擅自将“近 5 天”解释为固定 120 小时。已知超窗时应停止无效重试；可继续依赖现有本地备份、日志和思迅流水诊断，但能否用本地备份补推需独立预检。

### 成功样本结构

已读取并解析用户的成功响应附件，仅记录字段结构，不复制签名、原始请求或客户信息。该样本顶层含 `data`（对象）、`result_code=1`、`success_map`、`mt_request_id`。

业务数据中存在 `order_id`、`app_poi_code`、`status`、`ctime`、`utime`、`order_completed_time`、`total`、`original_price`；`detail`、`extras`、`sku_benefit_detail`、`poi_receive_detail` 为字符串。具体解码及 DTO 兼容需要结合 SDK 和原解码器核验，不能仅凭字符串类型判断是否需要 URL 解码。

未来解析顺序为：SDK 传输结果 → 顶层业务状态/错误信息 → data 是否为有效对象 → 必要字段与请求订单匹配 → 门店权限及业务资格 → DTO 转换。失败样本的 `data="ng"` 不应继续转换为 `MeituanToTRm`，也不能流入 BI、报文保存和思迅写入步骤。`result_code=1` 是已观察到的成功样本值，其他完整枚举尚未提供。

订单号即使响应为 JSON 数字，也需精确解析并以字符串作为平台内部标识，避免前端浮点精度丢失；金额使用 BigDecimal。采集响应不等于允许把完整报文发送给模型。

## 本地 SDK 静态检查

已只读检查用户提供的目录 `E:\Repositories\Maven\cn\com\baby\shangou-platform-open-sdk\1.0.27.1-SNAPSHOT`，使用 JDK 17 的 jar/javap 查看类和字节码，未执行 SDK 请求或初始化业务服务。

- 本地 POM 坐标为 `cn.com.baby:shangou-platform-open-sdk:1.0.27.1-SNAPSHOT`；目录存在对应 JAR 和时间戳版本 `1.0.27.1-20220808.092540-1`。不能把旧响应里的 `request_source` 字符串直接当作该 Maven 包版本。
- 请求类为 `com.sankuai.meituan.shangou.open.sdk.request.OrderGetOrderDetailRequest`，父类为 `SgOpenRequest`；构造方法指定 GET `/api/v1/order/getOrderDetail`，订单号参数为 String。
- 响应类为 `com.sankuai.meituan.shangou.open.sdk.response.SgOpenResponse`，提供 requestResult、requestSig 和 baseString。查询返回仍需应用校验业务结果。
- `com.sankuai.meituan.shangou.open.sdk.domain.SystemParam` 存在三种构造方式：`(appId, appSecret)`、`(appId, appSecret, appPoiCode)`、`(appId, appSecret, appPoiAccessToken, appPoiCode)`。三参数版本的第三个参数是门店编码，不是 Token。
- 父类存在签名/OAuth 相关分支、门店 Token 拼接及特定 Token 错误重试路径。因此不能根据四个成员字段就断定实际调用必须填满四个字段，也不能认定当前运行配置选用了哪种鉴权模式。
- SystemParam 的 `toString()` 会包含 appSecret；后续日志不得直接打印该对象。SgOpenResponse 的 `toString()` 也包含签名、原响应和 baseString，应采用允许字段输出。

本次仅证明类结构与静态调用方式，不证明 SDK 与 Boot 4 的运行兼容，也未验证真实凭证、超时或网络行为。后续第一阶段已经采用隔离类加载器接入此 SDK 的只读订单查询；原有 BI/报文保存/思迅写入逻辑仍保留在旧业务系统，未迁移或执行。

## 下一批仍需补充

当前已有成功样本、超过 14 天失败样本及详情接口查询窗口说明，无需重复提交。下一步优先提供：

1. SDK 路径已收到且已检查，无需再提供。用户提供的 SystemParam 四字段定义也已核对。
2. 用户进一步确认按订单号即可查询，不需要先提供门店。已停止将 systemParam 初始化代码作为继续开发的前置资料；第一阶段按 appId/appSecret 签名模式实现，凭证由后端配置，门店从成功响应取得并校验权限。

后续补推阶段再按需核验 `pushToBi()`、`saveData()`、动态数据源路由、事务及两表的索引约束，不阻断当前只读实现。5–14 天错误分支的具体错误码、限流和精确时间边界可从 SDK/接口正文继续核验，无需为了补齐样本主动请求生产接口。如需识别过账，再提供实际查询方式。不使用 Mock、不运行现有 test 验证真实补推。



## Version 3：用户补充的远程日志要求

用户确认 `order.detail.query` 使用 `OrderGetOrderDetailRequest`，`pos.flow.query` 连接思迅查询销售/支付两表；日志来源为服务器 `/data/service/b-push-center-server/logs` 下 `b-push-center-server.log.YYYY-MM-DD.*.gz`。用户先提供订单日期，并检索前后各一天；人工参考命令为 `zcat ... | grep -A 200 '订单号'`。

V3实现采用专用只读SFTP获取归档流、Java本地解压及逐行筛选，未开放Shell或执行该管道。数据库SELECT与SSH文件权限独立。没有收到真实主机、密钥、sshd/ACL、回调接收固定标记及多行关联格式；默认关闭远程读取，接收标记未确认时只提供订单线索。详见 [V3权限说明](version3/security.md)。本条是新增需求事实，不表示已连接服务器或完成联调。
