# CampusClaw Kubernetes 部署

本目录包含在 Kubernetes 中部署 CampusClaw 的 YAML 配置文件。

## 架构说明

Pod 包含两个容器：

1. **campusclaw** - 主应用容器
   - 基于 Eclipse Temurin JDK 21
   - 运行 CampusClaw Java 应用
   - 通过 TCP 连接访问 Docker 沙箱

2. **sandbox** - Docker 沙箱容器 (DinD)
   - 基于官方 `docker:dind` 镜像
   - 提供特权模式的 Docker 环境
   - 监听 TCP 2375 端口

## 文件说明

| 文件 | 说明 |
|------|------|
| `namespace.yaml` | 创建 campusclaw 命名空间 |
| `persistent-volume.yaml` | 本地存储卷配置（代码目录 + 数据目录） |
| `configmap.yaml` | 应用配置和启动脚本 |
| `deployment.yaml` | 主部署文件（双容器 Pod） |
| `service.yaml` | Service 配置（ClusterIP + NodePort） |
| `kustomization.yaml` | Kustomize 配置（可选） |

## 前置要求

1. Kubernetes 集群（已测试 minikube）
2. kubectl 命令行工具
3. 已构建的 CampusClaw JAR 文件

## 部署步骤

### 1. 构建 CampusClaw（如未构建）

```bash
cd /Users/simon/01.code/pi-mono-java
./mvnw package -DskipTests -pl modules/coding-agent-cli
```

### 2. 启动 minikube（如未启动）

```bash
minikube start --driver=docker --memory=4096 --cpus=2
```

### 3. 启用本地卷挂载

minikube 需要挂载本地目录：

```bash
# 在 minikube 中创建挂载目录
minikube ssh -- sudo mkdir -p /Users/simon/01.code/pi-mono-java
minikube ssh -- sudo mkdir -p /Users/simon/.campusclaw

# 挂载本地目录到 minikube
minikube mount /Users/simon/01.code/pi-mono-java:/Users/simon/01.code/pi-mono-java &
minikube mount /Users/simon/.campusclaw:/Users/simon/.campusclaw &
```

### 4. 应用配置

```bash
cd /Users/simon/01.code/pi-mono-java/modules/k8s

# 创建命名空间
kubectl apply -f namespace.yaml

# 创建存储卷
kubectl apply -f persistent-volume.yaml

# 创建 ConfigMap
kubectl apply -f configmap.yaml

# 部署应用
kubectl apply -f deployment.yaml

# 创建 Service
kubectl apply -f service.yaml
```

### 5. 验证部署

```bash
# 查看 Pod 状态
kubectl get pods -n campusclaw -w

# 查看容器日志
kubectl logs -n campusclaw deployment/campusclaw -c campusclaw
kubectl logs -n campusclaw deployment/campusclaw -c sandbox

# 进入容器调试
kubectl exec -n campusclaw -it deployment/campusclaw -c campusclaw -- /bin/sh
```

### 6. 访问服务

```bash
# 方法1: 使用 minikube service
minikube service -n campusclaw campusclaw-nodeport

# 方法2: 使用 kubectl port-forward
kubectl port-forward -n campusclaw svc/campusclaw 8080:8080
```

## 目录挂载说明

| 宿主机路径 | 容器路径 | 说明 |
|-----------|---------|------|
| `/Users/simon/01.code/pi-mono-java` | `/workspace` | 代码目录 |
| `/Users/simon/.campusclaw` | `/data` | 数据目录 |
| `emptyDir` | `/root/.m2` | Maven 缓存 |
| `emptyDir` | `/var/lib/docker` | Docker 存储 |

## 常见问题

### 1. Pod 无法启动，提示 JAR 文件不存在

确保在部署前已经构建了 JAR 文件：
```bash
./mvnw package -DskipTests -pl modules/coding-agent-cli
```

### 2. 沙箱容器启动失败

检查是否启用了特权模式：
```bash
kubectl describe pod -n campusclaw -l app.kubernetes.io/name=campusclaw
```

### 3. 本地卷无法挂载

确保 minikube 已正确挂载本地目录：
```bash
minikube ssh -- ls -la /Users/simon/01.code/pi-mono-java
```

### 4. Docker 沙箱无法连接

检查 campusclaw 容器的环境变量：
```bash
kubectl exec -n campusclaw -it deployment/campusclaw -c campusclaw -- env | grep DOCKER
```

## 清理资源

```bash
kubectl delete namespace campusclaw
```

## 使用 Kustomize（可选）

```bash
kubectl apply -k .
```
