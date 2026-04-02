#!/bin/bash
#
# 常驻容器模式测试脚本
#

set -e

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}=== 常驻容器模式测试 ===${NC}\n"

JAR_PATH="modules/coding-agent-cli/build/libs/campusclaw-agent-1.0.0-SNAPSHOT.jar"

# 清理函数
cleanup() {
    echo ""
    echo "清理测试容器..."
    docker ps -aq --filter "name=campusclaw-worker" | xargs -r docker rm -f 2>/dev/null || true
}

# 测试 1: 启动时创建常驻容器
echo "测试 1: 检查启动时是否创建常驻容器"
echo "-----------------------------------"

# 先清理旧容器
docker ps -aq --filter "name=campusclaw-worker" | xargs -r docker rm -f 2>/dev/null || true

echo "启动 CampusClaw（5秒后自动退出）..."
timeout 5 java -jar "$JAR_PATH" --verbose 2>&1 | grep -E "(Sandbox|worker|Docker)" || true

echo ""
echo "检查容器状态:"
docker ps --filter "name=campusclaw-worker" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"

WORKER_COUNT=$(docker ps -q --filter "name=campusclaw-worker" | wc -l)
if [ "$WORKER_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✓ 常驻容器已创建${NC}"
    WORKER_NAME=$(docker ps --filter "name=campusclaw-worker" --format "{{.Names}}")
    echo "  容器名: $WORKER_NAME"
else
    echo -e "${RED}✗ 未找到常驻容器${NC}"
    cleanup
    exit 1
fi

echo ""
echo "测试 2: 手动删除容器后自动恢复"
echo "-----------------------------------"

echo "手动删除 worker 容器..."
docker rm -f "$WORKER_NAME"

echo "检查容器是否被删除:"
WORKER_COUNT=$(docker ps -aq --filter "name=campusclaw-worker" | wc -l)
if [ "$WORKER_COUNT" -eq 0 ]; then
    echo -e "${GREEN}✓ 容器已删除${NC}"
else
    echo -e "${YELLOW}! 容器仍存在，尝试强制删除...${NC}"
    docker ps -aq --filter "name=campusclaw-worker" | xargs docker rm -f
fi

echo ""
echo "重新启动 CampusClaw 测试自动恢复..."
timeout 5 java -jar "$JAR_PATH" --verbose 2>&1 | grep -E "(recreat|recover|healthy)" || true

echo ""
echo "检查新容器是否被创建:"
docker ps --filter "name=campusclaw-worker" --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"

WORKER_COUNT=$(docker ps -q --filter "name=campusclaw-worker" | wc -l)
if [ "$WORKER_COUNT" -gt 0 ]; then
    echo -e "${GREEN}✓ 容器已自动恢复${NC}"
else
    echo -e "${RED}✗ 容器未自动恢复${NC}"
fi

echo ""
echo -e "${GREEN}=== 测试完成 ===${NC}"
echo ""
echo "清理测试容器..."
cleanup
