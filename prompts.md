# Pi-Mono Java 开发 Prompts

每个 prompt 对应一个需求，直接复制到新的 Claude Code 会话中执行。
**按依赖顺序排列**，从上到下依次执行。

**通用前置说明**（每个 prompt 已包含，无需额外添加）：
- 项目路径：`/Users/z/campusclaw/`
- 参考文档：`campusclaw-review.md`（计划）、`pi-mono-typescript-architecture.md`（TS 架构）
- 构建命令需带 JAVA_HOME：`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew ...`

---

## Phase 1: AI 核心类型（campusclaw-ai）

> 5 个需求可并行，但建议按以下顺序：AI-002 → AI-005 → AI-001 → AI-003 → AI-004

### 1.1 AI-002 ContentBlock 类型

```
请基于以下两个文档实现 AI-002 ContentBlock 类型需求：
- campusclaw-review.md（项目计划）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.2 ContentBlock 类型）

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/types/ 下创建
2. 使用 Java 21 sealed interface + record 实现：
   - sealed interface ContentBlock permits TextContent, ImageContent, ThinkingContent, ToolCall
   - record TextContent(String text, String textSignature) — textSignature 可选
   - record ImageContent(String data, String mimeType) — data 为 base64
   - record ThinkingContent(String thinking, String thinkingSignature, boolean redacted) — thinkingSignature 可选
   - record ToolCall(String id, String name, Map<String, Object> arguments, String thoughtSignature) — thoughtSignature 可选
3. 可选字段使用 @Nullable 注解（jakarta.annotation.Nullable）
4. 添加 Jackson 序列化注解（@JsonTypeInfo + @JsonSubTypes），基于 type 字段做多态序列化
5. 编写单元测试验证：创建实例、Jackson 序列化/反序列化、多态反序列化
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
7. 测试通过后 commit，消息格式：feat(ai): implement ContentBlock type hierarchy (AI-002)
```

### 1.2 AI-005 Usage & Cost 类型

```
请基于以下两个文档实现 AI-005 Usage & Cost 类型需求：
- campusclaw-review.md（项目计划）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.3 Usage & Cost）

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/types/ 下创建
2. 使用 Java 21 record 实现：
   - record Cost(double input, double output, double cacheRead, double cacheWrite, double total)
   - record Usage(int input, int output, int cacheRead, int cacheWrite, int totalTokens, Cost cost)
3. 提供静态工厂方法 Usage.empty() 返回零值实例，Cost.empty() 同理
4. 添加 Jackson 序列化注解
5. 编写单元测试验证：创建实例、empty() 工厂、序列化/反序列化
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
7. 测试通过后 commit，消息格式：feat(ai): implement Usage and Cost types (AI-005)
```

### 1.3 AI-001 Message 类型体系

```
请基于以下两个文档实现 AI-001 Message 类型体系需求：
- campusclaw-review.md（项目计划）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.1 Message 类型体系）

前置条件：AI-002 ContentBlock 和 AI-005 Usage 已在 com.campusclaw.ai.types 下实现。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/types/ 下创建
2. 使用 Java 21 sealed interface + record 实现：
   - sealed interface Message permits UserMessage, AssistantMessage, ToolResultMessage
   - record UserMessage(List<ContentBlock> content, long timestamp)
     - 提供便捷构造：接受纯 String，自动包装为 List.of(new TextContent(text, null))
   - record AssistantMessage(List<ContentBlock> content, String api, String provider,
     String model, String responseId, Usage usage, StopReason stopReason,
     String errorMessage, long timestamp)
     - responseId、errorMessage 可选
   - record ToolResultMessage(String toolCallId, String toolName,
     List<ContentBlock> content, Object details, boolean isError, long timestamp)
     - details 可选
   - enum StopReason { STOP, LENGTH, TOOL_USE, ERROR, ABORTED }
     - 每个值携带 @JsonValue String value（如 "stop"、"toolUse"）
3. 添加 Jackson 多态序列化注解（@JsonTypeInfo 基于 role 字段区分）
4. 编写单元测试覆盖：创建、序列化/反序列化、UserMessage 便捷构造、多态反序列化
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement Message type hierarchy (AI-001)
```

### 1.4 AI-003 Tool 定义类型

```
请基于以下两个文档实现 AI-003 Tool 定义类型需求：
- campusclaw-review.md（项目计划）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.5 Tool 定义）

前置条件：AI-001 Message 已在 com.campusclaw.ai.types 下实现。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/types/ 下创建
2. 使用 Java 21 record 实现：
   - record Tool(String name, String description, JsonNode parameters)
     - parameters 是 JSON Schema，使用 com.fasterxml.jackson.databind.JsonNode
   - record Context(String systemPrompt, List<Message> messages, List<Tool> tools)
     - systemPrompt 和 tools 可选（@Nullable）
3. 添加 Jackson 序列化注解
4. 编写单元测试：构建带 JSON Schema 的 Tool 实例、Context 创建、序列化/反序列化
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement Tool and Context types (AI-003)
```

### 1.5 AI-004 Model 定义类型

```
请基于以下两个文档实现 AI-004 Model 定义类型需求：
- campusclaw-review.md（项目计划）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.4 Model 定义、1.11 Provider 类型）

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/types/ 下创建
2. 使用 Java 21 record + enum 实现：
   - enum Provider — 每个值携带 @JsonValue String value：
     ANTHROPIC("anthropic"), OPENAI("openai"), GOOGLE("google"),
     GOOGLE_VERTEX("google-vertex"), MISTRAL("mistral"),
     AMAZON_BEDROCK("amazon-bedrock"), AZURE_OPENAI("azure-openai-responses"),
     OPENAI_CODEX("openai-codex")
   - enum Api — 每个值携带 @JsonValue String value：
     ANTHROPIC_MESSAGES("anthropic-messages"), OPENAI_RESPONSES("openai-responses"),
     OPENAI_COMPLETIONS("openai-completions"), BEDROCK_CONVERSE_STREAM("bedrock-converse-stream"),
     GOOGLE_GENERATIVE_AI("google-generative-ai"), GOOGLE_VERTEX("google-vertex"),
     MISTRAL_CONVERSATIONS("mistral-conversations"), AZURE_OPENAI_RESPONSES("azure-openai-responses"),
     OPENAI_CODEX_RESPONSES("openai-codex-responses")
   - enum InputModality { TEXT, IMAGE } — 携带 @JsonValue
   - record ModelCost(double input, double output, double cacheRead, double cacheWrite) — $/百万 token
   - record Model(String id, String name, Api api, Provider provider, String baseUrl,
     boolean reasoning, List<InputModality> inputModalities, ModelCost cost,
     int contextWindow, int maxTokens, Map<String, String> headers)
     - headers 可选
3. 枚举需要 @JsonCreator 反序列化支持（从 string → enum）
4. 编写单元测试
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement Model, Provider and Api types (AI-004)
```

---

## Phase 2: AI 基础设施（campusclaw-ai）

> 建议顺序：AI-011 → AI-006 → AI-007 → AI-008 → AI-009 → AI-010 → AI-012 → AI-013

### 2.1 AI-011 StreamOptions 类型

