#!/bin/bash
#
# CampusClaw Docker 沙箱运行脚本
# 使用方式: ./run-with-sandbox.sh [选项] [模型]
#

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== CampusClaw Docker 沙箱运行脚本 ===${NC}"

# 检查 Docker 是否安装
if ! command -v docker &> /dev/null; then
    echo -e "${RED}错误: Docker 未安装${NC}"
    echo "请安装 Docker:"
    echo "  macOS: brew install --cask docker"
    echo "  或使用 Docker Desktop: https://www.docker.com/products/docker-desktop"
    exit 1
fi

# 检查 Docker 是否运行
if ! docker info &> /dev/null; then
    echo -e "${YELLOW}警告: Docker 守护进程未运行${NC}"
    echo "尝试启动 Docker..."

    # 尝试启动 colima
    if command -v colima &> /dev/null; then
        echo "检测到 Colima，尝试启动..."
        colima start 2>/dev/null || true
    fi

    # 等待 Docker 启动
    sleep 3

    if ! docker info &> /dev/null; then
        echo -e "${RED}错误: 无法连接到 Docker 守护进程${NC}"
        echo "请手动启动 Docker Desktop 或 Colima:"
        echo "  colima start"
        exit 1
    fi
fi

echo -e "${GREEN}✓ Docker 已就绪${NC}"
docker version --format 'Server Version: {{.Server.Version}}'

# 设置沙箱环境变量
export TOOL_EXECUTION_DEFAULT_MODE="${EXEC_MODE:-SANDBOX}"
export TOOL_EXECUTION_SANDBOX_ENABLED="true"
export TOOL_EXECUTION_LOCAL_ENABLED="true"

# Docker 主机配置
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS - 检测 Colima 或 Docker Desktop
    if [[ -S "$HOME/.colima/default/docker.sock" ]]; then
        export DOCKER_HOST="${DOCKER_HOST:-unix://$HOME/.colima/default/docker.sock}"
    else
        export DOCKER_HOST="${DOCKER_HOST:-unix:///var/run/docker.sock}"
    fi
elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux
    export DOCKER_HOST="${DOCKER_HOST:-unix:///var/run/docker.sock}"
fi

echo ""
echo -e "${GREEN}执行模式: $TOOL_EXECUTION_DEFAULT_MODE${NC}"
echo -e "${GREEN}Docker 主机: $DOCKER_HOST${NC}"
echo ""

# 拉取沙箱镜像（如果不存在）
SANDBOX_IMAGE="alpine:3.19"
if ! docker image inspect "$SANDBOX_IMAGE" &> /dev/null; then
    echo -e "${YELLOW}拉取沙箱镜像 $SANDBOX_IMAGE...${NC}"
    docker pull "$SANDBOX_IMAGE" || echo -e "${YELLOW}警告: 拉取镜像失败，将继续尝试运行${NC}"
fi

# 检查 JAR 文件
JAR_PATH="modules/coding-agent-cli/target/campusclaw-agent.jar"
if [[ ! -f "$JAR_PATH" ]]; then
    echo -e "${YELLOW}JAR 文件不存在，开始构建...${NC}"
    ./mvnw package -DskipTests -q
fi

# 运行 CampusClaw
echo -e "${GREEN}启动 CampusClaw (沙箱模式)...${NC}"
echo ""

# 传递所有参数
java -jar "$JAR_PATH" --exec-mode sandbox "$@"
