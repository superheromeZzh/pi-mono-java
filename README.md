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
> Server / RPC 模式的详细接口文档见 [docs/server-api.md](docs/server-api.md)。

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