```
请基于以下两个文档实现 AI-011 StreamOptions 类型需求：
- campusclaw-review.md（项目计划，Phase 2）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.9 StreamOptions）

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/types/ 下创建
2. 使用 Java 21 record 实现：
   - record StreamOptions(Double temperature, Integer maxTokens, String apiKey,
     Transport transport, CacheRetention cacheRetention, String sessionId,
     Map<String, String> headers, Long maxRetryDelayMs, Map<String, Object> metadata)
     - 所有字段可选（@Nullable）
   - record SimpleStreamOptions 继承 StreamOptions 所有字段并添加：
     ThinkingLevel reasoning, ThinkingBudgets thinkingBudgets
   - enum Transport { SSE, WEBSOCKET, AUTO }
   - enum CacheRetention { NONE, SHORT, LONG }
   - enum ThinkingLevel { OFF, MINIMAL, LOW, MEDIUM, HIGH, XHIGH }
     - 每个值携带 @JsonValue String value
   - record ThinkingBudgets(Integer minimal, Integer low, Integer medium, Integer high)
3. 提供 Builder 模式构建 StreamOptions（字段太多不适合直接构造）
4. 编写单元测试
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement StreamOptions and related types (AI-011)
```

### 2.2 AI-006 EventStream 泛型类

```
请基于以下两个文档实现 AI-006 EventStream 泛型类需求：
- campusclaw-review.md（项目计划，Phase 2）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.6 EventStream 流式架构）

这是流式架构的核心组件，复杂度大，请先设计再实现。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/stream/ 下创建
2. 基于 Project Reactor 实现 EventStream<T, R>：
   - 内部使用 Sinks.Many<T> 实现 push 语义
   - push(T event) — 推送事件
   - end(R result) — 结束流并设置最终结果
   - end() — 无结果结束
   - asFlux() — 返回 Flux<T> 供消费者订阅
   - result() — 返回 Mono<R> 获取最终结果
   - 构造函数接受 Predicate<T> isComplete 和 Function<T, R> extractResult
3. 线程安全：多线程 push + 单线程消费
4. 错误处理：支持 error(Throwable) 传播错误
5. 编写单元测试覆盖：
   - 基本 push → subscribe 流
   - end() 结束后 result() 返回值
   - isComplete 自动结束
   - 错误传播
   - 多线程并发 push
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
7. 测试通过后 commit，消息格式：feat(ai): implement EventStream with Reactor Flux/Sinks (AI-006)
```

### 2.3 AI-007 AssistantMessageEvent 协议

```
请基于以下两个文档实现 AI-007 AssistantMessageEvent 协议需求：
- campusclaw-review.md（项目计划，Phase 2）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.7 AssistantMessageEvent 协议）

前置条件：AI-001~005 类型已实现。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/stream/ 下创建
2. 使用 Java 21 sealed interface + record 实现完整事件协议：
   sealed interface AssistantMessageEvent permits
     StartEvent, TextStartEvent, TextDeltaEvent, TextEndEvent,
     ThinkingStartEvent, ThinkingDeltaEvent, ThinkingEndEvent,
     ToolCallStartEvent, ToolCallDeltaEvent, ToolCallEndEvent,
     DoneEvent, ErrorEvent

   每个 record 的字段参考 TS 架构文档 1.7 节的定义，注意：
   - partial/message 字段类型为 AssistantMessage
   - contentIndex 为 int
   - delta 为 String
   - DoneEvent 的 reason 为 StopReason 枚举子集
   - ErrorEvent 的 reason 为字符串 "error" | "aborted"
3. 添加 Jackson 多态序列化注解（@JsonTypeInfo 基于 type 字段）
4. 编写单元测试验证各事件类型的创建和序列化
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement AssistantMessageEvent protocol (AI-007)
```

### 2.4 AI-008 AssistantMessageEventStream

```
请基于以下两个文档实现 AI-008 AssistantMessageEventStream 需求：
- campusclaw-review.md（项目计划，Phase 2）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.6 的特化流）

前置条件：AI-006 EventStream 和 AI-007 AssistantMessageEvent 已实现。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/stream/ 下创建
2. 实现 AssistantMessageEventStream，是 EventStream<AssistantMessageEvent, AssistantMessage> 的特化：
   - 继承或组合 EventStream
   - isComplete: 事件 type 为 done 或 error 时完成
   - extractResult: 从 DoneEvent 提取 message，从 ErrorEvent 提取 error
   - 提供便捷方法：
     pushTextDelta(int contentIndex, String delta, AssistantMessage partial)
     pushDone(StopReason reason, AssistantMessage message)
     pushError(String reason, AssistantMessage error)
3. 编写单元测试：模拟完整流式序列（start → text deltas → done）
4. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
5. 测试通过后 commit，消息格式：feat(ai): implement AssistantMessageEventStream (AI-008)
```

### 2.5 AI-009 ApiProvider 接口

```
请基于以下两个文档实现 AI-009 ApiProvider 接口需求：
- campusclaw-review.md（项目计划，Phase 2）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.8 ApiProvider 接口 & Registry）

前置条件：AI-006~008 流式组件和 AI-011 StreamOptions 已实现。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/provider/ 下创建
2. 定义 ApiProvider 接口：
   public interface ApiProvider {
     Api getApi();
     AssistantMessageEventStream stream(Model model, Context context, StreamOptions options);
     AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options);
   }
3. 接口设计要点：
   - 标记为 Spring 可扫描（实现类加 @Component）
   - stream 和 streamSimple 返回 AssistantMessageEventStream
   - options 参数可选（可为 null，使用默认值）
4. 编写一个 MockApiProvider 用于测试
5. 编写单元测试验证 mock provider 的流式行为
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
7. 测试通过后 commit，消息格式：feat(ai): implement ApiProvider interface (AI-009)
```

### 2.6 AI-010 ApiProviderRegistry

```
请基于以下两个文档实现 AI-010 ApiProviderRegistry 需求：
- campusclaw-review.md（项目计划，Phase 2）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.8 Registry 操作）

前置条件：AI-009 ApiProvider 接口已实现。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/provider/ 下创建
2. 使用 Spring @Service 实现 ApiProviderRegistry：
   @Service
   public class ApiProviderRegistry {
     // 通过 @Autowired List<ApiProvider> 自动收集所有 Spring Bean Provider
     // 同时支持运行时手动注册/注销（用于动态扩展）

     Optional<ApiProvider> getProvider(Api api);
     List<ApiProvider> getProviders();
     void register(ApiProvider provider, String sourceId);
     void unregister(String sourceId);
     void clear();
   }
3. 内部用 Map<Api, ApiProvider> 索引，支持按 sourceId 分组注销
4. 编写单元测试（使用 @SpringBootTest 或纯单元测试 + mock）
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement ApiProviderRegistry with Spring DI (AI-010)
```

### 2.7 AI-012 Model Registry

```
请基于以下两个文档实现 AI-012 Model Registry 需求：
- campusclaw-review.md（项目计划，Phase 2）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.4 Model 定义中的注册表描述）

前置条件：AI-004 Model/Provider/Api 类型已实现。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/model/ 下创建
2. 使用 Spring @Service 实现 ModelRegistry：
   @Service
   public class ModelRegistry {
     Optional<Model> getModel(Provider provider, String modelId);
     List<Model> getModels(Provider provider);
     List<Provider> getProviders();
     void register(Model model);
     void registerAll(List<Model> models);
     void clear();
   }
3. 内部用 Map<Provider, Map<String, Model>> 双层索引
4. 提供 @PostConstruct 初始化方法，预注册常用模型（至少包含）：
   - Anthropic: claude-sonnet-4-20250514, claude-opus-4-20250115, claude-haiku-3-5
   - OpenAI: gpt-4o, gpt-4o-mini, o3, o4-mini
   - 每个模型填入合理的 cost、contextWindow、maxTokens
5. 编写单元测试验证注册/查询
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
7. 测试通过后 commit，消息格式：feat(ai): implement ModelRegistry with built-in models (AI-012)
```

### 2.8 AI-013 Top-Level API

