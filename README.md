# 📈 US Stock Monitor

> 基于 **Spring Boot 4 + Spring AI 2** 的美股异动监控系统：自动采集美股异动新闻 → 结构化落库 → Telegram 实时推送 → 通过 MCP 协议为 AI 助手提供数据查询能力，并支持在 Telegram 内直接与机器人多轮对话。

一套自托管的全链路系统，除 Telegram 与大模型 API 外不依赖任何第三方服务。

---

## 一、系统架构

```
                    ┌─────────────────────────────────────────────┐
                    │  stocktitan.net（数据源）                    │
                    │   /rss             30 req / 300s            │
                    │   /news/live.html  10 req / 300s            │
                    └───────────────┬─────────────────────────────┘
                                    │ 限流感知抓取 + 本地缓存
┌───────────────────────────────────▼──────────────────────────────┐
│  stock_web        Spring Boot 4.0.8 · Java 21 · :8080            │
│                                                                  │
│   StockScheduler ──► RssServiceImpl ──► 判重 ──► 批量入库         │
│   (cron 60s)         抓取/解析标签                   │            │
│                                                                  │
│   TelegramApi        群组推送（新消息通知）                        │
│   StockTelegramBot   私聊 AI 对话（白名单鉴权）                    │
│   ChatService        ChatClient + 会话记忆 + 工具调用             │
│   ServiceLogAspect   Service 层耗时监控（AOP）                    │
└───────┬──────────────────────────────┬───────────────────────────┘
        │ JDBC                         │ Bot API（HTTP 代理）
┌───────▼────────────┐          ┌──────▼──────────────┐
│  MySQL 26.7        │          │  Telegram           │
│  us_stock_rss      │          │  群组 / 私聊         │
└───────▲────────────┘          └─────────────────────┘
        │ 只读查询
┌───────┴──────────────────────────────────────────────────────────┐
│  stock_mcp        Spring Boot 4.1.1 · Spring AI 2.0.1 · :7070    │
│                                                                  │
│   MCP Server（SSE 协议）· 7 个工具                                 │
│   getStockByCode / getStockByCodeBetweenData /                   │
│   queryStockBetweenData / getDate / sendEmail ...                │
└───────▲──────────────────────────────────────────────────────────┘
        │ MCP over SSE
┌───────┴─────────────┐
│ Cline / Claude 等    │
└─────────────────────┘
```

**分层职责**

| 模块 | 定位 | 技术栈 |
|---|---|---|
| `stock_web` | 数据采集、定时调度、消息推送、AI 对话入口 | Spring Boot 4.0.8、MyBatis-Plus、AOP、TelegramBots、Spring AI |
| `stock_mcp` | 数据查询能力输出（MCP Server） | Spring Boot 4.1.1、Spring AI 2.0.1、MCP SDK 2.0 |
| MySQL | 单一事实来源 | MySQL 26.7.0（Docker） |

---

## 二、核心功能

### 1. 异动新闻自动采集
- 每 60 秒调度一轮，从数据源拉取**最新 100 条**美股异动新闻
- 自动解析标题中的股票代码、发布时间（GMT / 北京时间双时区存储）
- 抓取列表页标签并与新闻**按标题建立映射**，中英文双列存储在库

### 2. 智能标签体系
- 内置 **31 个标签枚举**（低浮动、低价股、私募、财报盈利、FDA、临床试验…）
- 英文原文与中文释义**分列存储**（`tags_en` / `tags`），按需取用
- 未收录标签保留原文，避免数据丢失

### 3. Telegram 实时推送
- 新消息入库后自动汇总推送到群组，**一轮一条汇总**而非逐条刷屏
- 内置 HTTP 代理支持，适配受限网络环境
- 单条消息自动按 Telegram 4096 字符上限截断

### 4. AI 对话（本项目重点）
在 Telegram 私聊中直接与机器人对话，由大模型自主决定是否调用工具查询真实数据：

```
用户：12 月异动超过 5 次的股票有哪些？
机器人：（调用 queryStockBetweenData 工具）→ 2025-12-01 ~ 12-31 异动次数 ≥ 5 的股票共 12 只：
        SMX(34) SHEL(8) FCPT(7) DEC(6) HKD(6) OWLS(6) CNS(5) DGNX(5) ECDA(5) KKR(5) SIDU(5) TNMG(5)
```

