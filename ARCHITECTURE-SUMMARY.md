# CampusClaw 沙箱架构与开发部署总结

## 一、项目结构

```
pi-mono-java/
├── pom.xml                      # 根 POM（聚合项目）
├── modules/                     # 可编译的模块（推荐开发目录）
│   ├── ai/                      # LLM API 客户端（Anthropic/OpenAI/AWS等）
│   ├── agent-core/              # Agent 核心（循环、工具调用、事件系统）
│   ├── tui/                     # 终端 UI 组件（Lanterna/JLine）
│   ├── cron/                    # 定时任务调度
│   ├── assistant/               # WebSocket 网关服务
│   └── coding-agent-cli/        # CLI 入口 + 沙箱实现 ⭐主模块
├── mate-campusclaw/             # HiCampus 集成副本（代码同步目标）
│   └── src/main/java/com/huawei/hicampus/mate/matecampusclaw/
├── k8s/                         # Kubernetes 部署配置
│   └── mateservice-deployment.yaml
└── run-with-sandbox.sh          # 沙箱模式启动脚本
```

## 二、Modules 职责

| 模块 | 职责 | 是否包含 main() |
|------|------|----------------|
| `ai` | LLM SDK 封装（Claude/OpenAI/Gemini/Bedrock） | ❌ |
| `agent-core` | Agent 循环、工具执行框架、事件系统 | ❌ |
| `tui` | 终端 UI 组件（编辑器、选择列表、输入框） | ❌ |
| `cron` | Cron 表达式解析、任务调度 | ❌ |
| `assistant` | WebSocket 服务端、消息网关 | ❌ |
| `coding-agent-cli` | **Spring Boot 启动类、CLI 命令解析、沙箱模式实现** | ✅ |

## 三、沙箱代码位置

沙箱相关代码全部位于 `coding-agent-cli` 模块：

```
modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/
├── tool/sandbox/
│   ├── DockerSandboxClient.java      # Docker 客户端（执行 docker 命令）
│   ├── SandboxResult.java            # 沙箱执行结果
│   ├── SandboxSecurityPolicy.java    # 安全策略（危险命令检测）
│   └── ResourceLimits.java           # 资源限制
├── tool/hybrid/                      # 混合工具（支持沙箱路由）
│   ├── HybridBashTool.java
│   ├── HybridReadTool.java
│   ├── HybridWriteTool.java
│   ├── HybridEditTool.java
│   ├── HybridGlobTool.java
│   └── HybridGrepTool.java
├── tool/execution/
│   ├── ExecutionMode.java            # 执行模式枚举
│   └── ExecutionRouter.java          # 执行路由器
└── CampusClawApplication.java        # Spring Boot 启动类
```

## 四、开发工作流

### 1. 本地开发编译

```bash
# 编译所有模块（coding-agent-cli 会自动编译其依赖）
./mvnw clean package -DskipTests

# 或只编译 coding-agent-cli 及其依赖
./mvnw clean package -pl modules/coding-agent-cli -am -DskipTests
```

### 2. 本地沙箱测试

```bash
# 使用脚本启动（自动检查 Docker、设置环境变量）
./run-with-sandbox.sh --exec-mode sandbox -m glm-5

# 或手动启动
export TOOL_EXECUTION_DEFAULT_MODE=SANDBOX
export TOOL_EXECUTION_SANDBOX_ENABLED=true
export DOCKER_HOST=unix:///var/run/docker.sock  # macOS Colima 用户需修改
java -jar modules/coding-agent-cli/target/campusclaw-agent.jar --exec-mode sandbox
```

### 3. 代码同步到 mate-campusclaw

已提供同步脚本 `sync-to-mate.sh`：

```bash
# 同步所有模块
./sync-to-mate.sh

# 或只同步指定模块
./sync-to-mate.sh cron
./sync-to-mate.sh ai
```

同步映射关系：

| modules 目录 | mate-campusclaw 目录 | 包名变化 |
|-------------|---------------------|---------|
| `agent-core` | `agent` | `com.campusclaw.agent` → `com.huawei.hicampus.mate.matecampusclaw.agent` |
| `ai` | `ai` | `com.campusclaw.ai` → `com.huawei.hicampus.mate.matecampusclaw.ai` |
| `coding-agent-cli` | `codingagent` | `com.campusclaw.codingagent` → `com.huawei.hicampus.mate.matecampusclaw.codingagent` |
| `cron` | `cron` | `com.campusclaw.cron` → `com.huawei.hicampus.mate.matecampusclaw.cron` |
| `tui` | `tui` | `com.campusclaw.tui` → `com.huawei.hicampus.mate.matecampusclaw.tui` |