```
请基于以下两个文档实现 AI-013 Top-Level API 需求：
- campusclaw-review.md（项目计划，Phase 2）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 1.10 顶层 API）

前置条件：AI-006~012 全部已实现。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/ 下创建
2. 使用 Spring @Service 实现 CampusClawAiService（或叫 AiService）：
   @Service
   public class CampusClawAiService {
     // 注入 ApiProviderRegistry 和 ModelRegistry

     // 流式调用
     AssistantMessageEventStream stream(Model model, Context context, StreamOptions options);
     AssistantMessageEventStream streamSimple(Model model, Context context, SimpleStreamOptions options);

     // 阻塞调用（消费流到完成）
     Mono<AssistantMessage> complete(Model model, Context context, StreamOptions options);
     Mono<AssistantMessage> completeSimple(Model model, Context context, SimpleStreamOptions options);

     // 便捷方法
     Mono<AssistantMessage> complete(Model model, String userMessage);
   }
3. stream 方法从 Registry 查找 Provider 并委托调用
4. complete 方法消费 stream 的 Flux 直到 DoneEvent，返回 Mono<AssistantMessage>
5. 编写单元测试（mock Provider，验证 stream → complete 流程）
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
7. 测试通过后 commit，消息格式：feat(ai): implement top-level CampusClawAiService (AI-013)
```

---

## Phase 3: 优先 Provider（campusclaw-ai）

> 4 个 Provider 可并行开发

### 3.1 AI-014 Anthropic Provider

```
请基于以下两个文档实现 AI-014 Anthropic Provider 需求：
- campusclaw-review.md（项目计划，Phase 3）
- pi-mono-typescript-architecture.md（TS 架构参考）

前置条件：Phase 2 全部完成，ApiProvider 接口和 Registry 已就绪。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/provider/anthropic/ 下创建
2. 使用 com.anthropic:anthropic-java SDK 实现 AnthropicProvider：
   @Component
   public class AnthropicProvider implements ApiProvider {
     Api getApi() => Api.ANTHROPIC_MESSAGES
     // 使用 SDK 的流式 API，映射 Anthropic 事件到 AssistantMessageEvent
   }
3. 实现要点：
   - API key 从 StreamOptions.apiKey 或环境变量 ANTHROPIC_API_KEY 获取
   - 将 Context.messages 转换为 Anthropic SDK 的 MessageParam 格式
   - 将 Context.tools 转换为 Anthropic 的 Tool 格式
   - 流式响应映射：content_block_start/delta/stop → TextStartEvent/DeltaEvent/EndEvent
   - 支持 extended thinking（ThinkingContent 映射）
   - 支持 prompt caching（cacheRetention 参数）
   - Usage 统计映射（input/output tokens + cache tokens）
4. 编写单元测试（MockWebServer 模拟 Anthropic SSE 响应）
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement Anthropic provider with streaming (AI-014)
```

### 3.2 AI-015 OpenAI Completions Provider

```
请基于以下两个文档实现 AI-015 OpenAI Completions Provider 需求：
- campusclaw-review.md（项目计划，Phase 3）
- pi-mono-typescript-architecture.md（TS 架构参考）

前置条件：Phase 2 全部完成。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/provider/openai/ 下创建
2. 使用 com.openai:openai-java SDK 实现 OpenAICompletionsProvider：
   @Component
   public class OpenAICompletionsProvider implements ApiProvider {
     Api getApi() => Api.OPENAI_COMPLETIONS
   }
3. 实现要点：
   - API key 从 StreamOptions.apiKey 或环境变量 OPENAI_API_KEY 获取
   - 将 Context 转换为 OpenAI ChatCompletionCreateParams
   - 流式响应映射：choice delta → TextDeltaEvent / ToolCallDeltaEvent
   - 支持 function_call 和 tool_calls 格式
   - Usage 统计（prompt_tokens / completion_tokens）
4. 编写单元测试（MockWebServer 模拟 OpenAI SSE 响应）
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement OpenAI Completions provider (AI-015)
```

### 3.3 AI-016 OpenAI Responses Provider

```
请基于以下两个文档实现 AI-016 OpenAI Responses Provider 需求：
- campusclaw-review.md（项目计划，Phase 3）
- pi-mono-typescript-architecture.md（TS 架构参考）

前置条件：Phase 2 全部完成。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/provider/openai/ 下创建
2. 使用 com.openai:openai-java SDK 实现 OpenAIResponsesProvider：
   @Component
   public class OpenAIResponsesProvider implements ApiProvider {
     Api getApi() => Api.OPENAI_RESPONSES
   }
3. 实现要点：
   - 使用 OpenAI Responses API 端点（非 Chat Completions）
   - 流式事件映射到 AssistantMessageEvent
   - 支持 reasoning/thinking（如有）
   - 与 Completions Provider 共享工具/消息转换逻辑
4. 编写单元测试
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement OpenAI Responses provider (AI-016)
```

### 3.4 AI-017 AWS Bedrock Provider

```
请基于以下两个文档实现 AI-017 AWS Bedrock Provider 需求：
- campusclaw-review.md（项目计划，Phase 3）
- pi-mono-typescript-architecture.md（TS 架构参考）

前置条件：Phase 2 全部完成。

要求：
1. 在 modules/ai/src/main/java/com/mariozechner/pi/ai/provider/bedrock/ 下创建
2. 使用 software.amazon.awssdk:bedrockruntime SDK 实现 BedrockProvider：
   @Component
   public class BedrockProvider implements ApiProvider {
     Api getApi() => Api.BEDROCK_CONVERSE_STREAM
   }
3. 实现要点：
   - AWS credentials 从默认凭证链或 StreamOptions 获取
   - 使用 ConverseStreamRequest 构建请求
   - 流式响应映射：contentBlockStart/delta/stop → AssistantMessageEvent
   - 支持 Anthropic 模型通过 Bedrock（Claude on Bedrock）
   - Usage 统计映射
4. 编写单元测试
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:compileJava :modules:campusclaw-ai:test
6. 测试通过后 commit，消息格式：feat(ai): implement AWS Bedrock provider (AI-017)
```

---

## Phase 4: Agent Core（campusclaw-agent-core）

> 建议顺序：AC-002 → AC-003 → AC-001 → AC-007 → AC-008 → AC-004 → AC-005 → AC-006

### 4.1 AC-002 AgentTool 接口

```
请基于以下两个文档实现 AC-002 AgentTool 接口需求：
- campusclaw-review.md（项目计划，Phase 4）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 2.2 AgentTool）

前置条件：campusclaw-ai 的 Tool、ContentBlock 类型已实现。

要求：
1. 在 modules/agent-core/src/main/java/com/mariozechner/pi/agent/tool/ 下创建
2. 定义接口和类型：
   public interface AgentTool {
     String name();
     String label();
     String description();
     JsonNode parameters();  // JSON Schema
     AgentToolResult execute(String toolCallId, Map<String, Object> params,
       CancellationToken signal, AgentToolUpdateCallback onUpdate) throws Exception;
   }

   public record AgentToolResult(List<ContentBlock> content, Object details) {}

   @FunctionalInterface
   public interface AgentToolUpdateCallback {
     void onUpdate(AgentToolResult partialResult);
   }

   public class CancellationToken {
     boolean isCancelled();
     void cancel();
     void onCancel(Runnable callback);
   }
3. 编写单元测试（mock tool 实现）
4. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-agent-core:compileJava :modules:campusclaw-agent-core:test
5. 测试通过后 commit，消息格式：feat(agent-core): implement AgentTool interface (AC-002)
```

### 4.2 AC-003 AgentEvent 事件体系

