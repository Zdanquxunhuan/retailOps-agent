# ADR-0001 技术基线与复杂度控制

日期：2026-11-09。状态：建议接受，待用户设计审核；依赖解析与运行验证尚未执行。

## 已查证资料与决策

| 项目 | 决策 | 依据与验证边界 |
|---|---|---|
| Java / Boot | Java 17 + Boot 4.1.1，尊重用户选择与现有 pom | 本地 Java 17.0.12 已运行；[4.1.1 官方发布](https://github.com/spring-projects/spring-boot/releases/tag/v4.1.1)存在。当前[系统要求页](https://docs.spring.io/spring-boot/system-requirements.html)已滚动至 4.1.2，说明 Java 17 基线；不将其误称为 4.1.1 精确构建实测 |
| Spring AI | BOM 2.0.2，全部 AI starter 同一 BOM | [官方入门](https://docs.spring.io/spring-ai/reference/getting-started.html)显示 2.0.2，明确 2.0.x 支持 Boot 4.0.x/4.1.x；不选 2.1 预览版 |
| DeepSeek | 使用专用模型 starter，不默认依赖 OpenAI 云服务 | [官方接入](https://docs.spring.io/spring-ai/reference/api/chat/deepseek-chat.html)提供 spring-ai-starter-model-deepseek；真实账号模型能力仍待验证 |
| Embedding | 内网 BGE-M3，与聊天模型解耦 | [BAAI 模型卡](https://huggingface.co/BAAI/bge-m3)提供模型能力；精确 revision、推理镜像和硬件预算在阶段 5 固定 |
| 数据库 | PostgreSQL + pgvector，事务与知识共用基础设施 | [pgvector 官方](https://github.com/pgvector/pgvector)支持精确/近似检索；版本与镜像摘要在构建阶段验证锁定 |
| Workflow | Java 持久化状态机 | 当前仅五类受控业务，无须先部署重量流程引擎；将来复杂长任务增多再评估 |

正式编码前先验证 Boot parent、AI BOM 和 DeepSeek starter 均可解析，检查依赖树、Jackson 3/框架迁移 API、编译与 Mock 启动。官方兼容矩阵不是本工程已测试的证明。Maven Wrapper 下载配置也需验证。

## Spring AI 与 LangChain4j

| 维度 | Spring AI | LangChain4j |
|---|---|---|
| Java/Spring 集成 | 与当前 Boot、配置和观测栈贴合 | 同样有 Spring 集成，当前官方资料区分 Boot 3/4 starter |
| 模型、工具、RAG | 统一抽象，适合已有 Spring 项目 | AI Services 声明式接口和工具/RAG 抽象也适用 |
| 本项目治理重点 | 复用 Spring Security、事务、审计习惯 | 可以实现相同治理，不会自动消除工程工作 |
| 选择结论 | 采用，减少团队学习与依赖边界 | 作为可替代方案记录，不同时引入 |

LangChain4j 的现行 [Spring Boot 集成文档](https://docs.langchain4j.dev/tutorials/spring-boot-integration/)已提供 Boot 4 路径，因此不能用“不支持 Boot 4”的旧结论排除它。选择 Spring AI 是项目匹配度判断，不是宣称其所有能力优于另一框架。

## 其他决策

1. 模块化单体先行，旧系统通过 HTTP 保留 Java 8，不共享二进制依赖。
2. 单 Agent 配专业 Tools；多 Agent 只有在独立上下文与质量收益被证实时引入。
3. 先美团销售只读，再 RAG、受控补推和其余场景。MCP 初期不强制。
4. 初期 PostgreSQL 唯一约束/CAS/租约控制调度；Redis 仅在配额或吞吐瓶颈实测后增加。
5. 生产补推不交由模型自动工具循环；固定策略校验批准的计划后执行。
6. 不默认同时引入 Langfuse、Elasticsearch、Kafka、独立向量数据库或 Workflow 引擎。

这些选择减少部署与排错成本，但要求自行实现有限状态恢复、审计和业务权限测试。若规模或任务复杂度改变，应新增 ADR 记录触发条件、替代方案、迁移代价和回归证据。