| 能力 | 实现 |
|---|---|
| 多轮对话 | `MessageWindowChatMemory`（窗口 20 条），按会话 ID 隔离上下文 |
| 工具调用 | Spring AI `ChatClient` + MCP 工具自动挂载，模型自主选择工具与参数 |
| 权限控制 | 私聊白名单（校验 Telegram **user id**），非白名单用户直接拒绝 |
| 会话隔离 | 私聊以 `u:{userId}` 为键天然一人一会话；群聊按 `g:{chatId}:u:{userId}` 区分 |
| 容错降级 | 单条消息处理异常不影响后续消息；MCP 或模型不可用时返回友好提示 |
| Prompt 约束 | 系统提示词强制要求「股票数据必须调用工具查询，不得凭记忆回答」 |

### 5. MCP 能力输出
`stock_mcp` 将数据查询能力封装为标准 MCP Server（SSE 协议，7 个工具），
任何支持 MCP 的 AI 客户端（Cline、Claude Desktop 等）均可直接接入：

```json
{ "mcpServers": { "stock-mcp": { "url": "http://127.0.0.1:7070/sse" } } }
```

---

## 三、技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | 4.0.8 / 4.1.1 | 两个应用采用独立版本，验证了跨小版本兼容 |
| Java | 21 | |
| Spring AI | 2.0.1 | MCP Server + MCP Client + ChatClient 全套能力 |
| MCP SDK | 2.0.0 | MCP 协议实现 |
| MyBatis-Plus | 3.5.17 | 使用 Boot 4 专用 starter |
| MySQL | 26.7.0 | Docker 部署 |
| TelegramBots | 10.3.0 | 发送 + 长轮询接收（模块化坐标） |
| jsoup / rome | 1.21.2 / 2.1.0 | 列表页解析 / RSS 解析 |
| AOP | `spring-boot-starter-aspectj` | Service 层耗时监控 |

> 项目落地过程中主动适配了 Spring Boot 4 的多项破坏性变更（starter 更名与模块化拆分、
> MyBatis-Plus 与 Spring AI 的版本线切换），并验证了 **Spring AI 2.0.1 在 Spring Boot 4.0.8 上的可用性**。

---

## 四、工程亮点（实测量化）

| 优化项 | 优化前 | 优化后 | 提升 |
|---|---|---|---|
| **单轮外部请求数** | 101 次 | **2 次** | **−98%** |
| **单轮数据库读行数** | 2,175,300 行 | **302 行** | **约 7200×** |
| **切面日志量** | 301 条/轮 | **1 条/轮** | **−99.7%** |
| **标签翻译成功率** | 24/100（76 条为 null） | **36/100（0 条 null）** | 数据质量修复 |
| **MCP 工具响应** | — | **86 ms / 167 ms** | — |

**关键设计决策**

1. **限流感知采集**：解析服务端 `ratelimit-remaining` / `ratelimit-reset` 响应头做额度守卫，
   叠加 **5 分钟结果缓存** 与 **60 秒最小重试间隔**，把单轮请求从 100 次压到 1 次，彻底规避 429。
2. **索引覆盖查询**：为 `(stock_code, pub_date_gmt)` 建立复合索引后，
   区间聚合查询由全表扫描转为 **覆盖索引扫描**（Covering index range scan），读行数下降 4 个数量级。
3. **SQL 聚合前置**：将「24 小时 / 3 日 / 1 周」三个窗口的热度统计由 3 次查询合并为
   **1 次条件聚合**，并配合索引，单轮查询数从 300 次降至 100 次。
4. **幂等入库**：以业务唯一键 `link` 判重（而非数据库自增主键），
   配合 `IN` 批量查询 + `saveBatch` 批量写入，实现「重复执行无副作用」。
5. **可观测性**：通过 AOP 统一记录 Service 层调用耗时并按慢/正常分级输出；
   精确控制切点范围，避免循环内高频调用污染日志。
6. **故障隔离**：MCP 客户端采用延迟初始化 + 容错启动，
   即使 MCP 服务不可用也不影响采集与推送主链路。

---

## 五、数据模型

单表 `us_stock_rss`（9 字段 / 2 索引），初始种子数据约 2400 行：

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | varchar(64) | 主键，雪花 ID（19 位） |
| `stock_code` | varchar(64) | 股票代码 |
| `title` / `title_zh` | varchar(2550) | 英文 / 中文标题 |
| `link` | varchar(255) | **业务唯一键**，幂等判重依据 |
| `pub_date_gmt` / `pub_date_bj` | datetime | GMT / 北京时间 |
| `tags` / `tags_en` | varchar(255) | 中文标签 / 英文原文标签 |

