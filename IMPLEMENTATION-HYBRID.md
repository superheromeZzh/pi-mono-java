# pi-mono-java 混合执行架构文档

> 本文档记录 CampusClaw 混合执行架构（本地 + Docker 沙箱）的完整实现方案及所有修改点。
>
> - 创建日期：2026-03-30
> - 版本：v1.0

---

## 一、架构概述

### 1.1 设计目标

实现 **本地执行** 与 **Docker 沙箱执行** 的混合模式，让 `mateservice-deployment` Pod 既能高效执行安全的本地操作，又能隔离执行高风险命令。

### 1.2 核心特性

| 特性 | 说明 |
|------|------|
| 混合执行 | 支持 LOCAL（本地JVM）、SANDBOX（Docker容器）、AUTO（智能路由）三种模式 |
| 智能路由 | 基于风险评估自动选择执行方式，低风险操作本地执行，高风险操作沙箱执行 |
| 常驻沙箱 | DIND Sidecar 容器长期运行，避免频繁创建销毁容器的开销 |
| 安全策略 | 内置危险命令识别和受保护路径检查 |
| 灵活配置 | Spring Boot 配置属性支持环境特定配置 |

### 1.3 架构图

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

---

## 二、文件结构

### 2.1 新增文件（共 16 个）

#### 配置类（2 个）

```
modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/config/
├── ToolExecutionProperties.java          # 工具执行配置属性
└── HybridExecutionConfiguration.java     # 混合执行模式配置
```

#### 执行核心（4 个）

```
modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/tool/execution/
├── ExecutionMode.java                    # 执行模式枚举（LOCAL/SANDBOX/AUTO）
├── ExecutionRouter.java                  # 智能路由器（核心）
├── ToolExecutionStrategy.java            # 执行策略接口
└── RiskLevel.java                        # 风险等级枚举
```

#### 混合工具实现（6 个）

```
modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/tool/hybrid/
├── HybridReadTool.java                   # 文件读取工具
├── HybridWriteTool.java                  # 文件写入工具
├── HybridEditTool.java                   # 文件编辑工具
├── HybridBashTool.java                   # Bash命令执行工具
├── HybridGlobTool.java                   # 文件查找工具
└── HybridGrepTool.java                   # 内容搜索工具
```

#### 沙箱基础设施（4 个）

```
modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/tool/sandbox/
├── DockerSandboxClient.java              # Docker CLI 客户端
├── ResourceLimits.java                   # 资源限制配置
├── SandboxResult.java                    # 沙箱执行结果
└── SandboxSecurityPolicy.java            # 安全策略
```

#### 配置文件（1 个）

```
modules/coding-agent-cli/src/main/resources/
└── application-k8s.yml                   # K8s 环境配置
```

#### 部署文件（1 个）

```
k8s/
└── mateservice-deployment.yaml           # K8s Deployment + DIND Sidecar
```

#### 构建文件（1 个）

```
├── Dockerfile                            # 多阶段构建镜像
└── ARCHITECTURE-HYBRID.md               # 架构设计文档（已存在）
```

### 2.2 修改文件（共 2 个）

```
├── build.gradle.kts                      # 添加 Lombok 依赖
└── modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/
    └── CampusClawApplication.java        # 启用配置属性
```

---

## 三、详细修改点

### 3.1 build.gradle.kts

**位置**：项目根目录 `build.gradle.kts`

**修改内容**：添加 Lombok 依赖以支持 `@Data` 注解

```kotlin
dependencies {
    // ... 现有依赖 ...

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
}
```

### 3.2 CampusClawApplication.java

**位置**：`modules/coding-agent-cli/src/main/java/com/campusclaw/codingagent/CampusClawApplication.java`

**修改内容**：添加 `@EnableConfigurationProperties` 注解

```java
// 添加导入
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.campusclaw.codingagent.config.ToolExecutionProperties;

// 添加注解
@SpringBootApplication(scanBasePackages = "com.campusclaw")
@EnableConfigurationProperties(ToolExecutionProperties.class)  // 新增
public class CampusClawApplication implements CommandLineRunner, ExitCodeGenerator {
    // ... 现有代码 ...
}
```

