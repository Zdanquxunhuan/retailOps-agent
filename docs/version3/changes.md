# Version 3 开发了什么

## 从 V2 到 V3

| 项目 | V2 | V3 |
|---|---|---|
| 调查顺序 | Java 固定流程 | 身份核验固定，DeepSeek 在允许工具中选择流水/日志查询顺序，随后检索 SOP |
| 模型调用 | 意图提取、可选 SOP ID 选择 | 意图提取 + 原生 `tool_calls`，Java 显式执行循环 |
| 工具参数 | 各模块内部传递 | 对模型统一空对象，订单/门店/日期由可信上下文绑定 |
| 日志 | 本地未压缩文本 | 专用 SFTP，只读流式 `.gz` 解压，日期 D、D-1、D+1 |
| 日志解释 | 同行订单及门店匹配 | 另区分纯订单号线索、完整身份匹配、经配置的回调接收标记 |
| 时间线 | 文件修改时间 | 增加行首事件时间和来源关联级别；不将文件时间当事件时间 |
| 任务记录 | 开始和最终结果 | 每个工具前后原子检查点；重启后旧 RUNNING 展示 INTERRUPTED |
| 处理计划 | 建议文本 | 明确 `ADVISORY_ONLY`、`executable=false`、阻断原因及证据 |
| 并发 | V2 信号量 | V2/V3 共用全局诊断容量 |

## 代码位置

- `agent/AgentDiagnosisService`：入口编排、身份关卡、预算、工具执行和降级。
- `agent/DeepSeekDiagnosisPlanner`：原生工具定义、单次模型请求、调用验证、工具观察消息。
- `agent/DiagnosisTool`：业务名与函数名映射，定义回调禁止框架隐式执行。
- `agent/AgentContext`：单任务可信状态；不保存原始订单报文或用户提示。
- `agent/AgentReportAssembler`：确定性事实、SOP 引用复核、处理计划。
- `agent/AgentRunStore`：按所有者隔离的本地原子检查点。
- `integration/logs/RemoteGzipLogReader`：SFTP 读取与有界 gzip 扫描。
- `api/AgentDiagnosisController`、`domain/AgentDiagnosisRun`：新 API 和结果。
- `config/DiagnosisV3Properties`：受限配置。
- `scripts/Invoke-AgentDiagnosis.ps1`：带日期的真实只读调用脚本。
- `config/ssh/sshd_config.example`：管理员参考配置，不会自动执行。

美团仍复用 `MeituanSdkOrderAdapter`；思迅仍复用 `JdbcPosFlowAdapter`，不复制旧项目的临时补推 test，也不修改业务写库逻辑。
