# 功能与结果说明

## 用户能做什么

提交单个美团销售订单号和完整日期，取得平台身份核验、思迅两类流水状态、日期邻近的远程日志线索、适用 SOP、工具轨迹和人工处理建议。模型参与调查顺序选择，不能执行补推、SQL 或任意服务器命令。

| 工具业务名 | 实际调用 | 参数来源 |
|---|---|---|
| `order.detail.query` | `MeituanSdkOrderAdapter.query` → `OrderGetOrderDetailRequest` → `setOrder_id` → `doRequest` → `getRequestResult` → 响应解析 | 经意图提取及确定性格式验证的订单号 |
| `pos.flow.query` | `JdbcPosFlowAdapter.query`，固定参数化 SQL 查询 `t_rm_saleflow_frommail`、`t_rm_payflow_frommail` | 当前订单及已核验的 `app_poi_code`，服务端映射思迅门店 |
| `logs.search` | `RemoteGzipLogReader.search`，SFTP list/stat/read + Java gzip 解压 | 当前订单、可信门店、用户 `orderDate`；主机和目录来自管理员配置 |
| `knowledge.search` | `LocalKnowledgeRetriever.retrieve` | 确定性流水结果及授权日志标记 |

SDK 查询异常立即返回安全失败，不沿用“捕获后继续调用空响应的 `getRequestSig()`”的写法。签名、原始报文和用户数据不返回模型。现有 SDK 隔离加载方式保持不变，仍需本地验证运行兼容性。

## 响应怎么读

| 字段 | 含义 |
|---|---|
| `state` | 调查流程生命周期；`COMPLETED` 不代表业务同步成功或找到根因 |
| `stopReason` | 正常结束、规则降级、预算耗尽、缺少日期或身份信息等 |
| `report.outcome` | Java 根据真实查询结果计算的业务观测状态 |
| `report.postingStatus` | 始终 `UNKNOWN`；两表保留记录无法证明已过账 |
| `remoteLogs.requestedDates` | 请求扫描的三个日期；不是完整扫描证明，须同时看状态、限制与缺失日期 |
| `remoteLogs.search.status` | `NOT_RUN` / `DISABLED` / `NEED_INPUT` / `UNAVAILABLE` / `PARTIAL` / `SCANNED` |
| `remoteLogs.callbackReceipt` | `RECEIPT_MARKER_FOUND` 或 `UNKNOWN`，没有“未收到”结论 |
| `remoteLogs.timeline` | 只含证据 ID、解析出的事件时间、阶段标记、关联级别；时间未知时为 null |
| `steps` | 工具名、决策来源、结果码、开始时间、耗时、证据 ID；不包含模型私有思考过程 |
| `repairPlan` | 仅建议，始终不可执行，包含阻断条件及后续核查 |

`SCANNED` 仅指选定归档在本次限制内读完，不证明30天覆盖、活动日志覆盖或整个业务日志完整。未知、截断、读失败都不会转换成“没有接到订单”。

## 日志证据的三种强度

1. 精确订单号 token 出现，但没有可核对的同行门店字段：`ORDER_ID_ONLY_UNCONFIRMED`。只输出位置哈希等元数据，不能证明这是美团回调，也不据此生成数据库异常线索。
2. 同行显式订单号与美团门店字段均唯一匹配：`ORDER_AND_STORE_SAME_LINE`。可标记同行数据库异常、网络超时，仍不证明因果关系。
3. 满足第二项，且同行含管理员从真实回调源码核验的固定接收标记：`CALLBACK_RECEIVED_MARKER`。证明有相应接收日志记录，不证明处理成功、事务提交或日志不可篡改。

没有真实接收标记与日志格式时，默认只给线索，不虚构阶段。订单、门店分散到多行，或只出现线程名的后续异常，目前不会拼接成同一事件。文件名按日期检索，行首时间按配置时区解释。

## 暂未实现

正式补推 API、审批执行、多实例恢复、后台异步队列、多轮聊天记忆、向量 RAG、跨服务 traceId 因果图。SOP 仍是带权限和版本引用的结构化条件检索。V3 将后续 200 行计为检查窗口，但不会把无身份关联的邻行返回给用户或模型。
