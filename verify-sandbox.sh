#!/bin/bash
#
# 验证 CampusClaw 沙箱模式
#

set -e

echo "=== CampusClaw 沙箱模式验证 ==="
echo ""

# 设置 Docker 环境
if [[ -S "$HOME/.colima/default/docker.sock" ]]; then
    export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
fi

# 1. 检查 Docker
echo "1. 检查 Docker 状态..."
if docker version &>/dev/null; then
    echo "   ✓ Docker 运行正常"
else
    echo "   ✗ Docker 未运行"
    exit 1
fi

# 2. 检查镜像
echo ""
echo "2. 检查沙箱镜像..."
if docker image inspect alpine:3.19 &>/dev/null; then
    echo "   ✓ alpine:3.19 镜像存在"
else
    echo "   ✗ alpine:3.19 镜像不存在"
fi

# 3. 检查容器创建能力
echo ""
echo "3. 测试沙箱容器创建..."
TEST_OUTPUT=$(docker run --rm -v "$PWD:/workspace" -w /workspace alpine:3.19 \
    sh -c "echo 'Sandbox container is working!' && uname -a" 2>&1)

if [[ $? -eq 0 ]]; then
    echo "   ✓ 沙箱容器可以正常创建和运行"
    echo "   容器内系统信息:"
    echo "$TEST_OUTPUT" | grep -E "(working|Linux)" | sed 's/^/     /'
else
    echo "   ✗ 沙箱容器创建失败"
fi

# 4. 检查 CampusClaw JAR
echo ""
echo "4. 检查 CampusClaw 应用..."
JAR_PATH="modules/coding-agent-cli/build/libs/campusclaw-agent-1.0.0-SNAPSHOT.jar"
if [[ -f "$JAR_PATH" ]]; then
    echo "   ✓ JAR 文件存在"
    echo "   路径: $JAR_PATH"
else
    echo "   ✗ JAR 文件不存在，请先编译"
    exit 1
fi

# 5. 检查配置
echo ""
echo "5. 检查沙箱配置..."
if grep -q "hybrid-enabled: true" modules/coding-agent-cli/src/main/resources/application.yml 2>/dev/null; then
    echo "   ✓ 混合执行模式已启用 (hybrid-enabled: true)"
else
    echo "   ⚠ 混合执行模式未启用"
fi

if grep -q "sandbox-execution-enabled: true" modules/coding-agent-cli/src/main/resources/application.yml 2>/dev/null; then
    echo "   ✓ 沙箱执行已启用 (sandbox-execution-enabled: true)"
else
    echo "   ⚠ 沙箱执行未启用"
fi

# 6. 显示运行命令
echo ""
echo "6. 验证完成！"
echo ""
echo "你可以使用以下命令启动沙箱模式："
echo "  ./run-with-sandbox.sh -m k2p5"
echo ""
echo "启动后，在另一个终端运行以下命令查看沙箱容器："
echo "  docker ps -a | grep campusclaw"
echo ""
echo "或者实时监控："
echo "  watch -n 1 'docker ps --format \"table {{.Names}}\\t{{.Image}}\\t{{.Status}}\"'"
echo ""
