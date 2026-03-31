# Pi-Mono TypeScript 架构参考

本文档详细记录 pi-mono TypeScript 版本的架构、类型定义和实现细节，作为 Java 版本开发的参考。

TS 项目路径: `/Users/z/pi-mono/`

---

## 一、pi-ai 模块 (`packages/ai/`)

统一多 LLM 提供商 API 层。

### 1.1 Message 类型体系

```typescript
// 核心消息联合类型
type Message = UserMessage | AssistantMessage | ToolResultMessage

interface UserMessage {
  role: "user"
  content: string | (TextContent | ImageContent)[]
  timestamp: number  // Unix 毫秒
}

interface AssistantMessage {
  role: "assistant"
  content: (TextContent | ThinkingContent | ToolCall)[]
  api: Api               // "anthropic-messages" | "openai-responses" | ...
  provider: Provider     // "anthropic" | "openai" | ...
  model: string          // "claude-opus-4-6"
  responseId?: string
  usage: Usage
  stopReason: StopReason // "stop" | "length" | "toolUse" | "error" | "aborted"
  errorMessage?: string
  timestamp: number
}

interface ToolResultMessage<TDetails = any> {
  role: "toolResult"
  toolCallId: string
  toolName: string
  content: (TextContent | ImageContent)[]
  details?: TDetails
  isError: boolean
  timestamp: number
}
```

### 1.2 ContentBlock 类型

```typescript
interface TextContent {
  type: "text"
  text: string
  textSignature?: string  // OpenAI 元数据
}

interface ThinkingContent {
  type: "thinking"
  thinking: string
  thinkingSignature?: string
  redacted?: boolean
}

interface ImageContent {
  type: "image"
  data: string       // base64
  mimeType: string   // "image/jpeg" | "image/png" | ...
}

interface ToolCall {
  type: "toolCall"
  id: string
  name: string
  arguments: Record<string, any>
  thoughtSignature?: string  // Google 特有
}
```

**Java 映射**: 使用 `sealed interface ContentBlock permits TextContent, ImageContent, ThinkingContent, ToolCall`，每个实现为 `record`。

### 1.3 Usage & Cost

```typescript
interface Usage {
  input: number           // 输入 token 数
  output: number          // 输出 token 数
  cacheRead: number       // prompt cache 读取 token
  cacheWrite: number      // prompt cache 写入 token
  totalTokens: number
  cost: {
    input: number         // USD
    output: number
    cacheRead: number
    cacheWrite: number
    total: number
  }
}
```

### 1.4 Model 定义

```typescript
interface Model<TApi extends Api> {
  id: string
  name: string
  api: TApi
  provider: Provider
  baseUrl: string
  reasoning: boolean
  input: ("text" | "image")[]
  cost: {
    input: number         // $/百万 token
    output: number
    cacheRead: number
    cacheWrite: number
  }
  contextWindow: number
  maxTokens: number
  headers?: Record<string, string>
  compat?: OpenAICompletionsCompat | OpenAIResponsesCompat
}
```

模型注册表按 `Map<provider, Map<modelId, Model>>` 索引，从 `models.generated.ts` 加载。

### 1.5 Tool 定义

```typescript
interface Tool<TParameters extends TSchema = TSchema> {
  name: string
  description: string
  parameters: TParameters  // JSON Schema (TypeBox)
}

interface Context {
  systemPrompt?: string
  messages: Message[]
  tools?: Tool[]
}
```

TS 使用 TypeBox 库生成 JSON Schema + TypeScript 类型。Java 版使用 `com.networknt:json-schema-validator` 进行校验。

### 1.6 EventStream 流式架构

```typescript
class EventStream<T, R = T> implements AsyncIterable<T> {
  constructor(
    isComplete: (event: T) => boolean,
    extractResult: (event: T) => R
  )

  push(event: T): void    // 推送事件
  end(result?: R): void   // 结束流
  result(): Promise<R>    // 获取最终结果
  [Symbol.asyncIterator](): AsyncIterator<T>  // 异步迭代
}

// 特化流
class AssistantMessageEventStream extends EventStream<
  AssistantMessageEvent, AssistantMessage
>
```