```
请基于以下两个文档实现 AC-003 AgentEvent 事件体系需求：
- campusclaw-review.md（项目计划，Phase 4）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 2.4 AgentEvent 事件体系）

要求：
1. 在 modules/agent-core/src/main/java/com/mariozechner/pi/agent/event/ 下创建
2. 使用 Java 21 sealed interface + record 实现完整事件体系：
   sealed interface AgentEvent permits
     AgentStartEvent, AgentEndEvent,
     TurnStartEvent, TurnEndEvent,
     MessageStartEvent, MessageUpdateEvent, MessageEndEvent,
     ToolExecutionStartEvent, ToolExecutionUpdateEvent, ToolExecutionEndEvent

   各 record 字段参考 TS 架构文档 2.4 节
3. 提供 AgentEventListener 函数式接口：
   @FunctionalInterface
   public interface AgentEventListener {
     void onEvent(AgentEvent event);
   }
4. 编写单元测试
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-agent-core:compileJava :modules:campusclaw-agent-core:test
6. 测试通过后 commit，消息格式：feat(agent-core): implement AgentEvent hierarchy (AC-003)
```

### 4.3 AC-001 AgentState 状态机

```
请基于以下两个文档实现 AC-001 AgentState 状态机需求：
- campusclaw-review.md（项目计划，Phase 4）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 2.1 AgentState）

前置条件：AC-002 AgentTool 和 AC-003 AgentEvent 已实现。

要求：
1. 在 modules/agent-core/src/main/java/com/mariozechner/pi/agent/state/ 下创建
2. 实现 AgentState 类（可变状态容器，非 record）：
   public class AgentState {
     private String systemPrompt;
     private Model model;
     private ThinkingLevel thinkingLevel;
     private List<AgentTool> tools;
     private List<Message> messages;
     private volatile boolean streaming;
     private volatile Message streamMessage;
     private Set<String> pendingToolCalls;  // ConcurrentHashSet
     private String error;
     // getter/setter + 线程安全
   }
3. AgentState 需要线程安全（Agent Loop 在虚拟线程中执行工具时会并发修改）
4. 提供 snapshot() 方法返回当前状态的不可变快照
5. 编写单元测试验证状态变更和线程安全
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-agent-core:compileJava :modules:campusclaw-agent-core:test
7. 测试通过后 commit，消息格式：feat(agent-core): implement AgentState (AC-001)
```

### 4.4 AC-007 Context 转换

```
请基于以下两个文档实现 AC-007 Context 转换需求：
- campusclaw-review.md（项目计划，Phase 4）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 2.5 中的 convertToLlm 和 transformContext）

要求：
1. 在 modules/agent-core/src/main/java/com/mariozechner/pi/agent/context/ 下创建
2. 定义函数式接口：
   @FunctionalInterface
   public interface MessageConverter {
     List<Message> convert(List<Message> agentMessages);
   }

   @FunctionalInterface
   public interface ContextTransformer {
     CompletableFuture<List<Message>> transform(List<Message> messages, CancellationToken signal);
   }
3. 提供默认实现 DefaultMessageConverter（直接透传）
4. 编写单元测试
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-agent-core:compileJava :modules:campusclaw-agent-core:test
6. 测试通过后 commit，消息格式：feat(agent-core): implement context conversion interfaces (AC-007)
```

### 4.5 AC-008 Steering & Follow-up

```
请基于以下两个文档实现 AC-008 Steering & Follow-up 需求：
- campusclaw-review.md（项目计划，Phase 4）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 2.6 Agent 类的 steering/followUp 和 5.4 Steering & Follow-up）

要求：
1. 在 modules/agent-core/src/main/java/com/mariozechner/pi/agent/queue/ 下创建
2. 实现 MessageQueue（线程安全的消息队列）：
   public class MessageQueue {
     enum DeliveryMode { ALL, ONE_AT_A_TIME }

     void enqueue(Message message);
     List<Message> drain(DeliveryMode mode);
     void clear();
     boolean hasMessages();
     void setMode(DeliveryMode mode);
   }
3. 线程安全：Agent Loop 在工具执行期间可能有外部线程 enqueue steering 消息
4. 编写单元测试覆盖：ALL 模式一次取完、ONE_AT_A_TIME 逐条取
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-agent-core:compileJava :modules:campusclaw-agent-core:test
6. 测试通过后 commit，消息格式：feat(agent-core): implement steering and follow-up queues (AC-008)
```

### 4.6 AC-004 Tool 执行管道

```
请基于以下两个文档实现 AC-004 Tool 执行管道需求：
- campusclaw-review.md（项目计划，Phase 4）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 2.3 Tool 执行钩子、5.4 工具执行管道）

前置条件：AC-002 AgentTool 和 AC-003 AgentEvent 已实现。

要求：
1. 在 modules/agent-core/src/main/java/com/mariozechner/pi/agent/tool/ 下创建
2. 实现 ToolExecutionPipeline：
   public class ToolExecutionPipeline {
     void setBeforeToolCall(BeforeToolCallHandler handler);
     void setAfterToolCall(AfterToolCallHandler handler);

     ToolResultMessage execute(AgentTool tool, ToolCall toolCall,
       Map<String, Object> validatedArgs, AgentContext context,
       CancellationToken signal, AgentEventListener listener);

     List<ToolResultMessage> executeAll(List<ToolCallWithTool> calls,
       ToolExecutionMode mode, AgentContext context,
       CancellationToken signal, AgentEventListener listener);
   }
3. 执行流程：beforeToolCall → 参数校验 → execute → afterToolCall → 生成 ToolResultMessage
4. parallel 模式使用虚拟线程并发执行
5. 定义 BeforeToolCallContext/Result、AfterToolCallContext/Result（参考 TS 2.3 节）
6. 编写单元测试覆盖：正常执行、beforeToolCall 阻止、afterToolCall 覆盖结果、并行执行
7. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-agent-core:compileJava :modules:campusclaw-agent-core:test
8. 测试通过后 commit，消息格式：feat(agent-core): implement tool execution pipeline (AC-004)
```

### 4.7 AC-005 Agent Loop

```
请基于以下两个文档实现 AC-005 Agent Loop 需求：
- campusclaw-review.md（项目计划，Phase 4）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 2.5 Agent Loop 配置、2.7 Agent Loop 函数、2.8 事件发射序列）

前置条件：AC-001~004、AC-007、AC-008 全部已实现。这是 Agent Core 最核心的组件。

要求：
1. 在 modules/agent-core/src/main/java/com/mariozechner/pi/agent/loop/ 下创建
2. 实现 AgentLoop：
   public class AgentLoop {
     AgentLoop(AgentLoopConfig config);

     List<Message> run(List<Message> prompts, AgentContext context,
       AgentEventListener listener, CancellationToken signal);

     List<Message> continueLoop(AgentContext context,
       AgentEventListener listener, CancellationToken signal);
   }
3. AgentLoopConfig 包含：
   - Model model
   - MessageConverter convertToLlm
   - ContextTransformer transformContext（可选）
   - ToolExecutionPipeline toolPipeline
   - MessageQueue steeringQueue, followUpQueue
   - SimpleStreamOptions streamOptions
4. 循环逻辑（参考 TS 2.8 事件发射序列）：
   emit agent_start
   → 添加 prompt 到 context
   loop:
     emit turn_start → message_start
     调用 CampusClawAiService.stream() 获取 LLM 流式响应
     for each event: emit message_update
     emit message_end
     if has tool calls:
       toolPipeline.executeAll(toolCalls)
       check steering queue → 注入
       emit turn_end → continue loop
     else:
       check followUp queue → 如有则继续
       emit turn_end → break
   emit agent_end
5. 编写单元测试（mock CampusClawAiService，验证完整循环）
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-agent-core:compileJava :modules:campusclaw-agent-core:test
7. 测试通过后 commit，消息格式：feat(agent-core): implement AgentLoop (AC-005)
```

