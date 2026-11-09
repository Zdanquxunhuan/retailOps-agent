# 02 招聘需求分析

调研日：2026-11-09（Asia/Shanghai）；近 90 天观察窗口：2026-08-11 至 2026-11-09。

## 方法与限制

围绕 AI Agent 开发、Java Agent、大模型应用、Agent Workflow、RAG、AI 应用、大模型平台开发检索 BOSS、猎聘、智联、企业招聘和高校就业平台。按公司+职位+地点/业务描述去重；同一岗位转载不重复计数。目标是至少 20 个可分析岗位，本轮收集 20 条独立岗位线索，其中 14 条有可用公开职责材料（A），6 条仅摘要或访问受限（B）。**未达到 20 个完整可核验岗位的目标，也不能证明这些岗位均为最近 90 天发布。**

BOSS 聚合页可读，但点击企趣详情触发安全验证；猎聘上汽详情跳到验证码。没有登录、投递、联系招聘者或绕过限制。部分动态企业页面打开无正文，搜索材料可见，仍降为 B。网页抓取时间不是发布日期。A 表示有足够材料做保守技能编码，不表示已独立确认招聘方身份、岗位仍在招或所有隐藏内容。

只有 #6、#17 有明确近期完整发布日期；#14 是近期刷新，不计为近期首发；#19 只有相对日期；#15 的日期来自转载，不作为原平台发布时间。统计包含社招、校招、实习和一个混合算法岗位，有选择偏差，不能外推为国内全市场占比。

## 可追溯样本

技能标签只编码明确出现的可见要求（含加分项）；未列标签表示未观察到，不表示岗位不需要。Tool 包含明确工具/Skill 接口开发；Workflow 包含明确规划、任务编排或状态管理；Eval 不把普通单元测试自动算作效果评估。篇幅仅保留简短摘要，原文见链接。