**Java 映射**: 使用 `Flux<AssistantMessageEvent>` 作为流式载体，通过 `Sinks.Many` 实现 push 语义。最终结果通过 `Mono<AssistantMessage>` 获取。

### 1.7 AssistantMessageEvent 协议

```typescript
type AssistantMessageEvent =
  // 流开始
  | { type: "start"; partial: AssistantMessage }
  // 文本流
  | { type: "text_start"; contentIndex: number; partial: AssistantMessage }
  | { type: "text_delta"; contentIndex: number; delta: string; partial: AssistantMessage }
  | { type: "text_end"; contentIndex: number; content: string; partial: AssistantMessage }
  // 思考流
  | { type: "thinking_start"; contentIndex: number; partial: AssistantMessage }
  | { type: "thinking_delta"; contentIndex: number; delta: string; partial: AssistantMessage }
  | { type: "thinking_end"; contentIndex: number; content: string; partial: AssistantMessage }
  // 工具调用流
  | { type: "toolcall_start"; contentIndex: number; partial: AssistantMessage }
  | { type: "toolcall_delta"; contentIndex: number; delta: string; partial: AssistantMessage }
  | { type: "toolcall_end"; contentIndex: number; toolCall: ToolCall; partial: AssistantMessage }
  // 完成
  | { type: "done"; reason: "stop" | "length" | "toolUse"; message: AssistantMessage }
  | { type: "error"; reason: "error" | "aborted"; error: AssistantMessage }
```

**Java 映射**: `sealed interface AssistantMessageEvent permits TextStartEvent, TextDeltaEvent, ... DoneEvent, ErrorEvent`

### 1.8 ApiProvider 接口 & Registry

```typescript
interface ApiProvider<TApi extends Api, TOptions extends StreamOptions> {
  api: TApi
  stream: (model: Model<TApi>, context: Context, options?: TOptions) => AssistantMessageEventStream
  streamSimple: (model: Model<TApi>, context: Context, options?: SimpleStreamOptions) => AssistantMessageEventStream
}

// Registry 操作
function registerApiProvider(provider: ApiProvider, sourceId?: string): void
function getApiProvider(api: Api): ApiProvider | undefined
function getApiProviders(): ApiProvider[]
function unregisterApiProviders(sourceId: string): void
function clearApiProviders(): void
```

**Java 映射**: `ApiProvider` 接口 + `@Component` 注解，`ApiProviderRegistry` 通过 Spring `@Autowired List<ApiProvider>` 自动收集。

### 1.9 StreamOptions

```typescript
interface StreamOptions {
  temperature?: number
  maxTokens?: number
  signal?: AbortSignal
  apiKey?: string
  transport?: "sse" | "websocket" | "auto"
  cacheRetention?: "none" | "short" | "long"
  sessionId?: string
  onPayload?: (payload: unknown, model: Model) => unknown | undefined
  headers?: Record<string, string>
  maxRetryDelayMs?: number
  metadata?: Record<string, unknown>
}

interface SimpleStreamOptions extends StreamOptions {
  reasoning?: ThinkingLevel  // "minimal" | "low" | "medium" | "high" | "xhigh"
  thinkingBudgets?: ThinkingBudgets
}
```

### 1.10 顶层 API

```typescript
function stream(model, context, options?): AssistantMessageEventStream
async function complete(model, context, options?): Promise<AssistantMessage>
function streamSimple(model, context, options?): AssistantMessageEventStream
async function completeSimple(model, context, options?): Promise<AssistantMessage>
```

### 1.11 Provider 类型