### 4.8 AC-006 Agent 类

```
请基于以下两个文档实现 AC-006 Agent 类需求：
- campusclaw-review.md（项目计划，Phase 4）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 2.6 Agent 类完整 API）

前置条件：AC-001~005、AC-007、AC-008 全部已实现。

要求：
1. 在 modules/agent-core/src/main/java/com/mariozechner/pi/agent/ 下创建
2. 实现 Agent 类（Phase 4 的门面类）：
   @Service
   public class Agent {
     AgentState getState();
     void setSystemPrompt(String prompt);
     void setModel(Model model);
     void setThinkingLevel(ThinkingLevel level);
     void setTools(List<AgentTool> tools);
     void replaceMessages(List<Message> messages);
     void appendMessage(Message message);
     void clearMessages();
     void reset();

     void setBeforeToolCall(BeforeToolCallHandler handler);
     void setAfterToolCall(AfterToolCallHandler handler);

     void steer(Message message);
     void followUp(Message message);
     void clearSteeringQueue();
     void clearFollowUpQueue();

     CompletableFuture<Void> prompt(String message);
     CompletableFuture<Void> prompt(Message message);
     CompletableFuture<Void> continueExecution();
     void abort();
     CompletableFuture<Void> waitForIdle();

     Runnable subscribe(AgentEventListener listener);  // 返回 unsubscribe
   }
3. prompt() 在虚拟线程中启动 AgentLoop
4. abort() 通过 CancellationToken 取消
5. subscribe 返回取消订阅的 Runnable
6. 编写单元测试覆盖：prompt → 事件 → 完成、abort 取消、steering 注入
7. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-agent-core:compileJava :modules:campusclaw-agent-core:test
8. 测试通过后 commit，消息格式：feat(agent-core): implement Agent class (AC-006)
```

---

## Phase 5: TUI 核心（campusclaw-tui）

> 建议顺序：TU-007 → TU-001 → TU-002 → TU-003 → TU-004 → TU-006 → TU-005

### 5.1 TU-007 ANSI 工具函数

```
请基于以下两个文档实现 TU-007 ANSI 工具函数需求：
- campusclaw-review.md（项目计划，Phase 5）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 4.5 ANSI 工具函数）

这是 TUI 的基础工具层，其他组件依赖它，优先实现。

要求：
1. 在 modules/tui/src/main/java/com/mariozechner/pi/tui/ansi/ 下创建
2. 实现以下工具函数（静态方法或工具类）：
   - int visibleWidth(String text) — 计算可见宽度，忽略 ANSI 转义码，正确处理中文全角字符
   - String sliceByColumn(String text, int startCol, int endCol) — 按列切片，保留 ANSI 码
   - List<AnsiSegment> extractSegments(String text) — 分离 ANSI 码和可见文本
     record AnsiSegment(String text, boolean isAnsi)
   - List<String> wrapTextWithAnsi(String text, int maxWidth) — ANSI 感知的换行
   - String applyBackground(String line, int width, UnaryOperator<String> bgFn) — 应用背景色填充
3. 编写充分的单元测试覆盖：纯文本、含 ANSI 码、含中文、混合、边界条件
4. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-tui:compileJava :modules:campusclaw-tui:test
5. 测试通过后 commit，消息格式：feat(tui): implement ANSI utility functions (TU-007)
```

### 5.2 TU-001 Component 接口 + 差量渲染器

```
请基于以下两个文档实现 TU-001 Component 接口 + 差量渲染器需求：
- campusclaw-review.md（项目计划，Phase 5）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 4.1 Component 接口、4.6 差量渲染）

前置条件：TU-007 ANSI 工具函数已实现。

要求：
1. 在 modules/tui/src/main/java/com/mariozechner/pi/tui/ 下创建
2. 定义核心接口：
   public interface Component {
     List<String> render(int width);
     default void handleInput(String data) {}
     default boolean wantsKeyRelease() { return false; }
     void invalidate();
   }

   public interface Focusable {
     boolean isFocused();
     void setFocused(boolean focused);
   }
3. 实现 DiffRenderer：比较 lastRendered 和新渲染结果，返回最小更新集
   sealed interface RenderDiff permits LineUpdates, FullRerender
   record LineUpdates(List<LineUpdate> updates)
   record LineUpdate(int row, String content)
   record FullRerender()
4. 编写单元测试
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-tui:compileJava :modules:campusclaw-tui:test
6. 测试通过后 commit，消息格式：feat(tui): implement Component interface and DiffRenderer (TU-001)
```

### 5.3 TU-002 Terminal 抽象

```
请基于以下两个文档实现 TU-002 Terminal 抽象需求：
- campusclaw-review.md（项目计划，Phase 5）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 4.3 Terminal 抽象）

要求：
1. 在 modules/tui/src/main/java/com/mariozechner/pi/tui/terminal/ 下创建
2. 定义 Terminal 接口：
   public interface Terminal {
     void write(String data);
     void clear();
     void moveCursor(int row, int col);
     TerminalSize getSize();
     void onInput(Consumer<String> listener);
     void enterRawMode();
     void exitRawMode();
     void close();
   }
   record TerminalSize(int width, int height)
3. 实现 JLineTerminal（基于 JLine 3 库）：raw mode、终端大小检测、resize 监听
4. 实现 TestTerminal（用于测试的内存 Terminal）
5. 编写单元测试
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-tui:compileJava :modules:campusclaw-tui:test
7. 测试通过后 commit，消息格式：feat(tui): implement Terminal abstraction with JLine (TU-002)
```

### 5.4 TU-003 基础组件

```
请基于以下两个文档实现 TU-003 基础组件需求：
- campusclaw-review.md（项目计划，Phase 5）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 4.2 内置组件中的 Text、Container、Box）

前置条件：TU-001 Component 接口和 TU-007 ANSI 工具已实现。

要求：
1. 在 modules/tui/src/main/java/com/mariozechner/pi/tui/component/ 下创建
2. 实现三个基础组件：
   - Text — 文本显示（支持 padding、自定义背景）
   - Container — 纵向排列子组件
   - Box — 带边框/padding/背景色的容器（边框样式：single/double/rounded）
3. 每个组件实现 Component 接口的 render(width) 方法
4. 编写单元测试验证渲染输出
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-tui:compileJava :modules:campusclaw-tui:test
6. 测试通过后 commit，消息格式：feat(tui): implement Text, Container and Box components (TU-003)
```

### 5.5 TU-004 Markdown 渲染

```
请基于以下两个文档实现 TU-004 Markdown 渲染需求：
- campusclaw-review.md（项目计划，Phase 5）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 4.2 Markdown 组件）

前置条件：TU-001 Component 和 TU-007 ANSI 工具已实现。

要求：
1. 在 modules/tui/src/main/java/com/mariozechner/pi/tui/component/ 下创建
2. 实现 MarkdownComponent implements Component：
   - 将 Markdown 文本渲染为 ANSI 终端输出
   - 支持：标题（# ## ###）、代码块（```）、行内代码（`）、
     粗体（**）、斜体（*）、列表（- *）、有序列表（1.）、链接（[text](url)）
   - 代码块用背景色高亮，标题用粗体 + 颜色
3. 可使用简单的手写 parser 或引入轻量 Markdown 解析库
4. 编写单元测试验证各种 Markdown 元素的渲染
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-tui:compileJava :modules:campusclaw-tui:test
6. 测试通过后 commit，消息格式：feat(tui): implement Markdown rendering component (TU-004)
```

