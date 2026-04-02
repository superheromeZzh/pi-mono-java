# CampusClaw Server API 文档

CampusClaw 支持以 HTTP Server 模式运行，暴露 REST + SSE 接口，供外部应用（Web 前端、IDE 插件等）集成调用。

## 启动方式

```bash
# 默认端口 3000
campusclaw --mode server

# 指定端口
campusclaw --mode server --port 8080

# 指定模型 + 思考级别
campusclaw --mode server -m claude-sonnet-4 --thinking high --port 3000
```

也可通过脚本启动：

```bash
./campusclaw.sh --mode server --port 3000 -m glm-5
```

启动成功后输出：

```
CampusClaw API server started on http://localhost:3000
Endpoints:
  GET    /api/health
  POST   /api/chat
  DELETE /api/conversations/{id}
  POST   /api/skills
  GET    /api/skills
  DELETE /api/skills/{name}
```

---

## 接口列表

### 1. 健康检查

```
GET /api/health
```

**Response**

```json
{"status": "ok"}
```

---

### 2. 聊天（流式）

```
POST /api/chat
Content-Type: application/json
```

**Request Body**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `message` | string | 是 | 用户消息 |
| `conversation_id` | string | 否 | 会话 ID。不传则新建会话，传已有 ID 则续聊 |
| `model` | string | 否 | 本次请求使用的模型（如 `glm-5`），不影响其他会话 |
| `thinking` | string | 否 | 思考级别：`off` / `minimal` / `low` / `medium` / `high` / `xhigh` |

**请求示例**

```bash
# 新建会话
curl -N http://localhost:3000/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "用 Java 写一个快速排序"}'

# 续聊（使用上次返回的 conversation_id）
curl -N http://localhost:3000/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "加上泛型支持", "conversation_id": "a1b2c3d4e5f6"}'
```

**Response** — `Content-Type: text/event-stream`（SSE 流）

事件按以下顺序推送：

| 事件类型 | data 字段 | 说明 |
|----------|-----------|------|
| `message_start` | `{"conversation_id": "..."}` | 消息开始，携带会话 ID |
| `message_update` | `{"message": "..."}` | 文本增量更新（多次） |
| `tool_start` | `{"toolName": "bash", "toolCallId": "tc_xxx"}` | 工具调用开始 |
| `tool_end` | `{"toolCallId": "tc_xxx"}` | 工具调用结束 |
| `message_end` | `{"message": "..."}` | 消息结束，包含完整回复 |
| `done` | `{"conversation_id": "..."}` | 本次对话轮次完成 |
| `error` | `{"error": "...", "conversation_id": "..."}` | 出错（出错后流关闭） |

**SSE 输出示例**

```
event: message_start
data: {"conversation_id":"a1b2c3d4e5f6"}

event: message_update
data: {"message":"```java\n"}

event: message_update
data: {"message":"public class QuickSort {\n"}

event: tool_start
data: {"toolName":"write","toolCallId":"tc_01"}

event: tool_end
data: {"toolCallId":"tc_01"}

event: message_end
data: {"message":"已为你创建了 QuickSort.java 文件。"}

event: done
data: {"conversation_id":"a1b2c3d4e5f6"}
```

**断连取消**: 当 SSE 客户端断开连接时，后端会自动中止正在执行的 Agent 任务。

**错误响应**

| 状态码 | 说明 |
|--------|------|
| 400 | 缺少 `message`、无效的 `model` 或 `thinking` |
| 409 | 该会话上一个请求还在处理中（同一会话不支持并发，不同会话可并发） |
| 500 | 服务端内部错误 |

---

### 3. 删除会话

```
DELETE /api/conversations/{id}
```

**请求示例**

```bash
curl -X DELETE http://localhost:3000/api/conversations/a1b2c3d4e5f6
```

**Response** — `200 OK`

```json
{"message": "Removed conversation: a1b2c3d4e5f6"}
```

空闲超过 30 分钟的会话会被自动清理。

**错误响应**

| 状态码 | 说明 |
|--------|------|
| 404 | 会话不存在 |

---

### 4. 上传 Skill 压缩包

```
POST /api/skills
Content-Type: multipart/form-data
```

**Request**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | file | 是 | `.zip`、`.tar.gz` 或 `.tgz` 压缩包，内含 `SKILL.md` |

**请求示例**

```bash
curl http://localhost:3000/api/skills \
  -F "file=@my-skill.zip"