```typescript
type KnownProvider =
  | "anthropic" | "openai" | "azure-openai-responses" | "openai-codex"
  | "google" | "google-vertex" | "google-gemini-cli"
  | "mistral" | "amazon-bedrock" | ...

type KnownApi =
  | "anthropic-messages" | "openai-responses" | "openai-completions"
  | "openai-codex-responses" | "bedrock-converse-stream"
  | "google-generative-ai" | "google-vertex"
  | "mistral-conversations" | "azure-openai-responses" | ...
```

---

## 二、pi-agent-core 模块 (`packages/agent/`)

通用代理框架，提供状态管理、工具调用和事件系统。

### 2.1 AgentState

```typescript
interface AgentState {
  systemPrompt: string
  model: Model<any>
  thinkingLevel: ThinkingLevel  // "off" | "minimal" | "low" | "medium" | "high" | "xhigh"
  tools: AgentTool[]
  messages: AgentMessage[]
  isStreaming: boolean
  streamMessage: AgentMessage | null
  pendingToolCalls: Set<string>
  error?: string
}
```

### 2.2 AgentTool

```typescript
interface AgentTool<TParameters, TDetails = any> extends Tool<TParameters> {
  label: string
  execute(
    toolCallId: string,
    params: Static<TParameters>,
    signal?: AbortSignal,
    onUpdate?: AgentToolUpdateCallback<TDetails>
  ): Promise<AgentToolResult<TDetails>>
}

interface AgentToolResult<T> {
  content: (TextContent | ImageContent)[]
  details: T
}

type AgentToolUpdateCallback<T> = (partialResult: AgentToolResult<T>) => void
```

### 2.3 Tool 执行钩子

```typescript
interface BeforeToolCallContext {
  assistantMessage: AssistantMessage
  toolCall: AgentToolCall
  args: unknown
  context: AgentContext
}

interface BeforeToolCallResult {
  block?: boolean    // true = 阻止执行
  reason?: string
}

interface AfterToolCallContext {
  assistantMessage: AssistantMessage
  toolCall: AgentToolCall
  args: unknown
  result: AgentToolResult<any>
  isError: boolean
  context: AgentContext
}

interface AfterToolCallResult {
  content?: (TextContent | ImageContent)[]  // 覆盖结果
  details?: unknown
  isError?: boolean
}
```

### 2.4 AgentEvent 事件体系

```typescript
type AgentEvent =
  | { type: "agent_start" }
  | { type: "agent_end"; messages: AgentMessage[] }
  | { type: "turn_start" }
  | { type: "turn_end"; message: AgentMessage; toolResults: ToolResultMessage[] }
  | { type: "message_start"; message: AgentMessage }
  | { type: "message_update"; message: AgentMessage; assistantMessageEvent: AssistantMessageEvent }
  | { type: "message_end"; message: AgentMessage }
  | { type: "tool_execution_start"; toolCallId: string; toolName: string; args: any }
  | { type: "tool_execution_update"; toolCallId: string; toolName: string; args: any; partialResult: any }
  | { type: "tool_execution_end"; toolCallId: string; toolName: string; result: any; isError: boolean }
```

**Java 映射**: `sealed interface AgentEvent permits AgentStartEvent, AgentEndEvent, TurnStartEvent, ...`

### 2.5 Agent Loop 配置

```typescript
interface AgentLoopConfig extends SimpleStreamOptions {
  model: Model<any>

  // app 消息 → LLM 消息 转换
  convertToLlm: (messages: AgentMessage[]) => Message[]

  // 上下文转换（裁剪、注入）
  transformContext?: (messages: AgentMessage[], signal?) => Promise<AgentMessage[]>

  // 动态 API key（处理 token 过期）
  getApiKey?: (provider: string) => Promise<string | undefined>

  // Steering 消息（执行中方向变更）
  getSteeringMessages?: () => Promise<AgentMessage[]>

  // Follow-up 消息（agent 停止后处理）
  getFollowUpMessages?: () => Promise<AgentMessage[]>

  // 工具执行控制
  toolExecution?: "sequential" | "parallel"
  beforeToolCall?: (context: BeforeToolCallContext) => Promise<BeforeToolCallResult | undefined>
  afterToolCall?: (context: AfterToolCallContext) => Promise<AfterToolCallResult | undefined>
}
```

