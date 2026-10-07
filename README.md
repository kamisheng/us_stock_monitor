# 📈 US Stock Monitor — 美股异动监控与 AI 查询

把「美股异动新闻采集 → 落库 → Telegram 推送 → AI 客户端查询」串起来的自托管系统。
两个 Spring Boot 应用 + 一个 MySQL，全部本地运行，除 Telegram 和大模型 API 外不依赖任何第三方服务。

## 架构

```
                    ┌─────────────────────────────────────────────┐
                    │  stocktitan.net                             │
                    │   /rss            (30 req / 300s)           │
                    │   /news/live.html (10 req / 300s)           │
                    └───────────────┬─────────────────────────────┘
                                    │ 抓取（限流感知 + 5 分钟缓存）
┌───────────────────────────────────▼──────────────────────────────┐
│ stock_web   Spring Boot 4.0.8 · Java 21 · :8080                  │
│   StockScheduler   每 60s 一轮                                    │
│   RssServiceImpl   抓取 → 解析标签 → 判重 → 批量入库                │
│   TelegramApi      推送新消息到群                                  │
│   ServiceLogAspect 慢调用监控                                      │
└───────────────┬──────────────────────────────┬───────────────────┘
                │ JDBC                         │ 落库后推送
        ┌───────▼────────┐              ┌──────▼───────────┐
        │ MySQL 26.7     │              │ Telegram 群       │
        │ us_stock_rss   │              └──────────────────┘
        └───────▲────────┘
                │ 只读查询
┌───────────────┴──────────────────────────────────────────────────┐
│ stock_mcp   Spring Boot 4.1.1 · Spring AI 2.0.1 · :7070          │
│   MCP Server（SSE 协议，7 个工具）                                 │
│   getStockByCode / getStockByCodeBetweenData /                   │
│   queryStockBetweenData / sendEmail / getDate / ...              │
└───────────────▲──────────────────────────────────────────────────┘
                │ MCP over SSE
        ┌───────┴────────────────┐
        │ Cline / Claude Desktop │
        └────────────────────────┘
```

## 技术栈

| 组件 | 版本 | 说明 |
|---|---|---|
| Spring Boot | **4.0.8**（stock_web）/ **4.1.1**（stock_mcp） | 4.x 的模块化改动踩了不少坑，见下文 |
| Java | 21 | |
| MyBatis-Plus | 3.5.17 | 必须用 `mybatis-plus-spring-boot4-starter`；boot3 版依赖 mybatis-spring 3.x，只适配 Spring 6 |
| MySQL | 26.7.0（docker `mysql:latest`） | 宿主端口 5506 → 容器 3306 |
| rometools rome | 2.1.0 | RSS 解析 |
| jsoup | 1.21.2 | 列表页标签抓取 |
| TelegramBots | 10.3.0 | 10.x 起模块化：`telegrambots-client` / `-longpolling` / `-springboot-longpolling-starter`（老坐标 `org.telegram:telegrambots` 停更在 6.9.7.1） |
| Spring AI | 2.0.1 | **2.0.x 对应 Boot 4.1.x；1.1.x 及以前只适配 Boot 3.x** |
| AOP | `spring-boot-starter-aspectj` | Boot 4 里改的名；`spring-boot-starter-aop` 停更在 4.0.0-M2 |

## 快速开始

```bash
# 1. 数据库：首次启动自动导入 us_stock_monitor_dev.sql（约 2400 行种子数据）
cp .env.example .env          # 填 MYSQL_ROOT_PASSWORD / MYSQL_DATABASE / MYSQL_PORT
docker compose up -d

# 2. 配置（含真实密码，已被 gitignore，不会提交）
cp stock_web/src/main/resources/application-dev.example.yaml \
   stock_web/src/main/resources/application-dev.yaml     # 填入库密码、Bot Token、LLM Key
cp stock_mcp/src/main/resources/application-dev.example.yaml \
   stock_mcp/src/main/resources/application-dev.yaml     # 填入库密码、邮箱授权码

# 3. 采集 + 推送
cd stock_web && ./mvnw spring-boot:run

# 4. MCP 服务（要用 AI 客户端查询时才需要，必须常驻）
cd stock_mcp && ./mvnw spring-boot:run        # 监听 7070
```