| ID | 岗位与来源 | 可访问性 | 日期证据 | 观察到的技能 |
|---|---|---|---|---|
| 1 | [美团 / Agent 开发工程师](https://zhaopin.meituan.com/web/position/detail?highlightType=social&jobUnionId=4428189649) | B：搜索正文可见，直接页面无正文 | 未知 | Java,Python,Workflow,Tool,MCP,RAG,Vector,Prompt,Multi,Eval,Obs |
| 2 | [鑫锡软件 / Java 开发（智能体）](https://www.zhaopin.com/jobdetail/CCL1387109580J40848671605.htm) | A | 未知 | Java,SpringAI,LangChain4j,Workflow,Tool,RAG,Vector,Prompt,Security |
| 3 | [鑫锡软件 / AI 应用（气象）](https://www.zhaopin.com/jobdetail/CCL1387109580J40875171105.htm) | A | 未知 | LangChainGraph,Workflow,Tool,RAG,Multi,Obs |
| 4 | [深圳南区人瑞 / LLM Agent 高级开发](https://www.zhaopin.com/jobdetail/CCL1289361510J40876807101.htm) | A | 未知 | Python,LangChainGraph,Workflow,Tool,RAG,Multi,Eval,Obs,Security |
| 5 | [蚂蚁集团 / Java 研发（Agent 应用）](https://www.nowcoder.com/jobs/detail/470154) | A | 未知 | Java,Workflow,Tool,MCP,RAG,Multi,Eval,Obs,Security |
| 6 | [神州数码融信 / AI Agent 开发](https://career.nankai.edu.cn/correcruit/content/id/117965.html) | A：高校发布正文 | 2026-09-07 发布 | Python,LangChainGraph,Workflow,Tool,RAG,Vector,Prompt,Eval |
| 7 | [影石 / Data agent 全栈校招](https://arashivision.jobs.feishu.cn/campus/position/7678174819064318246/detail) | B：搜索正文可见，直接页面无正文 | 未知 | Java,Python,LangChainGraph,Tool,MCP,Security |
| 8 | [上海超级线索 / AI 大模型应用](https://www.zhaopin.com/jobdetail/CCL1518858530J40885152310.htm) | A：公开搜索返回岗位正文 | 未知 | Python,RAG,Vector,Prompt,Deploy |
| 9 | [上汽云计算中心 / AI Agent 平台研发](https://www.liepin.com/job/1981004461.shtml) | B：详情跳转验证码，仅摘要 | 5月28日更新，年份未知 | Workflow,Tool |
| 10 | [绿联科技 / AI Agent 前端开发](https://www.liepin.com/job/1983680297.shtml) | B：仅搜索材料 | 6月24日更新，年份未知 | Workflow,Tool,MCP |
| 11 | [吉林朗创 / AI 算法应用](https://m.zhaopin.com/jobs/CC343084180J40907399912.htm) | A：混合算法/应用岗位 | 10-23，年份未知 | Java,Python,LangChainGraph,Workflow,Tool,RAG,Vector,Prompt,Eval,Obs,Security,Deploy |
| 12 | [ZBANX / RAG 开发](https://www.zbanx.com/cn/careers/4) | A：企业公开岗位正文 | 未知 | Python,LangChainGraph,RAG |
| 13 | [智乐聘 / Agent 开发（客户匿名）](https://job.zhilepin.com/home/position/3355?role=elite) | A：招聘机构公开正文 | 未知 | Java,Python,Workflow,Tool,Prompt,Multi,Eval,Obs |
| 14 | [国金证券 / AI 平台实习](https://www.shixiseng.com/intern/inn_5m4csan0n5mz?pcm=pc_SearchList) | A：实习岗位，非社招 | 2026-10-15 刷新（非首发） | Java,Python,RAG,Eval,Obs |
| 15 | [杭州蓝目 / AI Agent 工程师](https://www.zhaopin.com/jobdetail/CCL1261794520J41040117802.htm) | A | 原平台未知；转载称 2026-10-16 | Python,LangChainGraph,Workflow,Tool,RAG,Vector,Eval |
| 16 | [苏州人瑞 / Agent 智能体开发](https://www.zhaopin.com/jobdetail/CC427775130J40961468115.htm) | A | 未知 | Python,LangChainGraph,Workflow,Tool,Prompt |
| 17 | [武汉启云方 / AI Agent 工程师](https://career.hebut.edu.cn/home/correcruit/content/id/79245.html) | A：高校发布正文 | 2026-08-18 发布 | Python,Workflow,Tool,RAG,Prompt,Obs,Security |
| 18 | [可利邦 / AI Agent 中级开发](https://zhaodagong.com/job/19725833) | B：详情抓取失败，仅摘要 | 未知 | Tool,MCP,RAG,Vector,Prompt,Deploy |
| 19 | [上海乳制品企业 / AI Agent 应用（企业名未公开）](https://origin.v2ex.com/t/1253828) | A：社区招聘自述，身份未独立核验 | 页面显示 5 days ago，相对日期 | Python,LangChainGraph,Workflow,Tool,RAG,Vector,Prompt,Multi,Eval,Obs,Security |
| 20 | [企趣科技 / AI Agent 应用（LangGraph/MCP）](https://www.zhipin.com/job_detail/58055107da7b19030nx_09S1GFtS.html) | B：BOSS 详情触发安全验证 | 未知 | LangChainGraph,MCP |

## 技能统计（仅 A 类，分母 14）

这些是可见材料的出现下限，来自人工保守编码；标题或语言标签与完整任职要求可能不一致，不能解释为必备要求比例。

| 技能 | 岗位数 | 比例 | 样本 ID |
|---|---:|---:|---|
| Java | 5/14 | 35.7% | 2, 5, 11, 13, 14 |
| Python | 11/14 | 78.6% | 4, 6, 8, 11, 12, 13, 14, 15, 16, 17, 19 |
| Spring AI | 1/14 | 7.1% | 2 |
| LangChain4j | 1/14 | 7.1% | 2 |
| LangChain / LangGraph | 8/14 | 57.1% | 3, 4, 6, 11, 12, 15, 16, 19 |
| Workflow / 规划与状态编排 | 11/14 | 78.6% | 2, 3, 4, 5, 6, 11, 13, 15, 16, 17, 19 |
| Tool / Function Calling | 11/14 | 78.6% | 2, 3, 4, 5, 6, 11, 13, 15, 16, 17, 19 |
| MCP | 1/14 | 7.1% | 5 |
| RAG | 12/14 | 85.7% | 2, 3, 4, 5, 6, 8, 11, 12, 14, 15, 17, 19 |
| 向量数据库 / 向量检索 | 6/14 | 42.9% | 2, 6, 8, 11, 15, 19 |
| Prompt Engineering | 8/14 | 57.1% | 2, 6, 8, 11, 13, 16, 17, 19 |
| Multi-Agent | 5/14 | 35.7% | 3, 4, 5, 13, 19 |
| 模型部署（明确提及） | 2/14 | 14.3% | 8, 11 |
| Agent / RAG 效果评估 | 8/14 | 57.1% | 4, 5, 6, 11, 13, 14, 15, 19 |
| 可观测性 / 监控追踪 | 8/14 | 57.1% | 3, 4, 5, 11, 13, 14, 17, 19 |
| 权限 / 安全控制 | 6/14 | 42.9% | 2, 4, 5, 11, 17, 19 |

## 分类与项目映射

以下分层是本项目的学习优先级判断，依据样本而非全国统计。样本内频繁出现的 RAG、工具调用、编排、Python 与工程交付属于基础；评估、可观测性、上下文治理和权限隔离是进阶。Java 的直接匹配样本包括 #2、#5；不能据此说 Spring AI 比 Python 生态更普遍。Multi-Agent 和 MCP 常作为部分岗位扩展或加分项，是否采用仍由业务需求决定。

| 招聘能力 | 为什么需要及实现 | 如何测试与面试证明 |
|---|---|---|
| Java 企业集成 | 旧系统 HTTP 适配器、状态与错误码隔离 | 五场景契约测试，展示真实调用链映射 |
| Agent Workflow | 受限取证计划和持久化状态机 | 缺参、重启、超时恢复演示 |
| Tool Calling | 类型化工具与服务端权限拦截 | 展示越权、参数注入、预算耗尽测试 |
| RAG / 向量检索 | SOP 与错误码引用，元数据过滤 | 给出 Recall@5、引用正确率与消融结果 |
| Prompt / 上下文 | 版本化模板、证据摘要、缺参澄清 | 相同测试集的版本对比及失败案例 |
| 安全 / 分布式一致性 | 审批绑定、唯一键、UNKNOWN 对账 | 并发补推、崩溃窗口、审批过期演示 |
| 评估 / 可观测性 | 任务 trace、业务指标、固定评估集 | 一笔故障从输入到最终状态的完整证据 |
| MCP | 后续按需暴露只读工具 | 跨客户端协议契约与认证拒绝测试 |
| Multi-Agent | 暂缓，单 Agent 足够 | 解释上下文边界和复杂度取舍，不编造实现 |
| 模型部署 / Python | BGE-M3 服务与离线评估辅助 | Embedding 服务探测、延迟及资源测试 |

本项目覆盖应用工程与企业集成，不覆盖预训练、强化学习或深度模型算法研究；不能据此声称具备算法岗全部能力。建议优先投递 Java + AI 应用/Agent 工程岗位，同时补足 Python 数据处理和评估能力，无须再引入第二套 Java Agent 框架。

## 后续补强

获得合法可见材料后补齐至少 20 条 A 类记录，并单独构建最近 90 天有明确日期的子样本。不要以聚合页岗位数、搜索引擎日期或重复转载凑数；对 #16 的不寻常框架经验年限要求保留来源但不照搬为合理学习门槛。

