# CampusClaw Docker 沙箱配置完成

## ✅ 配置状态

| 组件 | 状态 | 详情 |
|------|------|------|
| Colima | ✅ 运行中 | 4核 8GB 内存 |
| Docker | ✅ 可用 | v29.2.1 |
| 镜像加速器 | ✅ 已配置 | 5个国内镜像源 |
| Alpine 镜像 | ✅ 已拉取 | alpine:3.19 |
| 沙箱测试 | ✅ 通过 | 所有功能正常 |

## 🔧 镜像加速器配置

已通过 Colima 配置文件配置以下镜像源：

- `https://docker.mirrors.ustc.edu.cn` (中科大)
- `https://hub-mirror.c.163.com` (网易云)
- `https://mirror.baidubce.com` (百度云)
- `https://ccr.ccs.tencentyun.com` (腾讯云)
- `https://dockerproxy.com` (Docker代理)

配置文件位置：`~/.config/colima/colima.yaml`

## 🚀 使用方式

### 1. 使用脚本运行（推荐）

```bash
./run-with-sandbox.sh -m glm-5
```

### 2. 使用测试脚本

```bash
./test-sandbox-only.sh
```

### 3. 手动运行

```bash
# 设置环境变量
export DOCKER_HOST="unix:///Users/simon/.colima/default/docker.sock"
export TOOL_EXECUTION_DEFAULT_MODE=SANDBOX

# 运行
java -jar modules/coding-agent-cli/build/libs/campusclaw-agent-1.0.0-SNAPSHOT.jar \
  --exec-mode sandbox \
  -m glm-5
```

## 📝 管理命令

```bash
# 查看 Colima 状态
colima status

# 停止 Colima
colima stop

# 启动 Colima
colima start

# 重启 Colima
colima stop && colima start

# 查看 Docker 信息
docker info

# 查看镜像加速器配置
docker info | grep -A 10 "Registry Mirrors"

# 查看本地镜像
docker images
```

## 🧪 测试验证

所有测试已通过：
- ✅ Docker 连接正常
- ✅ 镜像加速器已配置
- ✅ alpine:3.19 镜像可用
- ✅ 沙箱容器可正常运行
- ✅ 共享卷工作正常
- ✅ 资源限制生效

## 🛠️ 故障排查

### 如果 Docker 拉取镜像失败

1. 检查镜像加速器配置：
```bash
docker info | grep -A 10 "Registry Mirrors"
```

2. 如果配置丢失，重新通过 SSH 设置：
```bash
colima ssh <<'EOF'
echo '{"registry-mirrors":["https://docker.mirrors.ustc.edu.cn","https://hub-mirror.c.163.com","https://mirror.baidubce.com","https://ccr.ccs.tencentyun.com"]}' | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker
EOF
```

3. 手动拉取镜像：
```bash
docker pull alpine:latest
docker tag alpine:latest alpine:3.19
```

### 如果 Colima 无法启动

```bash
# 完全删除并重新创建
colima delete
colima start
```

## 📂 相关文件

- `run-with-sandbox.sh` - 沙箱运行脚本
- `test-sandbox-only.sh` - 沙箱功能测试脚本
- `~/.config/colima/colima.yaml` - Colima 配置文件
- `DOCKER-SANDBOX-GUIDE.md` - 完整使用指南

---

配置日期：2026-03-31
状态：✅ 已完成
