# 10 平台数据库设计

以下全部是拟建 RetailOps 平台表，不是美团、京东或 POS 已有表。初期 PostgreSQL 同时承担任务存储、审计索引、知识元数据和向量存储；不把旧业务数据库复制成新的订单主库。表结构在阶段 3 用版本化迁移落地，本轮不创建 DDL 或数据库。

| 表 | 核心字段 | 约束和用途 |
|---|---|---|
| ops_session | id、tenant_id、environment、owner_id、confirmed_slots | 会话归属与隔离；不存无限原始对话 |
| diagnosis_run | id、session_id、order_key、status、quality、workflow_version、policy_version、version、deadline | 状态 CAS；保存模型/Prompt/知识版本快照 |
| workflow_step | run_id、node_key、status、attempt、lease_owner、lease_until、input_digest、output_ref | 唯一 run_id+node_key；尝试历史单独记录 |
| tool_invocation | id、run_id、step_id、tool_version、args_digest、outcome、started_at、ended_at | 每次尝试独立 id，记录重试父子关系 |
| evidence | id、run_id、source、instance_id、event_time、observed_at、coverage、payload_ref、digest | 来源引用、脱敏、内容完整性；按任务访问 |
| diagnosis_report | id、run_id、revision、facts、hypotheses、citations、unknowns | 唯一 run_id+revision，禁止覆盖旧版本 |
| service_instance | system_id、environment、instance_id、valid_from/to、capability_ref | 查询历史部署实例，地址只管理员可配 |
| capability | system_id、business_type、version、verification_status、verified_by | 未验证能力默认禁用 |
| replay_plan | id、order_key、action、payload_digest、state_version、expires_at、policy_version | 计划不可变，改参数生成新计划 |
| approval | id、plan_id、plan_digest、actor_id、decision、expires_at、consumed_at | 审批范围和计划绑定；原子消费 |
| replay_operation | id、business_idempotency_key、plan_id、status、remote_key、version、lease_until | 唯一业务幂等键；保存成功记录防止重复 |
| replay_attempt | id、operation_id、attempt_no、dispatch_state、response_ref、verification_ref | 重试属于同一 operation，不生成新业务键 |
| audit_event | id、actor、scope、action、target、decision、trace_id、created_at、digest | 追加写；不能依赖普通应用日志代替 |
| knowledge_document / chunk | id、version、hash、scope、source、effective_range、approved、embedding_revision | 发布版本与 ACL；pgvector 向量按模型版本隔离 |
| historical_case | id、source_run、root_cause、verified_outcome、reviewer、status | 人工确认后发布，不自动信任模型结论 |

## 一致性设计

所有业务作用域表都有 tenant_id/environment；单企业也可固定 tenant，但不能省略环境隔离。外键与查询同时约束作用域，不能只靠全局 UUID 不可猜。关键索引为作用域+平台+业务+门店+订单号、run_id+时间、operation_status+updated_at。

审批消费、执行操作占用、审计开始记录在同一事务提交。远程调用在事务外进行，禁止持有数据库行锁等待慢接口。数据库唯一键负责竞争，乐观版本控制推进；租约仅协调 worker，不保证远程仅执行一次。

发送后状态未知占用业务键直到核验或有权限人工处理，不能因 TTL 到期自动删除。确认失败后的新尝试必须重新预检与授权，并关联同一业务操作历史。POS 不支持幂等时不承诺 exactly-once。

## 数据保留与恢复

初始建议脱敏证据保留 30 天、任务报告 180 天、操作审计 365 天，最终由企业数据策略确认；幂等成功记录的生命周期必须覆盖业务可能重放期限，不能跟随日志清理。向量删除与源文档撤销联动。

对账任务扫描 DISPATCHING/VERIFYING/UNKNOWN，仅查询外部状态；无法确认就升级人工，不重新发业务请求。备份需同时覆盖任务、审批、幂等记录和知识版本；恢复后先关闭写入口，核对恢复点之后远程副作用再开放。
