# WebSocket `/api/ws/chat` 测试指南

目标：验证 `ServerMode` 的 WebSocket 端点从协议到真 agent 全链路可用。

相关文件：

- 协议契约：[`docs/asyncapi/chat-ws.yaml`](../asyncapi/chat-ws.yaml)
- 实现：`modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/mode/server/ChatWebSocketHandler.java`
- 装配：`modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/mode/server/ServerMode.java`
- 自动化测试：`modules/coding-agent-cli/src/test/java/com/campusclaw/codingagent/mode/server/ChatWebSocketHandlerTest.java`
- 实现计划（历史记录）：[`docs/plans/ws-chat-plan.md`](../plans/ws-chat-plan.md)

## 测试分层

按成本从低到高排列，建议每次改动后从第 1 层往下跑到需要的层级。

| 层 | 目标 | 耗时 | 何时跑 |
|---|---|---|---|
| 1 | 单测 / 烟测 | ~1s | 每次改 WS 相关代码 |
| 2 | AsyncAPI 协议文档静态校验 | ~15s | 改 `chat-ws.yaml` 之后 |
| 3 | 手动连真 server（wscat） | 几分钟 | 改 ServerMode / ChatWebSocketHandler 之后 |
| 4 | 浏览器 / 前端联调 | 视前端进度 | 对接前端时 |

---

## 1. 自动化烟测

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
cd <repo-root>
./mvnw -pl modules/coding-agent-cli test -Dtest=ChatWebSocketHandlerTest
```

覆盖：握手 → `prompt` 命令 → `response` 同步响应 → `agent_start/message_*` 事件流 → `done` 终止。用 Mockito 替换 `SessionPool` + `AgentSession`，不起真 LLM。

只跑全量测试确认无回归：

```bash
./mvnw test
```

CI 里接这条即可。

## 2. AsyncAPI 协议文档校验

```bash
npx --yes @asyncapi/cli@latest validate docs/asyncapi/chat-ws.yaml
```

期待 `File ... is valid`，`0 errors, 0 warnings`（可能会有 1 条 `information` 说建议升级到 3.1.0，可忽略）。

**可选：生成静态 HTML 文档**

给前端看协议时比 YAML 直观：

```bash
npx --yes @asyncapi/cli@latest generate fromTemplate \
    docs/asyncapi/chat-ws.yaml \
    @asyncapi/html-template \
    -o /tmp/chat-ws-html \
    --force-write
open /tmp/chat-ws-html/index.html   # macOS
# xdg-open /tmp/chat-ws-html/index.html  # Linux
```

## 3. 手动连真 server（wscat）

### 3.1 前置

```bash
npm install -g wscat      # 一次安装
```

确认已配好至少一个模型的 API Key（按 README 选一个）：

```bash
export ANTHROPIC_API_KEY="sk-ant-..."   # 或 OPENAI_API_KEY / GLM 对应的 key 等
```

### 3.2 启动 server

```bash
./campusclaw.sh --mode server --port 3000 -m glm-5
```

启动日志里应看到：

```
CampusClaw API server started on http://localhost:3000
Endpoints:
  GET    /api/health
  POST   /api/chat
  ...
  WS     /api/ws/chat
```

最后一行是本次加的 WS endpoint。

### 3.3 快速自检（非 WS）

```bash
# 健康检查
curl http://localhost:3000/api/health
# {"status":"ok"}

# 原来的 SSE 端点没被破坏
curl -N http://localhost:3000/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"你好"}'
```

### 3.4 连 WebSocket

```bash
wscat -c ws://localhost:3000/api/ws/chat
```

进入交互模式后每行敲一个 JSON 帧。

**3.4.1 状态查询**

```json
{"type":"get_state","id":"1"}
```

期望响应：

```json
{"type":"response","id":"1","success":true,
 "data":{"conversation_id":"abc...","isStreaming":false,
         "model":"glm-5","thinkingLevel":"medium","messageCount":0}}
