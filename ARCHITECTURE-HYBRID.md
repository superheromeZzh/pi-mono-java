# CampusClaw 混合执行架构（Local + Docker Sandbox）

## 架构概述

本架构实现了 **本地执行** 与 **Docker 沙箱执行** 的混合模式，让 `mateservice-deployment` Pod 既能高效执行安全的本地操作，又能隔离执行高风险命令。

```
┌─────────────────────────────────────────────────────────────┐
│                    mateservice-deployment-xxx                │
│  ┌─────────────────────────┐  ┌──────────────────────────┐  │
│  │   mateservice           │  │   dind-sandbox (sidecar) │  │
│  │   (pi-mono-java)        │  │                          │  │
│  │                         │  │   ┌──────────────────┐   │  │
│  │   ┌─────────────────┐   │  │   │ Docker Daemon    │   │  │
│  │   │ ExecutionRouter │◄──┼──┼──►│ (tcp://localhost)│   │  │
│  │   │                 │   │  │   └──────────────────┘   │  │
│  │   │ - 风险评估      │   │  │                          │  │
│  │   │ - 本地执行      │───┼──┼──►│ ┌────────────────┐   │  │
│  │   │ - 沙箱执行      │   │  │   │ sandbox-worker │   │  │
│  │   └─────────────────┘   │  │   │ (常驻容器)      │   │  │
│  │                         │  │   └────────────────┘   │  │
│  └─────────────────────────┘  │                          │  │
│                               │   ┌──────────────────┐   │  │
│   Shared: /workspace          │   │ 临时执行容器     │   │  │
│                               │   │ (动态创建/销毁)  │   │  │
└───────────────────────────────┴───┴──────────────────┴───┘  │
```

## 核心组件

### 1. 执行模式（ExecutionMode）

```java
public enum ExecutionMode {
    LOCAL,   // 本地执行（JVM 进程内）
    SANDBOX, // Docker 沙箱执行
    AUTO     // 自动选择（默认）
}
```

### 2. 智能路由（ExecutionRouter）

- **低风险操作**（read/glob/grep）→ 本地执行（高性能）
- **中风险操作**（write/edit/bash 安全命令）→ 本地执行 + 安全检查
- **高风险操作**（系统路径、危险命令）→ 强制沙箱执行
- **危险命令**（rm -rf / 等）→ 拦截或强制沙箱

### 3. 混合工具类

| 工具 | 类名 | 说明 |
|------|------|------|
| Read | `HybridReadTool` | 文件读取，支持 `_executionMode` 参数 |
| Write | `HybridWriteTool` | 文件写入，受保护路径自动使用沙箱 |
| Edit | `HybridEditTool` | 文件编辑，支持 sed 替换 |
| Bash | `HybridBashTool` | 命令执行，危险命令自动识别 |
| Glob | `HybridGlobTool` | 文件查找 |
| Grep | `HybridGrepTool` | 内容搜索 |

## 配置说明

### 本地开发（默认）

```yaml
# application.yml (默认)
tool:
  execution:
    default-mode: LOCAL
    local-execution-enabled: true
    sandbox-execution-enabled: false
```

### K8s 生产环境

```yaml
# application-k8s.yml
tool:
  execution:
    default-mode: AUTO        # 智能路由
    hybrid-enabled: true      # 启用混合工具
    local-execution-enabled: true
    sandbox-execution-enabled: true
    docker-host: tcp://localhost:2375
```

## 使用方法

### 1. 自动模式（默认）

```java
// AI 调用工具时会自动选择执行方式
// 读取文件 → 本地执行
{"tool": "read", "params": {"path": "src/main/java/App.java"}}

// 危险命令 → 沙箱执行
{"tool": "bash", "params": {"command": "rm -rf /tmp/*"}}
```

### 2. 显式指定模式

```java
// 强制本地执行
{"tool": "read", "params": {
    "path": "src/main/java/App.java",
    "_executionMode": "local"
}}

// 强制沙箱执行
{"tool": "bash", "params": {
    "command": "curl https://api.example.com/data",
    "_executionMode": "sandbox"
}}
```