### 5.6 TU-006 SelectList 组件

```
请基于以下两个文档实现 TU-006 SelectList 组件需求：
- campusclaw-review.md（项目计划，Phase 5）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 4.2 SelectList）

前置条件：TU-001 Component 已实现。

要求：
1. 在 modules/tui/src/main/java/com/mariozechner/pi/tui/component/ 下创建
2. 实现 SelectList<T>（implements Component, Focusable）：
   - 构造：items 列表、renderItem 函数、可选 maxHeight 和 theme
   - getSelectedItem() / setSelectedIndex()
   - handleInput()：上下方向键导航、Enter 选择
   - 滚动窗口：当列表超过 maxHeight 时自动滚动
   - 自定义主题：selectedBg、selectedFg、normalFg
3. 编写单元测试验证导航和渲染
4. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-tui:compileJava :modules:campusclaw-tui:test
5. 测试通过后 commit，消息格式：feat(tui): implement SelectList component (TU-006)
```

### 5.7 TU-005 Editor/Input 组件

```
请基于以下两个文档实现 TU-005 Editor/Input 组件需求：
- campusclaw-review.md（项目计划，Phase 5）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 4.2 Editor/Input、4.7 输入处理、4.8 Word Wrap）

前置条件：TU-001 Component、TU-002 Terminal、TU-007 ANSI 工具已实现。

要求：
1. 在 modules/tui/src/main/java/com/mariozechner/pi/tui/component/ 下创建
2. 实现 Editor（多行编辑器，implements Component, Focusable）：
   - getText() / setText() / getCursorPosition() / setCursorPosition()
   - handleInput()：方向键、Home/End、Ctrl+A/E、Backspace/Delete、Enter
     Ctrl+K（kill line）、Ctrl+Y（yank）、Ctrl+Z / Ctrl+Shift+Z（undo/redo）
   - UndoStack、KillRing、Word wrap
3. 实现 Input（单行输入，implements Component, Focusable）：
   - getValue() / setValue()、placeholder 支持
4. 实现辅助类：UndoStack<T>、KillRing
5. 编写单元测试
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-tui:compileJava :modules:campusclaw-tui:test
7. 测试通过后 commit，消息格式：feat(tui): implement Editor and Input components (TU-005)
```

---

## Phase 6: CLI + 内置工具（campusclaw-coding-agent）

> 建议顺序：基础工具 → Operations → Executor → 各 Tool → 系统提示 → CLI 入口
> CA-003/004 → CA-013 → CA-005 → CA-006 → CA-007~012（可并行）→ CA-002 → CA-001

### 6.1 CA-003 截断工具

```
请基于以下两个文档实现 CA-003 截断工具需求：
- campusclaw-review.md（项目计划，Phase 6）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 3.5 截断工具）

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/util/ 下创建
2. 实现 TruncationUtils 工具类：
   - record TruncationResult(boolean truncated, int outputLines, int totalLines,
     Integer maxLines, Integer maxBytes, boolean firstLineExceedsLimit, String truncatedBy)
   - static TruncationResult truncateHead(String text, int maxLines, int maxBytes)
   - static TruncationResult truncateTail(String text, int maxLines, int maxBytes)
   - static String truncateLine(String line, int maxBytes)
   - static String formatSize(long bytes) — "32KB"、"1.5MB"
3. 编写单元测试覆盖各种截断场景
4. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
5. 测试通过后 commit，消息格式：feat(coding-agent): implement truncation utilities (CA-003)
```

### 6.2 CA-004 路径解析工具

```
请基于以下两个文档实现 CA-004 路径解析工具需求：
- campusclaw-review.md（项目计划，Phase 6）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 3.7 路径解析）

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/util/ 下创建
2. 实现 PathUtils 工具类：
   - static Path resolveToCwd(String input, Path cwd)
   - static Path resolveReadPath(String input, Path cwd)
   - 防止目录遍历攻击：解析后的路径必须在 cwd 子树内，否则抛 SecurityException
3. 编写单元测试覆盖：正常路径、../ 攻击、绝对路径、符号链接
4. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
5. 测试通过后 commit，消息格式：feat(coding-agent): implement path resolution utilities (CA-004)
```

### 6.3 CA-013 Tool Operations 接口

```
请基于以下两个文档实现 CA-013 Tool Operations 接口需求：
- campusclaw-review.md（项目计划，Phase 6）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 3.3 可插拔 Operations 模式）

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/tool/ops/ 下创建
2. 定义 Operations 接口：
   public interface ReadOperations {
     byte[] readFile(Path path) throws IOException;
     boolean exists(Path path);
     String detectMimeType(Path path) throws IOException;
   }
   public interface WriteOperations {
     void writeFile(Path path, String content) throws IOException;
     void mkdir(Path dir) throws IOException;
   }
   public interface EditOperations extends ReadOperations, WriteOperations {}
   public interface BashOperations {
     BashResult exec(String command, Path cwd, BashExecOptions options) throws IOException;
   }
   record BashExecOptions(Consumer<byte[]> onData, CancellationToken signal,
     Duration timeout, Map<String, String> env)
   record BashResult(Integer exitCode)
3. 提供默认本地实现 LocalReadOperations、LocalWriteOperations、LocalBashOperations
4. 编写单元测试
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
6. 测试通过后 commit，消息格式：feat(coding-agent): implement pluggable tool operations (CA-013)
```

### 6.4 CA-005 文件变更队列

```
请基于以下两个文档实现 CA-005 文件变更队列需求：
- campusclaw-review.md（项目计划，Phase 6）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 3.6 文件变更队列）

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/util/ 下创建
2. 实现 FileMutationQueue：
   public class FileMutationQueue {
     <T> T withLock(Path filePath, Callable<T> action) throws Exception;
   }
3. 内部使用 ConcurrentHashMap<String, ReentrantLock> 管理锁
4. 同一文件串行执行，不同文件可并行
5. 编写单元测试验证：同文件串行、不同文件并行
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
7. 测试通过后 commit，消息格式：feat(coding-agent): implement file mutation queue (CA-005)
```

### 6.5 CA-006 Bash Executor

```
请基于以下两个文档实现 CA-006 Bash Executor 需求：
- campusclaw-review.md（项目计划，Phase 6）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 3.9 Bash Executor）

前置条件：CA-013 BashOperations 接口已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/tool/bash/ 下创建
2. 实现 BashExecutor：
   @Service
   public class BashExecutor {
     BashExecutionResult execute(String command, Path cwd, BashExecutorOptions options);
   }
   record BashExecutorOptions(Duration timeout, CancellationToken signal, Map<String, String> env)
   record BashExecutionResult(Integer exitCode, String stdout, String stderr)
3. 实现要点：
   - ProcessBuilder 启动 /bin/bash -c "command"
   - 虚拟线程读取 stdout/stderr
   - 超时处理、CancellationToken 中断、环境变量合并
4. 编写单元测试覆盖：正常执行、超时、取消、非零退出码
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
6. 测试通过后 commit，消息格式：feat(coding-agent): implement BashExecutor (CA-006)
```

### 6.6 CA-007 Bash Tool

```
请基于以下文档实现 CA-007 Bash Tool 需求：
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 3.1 Bash Tool）

前置条件：CA-006 BashExecutor、CA-003 TruncationUtils、AC-002 AgentTool 接口已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/tool/bash/ 下创建
2. 实现 BashTool implements AgentTool：
   - 参数：command (String), timeout (Integer, 可选)
   - 执行：委托给 BashExecutor
   - 输出：截断后的 stdout+stderr 作为 TextContent
   - details 包含 TruncationResult 和 fullOutputPath
3. 编写单元测试
4. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
5. 测试通过后 commit，消息格式：feat(coding-agent): implement Bash tool (CA-007)
```