```

**3.4.2 发一个 prompt**

```json
{"type":"prompt","id":"2","message":"用 Java 写一个快速排序"}
```

按顺序应看到：

1. `{"type":"response","id":"2","success":true,"data":{"conversation_id":"..."}}` — 命令已接收
2. `{"type":"agent_start","conversation_id":"..."}`
3. `{"type":"message_start","conversation_id":"...","message":{...}}`
4. 多条 `{"type":"message_update","message":{...}}` — 流式增量
5. 可能出现 `{"type":"tool_start",...}` / `{"type":"tool_end",...}` —— 如果 agent 调工具
6. `{"type":"message_end","message":{...}}`
7. `{"type":"done","conversation_id":"...","finalText":"...","stopReason":"stop","usage":{...}}` —— `finalText` 是末条 assistant 所有 text 块拼接，可直接作为"最终答案"；`usage` 和 `stopReason` 来自同一条 assistant。`stopReason=error` 时会再追加一条 `error` 帧。

**3.4.3 中途 steer**

生成到一半时输入：

```json
{"type":"steer","message":"改用 Python 实现"}
```

预期：当前流未断，之后的 `message_update` 内容改用 Python。

**3.4.4 硬中止**

```json
{"type":"abort"}
```

预期：收到 `done` 帧，`isStreaming` 重新为 `false`。

**3.4.5 切模型**

```json
{"type":"set_model","id":"3","model":"sonnet"}
```

预期：`{"type":"response","id":"3","success":true,"data":{"model":"claude-sonnet-4-..."}}`。

**3.4.6 换思考级别**

```json
{"type":"set_thinking_level","id":"4","level":"high"}
```

非法值测试：

```json
{"type":"set_thinking_level","id":"5","level":"turbo"}
```

预期：`{"type":"response","id":"5","success":false,"error":"Invalid thinking level: turbo"}`。

**3.4.7 清空历史**

```json
{"type":"new_session","id":"6"}
```

之后 `{"type":"get_state","id":"7"}` 的 `messageCount` 应该归 0，但 `model` / `thinkingLevel` 保留。

**3.4.8 心跳**

连接挂着别动，每 20 秒应该收到：

```json
{"type":"pong"}
```

客户端发：

```json
{"type":"ping"}
```

也会立刻收到一个 `pong`。

**3.4.9 续聊（reconnect）**

先记下当前 `conversation_id`（`get_state` 的 data 里有），然后：

1. Ctrl+C 关掉 wscat
2. 重新带参数连上：

```bash
wscat -c "ws://localhost:3000/api/ws/chat?conversation_id=<刚才那个 id>"
```

3. 发：

```json
{"type":"get_state","id":"1"}
```

预期：`messageCount` 大于 0，说明复用了同一个 `AgentSession`。

**3.4.10 并发保护**

发一个 `prompt` 没等 `done` 就再发一个：

```json
{"type":"prompt","id":"1","message":"题目一"}
{"type":"prompt","id":"2","message":"题目二"}
```

预期：第二个返回 `{"type":"response","id":"2","success":false,"error":"conversation is already processing a prompt"}`。

**3.4.11 断连自动 abort**

发完 `prompt` 立刻 Ctrl+C 关 wscat。server 日志应出现：

```
WebSocket closed: conversation=... signal=...
```

然后 `isStreaming` 自动归 `false`（可在重新连上后用 `get_state` 确认）。

## 4. 浏览器 / 前端联调

### 4.1 自带的测试页 `docs/testing/ws-chat-client.html`

单文件 HTML + JS，无构建、无服务端依赖，**直接用浏览器打开就能用**，渲染效果接近 TUI：

- 💭 **Thinking** — 灰色折叠块，模型的思考过程
- 📝 **Text** — 助手正文，流式增量渲染
- 🔧 **Tool call** — 工具调用卡片，实时显示 `pending → running → done / error` 状态，自动把 `tool_end` 事件的 `result` 绑回对应卡片
- 👤 **User / Steer / Error** — 左侧彩色边条区分来源

布局：

```
┌─ Header: 连接状态 · conv · model · thinking · streaming · [Refresh] [New session] ──┐
│                                                                                   │
│  [message list]                                            │  Connection          │
│  - User bubble                                             │  Model / Thinking    │
│  - Assistant bubble (thinking / text / toolcall)           │  Event log (wire)    │
│  - Tool result attached to its toolcall card               │                      │
│                                                            │                      │
├─ Controls: [prompt textarea] [Prompt] [Steer] [Abort] ─────┴──────────────────────┤
```

#### 打开方式

```bash
# macOS
open docs/testing/ws-chat-client.html

# Linux
xdg-open docs/testing/ws-chat-client.html