> 启动顺序：先 `stock_mcp`（可选），再 `stock_web`。
> `stock_web` 里的 `spring.ai.mcp.client.initialized` 已置 `false`，MCP 没开也不影响采集推送。

## 数据模型

单表 `us_stock_rss`：

| 列 | 类型 | 说明 |
|---|---|---|
| `id` | varchar(64) | 主键，MyBatis-Plus `ASSIGN_ID` 生成的 19 位雪花串 |
| `stock_code` | varchar(64) | 股票代码 |
| `title` / `title_zh` | varchar(2550) | 英文标题 / 中文标题 |
| `link` | varchar(255) | **业务唯一键**，判重依据 |
| `pub_date_gmt` / `pub_date_bj` | datetime | GMT / 北京时间 |
| `tags` / `tags_en` | varchar(255) | 中文标签 / 英文原文标签 |

索引：`PRIMARY(id)` + `KEY idx_code_date(stock_code, pub_date_gmt)`

## 核心设计与踩坑记录

### 1. RSS 被服务端强制 gzip，而客户端不会自动解压

`stocktitan.net` 在客户端**没有**发送 `Accept-Encoding` 的情况下依然返回 `Content-Encoding: gzip`，
而 `HttpURLConnection` 从不自动解压 → gzip 字节流（`1f 8b`）被当成 XML 解析：

```
ParsingFeedException: Invalid XML: Error on line 1: 前言中不允许有内容
```

**修复**：自己读 `Content-Encoding` 响应头，命中 gzip 就套 `GZIPInputStream`，再交给 Rome 解析。

### 2. 限流：从「第 11 次即封」到分钟级安全

实测服务端给出的限额：

| 端点 | 限额 |
|---|---|
| `/rss` | `ratelimit-policy: 30;w=300`（30 次 / 300 秒） |
| `/news/live.html` | `ratelimit-policy: 10;w=300`（10 次 / 300 秒） |

**原始 bug**：100 条新闻，每条都去抓一次列表页 → **100 次请求**，第 11 次就吃 429。
**修复后**：一轮只抓 **1 次** 列表页（**100 → 1，降低 99%**），并叠加三层保护：

- **5 分钟缓存**（与对方 300 秒限流窗口对齐）；
- **最小重试间隔 60 秒**（失败时缓存不刷新，防止跟着调度频率疯狂重试）；
- **额度守卫**：读 `ratelimit-remaining` / `ratelimit-reset`，见底就不发请求。

另外实测发现**对方真实限流比宣称的更严**：曾在 `ratelimit-remaining: 7` 时就返回 429。

### 3. 标签只存在于列表页「最近 50 行」

列表页只渲染最近 **50 行**，实测覆盖窗口 **5.7 ~ 8.6 小时**；而 RSS 有 **100 条**。统计结果：

| 分类 | 条数 |
|---|---|
| 能在列表页找到的 | 49 |
| 其中真正带标签的 | 36 |
| 在窗口外（永远拿不到标签） | 51 |

所以约一半 RSS 条目标签为空属**结构性限制**，不是 bug。

### 4. 标签翻译：76/100 为 null → 全部修复

**错误写法**：把多个标签拼成整串，再拿去和枚举里单个 key 做 `equalsIgnoreCase`：

```java
StockTag.getTagValue(getTagsStr(tags))   // "private placement, penny stock" 永远匹配不上 → null
```

100 条里 **76 条是 null**（51 条不在页面 + 13 条页面无标签 + 9 条多标签 + 3 条枚举键不匹配）。
**修复**：逐个 key 翻译再 join，并补上 `dividends`（复数）枚举键 → 未收录标签保留原文，
**null 归零，36 条正确输出中文**。

### 5. 判重必须用业务键，不能用主键

`id` 是**插入时**才由 MyBatis-Plus 生成的，插入前恒为 null，`getById(null)` 永远返回 null
→ 结果是每分钟重复插入 100 条。改用 `link` 做业务唯一键 + 一次 `IN` 查询取回已存在的 link，
再做 `saveBatch(toSave, 500)`。

