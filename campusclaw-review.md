# Pi-Mono Java 开发计划

## 概览

基于 pi-mono TypeScript 版本，使用 Java 21 + Spring Boot 3.x 实现 4 个核心模块：campusclaw-ai、campusclaw-agent-core、campusclaw-coding-agent、campusclaw-tui，以支撑 openclaw 项目。

---

## 一、技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| **语言** | Java 21 LTS | records、sealed interfaces、pattern matching、虚拟线程、文本块 |
| **框架** | Spring Boot 3.4.1 | 自动配置、DI 容器、统一生命周期管理 |
| **异步/响应式** | Project Reactor (WebFlux) | `Mono`/`Flux` 用于 LLM 流式响应 |
| **HTTP 客户端** | Spring WebClient (Reactor Netty) | 原生 SSE 流式、响应式背压 |
| **序列化** | Jackson (Spring Boot 默认) | JSON 序列化/反序列化 |
| **配置** | Spring Boot `application.yml` | profile、env var、`@ConfigurationProperties` |
| **DI** | Spring IoC | `@Component` 自动注册、`@Autowired` 注入 |
| **CLI** | Picocli + `picocli-spring-boot-starter` | CLI 框架与 Spring Boot 桥接 |
| **TUI** | JLine 3 + Lanterna | 纯终端 ANSI 渲染 |
| **测试** | Spring Boot Test + JUnit 5 + Mockito + MockWebServer | `@SpringBootTest`、`WebTestClient` |
| **监控** | Spring Boot Actuator + Micrometer | 健康检查、指标 |
| **日志** | SLF4J + Logback (Spring Boot 默认) | |
| **打包** | Spring Boot Gradle 插件 | 可执行 JAR |
| **构建** | Gradle 8.10.2 (Kotlin DSL) | 多模块构建 |

### LLM SDK 依赖

| 用途 | Maven 坐标 | 版本 |
|------|-----------|------|
| **Anthropic** | `com.anthropic:anthropic-java` | 2.18.0 |
| **OpenAI** | `com.openai:openai-java` | 4.29.1 |
| **Google Vertex AI** | `com.google.cloud:google-cloud-aiplatform` | 3.79.0 |
| **AWS Bedrock** | `software.amazon.awssdk:bedrockruntime` | 2.41.34 |
| **JSON Schema 校验** | `com.networknt:json-schema-validator` | 1.5.2 |

---

## 二、模块架构

```
campusclaw-coding-agent (Spring Boot 应用)
    ├── campusclaw-agent-core (Spring Boot 库)
    │     └── campusclaw-ai (Spring Boot 库)
    └── campusclaw-tui (纯 Java 库，不依赖 Spring)
```

### 模块职责

| 模块 | Gradle 路径 | 包名 | 角色 |
|------|------------|------|------|
| **campusclaw-ai** | `:modules:campusclaw-ai` | `com.campusclaw.ai` | 统一多 LLM 提供商 API、类型定义、流式协议 |
| **campusclaw-agent-core** | `:modules:campusclaw-agent-core` | `com.campusclaw.agent` | 代理运行时、状态管理、工具框架、Agent 循环 |
| **campusclaw-coding-agent** | `:modules:campusclaw-coding-agent` | `com.campusclaw.codingagent` | CLI 入口、内置工具、系统提示、运行模式 |
| **campusclaw-tui** | `:modules:campusclaw-tui` | `com.campusclaw.tui` | 终端 UI 组件、ANSI 渲染、差量更新 |

### Spring Boot 架构要点

1. **Provider 注册**: 每个 Provider 实现 `ApiProvider` 接口并标记 `@Component`，Spring 自动收集注入到 `ApiProviderRegistry`
2. **配置管理**: `@ConfigurationProperties` 绑定 API keys 和模型配置
3. **流式响应**: `Flux<AssistantMessageEvent>` 作为流式协议载体
4. **Tool 执行**: Spring Bean 定义工具，虚拟线程执行阻塞 I/O
5. **CLI 启动**: `SpringApplication` + Picocli，通过 `picocli-spring-boot-starter` 桥接
6. **启动速度**: `spring.main.lazy-initialization=true` + AOT 编译优化
7. **模块边界**: campusclaw-ai/campusclaw-agent-core 作为 Spring Boot starter 库，campusclaw-coding-agent 作为 Spring Boot 应用，campusclaw-tui 不依赖 Spring

