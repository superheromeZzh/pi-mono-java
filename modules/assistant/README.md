# Assistant 模块 — Channel 与 Memory 子模块对接文档

> 本文档面向 Agent 开发人员，详细说明 `campusclaw-assistant` 模块中 Channel（WebSocket Gateway）和 Memory（ChatMemory）两个子模块的架构设计、核心类、关键接口以及对接方式。

---

## 目录

- [整体架构](#整体架构)
- [Channel 子模块](#channel-子模块)
  - [架构概览](#channel-架构概览)
  - [核心接口](#channel-核心接口)
  - [WebSocket Gateway 实现](#websocket-gateway-实现)
  - [OpenClaw 协议格式](#openclaw-协议格式)
  - [关键流程](#channel-关键流程)
  - [配置项](#channel-配置项)
  - [对接方式](#channel-对接方式)
- [Memory 子模块](#memory-子模块)
  - [架构概览](#memory-架构概览)
  - [核心类](#memory-核心类)
  - [数据库表结构](#数据库表结构)
  - [消息序列化](#消息序列化)
  - [配置项](#memory-配置项)
  - [对接方式](#memory-对接方式)
- [Agent 侧集成指南](#agent-侧集成指南)

---

## 整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     coding-agent-cli                        │
│                                                              │
│  InteractiveMode ──► AgentSession ──► Agent ──► LLM API    │
│       │                    │                                │
│       │                    │                                │
│       │  ① ChatMemoryStore (消息持久化)                      │
│       │  ② AgentResponseEvent (响应事件发布)                  │
│       │                    │                                │
└───────┼────────────────────┼────────────────────────────────┘
        │                    │
        ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│                     assistant 模块                           │
│                                                              │
│  ┌─── Memory 子模块 ───┐  ┌─── Channel 子模块 ──────────┐ │
│  │ ChatMemoryStore     │  │ GatewayChannel               │ │
│  │   └─ ChatMemoryRepo │  │   └─ GatewayWebSocketHandler  │ │
│  │       └─ MyBatisRepo│  │       └─ OpenClaw Protocol    │ │
│  │           └─ Mapper │  │                              │ │
│  │               (GaussDB)│ │ AgentResponseEvent ◄──┐     │ │
│  └─────────────────────┘  └──────────────────────│────┘     │
│                                                  │           │
│                           Spring ApplicationEvent│           │
└─────────────────────────────────────────────────────────────┘
```

**两个子模块之间的连接方式**：通过 Spring `ApplicationEvent`。Agent 完成处理后发布 `AgentResponseEvent`，GatewayChannel 监听该事件并将结果通过 WebSocket 返回给客户端。

---

## Channel 子模块

### Channel 架构概览

```
com.campusclaw.assistant.channel/
├── Channel.java                    # 抽象接口
├── ChannelRegistry.java            # Channel 注册表 (@Service)
├── ChannelMessageReceivedEvent.java # 消息接收事件 (record)
├── MessageSubmitter.java           # 消息提交接口
└── gateway/                        # WebSocket Gateway 实现
    ├── GatewayChannel.java         # 核心: 会话管理 + 事件监听 (@Component)
    ├── GatewayWebSocketHandler.java # 核心: 协议处理 + 帧收发
    ├── WebSocketGatewayConfig.java  # Netty 服务器配置 (@Configuration)
    ├── WebSocketGatewayProperties.java # 配置属性
    ├── AgentResponseEvent.java      # Spring 事件: Agent 响应
    └── protocol/                    # OpenClaw 协议 DTO
        ├── GatewayFrame.java
        ├── ConnectParams.java
        ├── SessionsSendParams.java
        ├── HelloOkPayload.java
        ├── ChatEventPayload.java
        ├── AuthInfo.java
        ├── ClientInfo.java
        ├── ServerInfo.java
        ├── ErrorBody.java
        ├── FeaturesInfo.java
        └── PolicyInfo.java
```

### Channel 核心接口

#### `Channel` — 所有通道的抽象接口

```java
public interface Channel {
    /** 返回通道名称 */
    String getName();

    /** 向该通道的所有客户端广播消息 */
    void sendMessage(String message);
}
```

#### `MessageSubmitter` — 消息提交接口（由 Agent 侧实现）

```java
public interface MessageSubmitter {
    /**
     * 将消息提交到当前 Agent 会话。
     * @return true 如果成功提交
     */
    boolean submitMessage(String message);
}
```

**实现者**：`LoopManager`（coding-agent-cli 中的 `@Service`），通过 `BlockingQueue` 将消息投递到 Agent 的处理循环。

#### `ChannelRegistry` — 全局通道注册表

```java
@Service
public class ChannelRegistry {
    /** 注册一个 Channel */
    public void register(Channel channel);

    /** 按名称获取 Channel */
    public Channel get(String name);

    /** 获取最近注册的 Channel（通常用于默认通道） */
    public Channel getLatest();

    /** 获取所有已注册的 Channel */
    public Collection<Channel> getAll();
}
```

#### `ChannelMessageReceivedEvent` — 消息接收事件

```java
public record ChannelMessageReceivedEvent(
    String channelName,   // 来源通道名称
    String message,       // 消息内容
    Instant timestamp     // 接收时间（默认当前时间）
) {}
```

### WebSocket Gateway 实现

#### `GatewayChannel` — 核心组件

```
@Component (条件: pi.assistant.gateway.enabled=true)
```

**职责**：管理 WebSocket 会话生命周期，桥接 WebSocket 客户端与 Agent 会话。

**核心字段**：

| 字段 | 类型 | 用途 |
|------|------|------|
| `sessionContexts` | `Map<String, ChannelHandlerContext>` | channelId → Netty 连接上下文 |
| `sessionKeyToChannel` | `Map<String, String>` | sessionKey → channelId 映射 |
| `channelToSessionKey` | `Map<String, String>` | channelId → sessionKey 映射 |
| `pendingSessionsSend` | `Map<String, PendingRequest>` | reqId → 待完成的 sessions.send 请求 |

**核心方法**：

```java
public class GatewayChannel implements Channel {

    /** 注册新的 WebSocket 会话 */
    public void registerSession(String channelId, ChannelHandlerContext ctx);

    /** 移除会话并清理所有状态 */
    public void removeSession(String channelId);

    /** 注册待处理的 sessions.send 请求 */
    public void registerPendingSessionsSend(String reqId, String channelId, String sessionKey);

    /** 处理来自 WebSocket 客户端的消息，转发给 Agent */
    public void handleIncomingMessage(String channelId, String sessionKey, String messageContent);

    /** 监听 AgentResponseEvent，发送最终结果 */
    @EventListener
    public void onAgentResponse(AgentResponseEvent event);
}
```

**`onAgentResponse()` 工作流程**：
1. 遍历所有 pending 的 `sessions.send` 请求
2. 构造 `{type: "res", id: reqId, ok: true, payload: {status: "final", message: ...}}` 响应帧
3. 调用 `GatewayWebSocketHandler.sendResponseFrame()` 通过 WebSocket 发回
4. 清理已完成的 pending 请求

#### `GatewayWebSocketHandler` — 协议处理

```java
public class GatewayWebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {
```

**连接生命周期**：

```
channelActive()        → 客户端连接，创建 session 记录
userEventTriggered()   → WebSocket 握手完成
                          ├─ 发送 connect.challenge 事件
                          └─ 启动定时 tick 事件（心跳保活）
channelRead0()         → 收到文本帧，解析 JSON
                          ├─ type="req" → handleRequest()
                          └─ type="event" → handleClientEvent()
channelInactive()      → 客户端断开，清理所有状态
```

**请求处理 (`handleRequest`)**：

```
connect (无需认证)  → handleConnect()
                       ├─ 校验 token
                       ├─ 标记已认证
                       ├─ 注册 session
                       └─ 发送 hello-ok 响应

sessions.send (需认证) → handleSessionsSend()
                           ├─ 注册 pending 请求
                           ├─ 发送 {status: "accepted"} 确认
                           └─ gatewayChannel.handleIncomingMessage()

其他方法 (需认证)     → 返回 unknownMethod 错误
```

#### `WebSocketGatewayConfig` — Netty 服务器

```java
@Configuration (条件: pi.assistant.gateway.enabled=true)
```

实现 `SmartLifecycle` 接口，在 Spring 容器启动后自动绑定端口。

**Netty Pipeline**：
```
HttpRequestDecoder → HttpResponseEncoder → HttpObjectAggregator
    → WebSocketServerProtocolHandler → GatewayWebSocketHandler
```

### OpenClaw 协议格式

所有 WebSocket 通信使用 JSON 帧格式。

#### 顶层帧 `GatewayFrame`

```json
{
  "type": "req" | "res" | "event",
  "id": "请求 ID（res 类型必填）",
  "method": "方法名（req 类型）",
  "params": { ... },
  "event": "事件名（event 类型）",
  "payload": { ... },
  "ok": true | false,
  "error": { "code": "...", "message": "...", "retryable": false },
  "seq": 0,
  "stateVersion": { "presence": 0, "health": 0 }
}
```

#### 握手流程

```
服务端                                    客户端
  │                                         │
  ├──── connect.challenge ────────────────► │  (含 nonce, ts)
  │                                         │
  │  ◄──── connect (req) ─────────────────┤  (含 auth.token)
  │                                         │
  ├──── hello-ok (res) ──────────────────► │  (含 protocol, server, features)
  │                                         │
  │  ◄──── sessions.send (req) ──────────┤  (含 key, message)
  │                                         │
  ├──── {status: "accepted"} (res) ──────► │  (立即确认)
  │                                         │
  │         ... Agent 处理中 ...            │
  │                                         │
  ├──── {status: "final"} (res) ─────────► │  (最终结果，复用原 reqId)
  │                                         │
  ├──── tick (event) ─────────────────────► │  (每 30s 心跳)
```

#### 关键 DTO

| 类 | 用途 |
|----|------|
| `ConnectParams` | connect 请求参数：`{minProtocol, maxProtocol, client, auth}` |
| `AuthInfo` | 认证信息：`{token: "xxx"}` |
| `ClientInfo` | 客户端信息：`{name, version, os}` |
| `HelloOkPayload` | 连接成功响应：`{type, protocol, server, features, snapshot, policy}` |
| `SessionsSendParams` | 消息发送参数：`{key: "session-key", message: "..."}` |
| `ChatEventPayload` | 聊天事件：`{runId, sessionKey, seq, state, message, stopReason}` |
| `ErrorBody` | 错误信息：`{code, message, retryable, stack, status}` |

### Channel 关键流程

#### 完整消息处理流程

```
1. 客户端 WebSocket 连接 ws://127.0.0.1:18788/
2. GatewayWebSocketHandler.channelActive() → 记录 session
3. 握手完成 → 发送 connect.challenge
4. 客户端发送 connect 请求 → 校验 token → 注册 session → 发送 hello-ok
5. 客户端发送 sessions.send 请求
   ├─ registerPendingSessionsSend(reqId, channelId, sessionKey)
   ├─ 发送 {status: "accepted"} 确认
   └─ GatewayChannel.handleIncomingMessage()
       └─ messageSubmitter.submitMessage(message)
           └─ LoopManager 将消息投递到 Agent 处理队列
6. Agent 完成处理
   └─ InteractiveMode 发布 AgentResponseEvent(replyText)
7. GatewayChannel.onAgentResponse() 监听到事件
   ├─ 遍历 pendingSessionsSend
   ├─ 构造 {status: "final"} 响应帧
   └─ handler.sendResponseFrame(ctx, reqId, payload)
8. 客户端收到最终响应，按 reqId 关联请求
```

### Channel 配置项

配置前缀：`pi.assistant.gateway`

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | `false` | 是否启用 Gateway |
| `name` | `"gateway"` | Gateway 名称 |
| `port` | `18788` | 监听端口 |
| `path` | `"/"` | WebSocket 路径 |
| `token` | 无 | 认证 token（不设置则不校验） |
| `tickIntervalMs` | `30000` | 心跳间隔（毫秒） |
| `protocolVersion` | `3` | 协议版本号 |
| `serverVersion` | `"1.0.0"` | 服务版本号 |
| `maxPayload` | `16777216` | 最大 payload 字节数（16MB） |
| `maxBufferedBytes` | `1048576` | 最大缓冲字节数（1MB） |

### Channel 对接方式

#### 1. 接入新的客户端

客户端需要实现 OpenClaw 协议的握手流程：
1. 连接 WebSocket
2. 收到 `connect.challenge` 后，发送 `connect` 方法（含 token）
3. 收到 `hello-ok` 后，发送 `sessions.send` 方法
4. 收到 `{status: "accepted"}` 后等待
5. 收到 `{status: "final"}` 响应获取 Agent 结果

#### 2. 自定义 Channel 实现

```java
@Component
public class CustomChannel implements Channel, MessageSubmitter {
    private final ChannelRegistry registry;

    @PostConstruct
    public void init() {
        registry.register(this);
    }

    @Override
    public String getName() { return "custom"; }

    @Override
    public void sendMessage(String message) {
        // 你的推送逻辑
    }

    @Override
    public boolean submitMessage(String message) {
        // 你的消息投递逻辑，返回 true 表示成功
        return true;
    }
}
```

#### 3. 监听 Agent 响应

如果你的 Channel 需要在 Agent 完成处理后做额外处理：

```java
@Component
public class CustomResponseHandler {

    @EventListener
    public void onAgentResponse(AgentResponseEvent event) {
        String replyText = event.getMessage();
        // 处理 Agent 响应
    }
}
```

---

## Memory 子模块

### Memory 架构概览

```
com.campusclaw.assistant.memory/
├── ChatMemoryEntity.java            # 数据库实体 (record)
├── ChatMemoryRepository.java        # 持久化接口
├── ChatMemoryStore.java             # 服务层 (@Service)
└── MyBatisChatMemoryRepository.java # MyBatis 实现

com.campusclaw.assistant.mapper/
└── ChatMemoryMapper.java            # MyBatis Mapper 接口

src/main/resources/
└── schema.sql                       # GaussDB 建表语句
```

### Memory 核心类

#### `ChatMemoryEntity` — 数据库记录

```java
public record ChatMemoryEntity(
    Long id,               // 主键（自增）
    String conversationId,  // 会话 ID
    String role,            // 消息角色: "user" | "assistant" | "toolResult"
    String content,         // 消息内容（JSON 序列化）
    int sequence,          // 消息序号（同会话内递增）
    LocalDateTime createdAt // 创建时间
) {
    // 简化构造器（用于新增记录）
    public ChatMemoryEntity(String conversationId, String role, String content, int sequence) {
        this(null, conversationId, role, content, sequence, null);
    }
}
```

#### `ChatMemoryRepository` — 持久化接口

```java
public interface ChatMemoryRepository {

    /** 加载指定会话的所有消息（按 sequence 排序） */
    List<Message> load(String conversationId);

    /** 追加消息到指定会话 */
    void append(String conversationId, List<Message> messages);

    /** 清除指定会话的所有消息 */
    void clear(String conversationId);
}
```

#### `ChatMemoryStore` — 服务层

```java
@Service
public class ChatMemoryStore {

    private final ChatMemoryRepository repository;

    public List<Message> load(String conversationId);
    public void append(String conversationId, List<Message> messages);
    public void clear(String conversationId);
}
```

**设计**：Facade 模式，委托给 `ChatMemoryRepository`。作为 Spring Bean 供其他模块注入。

#### `MyBatisChatMemoryRepository` — MyBatis 实现

```java
public class MyBatisChatMemoryRepository implements ChatMemoryRepository {
```

**关键实现细节**：

| 方法 | 实现逻辑 |
|------|----------|
| `load()` | 查询 DB → 遍历 `ChatMemoryEntity` → `objectMapper.readValue(content, Message.class)` 反序列化 |
| `append()` | 查询当前最大 sequence → 从 `nextSequence` 开始逐条插入 → 通过 `extractRole()` 提取角色 → `objectMapper.writeValueAsString(message)` 序列化 |
| `clear()` | 直接删除该 conversationId 的所有记录 |

**角色提取**（使用 Java pattern matching）：
```java
private String extractRole(Message message) {
    return switch (message) {
        case UserMessage m -> "user";
        case AssistantMessage m -> "assistant";
        case ToolResultMessage m -> "toolResult";
    };
}
```

#### `ChatMemoryMapper` — MyBatis Mapper

```java
@Mapper
public interface ChatMemoryMapper {

    @Select("SELECT id, conversation_id, role, content, sequence, created_at "
          + "FROM chat_memory WHERE conversation_id = #{conversationId} ORDER BY sequence")
    List<ChatMemoryEntity> selectByConversationId(String conversationId);

    @Insert("INSERT INTO chat_memory (conversation_id, role, content, sequence) "
          + "VALUES (#{conversationId}, #{role}, #{content}, #{sequence})")
    void insert(ChatMemoryEntity entity);

    @Delete("DELETE FROM chat_memory WHERE conversation_id = #{conversationId}")
    void deleteByConversationId(String conversationId);
}
```

### 数据库表结构

```sql
CREATE TABLE IF NOT EXISTS chat_memory (
    id              BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    conversation_id VARCHAR(255) NOT NULL,
    role            VARCHAR(50)  NOT NULL,
    content         TEXT         NOT NULL,
    sequence        INT          NOT NULL,
    created_at      TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_chat_memory_conversation
    ON chat_memory (conversation_id, sequence);
```

| 列 | 类型 | 说明 |
|----|------|------|
| `id` | BIGINT | 自增主键 |
| `conversation_id` | VARCHAR(255) | 会话标识（对应 `SessionManager.getSessionId()`） |
| `role` | VARCHAR(50) | 消息角色 |
| `content` | TEXT | 消息 JSON 内容（完整的 `Message` 对象序列化） |
| `sequence` | INT | 消息在会话内的序号 |
| `created_at` | TIMESTAMP | 记录创建时间 |

**兼容性**：使用 PostgreSQL 标准语法，兼容 GaussDB。

### 消息序列化

消息使用 Jackson 的多态反序列化。`Message` 接口的定义：

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "role")
@JsonSubTypes({
    @JsonSubTypes.Type(value = UserMessage.class, name = "user"),
    @JsonSubTypes.Type(value = AssistantMessage.class, name = "assistant"),
    @JsonSubTypes.Type(value = ToolResultMessage.class, name = "toolResult")
})
public sealed interface Message permits UserMessage, AssistantMessage, ToolResultMessage {}
```

`content` 列存储的是完整的 `Message` 对象 JSON，例如：

```json
{
  "role": "user",
  "content": [
    { "type": "text", "text": "帮我修复这个 bug" }
  ],
  "timestamp": 1712345678000
}
```

反序列化时通过 `"role"` 字段自动路由到正确的子类型。

### Memory 配置项

通过环境变量或 `application.yml` 配置：

```yaml
spring:
  datasource:
    url: ${GAUSSDB_URL:jdbc:postgresql://localhost:5432/pi_assistant}
    username: ${GAUSSDB_USER:root}
    password: ${GAUSSDB_PASSWORD:root}
    driver-class-name: org.postgresql.Driver

mybatis:
  configuration:
    map-underscore-to-camel-case: true   # 数据库下划线 → Java 驼峰
```

**环境变量**：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `GAUSSDB_URL` | `jdbc:postgresql://localhost:5432/pi_assistant` | 数据库连接 URL |
| `GAUSSDB_USER` | `root` | 数据库用户名 |
| `GAUSSDB_PASSWORD` | `root` | 数据库密码 |

### Memory 对接方式

#### 1. 直接注入 ChatMemoryStore

```java
@Autowired
private ChatMemoryStore chatMemoryStore;

// 加载历史
List<Message> history = chatMemoryStore.load("session-abc123");

// 追加消息
chatMemoryStore.append("session-abc123", List.of(userMessage, assistantMessage));

// 清除会话
chatMemoryStore.clear("session-abc123");
```

#### 2. 自定义 Repository 实现

如果需要对接其他存储（如 Redis、Elasticsearch），实现 `ChatMemoryRepository` 接口即可：

```java
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    @Override
    public List<Message> load(String conversationId) { /* ... */ }

    @Override
    public void append(String conversationId, List<Message> messages) { /* ... */ }

    @Override
    public void clear(String conversationId) { /* ... */ }
}
```

`CampusClawAssistantAutoConfiguration` 中的 Bean 注册使用了 `@ConditionalOnMissingBean`，自定义实现会自动优先。

#### 3. 在 Agent 事件流中接入

```java
@EventListener
public void onMessageEnd(MessageEndEvent event) {
    if (event.message() instanceof AssistantMessage msg) {
        chatMemoryStore.append(conversationId, List.of(msg));
    }
}
```

---

## Agent 侧集成指南

### Bean 自动装配

`CampusClawAssistantAutoConfiguration` 会自动注册以下 Bean：

| Bean | 条件 | 类型 |
|------|------|------|
| `ObjectMapper` | 无同类型 Bean | Jackson ObjectMapper（含 JavaTimeModule） |
| `ChatMemoryRepository` | `ChatMemoryMapper` 存在 | `MyBatisChatMemoryRepository` |
| `ChatMemoryStore` | 自动（`@Service`） | 委托给 `ChatMemoryRepository` |
| `ChannelRegistry` | 自动（`@Service`） | Channel 注册表 |
| `GatewayChannel` | `pi.assistant.gateway.enabled=true` | WebSocket 通道实现 |

### 优雅降级

所有数据库操作都设计了降级路径：

- **无数据库连接**：`ChatMemoryStore` Bean 创建失败 → `InteractiveMode` 中 try-catch 捕获 → `chatMemoryStore = null` → 跳过所有 DB 操作
- **session 恢复**：优先从 ChatMemory 加载，失败则 fallback 到 JSONL 文件
- **消息持久化失败**：try-catch 捕获 → 打印 stderr 日志 → 不影响 Agent 正常运行

### 模块依赖

```xml
<!-- 在你的模块中依赖 assistant -->
<dependency>
    <groupId>com.campusclaw</groupId>
    <artifactId>campusclaw-assistant</artifactId>
</dependency>
```

该依赖会传递引入 `campusclaw-ai`、`campusclaw-agent-core`、Spring Context、Netty、MyBatis、PostgreSQL 驱动。
