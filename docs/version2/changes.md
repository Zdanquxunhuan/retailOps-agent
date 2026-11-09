# Version 2 开发了什么

Version 1 已能根据自然语言识别美团销售订单，查询美团详情、校验门店权限，再查思迅销售/支付流水。Version 2 在这个确定性结果后增加日志线索和可追溯 SOP 解释，帮助运维知道下一步应该核对什么。

## 功能对比

| 能力 | Version 1 | Version 2 |
|---|---|---|
| 真实美团 SDK、思迅固定 SELECT | 已实现 | 复用，不迁移旧写入链路 |
| 日志读取 | 无 | 管理员配置本地只读目录；有界读取，严格关联订单及门店 |
| 日志结果 | 无 | 命中/未命中/不可读/部分扫描；只输出行号、文件别名、哈希和异常标记 |
| SOP 检索 | 无 | 内置 6 条业务说明；按查询结果与日志标记过滤；支持本地自定义目录文件 |
| 知识权限及溯源 | 无 | 共享知识/门店知识范围、版本、内容哈希、证据引用；读取历史任务时复核 |
| DeepSeek 解释 | 仅意图提取 | 可选第二次调用，从已检索 SOP 中选择解释 ID；输出由 Java 渲染 |
| 模型解释失败 | 无对应环节 | 规则回退，保留原有查询事实 |
| 接口 | POST 诊断、GET 任务 | 路径保持兼容，报告增加可空 `details` |
| 使用脚本 | 密码哈希工具 | 增加 `Invoke-Diagnosis.ps1`，输入订单号、展示报告、可选保存 JSON |

内置知识是对已确认业务事实和保守排障规则的编码，不是模拟订单、模拟日志或虚构历史事故。`shared=true` 表示这条通用说明可供所有已通过门店授权的操作员使用，不能用于存储客户信息或私有案例。

## 新增/修改代码

| 类/资源 | 职责 |
|---|---|
| `config/DiagnosisV2Properties` | 日志、知识、模型解释开关及硬上限 |
| `integration/logs/LocalLogEvidenceReader` | 本地文件有界扫描、身份匹配、最小化证据 |
| `knowledge/LocalKnowledgeRetriever` | 启动加载知识目录、范围过滤、确定性排序、引用复核 |
| `agent/EvidenceExplanationSelector` | DeepSeek 选择允许的知识 ID，异常时回退 |
| `workflow/DiagnosisEnrichment` | 按授权顺序连接新模块 |
| `domain/DiagnosisDetails` | V2 扩展报告模型 |
| `support/ContentHash` | SHA-256 摘要计算 |
| `workflow/DiagnosisWorkflow` | 在原流程完成后增强，历史读取增加知识引用检查 |
| `domain/DiagnosisReport` | 增加 `details` 及复制方法 |
| `src/main/resources/knowledge/meituan-sales.json` | 6 条版本化 SOP 条目 |
| 配置模板、启动配置、脚本和文档 | 默认值、操作入口及完整边界 |

Java 类均位于 `src/main/java/com/yu/retailops` 下。

## 明确未完成

- 日志远程采集/SSH、多实例聚合、压缩日志解压、跨行堆栈关联、完整30天覆盖证明。
- 通用文档上传、向量 Embedding、pgvector、语义相似度检索、重排和检索指标评测。
- 自主 Tool Calling、ReAct、多 Agent 协作；当前仍是 Java 编排与结构化模型调用。
- 正式补推入口、审批、幂等写入、自动自愈、退货、京东相关业务。
- 分布式任务恢复、不可篡改审计、SSO、自动续跑。

这次的 RAG 是“小型本地 SOP 检索 + 受限模型选择解释”的增量。未接入向量库，不能在简历中写成已完成企业级向量 RAG 或自动根因定位。

## 兼容和迁移

无数据库 DDL 变化、不修改现有任务文件。旧 JSON 没有 `details`，按可空扩展字段读取；历史兼容仍需本地集成验收。旧客户端若禁止未知 JSON 字段，需要允许新增 `details`。

日志、二次模型调用默认关闭；知识默认读取内置通用规则。启用二次模型调用后，单次成功授权诊断最多新增一次应用层 DeepSeek 调用。Maven 产物名沿用旧版本，避免已有启动命令失效；功能版本以本文及 `details.version` 为准。