---

## 三、Agent Team 工作流

### 角色定义

| 角色 | 权限 | 职责 |
|------|------|------|
| **Architect** | 只读（Read、Grep、Glob） | 分析需求、设计接口、输出技术规格文档 |
| **Developer** | 读写（+ Edit、Write、Bash） | 实现代码、编写单元测试 |
| **Tester** | 读写 | 集成测试、端到端测试、回归测试 |

### 需求执行流程

1. Architect 阅读需求 + pi-mono-typescript-architecture.md → 输出接口设计和实现方案
2. Developer 根据方案实现代码 + 单元测试
3. Tester 编写集成测试（仅复杂需求需要）

### 效率优化

- **并行执行**: 同阶段内无依赖的需求可分配给多个 Developer 并行
- **简单需求跳过 Architect**: 纯类型定义等简单需求直接给 Developer
- **Developer 自写单测**: Tester 只负责集成/端到端测试

---

## 四、需求列表

### Phase 1: AI 核心类型（campusclaw-ai）

| ID | 需求 | 复杂度 | 说明 |
|----|------|--------|------|
| AI-001 | Message 类型体系 | 中 | UserMessage、AssistantMessage、ToolResultMessage，使用 sealed interface + record |
| AI-002 | ContentBlock 类型 | 小 | TextContent、ImageContent、ThinkingContent、ToolCall |
| AI-003 | Tool 定义类型 | 小 | Tool record（name、description、parameters JSON Schema） |
| AI-004 | Model 定义类型 | 小 | Model record（id、provider、api、cost、contextWindow、maxTokens） |
| AI-005 | Usage & Cost 类型 | 小 | Usage record（input/output/cache tokens + cost USD） |

### Phase 2: AI 基础设施（campusclaw-ai）

| ID | 需求 | 复杂度 | 说明 |
|----|------|--------|------|
| AI-006 | EventStream 泛型类 | 大 | 异步事件流，支持 push/end/result，实现 Flux 适配 |
| AI-007 | AssistantMessageEvent 协议 | 中 | start/text_delta/thinking_delta/toolcall_delta/done/error 事件 |
| AI-008 | AssistantMessageEventStream | 中 | 特化 EventStream，解析 SSE 为 AssistantMessageEvent |
| AI-009 | ApiProvider 接口 | 中 | stream/streamSimple 方法签名，Provider 注册机制 |
| AI-010 | ApiProviderRegistry | 中 | Spring 自动收集 `@Component` Provider，按 API 类型查找 |
| AI-011 | StreamOptions 类型 | 小 | temperature、maxTokens、signal、apiKey、transport 等配置 |
| AI-012 | Model Registry | 中 | 模型注册表，按 provider + modelId 索引，自动发现 |
| AI-013 | Top-Level API (stream/complete) | 中 | 顶层 stream()、complete()、streamSimple()、completeSimple() |

### Phase 3: 优先 Provider（campusclaw-ai）

| ID | 需求 | 复杂度 | 说明 |
|----|------|--------|------|
| AI-014 | Anthropic Provider | 大 | 使用 anthropic-java SDK，SSE 流式，映射 content blocks |
| AI-015 | OpenAI Completions Provider | 大 | 使用 openai-java SDK，SSE 流式 |
| AI-016 | OpenAI Responses Provider | 大 | 新版 OpenAI Responses API，openclaw 需要 |
| AI-017 | AWS Bedrock Provider | 大 | 使用 bedrockruntime SDK，ConverseStream API |

### Phase 4: Agent Core（campusclaw-agent-core）

