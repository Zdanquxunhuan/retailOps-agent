# 验证记录与手工验收

日期：2026-11-09。遵循当前项目要求：不使用Mock、不新增或执行测试代码、不启动真实应用、不连接生产业务服务或执行补推。

## 本次实际检查

- 阅读当前master源码、V2设计及已确认业务事实。
- 核对Spring AI 2.0.2公开工具定义、ChatModel和消息API；核对OpenSSH只读SFTP、chroot、转发限制及Linux删除权限资料。
- Java 17对全部生产Java源码执行编译器语法解析，检查语法，不加载应用、不给第三方类做类型归属。此项不是完整编译。
- Java 17编译器API成功编译仅依赖JDK的domain记录、`ContentHash`和`AgentContext`；没有启动任何业务类。命令行`javac`不在PATH，使用同一JDK的ToolProvider编译器入口。
- `git diff --check` 检查空白错误；配置、文档相对链接、源码新增范围做静态检查。
- 尝试 `bash mvnw --batch-mode --no-transfer-progress -Dmaven.test.skip=true package`：失败在依赖解析阶段，`repo.maven.apache.org` DNS解析失败，`spring-boot-starter-parent:4.1.1`无法下载；未进入项目编译。

完整依赖解析/类型编译、启动、Spring Bean装配、DeepSeek原生tool_calls、SFTP兼容及现网联调均尚未验证。无测试通过率或真实调用成功率可报告。没有运行SDK、数据库、SFTP或模型请求。

## 本地编译

```powershell
$env:JAVA_HOME = 'F:\ayd\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd --batch-mode --no-transfer-progress '-Dmaven.test.skip=true' package
```

编译失败先排查具体依赖或API差异，不通过切换Mock掩盖。完成编译后按usage准备实际配置，再由用户自行决定并执行真实只读联调。

## 手工验收设计（未执行）

| 场景 | 操作/条件 | 预期 |
|---|---|---|
| 缺少日期 | 省略orderDate | NEED_INPUT，不调用模型及任何业务工具 |
| 日期格式错误 | 2026-11-31 | 400，不连接业务服务 |
| 日期不可用 | 未来日期或2000年以前 | NEED_INPUT |
| 正常身份 | 授权门店内的真实美团销售单 | ORDER工具先核验，再可调用POS/LOGS |
| 越权 | 当前账号不包含返回门店 | 403，不查思迅/日志，不持久化越权平台事实 |
| 806/超窗 | 使用已有实际排查场景，不为收集样本额外探测生产 | UNKNOWN/超窗说明，停止后续门店工具 |
| 两类流水均存在 | 已知正常写入单 | BOTH_FLOWS_PRESENT，过账UNKNOWN，禁止重复补推 |
| 一类流水存在 | 已知待排查单，不能人为破坏生产制造样本 | PARTIAL_FLOWS_PRESENT，建议核查事务和进度 |
| 思迅失败 | 由管理员在合法联调场景观察 | UNKNOWN，不置零、不补推 |
| 三日归档 | 用户给定订单日期 | 日期顺序D、D-1、D+1；缺档明确显示 |
| 活动日志未归档 | 当天只有活动log | 显示缺口，不能断言未接单 |
| 订单号子串 | 已有日志存在相近长单号 | 不把另一单号当目标 |
| 邻行其他订单 | 人工已知的多线程交错日志 | 不返回他单行，不继承邻近身份 |
| 只有订单号 | 无同行可信门店/接收标记 | ORDER_ID_ONLY_UNCONFIRMED，接收UNKNOWN |
| 有接收标记 | 源码核验的固定标记+同行订单及门店 | RECEIPT_MARKER_FOUND，仅确认日志接收线索 |
| 长行/损坏压缩/限额 | 使用管理员批准的真实归档观察，不改生产文件 | PARTIAL及对应限制码 |
| 原生工具调用 | 本地联调真实DeepSeek | steps出现DEEPSEEK_TOOL_CALL；业务参数由Java绑定 |
| 规划失败或非法工具 | 审查控制路径或观察实际失败 | 拒绝并记录安全码，剩余步骤RULE_FALLBACK，无重复业务请求 |
| 时间/步骤耗尽 | 合法配置下观察 | STOPPED，保留已得到事实，报告未完成部分 |
| 历史权限变化 | 管理员按正常流程变更门店权限 | 原任务访问403 |
| SOP内容/版本变化 | 正常维护知识目录后重启 | 旧引用失效时拒绝历史报告 |
| 重启中断任务 | 用户正常运维期间已有中断任务 | 旧RUNNING展示INTERRUPTED，不自动调用业务 |
| V2兼容 | 原只读API | 保留原行为，与V3共享并发上限 |

权限验收优先检查sshd有效配置、ACL和只读挂载。任何写权限拒绝验证都必须使用管理员专门准备的隔离临时目录，不能尝试删除、覆盖真实业务日志；本次未执行这类操作。
