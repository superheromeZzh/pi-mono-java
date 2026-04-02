#!/bin/bash
#
# 主动测试 CampusClaw 沙箱模式
# 直接调用工具触发沙箱容器创建
#

set -e

echo "=== CampusClaw 沙箱主动测试 ==="
echo ""

# 设置 Docker 环境
if [[ -S "$HOME/.colima/default/docker.sock" ]]; then
    export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
fi

# 检查 Docker
echo "1. 检查 Docker..."
if ! docker version &>/dev/null; then
    echo "   ✗ Docker 未运行"
    exit 1
fi
echo "   ✓ Docker 运行正常"

# 在后台启动容器监控
echo ""
echo "2. 启动容器监控（持续 30 秒）..."
echo "   请观察是否有 campusclaw-temp-xxx 容器出现"
echo ""

# 后台监控
timeout 30 bash -c '
export DOCKER_HOST="unix:///Users/simon/.colima/default/docker.sock"
while true; do
    CONTAINERS=$(docker ps --format "{{.Names}}" 2>/dev/null | grep campusclaw || true)
    if [[ -n "$CONTAINERS" ]]; then
        echo "[$(date +%H:%M:%S)] 发现容器: $CONTAINERS"
    fi
    sleep 1
done
' &
MONITOR_PID=$!

# 等待监控启动
sleep 2

# 直接测试沙箱客户端
echo "3. 直接测试 DockerSandboxClient..."
echo ""

# 创建测试 Java 程序
cat > /tmp/TestSandbox.java << 'JAVAEOF'
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.ArrayList;

public class TestSandbox {
    public static void main(String[] args) throws Exception {
        System.out.println("测试 Docker 沙箱执行...");

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-v");
        cmd.add(System.getProperty("user.dir") + ":/workspace");
        cmd.add("-w");
        cmd.add("/workspace");
        cmd.add("alpine:3.19");
        cmd.add("sh");
        cmd.add("-c");
        cmd.add("echo 'Hello from Sandbox Container!' && uname -a && date");

        System.out.println("执行命令: " + String.join(" ", cmd));
        System.out.println();

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();

        System.out.println();
        System.out.println("退出码: " + exitCode);
    }
}
JAVAEOF

# 编译并运行测试
cd /tmp
javac TestSandbox.java 2>/dev/null || echo "编译失败，使用直接 docker 命令测试..."

if [[ -f TestSandbox.class ]]; then
    java TestSandbox
else
    # 直接使用 docker 命令测试
    echo "执行: docker run --rm -v $(pwd):/workspace -w /workspace alpine:3.19 sh -c 'echo Hello from Sandbox!'"
    docker run --rm -v "$(pwd):/workspace" -w /workspace alpine:3.19 sh -c "echo 'Hello from Sandbox Container!' && uname -a"
fi

echo ""
echo "4. 等待监控结束..."
wait $MONITOR_PID 2>/dev/null || true

echo ""
echo "=== 测试完成 ==="
echo ""
echo "如果你在步骤 2 中看到了容器，说明沙箱功能正常。"
echo "如果没看到容器，说明 CampusClaw 可能没有触发工具调用。"
echo ""
echo "请确保在 CampusClaw 中实际使用了工具，例如："
echo "  - 要求 AI '读取文件 xxx'"
echo "  - 要求 AI '执行命令 ls'"
echo ""
