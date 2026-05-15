# XXXClaw

基于 Java 21 + Spring Boot 3.4.1 实现的终端 AI 编程助手，支持多模型供应商，提供交互式 TUI 界面和丰富的代码操作工具。

## 前置要求

- **JDK 21**
- 至少一个 AI 供应商的 API Key（见「支持的供应商」章节）

### 安装 JDK 21

| 操作系统 | 安装方式 |
|----------|----------|
| macOS | `brew install openjdk@21` |
| Linux (Ubuntu/Debian) | `sudo apt install openjdk-21-jdk` |
| Linux (Fedora) | `sudo dnf install java-21-openjdk-devel` |
| Windows | 从 [Adoptium](https://adoptium.net/) 下载安装，或 `winget install EclipseAdoptium.Temurin.21.JDK` |
| 通用 | [SDKMAN](https://sdkman.io/)：`sdk install java 21-tem` |

安装后确认版本：

```bash
java -version
# 应输出 openjdk version "21.x.x"
```

## 快速开始

### 1. 设置 API Key 环境变量

根据你使用的模型供应商，配置对应的环境变量：

**macOS / Linux** — 添加到 `~/.zshrc` 或 `~/.bashrc`：

```bash
export ANTHROPIC_API_KEY="sk-ant-..."   # Anthropic (Claude)
export OPENAI_API_KEY="sk-..."          # OpenAI
export GOOGLE_API_KEY="..."             # Google
# 其他供应商见「支持的供应商」章节
```

**Windows** — 通过系统环境变量或 PowerShell：

```powershell
# 当前会话临时生效
$env:ANTHROPIC_API_KEY = "sk-ant-..."

# 永久生效（需要重启终端）
[Environment]::SetEnvironmentVariable("ANTHROPIC_API_KEY", "sk-ant-...", "User")
```

### 2. 启动

#### 一键启动（推荐）

脚本自动检测 JDK 21、首次自动构建、源码变更后自动重建。

```bash
# macOS / Linux
./campusclaw.sh -m glm-5

# Windows
campusclaw.bat -m glm-5
```

传入 `--rebuild` 可强制重新构建：

```bash
./campusclaw.sh --rebuild -m glm-5
```

> 如果系统安装了多个 JDK 版本且脚本无法自动找到 JDK 21，请手动设置 `JAVA_HOME`：
>
> ```bash
> # macOS / Linux
> export JAVA_HOME=/path/to/jdk-21
>
> # Windows PowerShell
> $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21"
> ```

#### Maven 开发模式

```bash
# macOS / Linux
./mvnw -pl modules/coding-agent-cli spring-boot:run -Dspring-boot.run.arguments='-m glm-5'

# Windows
mvnw.cmd -pl modules/coding-agent-cli spring-boot:run -Dspring-boot.run.arguments="-m glm-5"
```

#### 手动构建后运行

```bash
# 构建 fat JAR
./mvnw package -pl modules/coding-agent-cli -am -DskipTests    # macOS / Linux
mvnw.cmd package -pl modules/coding-agent-cli -am -DskipTests  # Windows

# 运行
java -jar modules/coding-agent-cli/target/campusclaw-agent.jar -m glm-5
```

#### 常用 Maven 命令

| 命令 | 说明 |
|------|------|
| `./mvnw compile` | 编译所有模块 |
| `./mvnw test` | 运行测试 |
| `./mvnw verify` | 完整构建（含测试） |
| `./mvnw package -DskipTests` | 构建全部 JAR（跳过测试） |
| `./mvnw clean` | 清理 target/ |
| `./mvnw spotless:apply` | 格式化代码 |

> Windows 用户将 `./mvnw` 替换为 `mvnw.cmd`。
> 如果 Maven 报 JDK 版本不兼容，在命令前加上 `JAVA_HOME=...`（macOS/Linux）或 `set JAVA_HOME=...`（Windows CMD）。

## 用法

```
campusclaw [OPTIONS] [PROMPT...]
```

### 核心选项

| 选项 | 说明 | 示例 |
|------|------|------|
| `-m, --model` | 指定模型 | `-m claude-sonnet-4` |
| `--provider` | 指定供应商 | `--provider openai` |
| `--api-key` | 覆盖 API Key | `--api-key sk-...` |
| `--thinking` | 思考级别：off/minimal/low/medium/high/xhigh | `--thinking high` |
| `-p, --print` | 非交互模式，输出后退出 | `-p "解释这段代码"` |
| `--mode` | 执行模式：interactive/one-shot/rpc/server/print | `--mode server` |
| `--port` | HTTP 服务端口（server 模式） | `--port 8080` |
| `--tools` | 指定启用的工具（逗号分隔） | `--tools read,bash,edit` |
| `--no-tools` | 禁用所有内置工具 | |

### 会话管理

| 选项 | 说明 |
|------|------|
| `-c, --continue` | 继续上一次会话 |
| `-r, --resume` | 交互式选择历史会话 |
| `--session <path>` | 使用指定会话文件 |
| `--fork <path>` | 基于已有会话创建分支 |
| `--no-session` | 临时会话（不保存） |
| `--export <in> [out]` | 导出会话为 HTML |

### 其他选项

| 选项 | 说明 |
|------|------|
| `--system-prompt` | 替换默认系统提示词 |
| `--append-system-prompt` | 追加内容到系统提示词 |
| `--models` | 逗号分隔的模型列表，用于 Ctrl+P 切换 |
| `--list-models [pattern]` | 列出可用模型并退出 |
| `--cwd <path>` | 设置工作目录 |
| `--verbose` | 显示详细启动信息 |

### 使用示例

```bash
# 交互模式（默认）
./campusclaw.sh -m claude-sonnet-4

# 单次提问
./campusclaw.sh -m glm-5 -p "这个项目的架构是什么？"

# 高级思考模式
./campusclaw.sh -m claude-sonnet-4 --thinking high

# 继续上次会话
./campusclaw.sh -m glm-5 -c

# 使用文件内容作为输入（@ 前缀）
./campusclaw.sh -m glm-5 "请审查这个文件 @src/main/java/App.java"

# 列出所有可用模型
./campusclaw.sh --list-models

# HTTP Server 模式（供 Web 前端 / IDE 插件调用）
./campusclaw.sh --mode server --port 3000 -m glm-5

# RPC 模式（stdin/stdout JSONL，供进程间通信）
./campusclaw.sh --mode rpc -m glm-5
```

> Windows 用户将上述 `./campusclaw.sh` 替换为 `campusclaw.bat`。
>
> Server 模式的接口文档见 [docs/openapi/campusclaw-api.yaml](docs/openapi/campusclaw-api.yaml)（REST，OpenAPI 3）与 [docs/asyncapi/chat-ws.yaml](docs/asyncapi/chat-ws.yaml)（WebSocket）。`docs/server-api.md` 已停止维护，仅作历史快照保留。

## 内置工具

Agent 内置 8 个代码操作工具：

| 工具 | 功能 |
|------|------|
| `read` | 读取文件内容，支持行范围和图片 |
| `write` | 写入文件 |
| `edit` | 按行编辑文件 |
| `editdiff` | 应用 unified diff 补丁 |
| `bash` | 执行 shell 命令 |
| `glob` | 按模式搜索文件 |
| `grep` | 按正则搜索文件内容 |
| `ls` | 列出目录内容 |

## 支持的供应商

| 供应商 | 环境变量 |
|--------|----------|
| Anthropic (Claude) | `ANTHROPIC_API_KEY` |
| OpenAI | `OPENAI_API_KEY` |
| Google Generative AI | `GOOGLE_API_KEY` |
| Google Vertex AI | `GOOGLE_CLOUD_PROJECT` + `GOOGLE_CLOUD_API_KEY` |
| AWS Bedrock | AWS 标准凭证链 |
| Azure OpenAI | `AZURE_OPENAI_API_KEY` |
| Mistral | `MISTRAL_API_KEY` |
| ZAI | `ZAI_API_KEY` |
| Kimi | `KIMI_API_KEY` |
| MiniMax | `MINIMAX_API_KEY` |
| xAI (Grok) | `XAI_API_KEY` |
| Groq | `GROQ_API_KEY` |
| Cerebras | `CEREBRAS_API_KEY` |
| OpenRouter | `OPENROUTER_API_KEY` |
| HuggingFace | `HF_TOKEN` |
| GitHub Copilot | `COPILOT_GITHUB_TOKEN` / `GH_TOKEN` |

## 配置文件

用户配置路径：

| 操作系统 | 路径 |
|----------|------|
| macOS / Linux | `~/.campusclaw/settings.json` |
| Windows | `%USERPROFILE%\.campusclaw\settings.json` |

可设置项：

- `defaultModel` — 默认模型
- `defaultThinkingLevel` — 默认思考级别
- `enabledModels` — 启用的模型列表
- `customModels` — 自定义模型（自定义 baseUrl、apiKey 等）
- `packages` — 已安装的扩展包

## 项目结构

```
campusclaw/
├── modules/
│   ├── ai/                  # campusclaw-ai — 统一 LLM 调用层，多供应商适配
│   ├── agent-core/          # campusclaw-agent-core — Agent 循环、工具执行管线
│   ├── coding-agent-cli/    # campusclaw-coding-agent — CLI 入口 + TUI 界面
│   └── tui/                 # campusclaw-tui — 终端 UI 组件（JLine + Lanterna）
├── pom.xml                  # 根构建配置（Maven）
├── campusclaw.sh            # 启动脚本（macOS / Linux）
├── campusclaw.bat           # 启动脚本（Windows）
└── README.md
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Java 21（Records, Sealed Interfaces, Pattern Matching） |
| 框架 | Spring Boot 3.4.1 |
| 异步 | Project Reactor (Mono/Flux) |
| HTTP | Spring WebClient (SSE 流式) |
| CLI | Picocli 4.7.6 |
| 终端 | JLine 3.26.2 + Lanterna 3.1.2 |
| 构建 | Maven 3.9.11 |

## 开发

```bash
# 构建所有模块
./mvnw verify

# 运行测试
./mvnw test

# 仅构建 JAR
./mvnw package -pl modules/coding-agent-cli -am -DskipTests
```

> Windows 用户将 `./mvnw` 替换为 `mvnw.cmd`。
> 如果默认 JDK 不是 21，需在命令前设置 `JAVA_HOME`。

### 同步 modules/* 到 mate-campusclaw

`mate-campusclaw/` 是为对接公司 `mate` 父项目而维护的单模块镜像，包名重写为 `com.huawei.hicampus.mate.matecampusclaw`。日常在 `modules/*` 里开发，通过下面的脚本把变更同步进 `mate-campusclaw/`：

```bash
./scripts/sync-mate-campusclaw.sh             # 同步 + 编译验证
./scripts/sync-mate-campusclaw.sh --dry-run   # 预览改动，不写盘
./scripts/sync-mate-campusclaw.sh --no-verify # 跳过 mvn compile
./scripts/sync-mate-campusclaw.sh --no-apply  # 仅生成 build/，不动 mate-campusclaw/
```

脚本三阶段：

1. **Stage** — 把 `modules/{ai,tui,agent-core,assistant,cron,coding-agent-cli}` 复制到 `build/mate-campusclaw/`，并把 `com.campusclaw` 替换成 `com.huawei.hicampus.mate.matecampusclaw`（`.java/.yml/.properties/.imports/...` 全部覆盖）。
2. **Apply** — `rsync --delete` 把 `build/` 同步到 `mate-campusclaw/`，跳过 `scripts/sync-mate-exclude.txt` 中登记的 mate 侧独有路径（如 `assistant/config/`、`codingagent/channel/`）。
3. **Verify** — 在 `mate-campusclaw/` 跑 `mvn compile`（自动找 JDK 21，跳过 checkstyle/spotless）。

> 在 `mate-campusclaw/` 下手写新文件、且与 `modules/*` 没有对应关系时，记得把路径加进 `scripts/sync-mate-exclude.txt`，否则下次 `--delete` 会清掉它。`application.properties` 和 `application-assistant.yml` 是按环境手工维护的，脚本永远不动；只有 `schema.sql` 和 `META-INF/spring/*.imports` 会从 `modules/*` 同步过来。

#### pre-push 自动校验

仓库自带一个 `scripts/git-hooks/pre-push` 钩子，在 `git push` 前自动检查 `mate-campusclaw/` 是否与 `modules/*` 同步——不同步就拦住 push 并提示先跑同步脚本。每次新 clone 仓库后启用一次即可：

```bash
git config core.hooksPath scripts/git-hooks
```

只有当本次 push 的提交范围里**真的动了** `modules/`、`mate-campusclaw/` 或 `scripts/sync-mate*` 时，钩子才会跑校验（其他改动直接放行，不会拖慢 push）。如果你确认要先 push、稍后再补同步，可以临时绕过：

```bash
git push --no-verify
```

## 故障排查

### Maven 构建失败：JDK 版本不兼容

**现象**：`Unsupported class file major version` 或编译报错

**原因**：项目要求 JDK 21+

**解决**：

```bash
# macOS / Linux — 指定 JAVA_HOME
export JAVA_HOME=/path/to/jdk-21
./mvnw verify

# Windows CMD
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
mvnw.cmd verify
```

### 启动脚本找不到 JDK 21

**现象**：`Error: JDK 21 not found`

**解决**：手动设置 `JAVA_HOME` 环境变量指向 JDK 21 安装目录，然后重新运行脚本。

### 终端显示乱码或无颜色

**现象**：TUI 界面渲染异常

**解决**：
- 确保终端支持 ANSI 转义序列（Windows 推荐使用 Windows Terminal，不要用 cmd.exe）
- 检查终端编码为 UTF-8：`chcp 65001`（Windows）
- macOS/Linux 下确认 `TERM` 环境变量不为 `dumb`

### API 调用报 401 / Unauthorized

**现象**：模型请求返回认证错误

**解决**：
- 确认对应供应商的环境变量已设置且值正确
- 运行 `echo $ANTHROPIC_API_KEY`（macOS/Linux）或 `echo %ANTHROPIC_API_KEY%`（Windows）验证
- 也可通过 `--api-key` 参数直接传入

### 源码改了但启动脚本没有重新构建

**现象**：修改了 Java 代码但运行行为没变

**解决**：启动脚本会自动检测源码变更并重新构建。如果检测不准确，可手动强制：

```bash
./campusclaw.sh --rebuild -m glm-5      # macOS / Linux
campusclaw.bat --rebuild -m glm-5       # Windows
```

## 工程经验沉淀：跨平台子进程 + 流式 IO 调试

> 这一章记录 2026-05 月 spawn_agent 工具在 Windows 上"子 agent 有输出、主 agent 不出答案"的完整定位过程沉淀下来的工程经验，按"症状 → 误判 → 根因 → 修复"四段写。每条经验后面挂的是它对应的 PR / commit，方便回溯当时改了什么。**不只针对 spawn_agent**——任何在 JVM 里管子进程 + stdio pipe + Reactor 流的代码都可能踩同样的坑。

### 经验 1：Windows 上 `process.destroy()` 不杀子进程树

**症状**：JVM 调 `process.destroy()` 后子进程"死了"，但被它派生的孙子进程仍然活着，继续持有 stdin/stdout pipe handle。

**为什么**：`ProcessBuilder` 在 Windows 上启动 npm 安装的 CLI（如 `claude-code-acp`）时实际启动的是 `cmd.exe /c <name>.cmd ...`——cmd.exe 是 wrapper，`.cmd` shim 内部又派生出真正干活的 node.js。Windows 没有 POSIX 那样的"进程组级 SIGTERM"，`process.destroy()` 只发 `TerminateProcess`，**只杀 cmd.exe 这一个进程**。cmd.exe 一死，OS 层的 parent-child 关系断裂，node.js 成为孤儿（被 init/system 接管），继续持有从 cmd.exe 继承来的 pipe handle。

**误判**：先调 `process.descendants().forEach(destroyForcibly)` 兜底——结果：cmd.exe 死了之后 `descendants()` 返回**空**。

**修复**：**snapshot `descendants()` 必须在 destroy parent 之前做**，然后一次性 `destroyForcibly` 整树，再 `waitFor` + `ProcessHandle.onExit().get(timeout)` 等所有进程真正退完。

参考实现：`modules/agent-core/.../ProcessAcpBackend.java#destroyTree`。引入于 PR #74（commit `2e3ac9e0`）。

### 经验 2：Windows JDK `PipeInputStream.close()` 与 `BufferedReader.readLine()` 死锁

**症状**：在另一个线程上对正被 `readLine()` 阻塞读取的 pipe 调用 `close()`，**调用方挂死**——但只在 Windows 上挂死，macOS / Linux 立即返回。

**为什么**：POSIX `close(fd)` 让所有同时在 read 的线程立刻拿到 `IOException / EBADF`。Windows JDK 的 `PipeInputStream.close()` 实现选了相反的语义：**等 pending read 完成才返回**。reader 等不到数据 / EOF，close 等不到 read 完成——经典互锁。

**关键的"为什么"在 POSIX 上没事**：Linux/macOS 上 close 是单向解锁；Windows JDK 这条路径选了和 NIO Channel 等组件不同的语义，且没有公开的 timeout 旋钮可调。

**修复有两层**，配合使用：

1. **治本顺序**：在 close 流之前先杀进程树（见经验 1）让 pipe write-end 在 OS 层关闭、reader 的 `readLine` 拿到 EOF 自然退出，**然后**调 `client.close → transport.close → input.close`——此时没有 pending read，close 立即返回。引入于 PR #73（`c52c60a9`）+ PR #74（`2e3ac9e0`）。
2. **防御性兜底**：`AcpTransport.close()` 把 `input.close()` / `output.close()` 移到守护虚拟线程上跑，调用者立即返回。万一未来 shim 派生模式变化漏掉某个孙子进程，agent loop 也不会被 close 冻住。引入于 PR #74。

参考实现：`modules/agent-core/.../AcpTransport.java#close`。

### 经验 3：Reactor `Sinks.Many.multicast()` 的跨线程 emit 不保证顺序

**症状**：reader 线程持续 emit `TextDelta` 流式片段，prompt 线程在 RPC 响应回来后 emit `Done` 收尾。下游用 `.takeUntil(Done)` 截断 Flux——**偶发**最后一段 `TextDelta` 被截掉（在 Mac 上几乎不复现，Windows 上稳定复现）。

**为什么**：`multicast().onBackpressureBuffer` 不是 serialized sink——两个线程同时 `tryEmitNext` 会有一个 `FAIL_NON_SERIALIZED`，默认行为是**静默丢事件**。即便用 `busyLooping` retry handler 解决了"丢事件"，仍然解决不了"乱序"：prompt 线程的 emit 完全可能比 reader 线程**最后一个** TextDelta 先到下游。`takeUntil(Done)` 看到 Done 就关流，已经在 reader 线程 buffer 里、还没到下游的 TextDelta 就被丢了。

**误判一**：以为 busy-loop retry 已经修好（commit `e00bf4c9`）——retry 只解决并发**emit 失败**，不解决**到达顺序**。

**误判二**：以为 Windows 不丢事件就行——其实 Mac 上同样存在竞态，只是调度宽松没暴露。

**修复**：把 `Done` 的合成与 emit 从 prompt 线程**搬到 reader 线程**——在 `AcpClient.handleResponse()` 收到 `session/prompt` 响应时，**同一线程**顺序地 emit 完所有 TextDelta、紧接着 emit Done。物理消除竞态，而不是靠 retry 打补丁。引入于 PR #71（`2b51c9e6`）。

参考实现：`modules/agent-core/.../AcpClient.java#handleResponse`。

**普适教训**：跨线程共享 Reactor `Sinks.Many` 时，要么严格"单 producer 线程"，要么用 `Sinks.unsafe().many()` + 显式 lock，要么改 `Sinks.one().asMono()` 那种天然单值语义的 sink。**multicast sink 不替你处理 producer 间的顺序**。

### 经验 4：流式异步链路的可观测性——日志吃不到的时候怎么排查

**症状**：bug 只在 Windows 上复现，TUI 界面又把 logback console 输出吞掉，env var 在 Windows shell 之间传递也不可靠，远程协作时拿不到任何诊断信息。`log.debug` / `log.info` 的开关根本没机会生效。

**解决方案**（演进过的，按顺序）：

1. **绕开 logback，写专用 trace 文件**——`AcpTransport` 在静态初始化时打开 `~/.campusclaw/acp-trace.jsonl`，每次 JVM 启动 TRUNCATE 重写。默认开启，关掉用 `CAMPUSCLAW_ACP_TRACE=0` 或 `-Dcampusclaw.acp.trace=0`。文件存在本身证明 JVM 跑了。引入于 `9a5b06fc`、`876d07e9`、`5a0bc9d8`。
2. **结构化 trace + 自由文本 marker 并存**——`>`/`<` 前缀记原始 JSON-RPC envelope，`#` 前缀的 `AcpTransport.note()` 静态方法供 `AcpClient` / `SpawnAgentTool` / `AgentLoop` / `ProcessAcpBackend` 在关键路径上插**人类可读的 checkpoint**。`grep '^#'` 直接拉出全链路里程碑。
3. **在每个跨线程 / 跨进程 / 跨阶段边界都埋一个 note**——一次定位失败之后**补 trace 而不是补 log**。Windows 上拉个 jstack 是大工程；拉一份 jsonl 是 Ctrl+C。本仓常用埋点位置：`AcpClient.emit/emit-done`、`SpawnAgentTool.recv/done`、`AgentLoop.turn=N start/runToolPhase/invokeModel returned`、`ProcessAcpBackend.close/destroyTree`、`AcpTransport.close streams closed`。引入于 PR #72（`2d4ac8b1`）+ commit `343eaf81`、`85cd54fd`、`ed6018fc`。
4. **每条 note 自带"我是谁"**——`SpawnAgentTool.done stopReason=END_TURN transcriptLen=776 thoughtLen=2989` 这种**带量级**的标记一次性告诉读者数据走到哪一步、还有多少。一行胜过五行通用日志。

参考实现：`modules/agent-core/.../AcpTransport.java#openTrace`、`#note`。

### 经验 5：bug 定位方法论——按"trace 最后一行"反推

这一次定位经历了四次方向修正：

| 阶段 | trace 末尾的关键标记 | 当时判断 | 实际情况 |
|---|---|---|---|
| 1 | `AcpClient.emit-done Done` 之后没 `SpawnAgentTool.recv Done` | Reactor sink 丢事件 | ✓ 确认（PR #71 修） |
| 2 | trace 跑到了 `SpawnAgentTool.done transcriptLen=776`，但**没有** `invokeModel returned` | 主 LLM 沉默 | ✗ 误判——实际是 AgentLoop 没进入下一轮 |
| 3 | 末尾停在 `AcpClient.close events.emitComplete done` | `input.close` 死锁（PR #73 调换 close 顺序） | 部分正确，但 PR #73 不够 |
| 4 | 同一行 + 用户在 Windows 没看到 `process.destroy pid=` | cmd.exe wrapper 留孤儿（PR #74 修） | ✓ 真根因 |

**通用做法**：

- **每一轮根据 trace 末尾最后一条 note 反推 hang 点**，对照源码看下一条预期 note 应该出现在哪儿、为什么没出现。
- **每次定位失败先补 trace 再继续**——别在原假设上反复加猜测。每次"为什么我以为该出现的标记没出现"都是一次方向修正的信号。
- **不要执着于一次性修对**——四次迭代里前两次的修复方向都不完整，但每次都把不确定范围缩小一半，最后定位到 OS 级的 pipe / process 语义就不再有歧义了。

### 经验 6：每条规则背后都要写下"为什么不能简化"

修这类 bug 时容易产生"为什么不能直接……？"的诱惑。把每个被验证过的反例记下来，下次同事或自己再问的时候有据可查：

| 想偷的懒 | 为什么不行 |
|---|---|
| "直接同步 `input.close()` 不就行了" | Windows JDK PipeInputStream.close 等 pending read 完成，会死锁（经验 2） |
| "在 `process.destroy()` 后再调 `process.descendants()` 不就拿到子进程列表了吗" | parent 死后 OS 层 parent-child link 断了，返回空（经验 1） |
| "Reactor sink 加个 `RETRY_NON_SERIALIZED` 不就稳了" | 解决丢事件不解决乱序（经验 3） |
| "用 `log.debug` 排查 Windows 上的问题" | TUI 吃日志、env 传递不稳定、`-Dlog.level` 经常忘配（经验 4） |
| "用 `cmd.exe` 包一层兼容 .cmd shim 不就好了" | 引入 wrapper 进程，destroy 杀不彻底——孤儿子进程是后面所有问题的源头（经验 1） |
| "spawn_agent 工具不再卡死就行" | 同根因下任何**进程 + pipe + 多线程**组合都会触发，不是 spawn_agent 专属——经验要按通用原则沉淀（本章存在的原因） |
