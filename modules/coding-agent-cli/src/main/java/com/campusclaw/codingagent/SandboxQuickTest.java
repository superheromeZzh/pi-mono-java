package com.campusclaw.codingagent;

import com.campusclaw.codingagent.config.ToolExecutionProperties;
import com.campusclaw.codingagent.tool.sandbox.*;

public class SandboxQuickTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== Docker 沙箱测试 ===\n");

        // 创建配置 - 使用常驻容器模式
        ToolExecutionProperties props = new ToolExecutionProperties();
        props.setSandboxExecutionEnabled(true);
        props.setUseEphemeralContainers(false); // 关键：测试常驻容器模式
        props.setDockerHost("unix:///var/run/docker.sock");
        props.setSandboxWorkerImage("alpine:3.19");
        props.setSandboxWorkerMemory("512m");
        props.setSandboxWorkerCpu(1.0);

        // 创建安全策略
        SandboxSecurityPolicy policy = new SandboxSecurityPolicy();

        System.out.println("初始化 Docker 客户端...");
        System.out.println("配置: useEphemeralContainers = " + props.isUseEphemeralContainers());

        // 创建 Docker 客户端
        DockerSandboxClient client = new DockerSandboxClient(props, policy);

        System.out.println("\nDocker 可用: " + client.isAvailable());
        System.out.println("Worker 容器 ID: " + client.getWorkerContainerId());

        // 如果可用，执行一个测试命令
        if (client.isAvailable()) {
            System.out.println("\n执行测试命令: echo 'Hello from Sandbox'");
            SandboxResult result = client.execute(
                java.util.List.of("echo", "Hello from Sandbox"),
                ResourceLimits.defaults()
            );
            System.out.println("退出码: " + result.getExitCode());
            System.out.println("输出: " + result.getStdout().trim());
        } else {
            System.out.println("\n沙箱不可用！");
        }

        // 保持运行，方便检查容器状态
        System.out.println("\n按 Enter 键关闭沙箱并退出...");
        System.in.read();

        // 关闭
        client.shutdown();
        System.out.println("沙箱已关闭");
    }
}
