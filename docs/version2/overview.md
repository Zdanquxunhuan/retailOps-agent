# Version 2 功能说明

本版本回答两个层次的问题：第一层是“当前查到了什么”，由 Java 固定规则决定；第二层是“按已确认业务规则接下来应查什么”，由 SOP 提供内容、可选由 DeepSeek 选择条目。第二层不覆盖第一层事实。

## 一次请求的结果

`POST /api/v1/diagnoses/meituan-sales` 仍只接收 `message`。用户不指定日志路径、SQL、门店、模型工具名或补推接口。美团详情返回可信门店并通过当前用户授权后，才有思迅查询和 V2 增强。

| 字段 | 含义 |
|---|---|
| `outcome` | 原有查询结论，例如 `PARTIAL_FLOWS_PRESENT` |
| `summary` | 确定性业务描述，模型不能重写 |
| `evidence` | E1 意图、E2 美团详情、E3 思迅观测 |
| `postingStatus` | 始终 `UNKNOWN`，没有独立过账状态来源 |
| `replayExecuted` | 始终 `false`，系统没有补推执行路径 |
| `details.version` | `2` |
| `details.logs` | 扫描状态、观测时间、扫描量、局限及 L 系列证据 |
| `details.knowledge` | 检索状态、目录哈希、知识条目及其证据引用 |
| `details.explanation` | 选择方式、局限提示和最终解释条目 |

缺少订单号、模型意图失败、806、窗口错误等未取得可授权门店的结果，`details=null`。不能因为有订单号就搜索全门店日志。

## 日志状态

| status | 如何理解 |
|---|---|
| `DISABLED` | 没有开启日志读取，没有日志证据 |
| `UNAVAILABLE` | 没有可读取文件，可能目录缺失、为空、权限不足或没有匹配文件 |
| `PARTIAL` | 遇到容量/时间上限、文件变化、部分不可读、过长/无效编码/未结束的行等；可能仍有命中 |
| `NO_MATCH` | 本次受限扫描未命中；不证明本地未接单 |
| `MATCHED` | 至少有符合严格身份匹配的日志行；不代表已定位根因 |

一个 `LogMatch` 包含 `id=L1…`、`source=file-…`、`lineNumber`、`fileModifiedAt`、`lineSha256`、`category`。`source` 是文件名 UTF-8 SHA-256 的前12个十六进制字符；在批准的目录内帮助运维定位文件，不暴露原路径。行哈希使用不含 LF、保留可能的 CR 的原始行字节；不是不可篡改签名。

`DATABASE_EXCEPTION_MARKER` 仅表示出现 `SQLException`/`SQLServerException` 字符串；`NETWORK_TIMEOUT_MARKER` 仅表示出现 `SocketTimeoutException`/`ConnectTimeoutException`；其余为 `ORDER_MENTION`。同一行先匹配数据库标记再匹配网络标记。没有解释自由格式日志的能力，也不读取跨行堆栈上下文。

文件修改时间不是日志事件时间。单实例、保留30天是既有部署事实，但本扫描器不验证留存完整性、时间覆盖或日志是否被删除。

## SOP 引用

每条知识包含 `id`、`version`、`title`、`source`、`contentSha256`、`explanation`、`nextCheck`、`evidenceIds`。`source` 是来源文档的标签，不会发起外部网页请求；它不代表已经建立一个可公开访问的文档 URL。

- `K01`：两表均有流水；避免重推，过账未知。
- `K02`：只有一表有流水；不完整，不直接断言永久部分写入。
- `K03`：两表均未查到；不是漏推证明或补推许可。
- `K04`：思迅查询失败；状态未知，不按零条处理。
- `K05`：关联数据库异常标记；进一步核验因果关系。
- `K06`：关联超时标记；请求结果可能不确定，禁止盲目重推。

匹配先检查知识适用门店，再检查 `outcomes`，有 `logCategories` 时必须存在相应 L 证据。适用的异常专项说明排在通用说明前，其余按 ID 排序，最多返回 `top-k` 条。E3 连接当前查询事实，L 连接关键词线索；这些引用不构成因果证明。

## 模型解释与降级

| status | 行为 |
|---|---|
| `DISABLED` | 不执行二次模型调用，直接呈现已检索的规则说明 |
| `NO_KNOWLEDGE` | 没有可引用知识，不让模型编造 |
| `MODEL_SELECTED` | 模型选择1–3个有效、不重复的已检索ID；Java渲染对应原文 |
| `RULE_FALLBACK` | 模型失败、空选择、重复ID、数量越界或未知ID，显示检索所得规则说明 |

二次调用只传查询 outcome、日志状态/类别、候选知识 ID 和说明，不传原始用户消息、订单号、门店号、流水详情、日志原文或签名。第一阶段意图提取仍会把用户输入发送给 DeepSeek；不要把本版本理解为全部数据都在本地处理。

历史任务 GET 仍检查所有者及当前门店权限；V2 再检查知识是否仍存在、当前范围是否允许、版本与内容哈希是否一致。知识撤回、变更或禁用后，含该引用的历史报告可能返回403，需重新诊断。这里是保守的整份拒绝，不是历史知识版本仓库。