---

## 四、核心组件说明

### 4.1 ExecutionMode（执行模式）

```java
public enum ExecutionMode {
    LOCAL,      // 本地执行（JVM 进程内）
    SANDBOX,    // Docker 沙箱执行
    AUTO        // 自动选择（默认）
}
```

### 4.2 ExecutionRouter（智能路由器）

核心方法：

| 方法 | 说明 |
|------|------|
| `route()` | 主路由方法，根据模式和风险选择执行策略 |
| `assessRisk()` | 风险评估：LOW/MEDIUM/HIGH/CRITICAL |
| `executeLocal()` | 本地执行，直接调用原工具 |
| `executeSandbox()` | 沙箱执行，通过 Docker 容器 |

### 4.3 风险评估规则

**危险命令模式**（强制沙箱）：
- `rm -rf /` 或 `rm -rf /*`
- `mkfs.*` - 格式化命令
- `dd if=/dev/zero` - 磁盘清零
- `:(){ :|:& };:` - Fork Bomb
- `curl.*\|.*sh` 或 `wget.*\|.*sh` - 管道执行远程脚本
- `eval \$\(.*\)` - 动态命令执行
- `> /dev/sd*` - 直接写入磁盘设备

**受保护路径**（强制沙箱）：
- `/etc/*` - 系统配置
- `/usr/*` - 系统程序
- `/bin/*`, `/sbin/*` - 系统命令
- `/sys/*`, `/proc/*` - 内核接口
- `/root/*` - root 用户目录
- `../*` - 路径遍历尝试

### 4.4 混合工具参数

所有混合工具支持以下特殊参数：

| 参数名 | 类型 | 说明 |
|--------|------|------|
| `_executionMode` | string | 强制指定执行模式：`local`/`sandbox`/`auto` |

示例：
```json
// 强制本地执行
{"tool": "read", "params": {"path": "file.txt", "_executionMode": "local"}}

// 强制沙箱执行
{"tool": "bash", "params": {"command": "rm -rf /tmp/*", "_executionMode": "sandbox"}}
```

---

## 五、配置说明

### 5.1 本地开发配置

文件：`application.yml`（默认）

```yaml
tool:
  execution:
    default-mode: LOCAL
    local-execution-enabled: true
    sandbox-execution-enabled: false
```

### 5.2 K8s 生产配置

文件：`application-k8s.yml`

```yaml
tool:
  execution:
    default-mode: AUTO              # 智能路由
    hybrid-enabled: true            # 启用混合工具
    local-execution-enabled: true   # 允许本地执行
    sandbox-execution-enabled: true # 允许沙箱执行
    docker-host: tcp://localhost:2375
    sandbox-workspace-path: /workspace
    sandbox-worker-image: alpine:3.19
    sandbox-worker-memory: 512m
    sandbox-worker-cpu: 1.0
    sandbox-timeout-seconds: 120
    local-timeout-seconds: 60
```

### 5.3 配置属性类

类：`ToolExecutionProperties.java`

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `defaultMode` | LOCAL | 默认执行模式 |
| `hybridEnabled` | true | 是否启用混合路由 |
| `localExecutionEnabled` | true | 是否允许本地执行 |
| `sandboxExecutionEnabled` | false | 是否允许沙箱执行 |
| `dockerHost` | tcp://localhost:2375 | Docker 守护进程地址 |
| `sandboxWorkspacePath` | /workspace | 沙箱工作目录 |
| `sandboxWorkerImage` | alpine:3.19 | 沙箱工作镜像 |
| `sandboxWorkerMemory` | 512m | 沙箱内存限制 |
| `sandboxWorkerCpu` | 1.0 | 沙箱 CPU 限制 |
| `sandboxTimeoutSeconds` | 120 | 沙箱执行超时 |
| `localTimeoutSeconds` | 60 | 本地执行超时 |

