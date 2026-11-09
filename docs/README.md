# RetailOps Agent 首轮设计审阅入口

设计日期：2026-11-09（Asia/Shanghai）。状态：设计草案，等待业务源码核验与用户审核。本轮只新增文档，不开发业务代码、不连接真实业务接口、不执行补推。

如需先了解建成后的实际效果，请阅读 [能力说明、美团销售完整示例与简历写法](16-delivered-capabilities-and-resume.md)，其中提供只读 MVP、销售闭环和完整平台分别适用的简历文案。

按任务要求的顺序审阅：

1. 项目可行性：[00-project-overview.md](00-project-overview.md)。
2. 招聘需求分析：[02-job-market-research.md](02-job-market-research.md)。样本与日期限制见该文档，不能宣称已验证 20 个最近 90 天内的完整岗位。
3. 总体设计：[04-architecture.md](04-architecture.md)，配套 [Agent](05-agent-design.md)、[Workflow](06-workflow-design.md)、[Tools](07-tool-design.md)、[RAG](08-rag-design.md)、[MCP](09-mcp-design.md)。
4. 模块与功能拆分：04 的模块表；[01-business-analysis.md](01-business-analysis.md) 包含五场景能力矩阵与待确认资料。
5. 分阶段计划：[03-project-roadmap.md](03-project-roadmap.md)。
6. 完整设计文档：还包括 [数据库](10-database-design.md)、[安全](11-security-design.md)、[可观测性](12-observability.md)、[测试评估](13-testing-and-evaluation.md)、[部署](14-deployment.md)、[简历面试](15-resume-and-interview.md) 与 [技术决策](adr/0001-technology-baseline.md)。
7. 本轮结论：采用 Java 17 + Boot 4.1.1 + Spring AI 2.0.2 + DeepSeek + PostgreSQL/pgvector + BGE-M3；先交付美团销售只读诊断，再加入 RAG 和受控补推，最后扩展另外四类业务。基础平台使用模块化单体，外部业务通过 HTTP 隔离，暂不引入多 Agent、Redis、独立流程引擎或强制 MCP。

## 已核验与未核验

- 已读用户完整任务书。当前工程只有启动类、contextLoads 测试、application.properties、Maven Wrapper 和基础 pom；未发现适用的 AGENTS.md。
- pom 已设置 Boot 4.1.1 与 Java 17；未添加 Spring AI。`F:\ayd\jdk-17\bin\java.exe -version` 返回 17.0.12。
- 美团、京东、POS 真实代码和协议均未提供。下文类名、平台自有表名、内部 DTO 均为拟建设计，不代表旧项目已有这些能力。
- 版本兼容依据是官方资料；尚未执行 Maven 依赖解析、编译或业务测试。Wrapper 所配置发行版的可下载性留给基础平台阶段核验。
- 需求中的“美团最近 5 天”按用户提供的业务约束建模，其精确边界、时区及适用订单类型仍须接口文档确认。

## 请优先提供

第一批只需美团销售完整调用链：接收 Controller → Service → DAO/Mapper → POS Client，相关 DTO、脱敏配置、表结构、成功/失败日志、平台查询/本地补推/POS 查询接口说明，以及实例部署与日志轮转说明。详细清单见 01。其余场景随后逐个提供，避免凭销售逻辑猜测退货与税率补充。

最值得形成简历证据的是：跨实例证据追溯、LLM 与确定性执行的权限边界、可恢复工作流、审批绑定与幂等控制、超时不确定结果对账、带引用的 RAG 和可复现评测。目前它们是设计目标，不能写成已上线成果。
