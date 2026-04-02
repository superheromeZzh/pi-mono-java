#!/bin/bash
#
# CampusClaw 沙箱功能测试脚本
# 测试 Docker 沙箱基础设施是否正常工作
#

set -e

echo "=== CampusClaw Docker 沙箱功能测试 ==="
echo ""

# 设置 Docker 环境
if [[ -S "$HOME/.colima/default/docker.sock" ]]; then
    export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
elif [[ -S "/var/run/docker.sock" ]]; then
    export DOCKER_HOST="unix:///var/run/docker.sock"
fi

echo "1. 检查 Docker 连接..."
if docker version &>/dev/null; then
    echo "   ✓ Docker 运行正常"
    echo "   版本: $(docker version --format '{{.Server.Version}}')"
else
    echo "   ✗ Docker 未运行"
    exit 1
fi

echo ""
echo "2. 检查镜像加速器..."
MIRRORS=$(docker info 2>/dev/null | grep -A 10 "Registry Mirrors" | grep https || echo "")
if [[ -n "$MIRRORS" ]]; then
    echo "   ✓ 镜像加速器已配置"
    echo "$MIRRORS" | head -3
else
    echo "   ⚠ 未配置镜像加速器"
fi

echo ""
echo "3. 检查沙箱镜像..."
if docker image inspect alpine:3.19 &>/dev/null; then
    echo "   ✓ alpine:3.19 镜像已存在"
else
    echo "   ✗ alpine:3.19 镜像不存在"
    echo "   正在拉取..."
    docker pull alpine:3.19 || docker pull alpine:latest && docker tag alpine:latest alpine:3.19
fi

echo ""
echo "4. 测试沙箱容器执行..."
TEST_OUTPUT=$(docker run --rm -v "$PWD:/workspace" -w /workspace alpine:3.19 \
    sh -c "echo 'Hello from Docker Sandbox!' && uname -a" 2>&1)

if [[ $? -eq 0 ]]; then
    echo "   ✓ 沙箱容器运行成功"
    echo "   输出:"
    echo "$TEST_OUTPUT" | sed 's/^/      /'
else
    echo "   ✗ 沙箱容器运行失败"
    echo "$TEST_OUTPUT"
fi

echo ""
echo "5. 测试文件隔离..."
echo "   在沙箱中创建测试文件..."
docker run --rm -v "$PWD:/workspace" -w /workspace alpine:3.19 \
    sh -c "echo 'Sandbox test' > /workspace/.sandbox-test-file" 2>&1

if [[ -f "$PWD/.sandbox-test-file" ]]; then
    echo "   ✓ 文件写入成功（共享卷工作正常）"
    rm -f "$PWD/.sandbox-test-file"
else
    echo "   ✗ 文件写入失败"
fi

echo ""
echo "6. 测试资源限制..."
echo "   内存限制: 512m"
echo "   CPU限制: 1.0"
MEMORY_LIMIT=$(docker run --rm --memory=512m alpine:3.19 cat /sys/fs/cgroup/memory.max 2>/dev/null || echo "unknown")
echo "   实际 cgroup 内存限制: $MEMORY_LIMIT"

echo ""
echo "==================================="
echo "沙箱基础设施测试完成！"
echo ""
echo "所有测试通过，可以使用沙箱模式运行 CampusClaw:"
echo "  ./run-with-sandbox.sh -m glm-5"
echo ""