---

## 六、部署指南

### 6.1 构建镜像

```bash
# 构建应用镜像
docker build -t campusclaw/mateservice:latest .

# 推送镜像到仓库
docker push campusclaw/mateservice:latest
```

### 6.2 部署到 Kubernetes

```bash
# 应用部署配置
kubectl apply -f k8s/mateservice-deployment.yaml

# 检查 Pod 状态
kubectl get pods -l app=mateservice

# 验证 DIND 连接
kubectl exec -it deployment/mateservice-deployment -c mateservice -- \
    docker -H tcp://localhost:2375 version

# 查看日志
kubectl logs -f deployment/mateservice-deployment -c mateservice
```

### 6.3 K8s 部署配置要点

**mateservice 容器**：
- 镜像：`campusclaw/mateservice:latest`
- 环境变量：`SPRING_PROFILES_ACTIVE=k8s`, `DOCKER_HOST=tcp://localhost:2375`
- 卷挂载：`/workspace` (emptyDir 共享卷)
- 资源：2Gi-4Gi 内存，1000m-2000m CPU

**dind-sandbox 容器**（Sidecar）：
- 镜像：`docker:25.0-dind-alpine3.19`
- 特权模式：`securityContext.privileged: true`
- 环境变量：`DOCKER_TLS_CERTDIR=""` (禁用 TLS)
- 卷挂载：`/workspace` (共享), `/var/lib/docker` (Docker 存储)
- 资源：1Gi-4Gi 内存，500m-2000m CPU

---

## 七、监控与日志

### 7.1 日志级别配置

```yaml
logging:
  level:
    com.campusclaw.codingagent.tool.execution: DEBUG
    com.campusclaw.codingagent.tool.sandbox: DEBUG
```

### 7.2 关键日志

```
[INFO] ExecutionRouter initialized with default mode: AUTO
[DEBUG] Routing tool [read] with mode: AUTO
[DEBUG] Risk assessment for [read]: LOW
[INFO] Hybrid mode enabled: Read tool using smart routing
[WARN] High risk operation, using sandbox: bash rm -rf /etc/config
[ERROR] Critical risk operation blocked: bash rm -rf /
```

---

## 八、故障排查

| 问题 | 原因 | 解决 |
|------|------|------|
| 沙箱不可用 | DIND 未启动 | 检查 `docker version` 在 sidecar 中是否工作 |
| 本地执行被拒绝 | 安全检查 | 检查命令是否在白名单中 |
| 文件找不到 | 工作目录不一致 | 确保 `/workspace` 在两个容器中正确挂载 |
| 权限不足 | 特权模式未启用 | 确认 `securityContext.privileged: true` |
| 沙箱执行超时 | 命令执行时间过长 | 调整 `sandbox-timeout-seconds` 配置 |

---

## 九、扩展指南

### 9.1 添加新的混合工具

1. 创建 `HybridXxxTool` 类，实现 `AgentTool` 接口
2. 注入 `ExecutionRouter`
3. 在 `execute()` 方法中调用 `router.route()`
4. 在 `HybridExecutionConfiguration` 中注册为 `@Primary` Bean

### 9.2 自定义安全策略

创建自定义策略类：

```java
@Component
public class CustomSecurityPolicy {
    public boolean isDangerous(String command) {
        // 自定义判断逻辑
        return false;
    }

    public boolean isProtectedPath(String path) {
        // 自定义路径检查
        return false;
    }
}
```

---

## 十、总结

本混合执行架构实现了：

1. **高性能**：低风险操作本地执行，无 Docker 开销
2. **高安全**：高风险操作隔离在沙箱中执行
3. **智能化**：自动风险评估，无需人工判断
4. **可扩展**：模块化设计，易于添加新工具
5. **可配置**：Spring Boot 配置属性支持多环境部署

---

**文档维护**：请随代码更新同步更新此文档
**相关文档**：`ARCHITECTURE-HYBRID.md`（架构设计）