### 2.6 Agent 类完整 API

```typescript
class Agent {
  // 构造
  constructor(opts?: AgentOptions)

  // 状态访问器
  get state(): AgentState
  get sessionId(): string | undefined
  get transport(): Transport
  get toolExecution(): ToolExecutionMode

  // 状态变更
  setSystemPrompt(v: string): void
  setModel(m: Model): void
  setThinkingLevel(l: ThinkingLevel): void
  setTools(t: AgentTool[]): void
  replaceMessages(ms: AgentMessage[]): void
  appendMessage(m: AgentMessage): void
  clearMessages(): void
  reset(): void

  // 工具钩子
  setBeforeToolCall(fn): void
  setAfterToolCall(fn): void

  // Steering & Follow-up
  steer(m: AgentMessage): void       // 执行中插入消息
  followUp(m: AgentMessage): void    // 停止后追加消息
  clearSteeringQueue(): void
  clearFollowUpQueue(): void
  setSteeringMode(mode: "all" | "one-at-a-time"): void
  setFollowUpMode(mode: "all" | "one-at-a-time"): void

  // 执行
  async prompt(message: AgentMessage | string): Promise<void>
  async continue(): Promise<void>
  abort(): void
  async waitForIdle(): Promise<void>

  // 事件
  subscribe(fn: (e: AgentEvent) => void): () => void  // 返回取消订阅函数
}
```

### 2.7 Agent Loop 函数

```typescript
// 返回 EventStream
function agentLoop(
  prompts: AgentMessage[],
  context: AgentContext,
  config: AgentLoopConfig,
  signal?: AbortSignal
): EventStream<AgentEvent, AgentMessage[]>

function agentLoopContinue(
  context: AgentContext,
  config: AgentLoopConfig,
  signal?: AbortSignal
): EventStream<AgentEvent, AgentMessage[]>

// 直接执行
async function runAgentLoop(
  prompts: AgentMessage[],
  context: AgentContext,
  config: AgentLoopConfig,
  emit: (event: AgentEvent) => void,
  signal?: AbortSignal
): Promise<AgentMessage[]>
```

### 2.8 事件发射序列

```
用户输入 "Do something"
  → agent_start
  → turn_start
  → message_start (user message)
  → message_end
  → [LLM 流式响应]
    → message_start (assistant message)
    → message_update × N (text_delta, toolcall_delta)
    → message_end
  → [工具执行（如有）]
    → tool_execution_start
    → tool_execution_update × N (流式结果)
    → tool_execution_end
  → [生成 ToolResultMessage]
  → turn_end
  → [检查 steering/followUp → 如有则循环回 turn_start]
  → agent_end
```

---

## 三、pi-coding-agent 模块 (`packages/coding-agent/`)

交互式编码代理 CLI，是平台主入口。

### 3.1 内置工具参数与结果

#### Read Tool

```typescript
// 输入
interface ReadToolInput {
  path: string          // 相对或绝对路径
  offset?: number       // 起始行号（1-indexed）
  limit?: number        // 最大行数
}

// 详情
interface ReadToolDetails {
  truncation?: TruncationResult
}

// 常量
const DEFAULT_MAX_BYTES = 32768  // 32KB
const DEFAULT_MAX_LINES = 500
```

#### Bash Tool

```typescript
// 输入
interface BashToolInput {
  command: string       // Bash 命令
  timeout?: number      // 超时秒数
}

// 详情
interface BashToolDetails {
  truncation?: TruncationResult
  fullOutputPath?: string  // 截断时的完整输出路径
}

// 执行器接口
interface BashOperations {
  exec(
    command: string,
    cwd: string,
    options: {
      onData: (data: Buffer) => void
      signal?: AbortSignal
      timeout?: number
      env?: ProcessEnv
    }
  ): Promise<{ exitCode: number | null }>
}
```

