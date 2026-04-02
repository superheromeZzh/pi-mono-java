# CampusClaw Docker 沙箱运行指南

本文档指导如何在 Docker 沙箱模式下运行 CampusClaw，实现安全的工具执行隔离。

## 目录

- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [运行方式](#运行方式)
- [配置说明](#配置说明)
- [故障排查](#故障排查)
- [架构说明](#架构说明)

---

## 环境要求

### 必需组件

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | Java 运行环境 |
| Docker | 20.10+ | Docker 引擎 |
| 内存 | 4GB+ | 沙箱容器需要内存 |
| 磁盘 | 2GB+ | 沙箱镜像和容器存储 |

### 安装 Docker

#### macOS

**方式 1: Docker Desktop（推荐）**
```bash
brew install --cask docker
# 或从官网下载: https://www.docker.com/products/docker-desktop
```

**方式 2: Colima（轻量级）**
```bash
brew install colima docker
# 首次启动
colima start --cpu 4 --memory 8
# 后续启动
colima start
```

#### Linux (Ubuntu/Debian)
```bash
# 安装 Docker
curl -fsSL https://get.docker.com | sh

# 添加用户到 docker 组
sudo usermod -aG docker $USER
# 重新登录后生效

# 启动 Docker
sudo systemctl start docker
```

#### Windows

1. 安装 [Docker Desktop](https://www.docker.com/products/docker-desktop)
2. 启用 WSL2 后端（推荐）
3. 重启系统

---

## 快速开始

### 1. 验证 Docker 安装

```bash
docker version
docker info
```

### 2. 使用脚本运行（推荐）

**macOS / Linux:**
```bash
./run-with-sandbox.sh -m glm-5
```

**Windows:**
```cmd
run-with-sandbox.bat -m glm-5
```

### 3. 手动运行

```bash
# 设置环境变量
export TOOL_EXECUTION_DEFAULT_MODE=SANDBOX
export TOOL_EXECUTION_SANDBOX_ENABLED=true
export DOCKER_HOST=unix:///var/run/docker.sock

# 运行
java -jar modules/coding-agent-cli/build/libs/campusclaw-agent-1.0.0-SNAPSHOT.jar \
  --exec-mode sandbox \
  -m glm-5
```

---

## 运行方式

### 方式 1: 纯沙箱模式（最安全）

所有工具都在 Docker 容器中执行：

```bash
./run-with-sandbox.sh --exec-mode sandbox -m glm-5
```

### 方式 2: 智能路由模式（推荐）

根据风险评估自动选择执行方式：

```bash
./run-with-sandbox.sh --exec-mode auto -m glm-5
```

风险评估：
- **LOW**: read/glob/grep → 本地执行
- **MEDIUM**: write/edit 到项目目录 → 本地执行
- **HIGH**: bash 危险命令、系统路径访问 → 沙箱执行

### 方式 3: 本地优先模式（高性能）

优先本地执行，仅危险操作使用沙箱：

```bash
./run-with-sandbox.sh --exec-mode local -m glm-5
```

---

## 配置说明

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `TOOL_EXECUTION_DEFAULT_MODE` | `AUTO` | 默认执行模式: LOCAL/SANDBOX/AUTO |
| `TOOL_EXECUTION_SANDBOX_ENABLED` | `true` | 启用沙箱执行 |
| `TOOL_EXECUTION_LOCAL_ENABLED` | `true` | 启用本地执行 |
| `DOCKER_HOST` | 平台相关 | Docker 守护进程地址 |

### Docker 主机地址

| 平台 | 默认地址 |
|------|----------|
| macOS (Docker Desktop) | `unix:///var/run/docker.sock` |
| macOS (Colima) | `unix:///Users/$USER/.colima/default/docker.sock` |
| Linux | `unix:///var/run/docker.sock` |
| Windows | `npipe:////./pipe/docker_engine` |

### 配置文件

编辑 `~/.campusclaw/settings.json`:

```json
{
  "defaultModel": "glm-5",
  "toolExecution": {
    "defaultMode": "AUTO",
    "localExecutionEnabled": true,
    "sandboxExecutionEnabled": true,
    "dockerHost": "unix:///var/run/docker.sock",
    "sandboxWorkerImage": "alpine:3.19",
    "sandboxWorkerMemory": "512m",
    "sandboxWorkerCpu": 1.0,
    "sandboxTimeoutSeconds": 120
  }
}
```

---

## 故障排查

### Docker 连接失败

**现象:**
```
Cannot connect to the Docker daemon
```

**解决:**

1. **macOS - Colima 用户:**
```bash
# 检查 Colima 状态
colima status

# 启动 Colima
colima start

# 设置正确的 Docker 主机
export DOCKER_HOST=unix:///Users/$USER/.colima/default/docker.sock
```

2. **Linux:**
```bash
# 检查 Docker 服务
sudo systemctl status docker

# 启动服务
sudo systemctl start docker

# 检查权限
groups | grep docker
# 如果无输出，执行:
sudo usermod -aG docker $USER
# 然后重新登录
```

3. **Windows:**
```powershell
# 检查 Docker Desktop 是否运行
# 确保已启用 WSL2 后端
```

### 沙箱镜像拉取失败

**现象:**
```
Error response from daemon: pull access denied
```

**解决:**
```bash
# 手动拉取镜像
docker pull alpine:3.19

# 或使用代理
export HTTP_PROXY=http://proxy.example.com:8080
docker pull alpine:3.19
```

### 权限拒绝

**现象:**
```
Permission denied while trying to connect to Docker daemon
```

**解决:**
```bash
# Linux/macOS
sudo chmod 666 /var/run/docker.sock

# 或添加用户到 docker 组
sudo usermod -aG docker $USER
# 重新登录
```

### 沙箱执行超时

**现象:**
命令在沙箱中执行时间过长。

**解决:**
```bash
# 增加超时时间
export TOOL_EXECUTION_SANDBOX_TIMEOUT_SECONDS=300

# 或修改配置
java -Dtool.execution.sandbox-timeout-seconds=300 \
  -jar campusclaw-agent.jar
```

### 网络连接问题

**现象:**
沙箱内无法访问网络。

**解决:**
```bash
# 检查 Docker 网络
docker network ls

# 创建自定义网络
docker network create campusclaw-network

# 在配置中指定网络
```

---

## 架构说明

### 沙箱执行流程

```
User Request
    ↓
HybridTool (read/write/edit/bash/...)
    ↓
ExecutionRouter
    ↓
    ├─ Mode = LOCAL → LocalExecutor → 直接执行
    ↓
    └─ Mode = SANDBOX → DockerSandboxClient
                              ↓
                        docker exec / docker run
                              ↓
                        Sandbox Container
                              ↓
                        Command Execution
                              ↓
                        Return Result
```

### 沙箱容器生命周期

1. **常驻工作容器**: `sandbox-worker` 长期运行，避免频繁创建销毁
2. **临时执行容器**: 高危命令创建独立容器，执行后立即销毁
3. **共享卷**: `/workspace` 在主机和容器间共享

### 安全边界

| 资源 | 沙箱隔离 |
|------|----------|
| 文件系统 | 仅 /workspace 可写，/etc、/usr 等只读或不可访问 |
| 网络 | 受限（可配置） |
| 进程 | PID 命名空间隔离 |
| 资源 | CPU/内存限制 |

---

## 高级配置

### 自定义沙箱镜像

编辑 `application.yml`:

```yaml
tool:
  execution:
    sandbox-worker-image: "my-custom-image:latest"
```

构建自定义镜像:

```dockerfile
FROM alpine:3.19
RUN apk add --no-cache git curl jq python3
WORKDIR /workspace
```

### 资源限制

```yaml
tool:
  execution:
    sandbox-worker-memory: 1g
    sandbox-worker-cpu: 2.0
```

### 安全策略自定义

实现 `SandboxSecurityPolicy`:

```java
@Component
public class CustomSecurityPolicy extends SandboxSecurityPolicy {
    @Override
    public boolean isDangerousCommand(String command) {
        // 自定义逻辑
        return super.isDangerousCommand(command)
            || command.contains("custom-dangerous-pattern");
    }
}
```

---

## 参考

- [ARCHITECTURE-HYBRID.md](ARCHITECTURE-HYBRID.md) - 混合架构设计
- [IMPLEMENTATION-HYBRID.md](IMPLEMENTATION-HYBRID.md) - 实现细节
- [README.md](README.md) - 主文档

---

**版本:** v1.0
**更新日期:** 2026-03-31