| ID | 需求 | 复杂度 | 说明 |
|----|------|--------|------|
| AC-001 | AgentState 状态机 | 中 | systemPrompt、model、tools、messages、isStreaming 等状态 |
| AC-002 | AgentTool 接口 | 中 | 扩展 Tool，添加 label、execute 方法、onUpdate 回调 |
| AC-003 | AgentEvent 事件体系 | 中 | agent_start/end、turn_start/end、message 事件、tool_execution 事件 |
| AC-004 | Tool 执行管道 | 大 | beforeToolCall → 参数校验 → execute → afterToolCall 钩子链 |
| AC-005 | Agent Loop | 大 | 消息循环：prompt → LLM 流式调用 → 工具执行 → 结果收集 → 循环 |
| AC-006 | Agent 类 | 大 | 状态管理、prompt/continue/abort、事件订阅、steering/followUp 队列 |
| AC-007 | Context 转换 | 中 | convertToLlm（app 消息 → LLM 消息）、transformContext（裁剪/注入） |
| AC-008 | Steering & Follow-up | 中 | 双层消息队列：steering（执行中插入）、followUp（停止后追加） |

### Phase 5: TUI 核心（campusclaw-tui）

| ID | 需求 | 复杂度 | 说明 |
|----|------|--------|------|
| TU-001 | Component 接口 + 差量渲染器 | 大 | render(width) → String[]、invalidate()、DiffRenderer |
| TU-002 | Terminal 抽象 | 中 | write、clear、moveCursor、getSize、raw mode、ANSI 序列 |
| TU-003 | 基础组件 | 中 | Text、Container、Box（边框/padding/背景色） |
| TU-004 | Markdown 渲染 | 大 | Markdown → ANSI 终端输出（代码块、标题、列表、链接） |
| TU-005 | Editor/Input 组件 | 大 | 多行编辑器（光标移动、选择、undo/redo、killring、word wrap） |
| TU-006 | SelectList 组件 | 中 | 可选列表（键盘导航、自定义渲染、maxHeight、fuzzy 过滤） |
| TU-007 | ANSI 工具函数 | 中 | visibleWidth、sliceByColumn、wrapTextWithAnsi、extractSegments |

### Phase 6: CLI + 内置工具（campusclaw-coding-agent）

| ID | 需求 | 复杂度 | 说明 |
|----|------|--------|------|
| CA-001 | CLI 入口 + 参数解析 | 中 | Picocli + Spring Boot 启动，model、prompt、mode 等参数 |
| CA-002 | 系统提示构建器 | 大 | 基础提示 + 工具描述 + 用户自定义 + 环境信息拼装 |
| CA-003 | 截断工具 | 小 | truncateHead、truncateTail、truncateLine、formatSize |
| CA-004 | 路径解析工具 | 小 | resolveToCwd、resolveReadPath、防目录遍历 |
| CA-005 | 文件变更队列 | 中 | 按文件粒度串行化写操作，防并发冲突 |
| CA-006 | Bash Executor | 大 | 进程管理、stdout/stderr 捕获、超时、环境变量、信号处理 |
| CA-007 | Bash Tool | 大 | 命令执行、输出截断、退出码处理 |
| CA-008 | Read Tool | 中 | 文件读取、行号偏移、行数限制、图片检测、输出截断 |
| CA-009 | Edit Tool | 大 | 精确文本替换、unified diff 生成、fuzzy 匹配 |
| CA-010 | Write Tool | 小 | 文件创建/覆写、自动创建父目录 |
| CA-011 | Grep Tool | 中 | 正则搜索、glob 过滤、文件类型过滤（封装 ripgrep 或 Java 实现） |
| CA-012 | Glob Tool | 中 | 文件模式搜索 |
| CA-013 | Tool Operations 接口 | 中 | 可插拔 I/O 操作层（本地/远程/SSH） |

### Phase 7: AgentSession + 运行模式 + Skill 系统（campusclaw-coding-agent）

| ID | 需求 | 复杂度 | 说明 |
|----|------|--------|------|
| CA-014 | AgentSession | 大 | 会话管理、历史记录、工具注册、事件分发 |
| CA-015 | 交互模式 | 大 | TUI 集成、实时流式输出、工具调用 UI 渲染 |
| CA-016 | One-shot 模式 | 中 | 单次 prompt → 执行 → 输出结果 |
| CA-017 | 会话持久化 | 中 | JSONL 格式导入/导出消息历史 |
| CA-018 | Skill 系统 | 大 | Skill 定义（SKILL.md + YAML frontmatter）、目录扫描加载、Registry、/skill:name 命令展开、系统提示集成 |