### 6. MyBatis 的「空行返回 null」陷阱

条件聚合查询 `SUM(...)` 在无匹配行时会返回 **1 行、3 个 NULL**；而 MyBatis 的
`returnInstanceForEmptyRow` 默认为 `false` —— **整行没有任何列被成功映射时返回的是 null 而不是空对象**
→ 调用方 NPE。两处修复：

```sql
SELECT COALESCE(SUM(...), 0) AS counts24Hour, ...   -- ① 别名必须等于实体字段名
```

```java
// ② 单参数方法要么用 #{startTime}，要么加 @Param 后才能写 #{dto.startTime}
List<StockCountsVO> query(@Param("dto") QueryCountsDTO dto);
```

不加 `@Param` 却写 `#{dto.startTime}` 会抛
`ReflectionException: There is no getter for property named 'dto'`。

### 7. 索引：读行数从 218 万降到 302

`WHERE stock_code=? AND pub_date_gmt BETWEEN ? AND ?` 原本**只有主键索引**，每次全表扫描。
100 只股票一轮的实测 `Innodb_rows_read` 增量：

| 方案 | 读行数 / 轮 |
|---|---|
| 300 次 count，无索引 | **2,175,300** |
| 合并成 1 条条件聚合，无索引 | 483,400 |
| 300 次 count + 索引 | 1,224 |
| **合并 + 索引（最终方案）** | **302** |

即 **约 7200 倍** 改善，且该查询是 **覆盖索引** 扫描（`Covering index range scan`），无需回表。

### 8. AOP 切点按「方法声明所在类型」匹配

`execution(* com.kami.stock_web.service.*.*(..))` 会切中 `service.impl` 下的实现类 ——
因为 AspectJ 是按**方法声明所在的类型**匹配，而两个接口正好在 `com.kami.stock_web.service` 包。
副作用：循环里每条新闻调 3 次的热度统计也被切中，一轮刷 **300 条** 日志。
排除后 **301 条/轮 → 1 条/轮**。

### 9. Spring Boot 4 的两个改名 + 一个启动期强依赖

| 坑 | 现象 | 解决 |
|---|---|---|
| `spring-boot-starter-aop` 没有 GA 版本 | 依赖找不到 | 改用 `spring-boot-starter-aspectj` |
| MyBatis-Plus boot3 starter 不适配 | 运行期报错 | 改用 `mybatis-plus-spring-boot4-starter` |
| `spring.ai.mcp.client.initialized` 默认 `true` | **MCP 没开时整个 web 起不来**（`BeanCreationException: mcpSyncClients`） | 置 `false`，由代码自行初始化并 try/catch |

### 10. Telegram 必须显式走 HTTP 代理

不加代理直接发送会报 `Unable to execute sendmessage method`。配置里显式指定
`telegram.proxy-host/proxy-port`，不依赖系统 TUN，换节点后立即生效。

另外两个细节：**Spring 不读 `.env` 文件**（那是 docker compose 的约定，Spring 只认环境变量 /
`-D` 参数 / 命令行参数 / `application*.yaml`）；群 id 是负数，写 YAML 时必须加引号
`chat-id: "-100xxxxxxxxxx"`。

## 实测数字速查

| 指标 | 数值 |
|---|---|
| 一轮处理的 RSS 条目 | 100 条 |
| 一轮实际外部请求数 | 1 次 RSS + 1 次列表页（原先 101 次） |
| 列表页标签抓取耗时 | 约 0.6 ~ 7 秒（网络波动） |
| 索引优化后 DB 读行数 | **302 行 / 轮**（原先 2,175,300） |
| 切面日志 | **1 条 / 轮**（原先 301 条） |
| 标签翻译 | 36/100 出中文，null **0** 条（原先 76 条 null） |
| MCP 工具调用延迟 | **86 ms**（getDate）/ **167 ms**（区间聚合查询） |
| MCP 已注册工具数 | 7 个 |
| Maven 依赖 | 190 个构件，**0** 版本冲突 |

## MCP Server 用法

服务端**默认走 SSE 协议**（不写 `spring.ai.mcp.server.protocol` 时实测 `/mcp` 返回 404、`/sse` 返回 200）：