```

**Response** — `200 OK`

```json
{
  "name": "my-skill",
  "skills": [
    {
      "name": "code-review",
      "description": "自动代码审查工具"
    }
  ]
}
```

**错误响应**

| 状态码 | 说明 |
|--------|------|
| 400 | 缺少 `file` 字段、格式不支持、压缩包内无 `SKILL.md`、目录已存在 |
| 500 | 解压或文件系统错误 |

---

### 5. 查询 Skill 列表

```
GET /api/skills
```

**请求示例**

```bash
curl http://localhost:3000/api/skills
```

**Response** — `200 OK`

```json
[
  {
    "name": "code-review",
    "sourceType": "archive",
    "source": "/tmp/my-skill.zip",
    "description": "自动代码审查工具"
  },
  {
    "name": "deploy-helper",
    "sourceType": "git",
    "source": "https://github.com/user/deploy-helper",
    "description": "部署辅助脚本"
  }
]
```

---

### 6. 删除 Skill

```
DELETE /api/skills/{name}
```

**请求示例**

```bash
curl -X DELETE http://localhost:3000/api/skills/my-skill
```

**Response** — `200 OK`

```json
{
  "message": "Removed skill: my-skill"
}
```

**错误响应**

| 状态码 | 说明 |
|--------|------|
| 400 | Skill 不存在 |
| 500 | 文件系统删除失败 |

---

## RPC 模式

除 HTTP Server 外，CampusClaw 也支持基于 stdin/stdout 的 JSONL RPC 协议，适合进程间通信（如 IDE 插件直接拉起子进程）。

### 启动

```bash
campusclaw --mode rpc
campusclaw --mode rpc -m claude-sonnet-4
```

### 协议

每行一个 JSON 对象（JSONL），通过 stdin 发送命令，通过 stdout 接收事件。

### 命令（stdin → agent）

| type | 字段 | 说明 |
|------|------|------|
| `prompt` | `id?`, `message` | 发送用户消息 |
| `steer` | `id?`, `message` | 中途引导（不新建对话轮次） |
| `abort` | `id?` | 中止当前生成 |
| `get_state` | `id?` | 查询当前状态（模型、是否在流式中） |
| `set_model` | `id?`, `model` | 切换模型 |
| `new_session` | `id?` | 新建会话（清空历史） |

### 事件（agent → stdout）

| type | data | 说明 |
|------|------|------|
| `message_start` | — | 消息开始 |
| `message_update` | `message` | 文本增量 |
| `message_end` | `message` | 消息完整内容 |
| `tool_start` | `toolName`, `toolCallId` | 工具调用开始 |
| `tool_end` | `toolCallId` | 工具调用结束 |
| `done` | — | 本轮完成 |
| `error` | `error` | 错误信息 |

对于带 `id` 的命令，会返回对应的响应事件：

```json
{"type":"response","requestId":"req-1","data":"ack"}
```

### 示例交互

```bash
# 终端 A：启动 RPC 模式
campusclaw --mode rpc -m glm-5

# stdin 输入：
{"type":"prompt","id":"req-1","message":"你好"}

# stdout 输出：
{"type":"response","requestId":"req-1","data":"ack","error":null}
{"type":"message_start","requestId":null,"data":null,"error":null}
{"type":"message_update","requestId":null,"data":{"message":"你好！"},"error":null}
{"type":"message_end","requestId":null,"data":{"message":"你好！有什么可以帮你的吗？"},"error":null}
{"type":"done","requestId":null,"data":null,"error":null}
```

---

## 完整模式对比

| 模式 | 启动参数 | 交互方式 | 适用场景 |
|------|----------|----------|----------|
| interactive（默认） | `--mode interactive` | 全屏 TUI | 开发者日常使用 |
| one-shot | `-p "prompt"` | 单次输出到 stdout | 脚本/CI 集成 |
| server | `--mode server --port 3000` | HTTP REST + SSE | Web 前端、远程调用 |
| rpc | `--mode rpc` | stdin/stdout JSONL | IDE 插件、进程间通信 |
