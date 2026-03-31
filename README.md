# xxxxClaw

基于 Java 21 + Spring Boot 3.4.1 实现的终端 AI 编程助手，支持多模型供应商，提供交互式 TUI 界面和丰富的代码操作工具。

## 特性亮点

- **混合执行架构**：本地执行 + Docker 沙箱执行，兼顾性能与安全
- **智能路由**：基于风险评估自动选择执行模式（LOCAL / SANDBOX / AUTO）
- **Kubernetes 部署**：支持 K8s + DIND Sidecar 模式运行
- **多模型支持**：Anthropic、OpenAI、Google 等 15+ 供应商
- **交互式 TUI**：JLine + Lanterna 打造的终端界面

## 前置要求

- **JDK 21**s
- 至少一个 AI 供应商的 API Key（见「支持的供应商」章节）
- **可选**：Docker（如需使用沙箱执行模式）

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

### 安装 Docker（可选，用于沙箱执行）

| 操作系统 | 安装方式 |
|----------|----------|
| macOS | `brew install docker` 或安装 [Docker Desktop](https://www.docker.com/products/docker-desktop) |
| Linux | `curl -fsSL https://get.docker.com \| sh` |
| Windows | 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop) |

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

**macOS / Linux：**

```bash
./campusclaw.sh -m glm-5
```

**Windows：**

```cmd
campusclaw.bat -m glm-5
```

启动脚本会自动检测 JDK 21、首次运行时自动构建，源码变更后自动重新构建。

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

### 3. 其他启动方式

**使用 Gradle `bootRun`（开发推荐，自动编译+运行）：**

```bash
# macOS / Linux
./gradlew :modules:campusclaw-coding-agent:bootRun --args='-m glm-5'

# Windows
gradlew.bat :modules:campusclaw-coding-agent:bootRun --args="-m glm-5"
```

**手动构建后运行：**

```bash
# 构建（只需执行一次，代码没改就不用重新构建）
./gradlew :modules:campusclaw-coding-agent:bootJar          # macOS / Linux
gradlew.bat :modules:campusclaw-coding-agent:bootJar         # Windows

# 运行
java -jar modules/coding-agent-cli/build/libs/campusclaw-agent-1.0.0-SNAPSHOT.jar -m glm-5
```

> 如果 Gradle 报 JDK 版本不兼容，在命令前加上 `JAVA_HOME=...`（macOS/Linux）或 `set JAVA_HOME=...`（Windows CMD）。

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
| `--mode` | 执行模式：interactive/one-shot/print | `--mode one-shot` |
| `--tools` | 指定启用的工具（逗号分隔） | `--tools read,bash,edit` |
| `--no-tools` | 禁用所有内置工具 | |
| `--exec-mode` | 工具执行模式：local/sandbox/auto | `--exec-mode sandbox` |

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