#### Edit Tool

```typescript
// 输入
interface EditToolInput {
  path: string          // 文件路径
  oldText: string       // 精确匹配的原文
  newText: string       // 替换文本
}

// 详情
interface EditToolDetails {
  diff: string          // Unified diff
  firstChangedLine?: number
}

// 编辑操作接口
interface EditOperations {
  readFile(path: string): Promise<Buffer>
  writeFile(path: string, content: string): Promise<void>
  access(path: string): Promise<void>
}

// Fuzzy 匹配
function fuzzyFindText(haystack: string, needle: string): { start: number; end: number } | null
```

#### Write Tool

```typescript
interface WriteToolInput {
  path: string          // 文件路径
  content: string       // 写入内容
}

interface WriteOperations {
  writeFile(path: string, content: string): Promise<void>
  mkdir(dir: string): Promise<void>  // 递归创建
}
```

#### Grep Tool

```typescript
interface GrepToolInput {
  pattern: string       // 正则表达式
  path?: string         // 文件或目录
  glob?: string         // Glob 模式（如 "*.ts"）
  type?: string         // 文件类型过滤（如 "js"、"py"）
}
```

#### Glob Tool

```typescript
interface GlobToolInput {
  pattern: string       // Glob 模式
  path?: string         // 起始目录
}
```

### 3.2 Tool 工厂

```typescript
// 单个工具创建
function createReadTool(cwd: string, options?: ReadToolOptions): AgentTool
function createBashTool(cwd: string, options?: BashToolOptions): AgentTool
function createEditTool(cwd: string): AgentTool
function createWriteTool(cwd: string): AgentTool
function createGrepTool(cwd: string): AgentTool
function createFindTool(cwd: string): AgentTool
function createLsTool(cwd: string): AgentTool

// 批量创建
function createCodingTools(cwd: string, options?: ToolsOptions): AgentTool[]
function createReadOnlyTools(cwd: string, options?: ToolsOptions): AgentTool[]
function createAllTools(cwd: string, options?: ToolsOptions): Record<ToolName, AgentTool>
```

### 3.3 可插拔 Operations 模式

所有工具通过 Operations 接口抽象 I/O，支持远程执行：

```typescript
// ReadOperations → 本地文件系统 / SSH / RPC
// BashOperations → 本地 shell / 远程 shell
// EditOperations → 本地 FS / 远程 FS
// WriteOperations → 本地 FS / 远程 FS

const tool = createReadTool(cwd, {
  operations: customSshOperations
})
```

**Java 映射**: 每个 Operations 定义为 Java `interface`，默认实现使用本地文件系统/进程。

### 3.4 ToolDefinition 包装

```typescript
interface ToolDefinition<TParameters, TDetails> {
  name: string
  label: string
  description: string
  promptSnippet: string       // 嵌入系统提示的工具描述
  promptGuidelines: string[]  // 使用指南
  parameters: TParameters     // JSON Schema

  execute(
    toolCallId: string,
    params: Static<TParameters>,
    signal?: AbortSignal,
    onUpdate?: (partialResult: any) => void,
    ctx?: any
  ): Promise<AgentToolResult<TDetails>>
}
```

### 3.5 截断工具

```typescript
interface TruncationOptions {
  maxBytes?: number
  maxLines?: number
}

interface TruncationResult {
  truncated: boolean
  outputLines: number
  totalLines: number
  maxLines?: number
  maxBytes?: number
  firstLineExceedsLimit: boolean
  truncatedBy?: "lines" | "bytes"
}

function truncateHead(text: string, options: TruncationOptions): TruncationResult
function truncateTail(text: string, options: TruncationOptions): TruncationResult
function truncateLine(line: string, maxBytes: number): TruncationResult
function formatSize(bytes: number): string  // "32KB"
```

### 3.6 文件变更队列

```typescript
// 按文件粒度串行化写操作，防止并发冲突
function withFileMutationQueue<T>(fn: () => Promise<T>): Promise<T>
```

