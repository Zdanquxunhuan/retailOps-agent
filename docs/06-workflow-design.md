# 06 工作流与受控自愈

## 只读诊断状态机

```mermaid
stateDiagram-v2
  [*] --> RECEIVED
  RECEIVED --> NEED_INPUT: 信息不足
  NEED_INPUT --> VALIDATED: 用户补齐
  RECEIVED --> VALIDATED: 信息完整且有权限
  VALIDATED --> COLLECTING
  COLLECTING --> ANALYZING: 收集完成或部分失败
  ANALYZING --> REPORTED
  COLLECTING --> FAILED: 无法继续
  REPORTED --> [*]
  FAILED --> [*]
```

每个节点提交输入摘要、输出引用、状态版本、尝试次数和 deadline；用乐观锁防止重复推进。只读节点可在租约到期后恢复。取消任务停止后续查询，已经发出的远程请求可能仍完成，审计须保留其结果。PARTIAL 为报告质量而非伪装成功。

```mermaid
sequenceDiagram
  actor U as 运维用户
  participant A as Agent
  participant W as Workflow
  participant T as ToolGateway
  participant S as 日志/本地/POS
  participant P as 平台查询
  participant K as RAG
  U->>A: 查询门店订单未同步原因
  A->>W: 结构化意图与缺参
  W->>W: 身份、范围、能力和时间校验
  W->>T: 并行只读取证
  T->>S: 按逻辑业务键查询
  S-->>W: 证据及覆盖/未知标志
  alt 本地缺失且可在平台窗口查询
    W->>T: 受预算限制的平台查询
    T->>P: 鉴权、分页与限流
    P-->>W: 响应和完整性标记
  else 超窗或能力未证实
    W->>W: 标记无法核实
  end
  W->>K: 按平台、业务、环境、版本检索
  K-->>A: 有来源的知识片段
  W->>A: 事实和冲突证据
  A-->>W: 引用式诊断草案
  W->>W: 引用和业务状态校验
  W-->>U: 报告、未知项、后续建议
```

MVP 尚无 RAG 时跳过 K，使用经确认规则。超过美团查询窗口时不发无效搜索；窗口边界由适配器按平台定义判定，不擅自解释为固定 120 小时。分页达到上限后标记不完整，空响应只有完整检索和有效权限前提下才支持“本次查询未找到”。

## 补推状态机（后续阶段）

PREVIEWED → AWAITING_APPROVAL → APPROVED → REVALIDATING → DISPATCHING → VERIFYING → VERIFIED / ACCEPTED_PENDING_POSTING / FAILED / UNKNOWN。

预检绑定业务键、当前状态/版本、请求摘要、策略版本、环境、申请人和过期时间。审批由有范围权限的独立审批人执行；参数变化、状态变化、权限撤销、审批过期都使计划失效。执行前重新查询 POS 和原系统状态，不能复用陈旧预检结论。

```mermaid
sequenceDiagram
  actor R as 申请人
  actor H as 审批人
  participant W as 修复流程
  participant D as PostgreSQL
  participant B as 原订单业务服务
  participant P as POS状态查询
  R->>W: 请求单订单补推预检
  W->>P: 最新状态查询
  W->>D: 保存计划摘要与审批范围
  H->>W: 审批指定计划
  W->>W: 再鉴权、校验期限并复查状态
  W->>D: 原子占用业务幂等键及执行许可
  W->>B: 使用原接口和原幂等键补推
  alt 响应明确
    B-->>W: 接收/拒绝结果
  else 超时或进程中断
    W->>D: 标记 UNKNOWN，禁止盲目重发
  end
  W->>P: 有界轮询最终状态
  P-->>W: 接收、业务处理与过账分别返回
  W->>D: 记录前后状态和核验结果
  W-->>R: 事实结果或待对账状态
```

## 崩溃与并发边界

本地数据库事务无法覆盖远程 POS。先持久化执行意图，再调用远程接口；发送后宕机或落库失败均视为结果未知。恢复进程先核验，不自动重新发送。只有下游有已验证幂等保障时，才允许在同一个 operationId 下安全重试。

幂等键以租户/环境/平台/业务/门店/业务单号/修复动作及必要业务版本构成，不包含每次随机请求 ID。退货与税率补充的业务标识等待源码确认。数据库唯一约束和状态 CAS 控制并发；释放租约不证明远程操作已结束。全链路不宣称 exactly-once。

无法确定下游幂等性或无法可靠核验时关闭自动补推，保留人工处置建议。失败补偿默认为对账、任务挂起和升级处理，不自动删 POS 单、回滚财务业务或整批重跑。

税率补充增加 TASK_TRIGGER → FETCH_PAGE → PARSE → LOCAL_PROCESS → POS_SEND 节点，按批次和单单保存结果；未知游标语义时不做批量修复。预览必须列出影响范围，MVP 自愈只支持单订单。