| 项 | 值 |
|---|---|
| 客户端连接 URL | `http://127.0.0.1:7070/sse` |
| 消息端点 | `/mcp/message?sessionId=...`（服务端通过 `event:endpoint` 下发，客户端自动使用） |
| 传输类型 | **SSE (Legacy)** |

Cline / Cherry Studio 配置：

```json
{ "mcpServers": { "stock-mcp": { "url": "http://127.0.0.1:7070/sse", "timeout": 120 } } }
```

想换成 Streamable HTTP（端点 `/mcp`）：加 `spring.ai.mcp.server.protocol: STREAMABLE`。

工具的写法（`@Tool` 与 `@McpTool` 都会被扫描注册，无需手写 `ToolCallbackProvider`）：

```java
@Component
public class StockTool {
    @Tool(description = "在起始结束时间内查询股票异动次数大于指定次数的股票，时间为北京时间 yyyy-MM-dd HH:mm:ss")
    public List<StockCountsVO> queryStockBetweenData(String counts, String startTime, String endTime) {
        return stockService.queryStockBetweenData(counts, startTime, endTime);
    }
}
```

调用效果（2025-12-01 ~ 2025-12-31，异动次数 ≥ 5）：

```
SMX(34) SHEL(8) FCPT(7) DEC(6) HKD(6) OWLS(6) CNS(5) DGNX(5) ECDA(5) KKR(5) SIDU(5) TNMG(5)   → 12 只
（改成严格 "> 5" 则为 6 只 —— 注意 HAVING 用 >= 还是 >）
```

## Telegram 机器人

| 能力 | 状态 |
|---|---|
| 群推送新消息 | ✅ `telegram.chat-id` 配置目标群 |
| 私聊 AI 对话（LLM + MCP 工具调用） | 🚧 进行中 |
| 指令式查询（`/count 5 2025-12-01 2025-12-31`） | 🔜 计划中 |

**私聊 vs 群聊**（实测差异）：

| | 群聊 | 私聊 |
|---|---|---|
| 收普通消息 | ❌ 隐私模式默认开启（`can_read_all_group_messages=false`），必须 @ 它 | ✅ 全部收到 |
| `chat.id` | 负数（超级群 `-100` 开头） | 正数（= 用户 id） |
| 会话隔离 | 全群共用一个 chatId，上下文会串味 | 天然一人一会话 |

**并发说明**：`telegrambots-longpolling:10.3.0` 只提供 `LongPollingSingleThreadUpdateConsumer`，
其内部是 `Executors.newSingleThreadExecutor(...)` —— **更新是排队串行消费的**，
一个人的复杂查询会挡住其他人；要并发需自己把业务丢到自定义线程池。

## 已知限制 / Roadmap

- **标签窗口只有约 6 小时**：新闻滑出列表页前 50 行后就永远拿不到标签，约 50% 条目标签为空。
- **长轮询独占 token**：同一 bot token 不能被两个进程同时 `getUpdates`，否则 409 Conflict。
- **`stock_mcp` 必须常驻**：独立进程，停了所有工具调用失效（已用 `initialized: false` 保证不影响采集推送）。
- **两个模块存在重复代码**：`USStockRss`、`StockService`、`StockTitanCrawler`、`GMTDateConverter`
  等 9 个类两边各一份，计划抽 `stock-common` 公共模块。
- **MCP 无鉴权**：当前监听所有网卡且无认证，跨机调用前需加鉴权。
- **`EmailTool` 有越权风险**：接入 LLM 自动决策后模型可能自行发信，需加白名单。
- 计划：Telegram 私聊 AI 对话（白名单 + `ChatMemory` + MCP 工具）、`stock-common` 公共模块、标签回补任务。

## 安全提示

真实密码 / token / api-key **只放本地**，不要提交：

- `application-dev.yaml` 与 `.env` 已在 `.gitignore` 中；
- 仓库里只保留 `application-dev.example.yaml` 模板；
- 已提交过敏感信息的仓库，请务必**轮换凭据**（改密码 / BotFather 撤销 token / 重新生成授权码），
  因为 Git 历史里的内容无法彻底抹除。