**Java 映射**: `ConcurrentHashMap<String, ReentrantLock>` 或 `Striped<Lock>`（Guava）按文件路径加锁。

### 3.7 路径解析

```typescript
function resolveReadPath(input: string, cwd: string): string   // 解析读路径
function resolveToCwd(input: string, cwd: string): string      // 解析到 CWD
// 防止目录遍历攻击
```

### 3.8 系统提示构建

系统提示由以下部分组成：

1. **基础提示**: 默认的角色定义和行为指南
2. **工具描述**: 每个工具的 `promptSnippet` + `promptGuidelines` 拼接
3. **用户自定义**: 环境变量、配置文件、命令行参数覆盖
4. **环境信息**: 当前目录、git 状态、操作系统等

### 3.9 Bash Executor

```typescript
// 独立于 Bash Tool 的执行引擎
async function executeBash(
  command: string,
  cwd: string,
  options?: {
    timeout?: number
    signal?: AbortSignal
    env?: ProcessEnv
  }
): Promise<{
  exitCode: number | null  // null = 被信号杀死
  stdout?: string
  stderr?: string
}>
```

### 3.10 CLI 入口

```
cli.ts → main.ts
  1. 解析命令行参数（model、prompt、mode、cwd）
  2. 加载配置（~/.pi/config.json）
  3. 初始化模式（interactive / one-shot / RPC）
  4. 创建 AgentSession
  5. 加载扩展
  6. 运行 Agent Loop
```

---

## 四、pi-tui 模块 (`packages/tui/`)

高性能终端 UI 组件库，支持差量渲染。

### 4.1 Component 接口

```typescript
interface Component {
  render(width: number): string[]    // 渲染为行数组
  handleInput?(data: string): void   // 处理键盘输入
  wantsKeyRelease?: boolean          // 是否需要按键释放事件
  invalidate(): void                 // 标记需要重渲染
}

interface Focusable {
  focused: boolean
}

function isFocusable(component: Component | null): component is Component & Focusable
```

**核心设计**: `render(width)` 返回 `string[]`，每个字符串是一行终端输出（含 ANSI 转义码）。组件不关心垂直位置，只关心可用宽度。

### 4.2 内置组件

#### Text

```typescript
class Text implements Component {
  constructor(text: string, paddingX?: number, paddingY?: number, customBgFn?)
  setText(text: string): void
  render(width: number): string[]
}
```

#### Container

```typescript
class Container implements Component {
  children: Component[] = []
  addChild(component: Component): void
  removeChild(component: Component): void
  clear(): void
  render(width: number): string[]  // 纵向排列子组件
}
```

#### Box

```typescript
class Box implements Component {
  constructor(child: Component, options?: {
    borderStyle?: "single" | "double" | "rounded"
    padding?: number
    paddingX?: number
    paddingY?: number
    bgColor?: string
    borderColor?: string
  })
  render(width: number): string[]
}
```

#### Editor（可聚焦）

```typescript
class Editor implements Component, Focusable {
  focused: boolean
  constructor(initialText?: string)

  getText(): string
  setText(text: string): void
  getCursorPosition(): { line: number; column: number }
  setCursorPosition(line: number, column: number): void
  handleInput(data: string): void
  render(width: number): string[]
}
```

功能：光标移动、选择、undo/redo、kill ring、word wrap、IME 支持。

#### SelectList

```typescript
class SelectList<T> implements Component, Focusable {
  focused: boolean
  constructor(items: T[], renderItem: (item: T) => string, options?: {
    maxHeight?: number
    theme?: { selectedBg?: string; selectedFg?: string; normalFg?: string }
  })

  getSelectedItem(): T | undefined
  setSelectedIndex(index: number): void
  handleInput(data: string): void
  render(width: number): string[]
}
```

#### Input

```typescript
class Input implements Component, Focusable {
  focused: boolean
  constructor(placeholder?: string)
  getValue(): string
  setValue(value: string): void
  handleInput(data: string): void
  render(width: number): string[]
}
```

