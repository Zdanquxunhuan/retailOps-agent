# 14 Windows 开发与部署设计

## 当前环境与命令

已验证 `F:\ayd\jdk-17\bin\java.exe` 为 Java 17.0.12，pom 设置 Java 17、Boot 4.1.1。未运行构建，也未验证 Docker Desktop 可用。当前 Wrapper 指向 Maven 3.10.0，后续先核验是否能从配置仓库解析；不要因为存在 Wrapper 文件就宣称构建成功。

下列 PowerShell 命令在设计审核后用于基础环境验证；本轮只执行了 java -version：

```powershell
Set-Location -LiteralPath 'F:\ggdownload\RetailOps-Agent'
$env:JAVA_HOME = 'F:\ayd\jdk-17'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
& "$env:JAVA_HOME\bin\java.exe" -version
.\mvnw.cmd -version
.\mvnw.cmd dependency:tree
.\mvnw.cmd test
docker version
docker compose version
```

设置仅影响当前进程；IDE 的 Project SDK 和 Maven Runner JDK 需分别设置。Java 17.0.12 是当前安装事实，生产补丁版本需企业维护策略确认。若 Wrapper 下载失败，先保留错误证据，再核查发行版、镜像与代理，不先认定 Java 不兼容。

## 环境规划

mock profile：HTTP stub、伪造日志、固定模型响应，无生产凭证，可重复演示。test profile：授权测试接口和脱敏数据。prod profile：未来上线审批后配置，默认关闭写入口。每个环境独立数据库/凭证/服务目录，避免通过请求参数切换到生产。

基础运行组件为 RetailOps 应用、PostgreSQL/pgvector；RAG 阶段增加 BGE-M3 内网服务；OTel Collector 复用已有设施或后续增加。Redis、Kafka、Elasticsearch、Langfuse、独立 Workflow 引擎不作为默认启动依赖。

Docker Compose、Dockerfile、数据库迁移、mock profile 尚未创建，不能现在执行不存在的 compose up 或声称业务可运行。阶段 3 将固定镜像版本和摘要、端口、健康检查、持久卷、资源限制及启动依赖，并交付直接可执行的 PowerShell 启动说明。

## 生产部署与恢复

应用容器采用非 root 用户、最小可写目录、TLS、受限出网；数据库与 Embedding 服务仅内网可达。日志读取进程分布在业务服务器并保持只读权限；旧 Java 8 项目独立运行，通过受控 HTTP 连接。

配置包含允许系统、平台账号配额、工具预算、模型标识、知识版本、补推开关、审计保留期；密钥用外部秘密注入。健康检查区分应用存活和业务依赖就绪，DeepSeek 故障不应导致无休止重启。

发布流程：迁移兼容性验证 → 备份 → Mock/测试回归 → 灰度只读 → 观察 → 必要时单独启用受控写。回滚应用不能回滚已发送的 POS 请求；回滚前冻结写操作并对账 UNKNOWN。恢复演练检查审批、幂等记录与远程状态一致性，不能只确认数据库能启动。

资源规模需要门店数、峰值查询、日志日增量、知识规模及机器配置后测算。BGE-M3 CPU 可以先做可行性测试，但吞吐/延迟必须实测，不承诺当前电脑满足生产要求。
