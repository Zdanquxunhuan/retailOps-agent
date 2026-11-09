# 07 工具调用与日志接入

## 调用契约

统一 ToolRequest 包含 toolName、toolVersion、taskId、typedArguments。TrustedContext 由服务端注入 principal、tenant、environment、allowedStores、allowedPlatforms、traceId 和 deadline，模型参数不能覆盖。ToolRegistry 静态注册工具，不接受模型动态加载实现。

ToolResult 包含 status（SUCCESS/PARTIAL/NOT_FOUND/UNSUPPORTED/ERROR）、data、evidenceIds、observedAt、coverage、truncated、nextCursor、errorCode、retryable 和 duration。NOT_FOUND 与调用失败、分页未完成、权限不可见严格分开。错误响应不泄漏凭证或内部连接串。

| 工具/服务 | 业务输入 | 输出与边界 |
|---|---|---|
| LogSearchTool | OrderKey、时间窗、逻辑 systemId、游标 | 脱敏行、堆栈、实例覆盖；无路径或 Shell |
| LocalOrderQueryTool | OrderKey | 原始状态及映射、更新时间、来源版本；无 SQL |
| PlatformOrderQueryTool | OrderKey、订单时间、游标 | 由平台独立适配器实现；能力未知则拒绝 |
| POSStatusQueryTool | OrderKey/受验证关联 ID | 接收、业务、过账三个独立事实 |
| KnowledgeSearchTool | 故障查询、受限元数据 | 带文档版本、段落和权限的片段 |
| HistoricalCaseSearchTool | 故障特征 | 已审核且脱敏案例；不作为当前订单事实 |
| OrderReplayPreviewTool | taskId、目标 OrderKey | 可行性、阻断原因、计划摘要与有效期 |
| OrderReplayService | 已批准 planId | 后端专用命令；不暴露给自动模型调用 |

OrderSyncDiagnosis 是顶层应用服务，不注册成内部 Agent 可递归调用的工具。Meituan/JD 共用参数封装，不共用未经核实的窗口、状态码和补推规则。

## Gateway 执行顺序

身份校验 → 任务所属范围 → 工具白名单 → Schema/长度/枚举/时间窗 → 门店平台授权 → 能力登记 → 总预算/平台配额 → 审计开始 → 调用适配器 → 脱敏/截断/状态映射 → 审计结束。即使有人直接调用 REST 或 MCP，仍走同一条授权路径。

只读查询对超时/临时网络错误有界重试；401/403 不重试，429 按平台 Retry-After 与任务剩余时间处理。按平台账号而非仅进程限流；多实例初期可通过数据库配额或单执行队列，扩容后才评估 Redis。熔断按目标系统隔离，不能因美团错误阻塞京东。写操作遵守 06 的未知结果规则。

## 日志接入比较

| 方式 | 初期成本 | 优势 | 局限/适用 |
|---|---|---|---|
| 业务网只读日志服务 | 中：部署与证书 | 可直接覆盖现有 log.info 文件 | 多实例扇出、轮转与权限需自行管理；先用 |
| 集中采集与检索平台 | 高：采集、索引、存储运维 | 跨实例查询稳定，历史搜索方便 | 已有平台时优先复用；没有时后续建设 |
| 业务服务结构化查询 API | 中：改造旧项目 | 可按业务键查状态 | 不能取代缺失日志，需要逐业务接入 |
| 人工导出脱敏日志 | 低 | 快速制作 Mock 与解析器 | 非实时，仅调研和开发阶段 |

初期不向 Agent 开放 SSH。只读日志进程使用最小文件权限、允许目录固定配置、只匹配字面订单标识；如实现正则则使用固定模板和资源上限，模型不能自带表达式。规范化路径并拒绝目录穿越、符号链接逃逸、任意归档解压；不拼接 grep 或 PowerShell 命令。

每次返回 expectedInstances、queriedInstances、failedInstances、retentionStart、rotationGaps、timeRange、truncated。多行日志需保留堆栈边界、行号/文件指纹；单次初始上限 500 行、1 MB，超过返回游标和部分覆盖。日志内订单号碰撞需结合门店与业务类型核验。

逐步改造：先统一日志时间格式与 instanceId，再补 orderId/storeId/businessType/traceId；最后增加 received、validated、pos_requested、pos_receipt、pos_business_result 等结构化事件。门店过账只能从真实 POS 状态来源采集，不能从打印“推送成功”推断。