### 3. 命令行工具过滤

```bash
# 只启用特定工具
pi --tools read,write,edit,bash

# 禁用所有工具
pi --no-tools
```

## 部署到 Kubernetes

### 1. 构建镜像

```bash
docker build -t campusclaw/mateservice:latest .
docker push campusclaw/mateservice:latest
```

### 2. 部署

```bash
kubectl apply -f k8s/mateservice-deployment.yaml
```

### 3. 验证

```bash
# 检查 Pod 状态
kubectl get pods -l app=mateservice

# 验证 DIND 连接
kubectl exec -it deployment/mateservice-deployment -c mateservice -- \
    docker -H tcp://localhost:2375 version

# 查看日志
kubectl logs -f deployment/mateservice-deployment -c mateservice
```

## 安全策略

### 危险命令拦截

```java
// 以下命令自动使用沙箱或拦截
- rm -rf /
- mkfs.*
- dd if=/dev/zero
- :(){ :|:& };:  (fork bomb)
- curl ... | sh
- eval $(...)
```

### 受保护路径

```java
// 访问以下路径强制使用沙箱
- /etc/*
- /usr/*
- /bin/*
- /sbin/*
- /sys/*
- /proc/*
- /root/*
- ../* (路径遍历)
```

## 文件结构

```
modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/
├── config/
│   ├── ToolExecutionProperties.java    # 配置属性
│   └── HybridExecutionConfiguration.java # 混合模式配置
├── tool/
│   ├── execution/
│   │   ├── ExecutionMode.java          # 执行模式枚举
│   │   ├── ExecutionRouter.java        # 智能路由器
│   │   └── ToolExecutionStrategy.java  # 策略接口
│   ├── hybrid/                         # 混合工具实现
│   │   ├── HybridReadTool.java
│   │   ├── HybridWriteTool.java
│   │   ├── HybridEditTool.java
│   │   ├── HybridBashTool.java
│   │   ├── HybridGlobTool.java
│   │   └── HybridGrepTool.java
│   └── sandbox/                        # 沙箱基础设施
│       ├── DockerSandboxClient.java
│       ├── ResourceLimits.java
│       ├── SandboxResult.java
│       └── SandboxSecurityPolicy.java
└── resources/
    └── application-k8s.yml             # K8s 配置

k8s/
└── mateservice-deployment.yaml         # K8s 部署配置
```

## 扩展指南

### 添加新的混合工具

1. 创建新的 `HybridXxxTool` 类，实现 `AgentTool`
2. 注入 `ExecutionRouter`
3. 在 `execute` 方法中调用 `router.route()`
4. 在 `HybridExecutionConfiguration` 中注册 Primary Bean

### 自定义安全策略

```java
@Component
public class CustomSecurityPolicy {
    public boolean isDangerous(String command) {
        // 自定义判断逻辑
    }
}
```

## 监控与日志

### 日志级别

```yaml
logging:
  level:
    com.campusclaw.codingagent.tool.execution: DEBUG
    com.campusclaw.codingagent.tool.sandbox: DEBUG
```

### 关键日志

```
[INFO] ExecutionRouter initialized with default mode: AUTO
[DEBUG] Routing tool [read] with mode: AUTO
[DEBUG] Risk assessment for [read]: LOW
[INFO] Hybrid mode enabled: Read tool using smart routing
[WARN] High risk operation, using sandbox: bash rm -rf /etc/config
```

## 故障排查

| 问题 | 原因 | 解决 |
|------|------|------|
| 沙箱不可用 | DIND 未启动 | 检查 `docker version` 在 sidecar 中是否工作 |
| 本地执行被拒绝 | 安全检查 | 检查命令是否在白名单中 |
| 文件找不到 | 工作目录不一致 | 确保 `/workspace` 在两个容器中正确挂载 |
| 权限不足 | 特权模式未启用 | 确认 `securityContext.privileged: true` |