### Phase 8: 集成测试

| ID | 需求 | 复杂度 | 说明 |
|----|------|--------|------|
| IT-001 | Provider 集成测试 | 大 | MockWebServer 模拟各 Provider SSE 响应 |
| IT-002 | Agent Loop 端到端测试 | 大 | 完整消息循环：prompt → LLM → tool → result |
| IT-003 | CLI 端到端测试 | 大 | 命令行参数 → 启动 → 执行 → 输出验证 |

### 延后的需求（非 MVP）

| ID | 需求 | 原因 |
|----|------|------|
| AI-018 | Google Gemini Provider | 非核心 Provider |
| AI-019 | Mistral Provider | 非核心 Provider |
| AI-020 | GitHub Copilot Provider | 非核心 Provider |
| AI-021 | GitHub Copilot OAuth | 依赖 AI-020 |
| CA-019 | Extension Loader/Runner | 非 MVP |
| TU-008 | 虚拟滚动 | 性能优化，非 MVP |
| TU-009 | 可访问性 | 非 MVP |

---

## 五、阶段排序（openclaw 优先）

| Phase | 内容 | 可并行 | 预期产出 |
|-------|------|--------|---------|
| **1** | AI 核心类型 (AI-001~005) | 5 个需求可并行 | 类型定义 |
| **2** | AI 基础设施 (AI-006~013) | 部分可并行 | 流式框架 + Registry |
| **3** | 优先 Provider (AI-014~017) | 4 个 Provider 可并行 | LLM 调用能力 |
| **4** | Agent Core (AC-001~008) | 类型定义可并行 | 代理运行时 |
| **5** | TUI 核心 (TU-001~007) | 部分可并行 | 终端 UI |
| **6** | CLI + 内置工具 (CA-001~013) | 工具可并行 | 编码工具链 |
| **7** | AgentSession + 运行模式 + Skill (CA-014~018) | | 完整应用 |
| **8** | 集成测试 (IT-001~003) | 可并行 | 质量保障 |

---

## 六、Java 21 特性使用指南

| Java 21 特性 | 应用场景 |
|-------------|---------|
| `record` | 所有不可变数据类型：Message、ContentBlock、Usage、Model、ToolCall |
| `sealed interface` | Message 类型层级、ContentBlock 类型层级、AgentEvent 类型层级 |
| `switch` pattern matching | 事件处理、消息类型分派 |
| 虚拟线程 (`Thread.ofVirtual()`) | 工具执行（Bash、文件 I/O）、并行工具调用 |
| 文本块 (`"""..."""`) | 系统提示模板、工具描述 |
| `Optional` + pattern matching | 配置项解析、nullable 字段处理 |

---

## 七、验证方法

1. `./gradlew dependencies` — 依赖可解析
2. `./gradlew compileJava` — 编译通过
3. `./gradlew test` — 测试通过
4. `./gradlew bootRun` — Spring Boot 应用可启动
5. `./gradlew build` — 全量构建
6. `./gradlew bootJar` — 可执行 JAR 打包

---

## 八、项目结构

```
campusclaw/
├── build.gradle.kts              # 根构建配置
├── settings.gradle.kts           # 模块注册
├── gradle.properties             # JVM 参数
├── ARCHITECTURE.md               # TS 架构参考（详细）
├── campusclaw-review.md        # 本文档
├── modules/
│   ├── ai/                       # campusclaw-ai 模块
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/mariozechner/pi/ai/
│   ├── agent-core/               # campusclaw-agent-core 模块
│   │   ├── build.gradle.kts
│   │   └── src/main/java/com/mariozechner/pi/agent/
│   ├── coding-agent-cli/         # campusclaw-coding-agent 模块
│   │   ├── build.gradle.kts
│   │   ├── src/main/java/com/mariozechner/pi/codingagent/
│   │   └── src/main/resources/application.yml
│   └── tui/                      # campusclaw-tui 模块
│       ├── build.gradle.kts
│       └── src/main/java/com/mariozechner/pi/tui/
└── gradle/
    └── wrapper/
```
