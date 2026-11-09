# 12 可观测性与审计

## 技术选择

基础采用 Spring/Micrometer 的 Observation 与 OTel 导出，先接企业已有可观测平台。Spring AI 提供模型及工具相关观测，但内容采集必须显式审查，不能为调试默认记录完整 Prompt 或日志。[Spring AI 官方观测文档](https://docs.spring.io/spring-ai/reference/observability/index.html)

Langfuse 是后续评估 Prompt/模型调用和实验对比的候选，不是强制基础设施。其 Java 官方仓库推荐通过 OpenTelemetry 发送追踪数据；如引入，先验证 OTLP 字段映射、脱敏、访问控制与自托管成本，不假设 Java SDK 和其他语言 SDK 完全对等。[官方 Java 仓库](https://github.com/langfuse/langfuse-java/blob/main/README.md)

## 追踪结构

request → diagnosis_run → workflow_step → tool_invocation → adapter HTTP；旁支为 llm_call 和 rag_retrieval；补推另外串联 planId、approvalId、operationId 和 verification。日志与指标通过 traceId/taskId 关联，订单 ID 放审计查询字段而非高基数 metric 标签。

每次决策记录所选允许流程、简短理由、证据 ID、模型/Prompt/知识/工具版本、拒绝原因和预算消耗。记录模型标识与请求 ID；不要存隐藏思维链。旧项目无法透传 trace 时，用业务键+时间窗关联并显式标注非精确链路。

## 指标与告警

| 指标 | 含义与行动 |
|---|---|
| diagnosis_duration / completion / partial_rate | 排查耗时与证据不足比例；先检查日志覆盖及平台失败 |
| tool_error / timeout / rate_limit / circuit_open | 按平台与业务类型聚合，定位外部依赖 |
| llm_latency / token_usage / schema_error | 记录 token 缺失为 UNKNOWN，避免虚构费用 |
| rag_recall_offline / citation_failure | 检索质量由离线标注集评估，在线引用失败触发降级 |
| replay_unknown_age / pending_verification | 超过核验期限转人工处理队列，不触发自动重发 |
| approval_rejected / scope_denied | 检测异常访问，区分正常拒绝和攻击性重复请求 |
| pos_receipt / business_success / posting_pending | 分离接口、业务、门店状态，禁止单一成功率掩盖差异 |

初始告警候选：同目标 5 分钟至少 20 次调用且错误率超 20%；UNKNOWN 超过 10 分钟；审批待办超过业务约定期限。阈值是设计起点，需结合真实流量调优，避免低样本误报。通知通道和收件人由企业另行配置，本轮不发送消息。

## 审计与运行日志分离

审计保存谁在何范围、何时、基于什么证据/策略执行什么动作，前后状态和核验结果是什么。追加写、角色隔离、受控保留与导出；普通 trace 可采样，操作审计不可采样。对证据做哈希只能辅助检查完整性，不等同不可篡改；生产若需要防篡改应增加独立存储和访问审计。

观测后端失效时允许只读诊断降级并标记监控缺口；业务审计持久化失败则禁止新补推。演示需能从一个 taskId 找到工具输入摘要、来源证据、模型版本、报告与结果核验，证明端到端可追溯。
