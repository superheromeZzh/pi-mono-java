package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.ToolExecutionProperties;

public class SandboxTest {
    public static void main(String[] args) {
        // 创建配置
        ToolExecutionProperties props = new ToolExecutionProperties();
        props.setSandboxExecutionEnabled(true);
        props.setUseEphemeralContainers(false); // 测试常驻容器模式
        props.setDockerHost("unix:///var/run/docker.sock");
        props.setSandboxWorkerImage("alpine:3.19");
        props.setSandboxWorkerMemory("512m");
        props.setSandboxWorkerCpu(1.0);

        // 创建安全策略
        SandboxSecurityPolicy policy = new SandboxSecurityPolicy();

        // 创建 Docker 客户端
        DockerSandboxClient client = new DockerSandboxClient(props, policy);

        System.out.println("Docker 可用: " + client.isAvailable());
        System.out.println("Worker 容器 ID: " + client.getWorkerContainerId());

        // 如果可用，执行一个测试命令
        if (client.isAvailable()) {
            System.out.println("\n执行测试命令: ls -la");
            SandboxResult result = client.execute(
                java.util.List.of("ls", "-la"),
                ResourceLimits.defaults()
            );
            System.out.println("退出码: " + result.getExitCode());
            System.out.println("输出:\n" + result.getStdout());
            if (!result.getStderr().isEmpty()) {
                System.out.println("错误: " + result.getStderr());
            }
        }

        // 关闭
        client.shutdown();
    }
}