#### Markdown

```typescript
class Markdown implements Component {
  constructor(content: string)
  setContent(content: string): void
  render(width: number): string[]
}
```

### 4.3 Terminal 抽象

```typescript
interface Terminal {
  write(data: string): void
  clear(): void
  moveCursor(row: number, col: number): void
  getSize(): { width: number; height: number }
  on(event: string, listener: (data: string) => void): void
}
```

### 4.4 TUI 类

```typescript
class TUI {
  constructor(component: Component, terminal?: Terminal, options?: TUIOptions)

  // 根组件
  setComponent(component: Component): void
  getComponent(): Component

  // Overlay 系统
  showOverlay(component: Component, options?: OverlayOptions): OverlayHandle

  // 事件
  on(event: string, handler: (data: any) => void): void
  off(event: string, handler: (data: any) => void): void

  // 渲染
  render(): void
  invalidate(): void

  // 生命周期
  start(): Promise<void>
  stop(): void

  // 焦点管理
  focus(component: Component): void
  blur(): void
}

interface OverlayOptions {
  width?: SizeValue       // number | "50%"
  minWidth?: number
  maxHeight?: SizeValue
  anchor?: OverlayAnchor  // "center" | "top-left" | ...
  offsetX?: number
  offsetY?: number
  row?: SizeValue
  col?: SizeValue
  margin?: OverlayMargin | number
  visible?: (termWidth: number, termHeight: number) => boolean
  nonCapturing?: boolean
}

interface OverlayHandle {
  hide(): void
  setHidden(hidden: boolean): void
  isHidden(): boolean
  focus(): void
  unfocus(): void
  isFocused(): boolean
}
```

### 4.5 ANSI 工具函数

```typescript
// 提取 ANSI 感知的文本段
function extractSegments(text: string): Array<{ text: string; isAnsi: boolean }>

// 按列切片（保留 ANSI 码）
function sliceByColumn(text: string, startCol: number, endCol: number): string
function sliceWithWidth(text: string, startIdx: number, width: number): string

// 计算可见宽度（忽略 ANSI 码）
function visibleWidth(text: string): number

// ANSI 感知换行
function wrapTextWithAnsi(text: string, maxWidth: number): string[]

// 应用背景色
function applyBackgroundToLine(line: string, width: number, bgFn: (text: string) => string): string
```

### 4.6 差量渲染

```typescript
class DiffRenderer {
  lastRendered: string[]
  currentRendered: string[]

  getDiff(): RenderDiff
}

interface RenderDiff {
  type: "line_update" | "full_rerender"
  startRow?: number
  updates?: Array<{ row: number; content: string }>
}
```

只重绘变化的行，而非整个屏幕。

### 4.7 输入处理

```typescript
function matchesKey(data: string, config: KeyConfig): boolean
function isKeyRelease(data: string): boolean
function decodeKittyPrintable(encoded: string): string

class UndoStack<T> {
  push(state: T): void
  undo(): T | undefined
  redo(): T | undefined
  clear(): void
}

class KillRing {
  push(text: string): void
  yank(): string | undefined
  yankPop(): string | undefined
}

const CURSOR_MARKER = "\x1b_pi:c\x07"  // IME 光标定位
```

### 4.8 Word Wrap

```typescript
function wordWrapLine(
  line: string,
  maxWidth: number,
  preSegmented?: Intl.SegmentData[]
): TextChunk[]

interface TextChunk {
  text: string
  startIndex: number
  endIndex: number
}
```

---

## 五、跨模块关注点

### 5.1 模块依赖图

