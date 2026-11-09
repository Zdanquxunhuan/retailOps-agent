# 05 Agent 设计

## 权责与生命周期

采用一个受控 Agent，逻辑拆成信息提取、允许计划选择、证据分析、报告生成四个职责，分别维护 Prompt 版本和评估集，不要求四次模型调用。Workflow 拥有最终控制权；Agent 输出不是授权令牌，不是业务事实。

IntentExtractor 输出 platform、businessType、orderId、storeId、orderTime、requestedAction、missingFields、ambiguities。枚举之外的值拒绝映射；缺字段用 null，不由模型猜测。租户、环境、用户 ID 和权限由后端会话注入，不接受模型覆盖。

PlanProposer 只能选择已登记 workflowId 和当前节点允许的只读取证步骤。服务端验证平台/业务组合、能力登记与预算。美团退货尚未验证时返回 UNSUPPORTED_CAPABILITY，不退回销售流程。

## 有限自适应取证

固定主干是鉴权、定位、状态取证、分析和报告。可变分支是：日志出现数据库错误则补查同 traceId 的受限上下文；本地无记录且时间满足平台窗口则请求平台搜索；证据冲突则补查 POS。Agent 提议工具后，Gateway 再做作用域校验，Workflow 决定是否可执行。不能修改调用目标、扩大门店、无限翻页或触发写入。

初始每任务最多 3 次模型调用、12 次工具调用；相同参数查询复用任务内结果并标注时间，不循环重复。耗尽预算返回 PARTIAL 和未解决事项。结构化结果校验失败最多修复一次，仍失败走确定性报告模板。

## 证据推理契约

Evidence 包含 evidenceId、sourceType、systemId、instanceId、observedAt、eventTime、queryScope、coverage、redactedPayload、digest、sourceRef。查询时间和事件时间分开，回执不等于最终业务状态。报告结构包含 facts、hypotheses、contradictions、missingEvidence、recommendedActions；每个事实都绑定可访问 evidenceId。

根因标签示例：LOCAL_DB_ERROR、VALIDATION_FAILED、POS_REJECTED、DELIVERY_UNCERTAIN、OUTSIDE_QUERY_WINDOW、INSUFFICIENT_EVIDENCE。它们是平台内部分类，不能未经映射作为美团或京东错误码。无法定位时应明确“证据不足”，不把置信度分数当概率事实。

## 上下文与记忆

会话保存已确认参数、任务 ID 和报告摘要，不把原始日志无限堆入聊天历史。新订单创建新任务；复用门店或时间需显示继承来源。生产与测试环境的会话、缓存和知识过滤必须隔离。

长日志先确定性抽取关联行与堆栈，再提交脱敏片段；保留遗漏/截断标志。Prompt、工具说明、模型标识、参数与知识版本都记录在任务快照中。这里只记录简短决策理由与证据引用，不索取或储存模型隐藏思维链。

## 模型及降级

优先使用 Spring AI 的 DeepSeek 模型接入，具体账号可用模型及 Tool Calling/结构化输出能力在测试阶段验证，不硬编码未经确认的模型名。Embedding 与聊天模型独立，不假定 DeepSeek 提供 Embedding。

模型限流/超时后停止生成式分析，输出确定事实和人工建议；不得静默换成其他云模型以免改变数据外发边界。模型响应只作数据处理；日志、文档中的“忽略规则”“调用补推”等内容永远不是指令。