# Windows
start docs/testing/ws-chat-client.html
```

也可以直接把 `file://` 路径粘到浏览器地址栏。**注意：浏览器 `file://` 下连 `ws://localhost` 在绝大多数现代浏览器里是允许的**；如果某些企业浏览器策略拦住了，把文件放到任意本地 HTTP server（如 `python -m http.server`）下打开即可。

#### 使用步骤

1. 侧边栏 **Connection** 区：WS URL 默认 `ws://localhost:3000/api/ws/chat`。**conversation_id** 留空 = 新开，填旧 ID = 续聊。
2. 点 **Connect** — 头部左侧圆点变绿表示握手成功；自动触发一次 `get_state` 把 conv / model / thinking 写到状态栏。
3. 下方输入框敲消息，**Cmd/Ctrl + Enter** 或点 **Prompt** 发送。
4. 生成过程中可点 **Steer** 调整方向、**Abort** 硬中止，或切 **Model / Thinking**。
5. **Event log**（右下）显示所有进出的原始 JSON 帧，排查协议问题时打开看。

#### 覆盖的事件 / 命令

页面已处理所有 AsyncAPI spec 里定义的 S→C 事件（`agent_start` / `message_start` / `message_update` / `message_end` / `tool_start` / `tool_update` / `tool_end` / `done` / `error` / `pong`），和 C→S 命令（`prompt` / `steer` / `abort` / `new_session` / `set_model` / `set_thinking_level` / `get_state`）。带 `id` 的命令走 Promise 等 `response`，30 秒超时。

#### 渲染策略的一个关键点

`message_update` 每次携带**完整的** AssistantMessage（不是增量），所以页面是 "replace-in-place"：整块清空重建。但 tool call 卡片有独立状态（running / done / error、工具结果），直接重建会丢状态。所以：

- 用一个 `toolCards: Map<toolCallId, DOMNode>` 缓存卡片节点
- `renderToolCall` 先查缓存，命中就重用（只更新 args），没命中才新建
- `tool_end` 事件收到时，根据 `toolCallId` 找到缓存里的卡片并写入结果
- 这样工具卡片的状态跨 `message_update` 重建依然保留

前端真接入时可以直接参考这段逻辑（源文件里有详细注释）。

### 4.2 前端真对接建议

生产前端对接时，参考 [`docs/asyncapi/chat-ws.yaml`](../asyncapi/chat-ws.yaml) 生成 TypeScript 客户端（`@asyncapi/modelina` 或手写），按 `type` 分发事件，对带 `id` 的命令维护一个 `requestId → Promise` 表实现同步调用语义。这份 HTML 客户端里的 `send()` / `onFrame()` / `renderAssistant()` / `toolCards` Map 策略可以直接抄过去。

## 常见故障速查

| 现象 | 原因 | 对策 |
|---|---|---|
| wscat 立刻收到 400/404 | 路径错了 | 确认是 `/api/ws/chat` |
| 连上但发 prompt 后只有 `response` 没有事件流 | Agent 没跑起来（模型没装 / API key 缺失） | 看 server 控制台报错，修 env |
| `response.success=false, error="conversation is already processing"` | 上一条没跑完 | 先等 `done` 或发 `abort` |
| `pong` 之间间隔远大于 20s | 本地没问题；反向代理 idle 超时太小 | 把 nginx/ALB 的 `proxy_read_timeout` 调到 ≥ 60s |
| 反代后面连 ws 立刻断 | 代理未开启 Upgrade | nginx 加 `proxy_http_version 1.1; proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection "upgrade";` |
| `set_model` 报 "Unknown model: xxx" | 模型名拼错或未授权 | `campusclaw --list-models` 查可用模型 |
| 服务启动失败且报 `TypeTag :: UNKNOWN` | JDK 版本不是 21 | `export JAVA_HOME=/opt/homebrew/opt/openjdk@21/...` 再启 |

## CI 建议

最小 CI 流水线只需两条：

```bash
./mvnw spotless:check
./mvnw test
```

`mvnw test` 会覆盖 `ChatWebSocketHandlerTest` 这条真连 WebSocket 的烟测。

AsyncAPI 校验可选加入，示例 GitHub Actions：

```yaml
- name: Validate AsyncAPI spec
  run: npx --yes @asyncapi/cli@latest validate docs/asyncapi/chat-ws.yaml
```