```
pi-ai (最底层 — 纯类型 & 流式)
  ├─ 无外部 AI 库依赖（可插拔）
  ├─ 使用 TypeBox 生成 JSON Schema
  └─ 导出：类型、EventStream、ApiProvider Registry

pi-agent-core (依赖 pi-ai)
  ├─ 实现 Agent Loop
  ├─ 消息转换 & 工具执行
  ├─ 事件发射模式
  └─ 导出：Agent 类、agentLoop 函数

pi-tui (独立)
  ├─ Terminal 抽象
  ├─ Component 模型
  ├─ 差量渲染
  └─ 导出：TUI 类、组件

pi-coding-agent (依赖全部)
  ├─ 实现 read/write/bash/edit/grep/glob 工具
  ├─ 系统提示构建
  ├─ CLI 参数解析
  ├─ 使用 pi-agent-core 驱动 Agent
  ├─ 使用 pi-tui 渲染交互界面
  └─ 主导出：AgentSession
```

### 5.2 配置流

```
优先级（高 → 低）:
  1. 命令行参数
  2. 环境变量
  3. 配置文件（~/.pi/config.json 或 ~/.claude/config.json）
  4. 默认值
```

### 5.3 错误处理模式

- **校验错误**: 参数不合法时抛出 ValidationError（code + details）
- **执行错误**: 工具执行失败返回 `ToolResultMessage { isError: true }`
- **LLM 错误**: `AssistantMessage { stopReason: "error", errorMessage: "..." }`
- **中断**: `AbortSignal` 取消 → `stopReason: "aborted"`

### 5.4 关键架构模式

#### 可插拔 Operations

所有工具通过 Operations 接口抽象 I/O，使工具逻辑与底层实现解耦：

```
Tool 逻辑 → Operations 接口 → 本地实现 / SSH 实现 / RPC 实现
```

#### 内容块灵活性

所有消息使用 ContentBlock 数组，支持混合内容：

```typescript
content: [
  { type: "text", text: "分析结果：" },
  { type: "image", data: "...", mimeType: "image/png" },
  { type: "thinking", thinking: "让我思考..." }
]
```

#### 事件驱动流式

```
Provider 推送事件 → EventStream
  → 消费者异步迭代（for await）
  → 或等待最终结果（.result()）
```

#### 上下文窗口管理

```typescript
transformContext?: (messages) => Promise<messages>
// 每次 LLM 调用前，裁剪/压缩历史消息以适应上下文窗口
```

#### 工具执行管道

```
收到 ToolCall
  → beforeToolCall 钩子（可阻止）
  → 参数校验（JSON Schema）
  → 执行工具 Operation
  → afterToolCall 钩子（可覆盖结果）
  → 生成 ToolResultMessage
  → 加入上下文供下次 LLM 调用
```

#### Steering & Follow-up

双层消息队列用于运行时控制：

- **Steering**: 工具执行期间插入，在下次 LLM 调用前投递
- **Follow-up**: Agent 停止后投递，如有则触发新一轮循环
- 模式：`"all"` 一次投递全部 / `"one-at-a-time"` 逐条投递

---

## 六、Java 实现映射指南

| TypeScript 概念 | Java 实现 |
|----------------|-----------|
| `type Union = A \| B \| C` | `sealed interface` + `record` |
| `interface` | `interface` 或 `record` |
| `Record<string, any>` | `Map<String, Object>` |
| `Promise<T>` | `Mono<T>` (Reactor) 或 `CompletableFuture<T>` |
| `AsyncIterable<T>` | `Flux<T>` (Reactor) |
| `AbortSignal` | `CancellationToken` 自定义 或 Reactor `Disposable` |
| TypeBox JSON Schema | `com.networknt:json-schema-validator` |
| `EventStream.push()` | `Sinks.Many<T>.tryEmitNext()` |
| `for await (const e of stream)` | `flux.subscribe()` 或 `flux.blockFirst()` |
| 模块级函数 | Spring `@Service` 类方法 |
| `process.env` | Spring `@Value` / `@ConfigurationProperties` |
| `fs.readFile()` | `java.nio.file.Files.readString()` |
| `child_process.spawn()` | `ProcessBuilder` + 虚拟线程 |
| ANSI 转义码 | JLine 3 `AttributedString` / 直接 `\033[...m` |