### 6.7 CA-008 Read Tool

```
请基于 pi-mono-typescript-architecture.md（3.1 Read Tool）实现 CA-008 Read Tool。

前置条件：CA-013 ReadOperations、CA-003 TruncationUtils、CA-004 PathUtils 已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/tool/ 下创建
2. 实现 ReadTool implements AgentTool：
   - 参数：path (String), offset (Integer, 可选), limit (Integer, 可选)
   - 文件读取 + 行号偏移 + 行数限制 + 图片检测 + 输出截断
   - 默认限制：32KB / 500 行
3. 编写单元测试
4. 构建测试：JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
5. commit：feat(coding-agent): implement Read tool (CA-008)
```

### 6.8 CA-009 Edit Tool

```
请基于 pi-mono-typescript-architecture.md（3.1 Edit Tool）实现 CA-009 Edit Tool。

前置条件：CA-013 EditOperations、CA-005 FileMutationQueue 已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/tool/ 下创建
2. 实现 EditTool implements AgentTool：
   - 参数：path (String), oldText (String), newText (String)
   - 精确文本替换，失败时 fuzzy matching
   - 生成 unified diff 作为 details
   - FileMutationQueue 串行化
3. 实现辅助：computeEditDiff()、fuzzyFindText()
4. 编写单元测试覆盖：精确匹配、fuzzy、多次出现报错
5. 构建测试：JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
6. commit：feat(coding-agent): implement Edit tool (CA-009)
```

### 6.9 CA-010 Write Tool

```
请基于 pi-mono-typescript-architecture.md（3.1 Write Tool）实现 CA-010 Write Tool。

前置条件：CA-013 WriteOperations、CA-005 FileMutationQueue 已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/tool/ 下创建
2. 实现 WriteTool implements AgentTool：
   - 参数：path (String), content (String)
   - 创建或覆写文件，自动创建父目录，FileMutationQueue 串行化
3. 编写单元测试
4. 构建测试：JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
5. commit：feat(coding-agent): implement Write tool (CA-010)
```

### 6.10 CA-011 Grep Tool

```
请基于 pi-mono-typescript-architecture.md（3.1 Grep Tool）实现 CA-011 Grep Tool。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/tool/ 下创建
2. 实现 GrepTool implements AgentTool：
   - 参数：pattern (String, 正则), path (String, 可选), glob (String, 可选), type (String, 可选)
   - 优先调用系统 ripgrep（rg），不可用时 fallback 到 Java 实现
   - Java fallback：Files.walkFileTree + Pattern.compile
   - glob 过滤 PathMatcher、type 过滤预定义扩展名映射
3. 编写单元测试
4. 构建测试：JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
5. commit：feat(coding-agent): implement Grep tool (CA-011)
```

### 6.11 CA-012 Glob Tool

```
请基于 pi-mono-typescript-architecture.md（3.1 Glob Tool）实现 CA-012 Glob Tool。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/tool/ 下创建
2. 实现 GlobTool implements AgentTool：
   - 参数：pattern (String, glob), path (String, 可选)
   - PathMatcher + Files.walkFileTree
   - 排除 .git、node_modules、build 等
   - 按修改时间排序
3. 编写单元测试
4. 构建测试：JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
5. commit：feat(coding-agent): implement Glob tool (CA-012)
```

### 6.12 CA-002 系统提示构建器

```
请基于以下两个文档实现 CA-002 系统提示构建器需求：
- campusclaw-review.md（项目计划，Phase 6）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 3.8 系统提示构建）

前置条件：CA-007~012 工具已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/prompt/ 下创建
2. 实现 SystemPromptBuilder：
   @Service
   public class SystemPromptBuilder {
     String build(SystemPromptConfig config);
   }
   record SystemPromptConfig(List<AgentTool> tools, List<Skill> skills,
     Path cwd, String customPrompt, Map<String, String> env)
3. 构建逻辑：
   - 基础角色定义（Java 文本块 """..."""）
   - 拼接每个工具的描述和使用指南
   - 拼接 Skill 列表（XML 格式，name + description + location）
   - 拼接环境信息（OS、CWD、git branch）
   - 追加用户自定义 prompt
4. 编写单元测试验证各部分正确拼接
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
6. 测试通过后 commit，消息格式：feat(coding-agent): implement SystemPromptBuilder (CA-002)
```

### 6.13 CA-001 CLI 入口 + 参数解析

```
请基于以下两个文档实现 CA-001 CLI 入口 + 参数解析需求：
- campusclaw-review.md（项目计划，Phase 6）
- pi-mono-typescript-architecture.md（TS 架构参考，重点看 3.10 CLI 入口）

前置条件：Phase 4 Agent 类和 Phase 6 工具/提示构建器已实现。

要求：
1. 修改 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/CampusClawApplication.java
2. 在 com.campusclaw.codingagent.cli/ 下创建 Picocli 命令类：
   @Command(name = "pi", description = "Pi Coding Agent")
   @Component
   public class CampusClawCommand implements Callable<Integer> {
     @Option(names = {"-m", "--model"}) String model;
     @Option(names = {"-p", "--prompt"}) String prompt;
     @Option(names = {"--mode"}) String mode = "interactive";
     @Option(names = {"--cwd"}) Path cwd;
     @Parameters List<String> promptArgs;
   }
3. 通过 picocli-spring-boot-starter 桥接 Spring Boot
4. 编写单元测试验证参数解析
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
6. 测试通过后 commit，消息格式：feat(coding-agent): implement CLI entry with Picocli (CA-001)
```

---

## Phase 7: AgentSession + 运行模式 + Skill 系统（campusclaw-coding-agent）

> 建议顺序：CA-018 → CA-014 → CA-017 → CA-016 → CA-015

### 7.1 CA-018 Skill 系统

