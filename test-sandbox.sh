#!/bin/bash
#
# CampusClaw 沙箱模式测试脚本
#

set -e

echo "=== CampusClaw Docker 沙箱测试 ==="
echo ""

# 检测并设置 Docker 主机
if [[ -S "$HOME/.colima/default/docker.sock" ]]; then
    export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
    echo "✓ 使用 Colima Docker: $DOCKER_HOST"
elif [[ -S "/var/run/docker.sock" ]]; then
    export DOCKER_HOST="unix:///var/run/docker.sock"
    echo "✓ 使用 Docker Desktop: $DOCKER_HOST"
else
    echo "✗ 未找到 Docker 套接字"
    exit 1
fi

# 检查 Docker 状态
echo ""
echo "检查 Docker 状态..."
if docker version &>/dev/null; then
    echo "✓ Docker 运行正常"
    docker version --format '  Server: {{.Server.Version}}'
else
    echo "✗ Docker 未运行"
    exit 1
fi

# 设置沙箱模式环境变量
export TOOL_EXECUTION_DEFAULT_MODE=SANDBOX
export TOOL_EXECUTION_SANDBOX_ENABLED=true
export TOOL_EXECUTION_LOCAL_ENABLED=true

echo ""
echo "配置:"
echo "  执行模式: SANDBOX"
echo "  临时容器: 启用"
echo ""

# 检查 JAR
JAR_PATH="modules/coding-agent-cli/build/libs/campusclaw-agent-1.0.0-SNAPSHOT.jar"
if [[ ! -f "$JAR_PATH" ]]; then
    echo "JAR 文件不存在，正在构建..."
    ./gradlew :modules:campusclaw-coding-agent:bootJar -x test --no-daemon
fi

echo "启动 CampusClaw 沙箱模式..."
echo "提示: 由于无法拉取 alpine 镜像，沙箱执行会失败，但会显示尝试过程"
echo ""

# 运行（使用测试模式）
java -jar "$JAR_PATH" \
  --exec-mode sandbox \
  --verbose \
  -p "请执行命令: ls -la" 2>&1 | tee /tmp/sandbox-test.log | head -50

echo ""
echo "测试完成！"
echo ""
echo "查看完整日志: cat /tmp/sandbox-test.log"