## 五、生产 K8s 部署架构

### Sidecar 模式（Pod 多容器）

```
┌─────────────────────────────────────────────────────────────┐
│                         Pod                                  │
│  ┌─────────────────────┐    ┌─────────────────────────────┐ │
│  │   mateservice       │    │    dind-sandbox             │ │
│  │   (主容器)           │    │    (Docker in Docker)       │ │
│  │                     │    │                             │ │
│  │  CampusClaw 应用     │◄──►│  Docker Daemon              │ │
│  │  - 执行 docker 命令  │    │  - 暴露 2375 端口            │ │
│  │  - 连接 localhost    │    │  - 特权模式运行              │ │
│  │    :2375            │    │  - 管理沙箱容器生命周期        │ │
│  │                     │    │                             │ │
│  │  环境变量:           │    │                             │ │
│  │  DOCKER_HOST=       │    │                             │ │
│  │  tcp://localhost    │    │                             │ │
│  │  :2375              │    │                             │ │
│  └─────────────────────┘    └─────────────────────────────┘ │
│           │                           │                      │
│           └──────────┬────────────────┘                      │
│                      ▼                                       │
│              ┌──────────────┐                                │
│              │  workspace   │  (emptyDir 共享卷)              │
│              │  (10Gi)      │                                │
│              └──────────────┘                                │
└─────────────────────────────────────────────────────────────┘
```

### K8s Deployment 关键配置

```yaml
spec:
  containers:
    # 主容器
    - name: mateservice
      image: campusclaw/mateservice:latest
      env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: DOCKER_HOST          # 关键配置
          value: "tcp://localhost:2375"
      volumeMounts:
        - name: workspace
          mountPath: /workspace

    # Sidecar 容器：DIND
    - name: dind-sandbox
      image: docker:25.0-dind-alpine3.19
      securityContext:
        privileged: true           # 必须特权模式
      env:
        - name: DOCKER_TLS_CERTDIR
          value: ""                # 禁用 TLS（同 Pod 内）
      ports:
        - containerPort: 2375      # Docker Daemon TCP 端口
      volumeMounts:
        - name: workspace
          mountPath: /workspace
        - name: tmp
          mountPath: /var/lib/docker

  volumes:
    - name: workspace
      emptyDir:
        sizeLimit: 10Gi
```

## 六、沙箱执行流程

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
                        检查 DOCKER_HOST 环境变量
                        执行: docker -H tcp://localhost:2375 run/exec ...
                              ↓
                        DIND Sidecar 接收请求
                              ↓
                        创建/执行沙箱容器
                              ↓
                        返回结果
```

## 七、关键配置点

| 配置项 | 本地开发 | K8s 生产 | 说明 |
|--------|---------|---------|------|
| `DOCKER_HOST` | `unix:///var/run/docker.sock` | `tcp://localhost:2375` | Docker Daemon 地址 |
| `sandbox-worker-image` | `alpine:3.19` | `alpine:3.19` | 沙箱基础镜像 |
| `sandbox-worker-memory` | `512m` | `512m` | 沙箱容器内存限制 |
| `use-ephemeral-containers` | `false` | `false` | 是否使用临时容器 |
| `default-mode` | `SANDBOX` | `AUTO` 或 `SANDBOX` | 默认执行模式 |

## 八、代码零侵入原理

为什么 K8s 部署不需要改动代码？

1. **DockerSandboxClient** 通过环境变量 `DOCKER_HOST` 动态配置 Docker 地址
2. **ProcessBuilder** 执行 `docker -H <host> <command>` 命令
3. 同 Pod 内通过 `localhost:2375` TCP 通信，无需挂载 Docker Socket
4. 共享 `emptyDir` 卷实现工作目录共享

```java
// DockerSandboxClient.java
String dockerHost = properties.getDockerHost();
if (dockerHost != null && !dockerHost.isEmpty()) {
    fullCmd.add("-H");
    fullCmd.add(dockerHost);  // tcp://localhost:2375
}
```

## 九、常用命令

```bash
# 本地编译
./mvnw clean package -DskipTests

# 本地沙箱运行
./run-with-sandbox.sh -m glm-5

# 同步到 mate-campusclaw
./sync-to-mate.sh

# K8s 部署
kubectl apply -f k8s/mateservice-deployment.yaml

# 查看 Pod 日志
kubectl logs <pod-name> -c mateservice
kubectl logs <pod-name> -c dind-sandbox

# 进入 Pod 调试
kubectl exec -it <pod-name> -c mateservice -- sh
```

---

**文档版本**: v1.0  
**更新日期**: 2026-04-10