```
请基于以下文档实现 CA-018 Skill 系统需求：
- campusclaw-review.md（项目计划，Phase 7）
- pi-mono-typescript-architecture.md（TS 架构参考）
- TS 参考实现路径：/Users/z/pi-mono/packages/coding-agent/src/core/skills.ts

Skill 系统是自包含的能力包，提供专用工作流和指令。遵循 Agent Skills 标准。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/skill/ 下创建

2. 实现 Skill 数据类型：
   public record Skill(
     String name,                    // 小写 a-z、0-9、连字符，最长 64 字符
     String description,             // 最长 1024 字符
     Path filePath,                  // SKILL.md 文件路径
     Path baseDir,                   // SKILL.md 所在目录
     String source,                  // "user" | "project"
     boolean disableModelInvocation  // true = 不在系统提示中显示
   ) {}

3. 实现 SkillLoader — 递归扫描目录加载 Skill：
   @Service
   public class SkillLoader {
     List<Skill> loadFromDirectory(Path dir, String source);
     Skill loadFromFile(Path filePath, String source);
   }
   加载逻辑：
   - 读取 SKILL.md 文件
   - 解析 YAML frontmatter（name、description、disable-model-invocation）
   - name 默认取父目录名，必须匹配 [a-z0-9-]{1,64}
   - description 必填，最长 1024 字符
   - 如果目录包含 SKILL.md 则为 skill 根（不再递归子目录）
   - 否则递归子目录查找 SKILL.md

4. 实现 SkillRegistry — 管理已加载的 Skill：
   @Service
   public class SkillRegistry {
     void register(Skill skill);
     void registerAll(List<Skill> skills);
     Optional<Skill> getByName(String name);
     List<Skill> getAll();
     List<Skill> getVisibleSkills();  // disableModelInvocation == false
     void clear();
   }

5. 实现 SkillExpander — 展开 /skill:name 命令：
   @Service
   public class SkillExpander {
     String expand(String userInput, SkillRegistry registry);
   }
   展开逻辑：
   - 匹配 /skill:name-here [args]
   - 读取 SKILL.md 内容，去除 YAML frontmatter
   - 包装为 XML 格式：
     <skill name="name" location="/path/to/SKILL.md">
     References are relative to /path/to/skill/dir.
     [skill body]
     </skill>
     [args]
   - 不匹配时原样返回

6. 实现 SkillPromptFormatter — 生成系统提示中的 Skill 列表：
   public class SkillPromptFormatter {
     static String format(List<Skill> visibleSkills);
   }
   输出 XML 格式：
   <available_skills>
     <skill>
       <name>...</name>
       <description>...</description>
       <location>...</location>
     </skill>
   </available_skills>

7. 发现路径（SkillLoader 初始化时扫描）：
   - ~/.pi/agent/skills/（用户级）
   - {cwd}/.pi/skills/（项目级）

8. YAML frontmatter 解析使用 SnakeYAML（已在 coding-agent-cli 依赖中）

9. 编写单元测试覆盖：
   - SKILL.md 解析（含/不含 frontmatter）
   - 目录递归扫描
   - name 校验（非法字符、过长）
   - /skill:name 命令展开
   - 系统提示格式化
   - disableModelInvocation 过滤

10. 完成后运行：
    JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
11. 测试通过后 commit，消息格式：feat(coding-agent): implement Skill system (CA-018)
```

### 7.2 CA-014 AgentSession

```
请基于以下文档实现 CA-014 AgentSession 需求：
- campusclaw-review.md（项目计划，Phase 7）

前置条件：Phase 4 Agent 类、Phase 6 工具和提示构建器、CA-018 Skill 系统全部已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/session/ 下创建
2. 实现 AgentSession：
   @Service
   public class AgentSession {
     void initialize(SessionConfig config);
     Agent getAgent();
     CompletableFuture<Void> prompt(String userInput);
     void abort();
     List<Message> getHistory();
   }
   record SessionConfig(String model, Path cwd, String customPrompt, String mode)
3. initialize 负责：
   - 从 ModelRegistry 解析模型
   - 使用 SkillLoader 加载 Skill（用户级 + 项目级）
   - 使用 SystemPromptBuilder 构建系统提示（含 Skill 列表）
   - 注册所有内置工具
   - 配置 Agent 实例
4. prompt 时先通过 SkillExpander 展开 /skill:name 命令
5. 编写单元测试
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
7. 测试通过后 commit，消息格式：feat(coding-agent): implement AgentSession (CA-014)
```

### 7.3 CA-017 会话持久化

```
请基于 campusclaw-review.md 实现 CA-017 会话持久化需求。

前置条件：CA-014 AgentSession 已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/session/ 下创建
2. 实现 SessionPersistence：
   @Service
   public class SessionPersistence {
     void save(String sessionId, List<Message> messages, Path outputPath);
     List<Message> load(Path inputPath);
   }
3. 格式：JSONL（每行一个 JSON 对象）
4. 使用 Jackson ObjectMapper，支持 Message 多态反序列化
5. 编写单元测试覆盖：保存、加载、多态消息
6. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
7. 测试通过后 commit，消息格式：feat(coding-agent): implement session persistence (CA-017)
```

### 7.4 CA-016 One-shot 模式

```
请基于 campusclaw-review.md 实现 CA-016 One-shot 模式需求。

前置条件：CA-014 AgentSession 已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/mode/ 下创建
2. 实现 OneShotMode：
   public class OneShotMode {
     int run(AgentSession session, String prompt);  // 返回退出码
   }
3. 发送 prompt → Agent 执行 → stdout 输出 → 退出（0=成功, 1=错误）
4. 编写单元测试
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
6. 测试通过后 commit，消息格式：feat(coding-agent): implement one-shot mode (CA-016)
```

### 7.5 CA-015 交互模式

```
请基于以下文档实现 CA-015 交互模式需求：
- campusclaw-review.md（项目计划，Phase 7）

前置条件：CA-014 AgentSession 和 Phase 5 TUI 组件已实现。

要求：
1. 在 modules/coding-agent-cli/src/main/java/com/mariozechner/pi/codingagent/mode/ 下创建
2. 实现 InteractiveMode：
   public class InteractiveMode {
     void run(AgentSession session, Terminal terminal);
   }
3. 功能：
   - TUI Editor 接收用户输入
   - 实时流式显示 LLM 输出（订阅 AgentEvent）
   - 工具调用 UI 渲染
   - Markdown 渲染 assistant 回复
   - /skill:name 命令支持
   - Ctrl+C 中断、/exit 退出
4. 编写单元测试（使用 TestTerminal）
5. 完成后运行：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:compileJava :modules:campusclaw-coding-agent:test
6. 测试通过后 commit，消息格式：feat(coding-agent): implement interactive mode with TUI (CA-015)
```

---

## Phase 8: 集成测试

> 3 个测试可并行

### 8.1 IT-001 Provider 集成测试

```
请基于 campusclaw-review.md 实现 IT-001 Provider 集成测试。

要求：
1. 在 modules/ai/src/test/java/com/mariozechner/pi/ai/provider/ 下创建
2. 为每个 Provider 编写 MockWebServer 集成测试：
   - AnthropicProviderIntegrationTest
   - OpenAICompletionsProviderIntegrationTest
   - OpenAIResponsesProviderIntegrationTest
   - BedrockProviderIntegrationTest
3. 验证：请求参数转换、SSE 事件映射、Usage 统计、错误处理
4. 运行：JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-ai:test
5. commit：test(ai): add provider integration tests (IT-001)
```

### 8.2 IT-002 Agent Loop 端到端测试

```
请基于 campusclaw-review.md 实现 IT-002 Agent Loop 端到端测试。

要求：
1. 在 modules/agent-core/src/test/java/com/mariozechner/pi/agent/ 下创建
2. 编写 AgentLoopIntegrationTest：
   - MockApiProvider 模拟 LLM
   - 完整循环：prompt → LLM → tool → result → LLM → done
   - 多轮工具调用、steering 注入、follow-up、abort
   - 验证事件发射顺序
3. 运行：JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-agent-core:test
4. commit：test(agent-core): add agent loop integration tests (IT-002)
```

### 8.3 IT-003 CLI 端到端测试

```
请基于 campusclaw-review.md 实现 IT-003 CLI 端到端测试。

要求：
1. 在 modules/coding-agent-cli/src/test/java/com/mariozechner/pi/codingagent/ 下创建
2. 编写 CliIntegrationTest（@SpringBootTest）：
   - One-shot 模式端到端
   - 工具执行端到端（bash、read、write、edit）
   - Skill 加载和展开
   - 会话持久化（保存 → 加载 → 验证）
   - MockApiProvider 模拟 LLM
3. 运行：JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew :modules:campusclaw-coding-agent:test
4. commit：test(coding-agent): add CLI integration tests (IT-003)
```

---

## 全量验证

```
请对 campusclaw 项目做全量验证：

1. 全量构建：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew clean build
2. 全量测试：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew test
3. Spring Boot 启动：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew bootRun
4. 可执行 JAR 打包：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew bootJar
5. 依赖冲突检查：
   JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew dependencies

如有失败，修复并重新验证直到全部通过。
commit：chore: verify full build and all tests pass
```