# 强制使用沙箱执行模式（更安全）
./campusclaw.sh -m glm-5 --exec-mode sandbox
```

> Windows 用户将上述 `./campusclaw.sh` 替换为 `campusclaw.bat`。

## 混合执行架构

CampusClaw 实现了**本地执行**与 **Docker 沙箱执行** 的混合模式，兼顾性能与安全：

```
┌─────────────────────────────────────────────────────────────┐
│                    mateservice-deployment-xxx                │
│  ┌─────────────────────────┐  ┌──────────────────────────┐  │
│  │   mateservice           │  │   dind-sandbox (sidecar) │  │
│  │   (CampusClaw)          │  │                          │  │
│  │                         │  │   ┌──────────────────┐   │  │
│  │   ┌─────────────────┐   │  │   │ Docker Daemon    │   │  │
│  │   │ ExecutionRouter │◄──┼──┼──►│ (tcp://localhost)│   │  │
│  │   │                 │   │  │   └──────────────────┘   │  │
│  │   │ - 风险评估      │   │  │                          │  │
│  │   │ - 本地执行      │───┼──┼──►│ ┌────────────────┐   │  │
│  │   │ - 沙箱执行      │   │  │   │ sandbox-worker │   │  │
│  │   └─────────────────┘   │  │   │ (常驻容器)      │   │  │
│  └─────────────────────────┘  │   └────────────────┘   │  │
│                               │                          │  │
│   Shared: /workspace          │   ┌──────────────────┐   │  │
│                               │   │ 临时执行容器     │   │  │
└───────────────────────────────┴───┴──────────────────┴───┘  │
```

### 执行模式

| 模式 | 说明 | 适用场景 |
|------|------|----------|
| **LOCAL** | 本地 JVM 直接执行 | 高性能，低风险操作（read/glob/grep） |
| **SANDBOX** | Docker 容器内执行 | 隔离执行，高风险操作（bash/write/edit） |
| **AUTO** | 智能路由（默认） | 基于风险评估自动选择执行方式 |

### 执行模式选择策略

**自动模式（AUTO）的风险评估：**

- **LOW（低风险）** → 本地执行
  - `read`、`glob`、`grep` 工具
  - 访问普通项目文件

- **MEDIUM（中风险）** → 本地执行 + 安全检查
  - `write`、`edit` 到项目目录
  - 普通 `bash` 命令（ls、cat、grep 等）

- **HIGH（高风险）** → 强制沙箱执行
  - 访问系统路径（/etc、/usr 等）
- **危险命令** → 拦截或强制沙箱
  - `rm -rf /`、`mkfs`、`dd if=/dev/zero` 等

### 强制指定执行模式

方式 1 - 命令行参数：
```bash
./campusclaw.sh --exec-mode sandbox -m glm-5
```

方式 2 - 环境变量：
```bash
export TOOL_EXECUTION_DEFAULT_MODE=SANDBOX
./campusclaw.sh -m glm-5
```

方式 3 - 单条命令参数：
```json
{"tool": "bash", "params": {"command": "rm -rf /tmp/*", "_executionMode": "sandbox"}}
```

## 内置工具

Agent 内置 8 个代码操作工具，支持**混合执行模式**：

| 工具 | 功能 | 默认模式 |
|------|------|----------|
| `read` | 读取文件内容，支持行范围和图片 | LOCAL |
| `write` | 写入文件 | AUTO（受保护路径→SANDBOX） |
| `edit` | 按行编辑文件 | AUTO（受保护路径→SANDBOX） |
| `editdiff` | 应用 unified diff 补丁 | LOCAL |
| `bash` | 执行 shell 命令 | AUTO（危险命令→SANDBOX） |
| `glob` | 按模式搜索文件 | LOCAL |
| `grep` | 按正则搜索文件内容 | LOCAL |
| `ls` | 列出目录内容 | LOCAL |

### 工具执行模式参数

所有工具支持 `_executionMode` 参数强制指定执行模式：

```json
// 强制本地执行
{"tool": "read", "params": {"path": "file.txt", "_executionMode": "local"}}

// 强制沙箱执行
{"tool": "bash", "params": {"command": "curl https://api.example.com/data", "_executionMode": "sandbox"}}

// 自动模式（默认）
{"tool": "write", "params": {"path": "output.txt", "content": "data"}}
```

## 配置

### 用户配置文件

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

### 工具执行配置

在 `~/.campusclaw/settings.json` 中添加：

```json
{
  "defaultModel": "glm-5",
  "toolExecution": {
    "defaultMode": "AUTO",
    "hybridEnabled": true,
    "localExecutionEnabled": true,
    "sandboxExecutionEnabled": true,
    "dockerHost": "unix:///var/run/docker.sock"
  }
}
```

配置项说明：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `defaultMode` | AUTO | 默认执行模式：LOCAL / SANDBOX / AUTO |
| `hybridEnabled` | true | 启用混合路由 |
| `localExecutionEnabled` | true | 允许本地执行 |
| `sandboxExecutionEnabled` | false | 允许沙箱执行（需 Docker） |
| `dockerHost` | unix:///var/run/docker.sock | Docker 守护进程地址 |
| `sandboxWorkerImage` | alpine:3.19 | 沙箱工作镜像 |
| `sandboxWorkerMemory` | 512m | 沙箱内存限制 |
| `sandboxWorkerCpu` | 1.0 | 沙箱 CPU 限制 |

## Kubernetes 部署

CampusClaw 支持在 Kubernetes 中部署，使用 DIND（Docker in Docker）Sidecar 模式：

### 构建镜像

```bash
docker build -t campusclaw/mateservice:latest .
docker push campusclaw/mateservice:latest
```

### 部署到 K8s

```bash
kubectl apply -f k8s/mateservice-deployment.yaml
```

### 验证部署

```bash
# 检查 Pod 状态
kubectl get pods -l app=mateservice

# 验证 DIND 连接
kubectl exec -it deployment/mateservice-deployment -c mateservice -- \
    docker -H tcp://localhost:2375 version

# 查看日志
kubectl logs -f deployment/mateservice-deployment -c mateservice
```

### K8s 部署架构

- **mateservice 容器**：主应用，Spring Boot CLI
- **dind-sandbox 容器**：Docker 守护进程，提供沙箱执行环境
- **共享卷**：`/workspace` 在容器间共享，用于文件操作

详见 [ARCHITECTURE-HYBRID.md](ARCHITECTURE-HYBRID.md) 和 [IMPLEMENTATION-HYBRID.md](IMPLEMENTATION-HYBRID.md)。

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

## 项目结构

```
campusclaw/
├── modules/
│   ├── ai/                       # campusclaw-ai — 统一 LLM 调用层
│   ├── agent-core/               # campusclaw-agent-core — Agent 循环、工具执行管线
│   ├── coding-agent-cli/         # campusclaw-coding-agent — CLI 入口 + TUI 界面
│   │   └── src/main/java/com/campusclaw/codingagent/
│   │       ├── config/           # 配置类（ToolExecutionProperties 等）
│   │       ├── tool/
│   │       │   ├── execution/    # 执行核心（ExecutionRouter、执行模式）
│   │       │   ├── hybrid/       # 混合工具实现（HybridReadTool 等）
│   │       │   └── sandbox/      # 沙箱基础设施（DockerSandboxClient 等）
│   │       └── resources/
│   │           └── application-k8s.yml   # K8s 环境配置
│   └── tui/                      # campusclaw-tui — 终端 UI 组件
├── k8s/
│   └── mateservice-deployment.yaml   # K8s 部署配置（含 DIND Sidecar）
├── Dockerfile                    # 多阶段构建镜像
├── ARCHITECTURE-HYBRID.md        # 混合架构设计文档
├── IMPLEMENTATION-HYBRID.md      # 实现细节文档
├── build.gradle.kts              # 根构建配置
├── settings.gradle.kts           # 模块声明
├── campusclaw.sh                 # 启动脚本（macOS / Linux）
├── campusclaw.bat                # 启动脚本（Windows）
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
| 沙箱 | Docker Java Client |
| 构建 | Gradle 8.10.2 (Kotlin DSL) |

## 开发

```bash
# 构建所有模块
./gradlew build

# 运行测试
./gradlew test

# 仅构建 JAR
./gradlew :modules:campusclaw-coding-agent:bootJar

# 构建 Docker 镜像
docker build -t campusclaw/mateservice:latest .
```

> Windows 用户将 `./gradlew` 替换为 `gradlew.bat`。
> 如果默认 JDK 不是 21，需在命令前设置 `JAVA_HOME`。

## 安全策略

### 危险命令拦截

以下命令会被识别为危险命令，强制使用沙箱执行或拦截：

- `rm -rf /` 或 `rm -rf /*` — 删除根目录
- `mkfs.*` — 格式化文件系统
- `dd if=/dev/zero` — 磁盘清零
- `:(){ :|:& };:` — Fork Bomb
- `curl ... | sh` / `wget ... | sh` — 管道执行远程脚本
- `eval $(...)` — 动态命令执行

### 受保护路径

访问以下路径会强制使用沙箱执行：

- `/etc/*` — 系统配置
- `/usr/*`、 `/bin/*`、 `/sbin/*` — 系统程序
- `/sys/*`、 `/proc/*` — 内核接口
- `/root/*` — root 用户目录
- `../*` — 路径遍历尝试

## 故障排查

### Gradle 构建失败：JDK 版本不兼容

**现象**：`Unsupported class file major version` 或 Gradle 直接报版本号错误

**原因**：Gradle 8.10 不支持 JDK 25+，且项目要求 JDK 21

**解决**：

```bash
# macOS / Linux — 指定 JAVA_HOME
export JAVA_HOME=/path/to/jdk-21
./gradlew build

# Windows CMD
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21
gradlew.bat build
```

或在 `gradle.properties` 中指定 toolchain 自动下载（需要网络）：

```properties
org.gradle.java.installations.auto-detect=true
org.gradle.java.installations.auto-download=true
```

### 启动脚本找不到 JDK 21

**现象**：`Error: JDK 21 not found`

**解决**：手动设置 `JAVA_HOME` 环境变量指向 JDK 21 安装目录，然后重新运行脚本。

### 沙箱执行失败

**现象**：`Sandbox execution failed` 或 `Docker connection refused`

**解决**：

1. 确认 Docker 已安装并运行：
   ```bash
   docker version
   ```

2. 检查 Docker 权限（Linux）：
   ```bash
   sudo usermod -aG docker $USER
   # 重新登录后生效
   ```

3. 验证混合执行配置：
   ```bash
   ./campusclaw.sh --verbose --exec-mode local -m glm-5
   ```

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

## 文档

- [ARCHITECTURE-HYBRID.md](ARCHITECTURE-HYBRID.md) — 混合执行架构设计文档
- [IMPLEMENTATION-HYBRID.md](IMPLEMENTATION-HYBRID.md) — 实现细节与修改点

## 许可证

[LICENSE](LICENSE)