索引：`PRIMARY(id)` + `KEY idx_code_date(stock_code, pub_date_gmt)`

---

## 六、快速开始

```bash
# 1. 启动数据库（首次自动导入建表脚本与种子数据）
cp .env.example .env          # 填写 MYSQL_ROOT_PASSWORD / MYSQL_DATABASE / MYSQL_PORT
docker compose up -d

# 2. 生成配置（模板已提交，真实密钥仅存本地且不入库）
cp stock_web/src/main/resources/application-dev.example.yaml \
   stock_web/src/main/resources/application-dev.yaml
cp stock_mcp/src/main/resources/application-dev.example.yaml \
   stock_mcp/src/main/resources/application-dev.yaml

# 3. 启动 MCP 服务（可选，需常驻）
cd stock_mcp && ./mvnw spring-boot:run        # :7070

# 4. 启动采集与对话服务
cd stock_web && ./mvnw spring-boot:run        # :8080
```

配置项说明（`application-dev.example.yaml` 内含完整注释）：

| 配置 | 用途 |
|---|---|
| `spring.datasource.*` | 数据库连接 |
| `spring.ai.mcp.client.sse.connections` | 连接的 MCP Server 地址 |
| `spring.ai.openai.base-url / api-key / chat.model` | 大模型接入（OpenAI 兼容协议，DeepSeek / OpenRouter / 百炼均可） |
| `telegram.bot-token / chat-id` | 机器人凭据与推送群组 |
| `telegram.allowed-user-ids` | 私聊白名单 |
| `telegram.proxy-host / proxy-port` | 访问 Telegram API 的 HTTP 代理 |

**安全实践**：真实密码、Bot Token、API Key 仅存于本地 `application-dev.yaml` 与 `.env`，
两者均已在 `.gitignore` 中；仓库仅保留 `*.example.yaml` 模板。

---

## 七、项目结构

```
us_stock_monitor_dev/
├── docker-compose.yml                  # MySQL 编排（健康检查 + 初始化脚本挂载）
├── us_stock_monitor_dev.sql            # 建表 + 种子数据
├── stock_web/                          # 采集 / 推送 / AI 对话
│   └── src/main/java/com/kami/stock_web/
│       ├── StockScheduler.java          # 定时调度（cron 60s）
│       ├── api/                         # TelegramBot、TelegramApi、MCP 客户端预热
│       ├── service/                     # 采集、聊天、查询服务
│       ├── entity/ enums/ mapper/       # 实体、标签枚举、MyBatis-Plus Mapper
│       ├── aspect/                      # 耗时监控切面
│       └── utils/                       # RSS 下载、列表页解析、时区转换
└── stock_mcp/                          # MCP Server（数据查询能力输出）
    └── src/main/java/com/kami/stock_mcp/
        ├── tool/                        # MCP 工具定义
        ├── service/ mapper/ entity/     # 查询层
        └── aspect/                      # 工具调用日志
```

---

## 八、后续规划

- 抽取 `stock-common` 公共模块，消除两个应用间的重复实体与查询代码
- 采集侧支持多数据源扩展（当前单一数据源）
- 为 MCP Server 增加鉴权，支持跨机调用
- 补齐单元测试与集成测试用例

---

## 附：技术难点摘要

| 难点 | 处理方式 |
|---|---|
| 数据源强制 gzip 且客户端不自动解压，导致 XML 解析失败 | 依据 `Content-Encoding` 响应头手动解压后再解析 |
| 服务端限流严格（10 次 / 300 秒） | 额度感知 + 本地缓存 + 重试节流，请求量降低 98% |
| 标签仅在列表页最近 50 行内可见 | 明确数据边界，中英文双列存储并保留未收录标签原文 |
| 全表扫描导致聚合查询缓慢 | 复合索引 + 覆盖索引 + 聚合下推，读行数降低约 7200 倍 |
| Spring Boot 4 破坏性变更 | 逐个核对 starter 与依赖版本线，完成全套适配 |
| MCP 服务不可用会阻断应用启动 | 客户端延迟初始化 + 容错启动，实现故障隔离 |
