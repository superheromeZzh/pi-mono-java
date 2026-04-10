package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.ToolExecutionProperties;

/**
 * 简单测试：验证常驻容器模式和自动恢复
 */
public class PersistentContainerTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== 常驻容器模式测试 ===\n");

        // 创建配置 - 使用常驻容器模式
        ToolExecutionProperties props = new ToolExecutionProperties();
        props.setSandboxExecutionEnabled(true);
        props.setUseEphemeralContainers(false); // 关键：常驻容器模式
        props.setDockerHost("unix:///var/run/docker.sock");
        props.setSandboxWorkerImage("alpine:3.19");
        props.setSandboxWorkerMemory("512m");
        props.setSandboxWorkerCpu(1.0);

        SandboxSecurityPolicy policy = new SandboxSecurityPolicy();

        System.out.println("1. 初始化 DockerSandboxClient...");
        System.out.println("   useEphemeralContainers = " + props.isUseEphemeralContainers());

        DockerSandboxClient client = new DockerSandboxClient(props, policy);

        System.out.println("   Docker 可用: " + client.isAvailable());
        System.out.println("   Worker ID: " + client.getWorkerContainerId());

        // 测试 1: 执行命令
        if (client.isAvailable()) {
            System.out.println("\n2. 测试执行命令...");
            SandboxResult result = client.execute(
                java.util.List.of("echo", "Hello from persistent container"),
                ResourceLimits.defaults()
            );
            System.out.println("   退出码: " + result.getExitCode());
            System.out.println("   输出: " + result.getStdout().trim());

            // 测试 2: 手动删除容器后自动恢复
            System.out.println("\n3. 测试容器删除后自动恢复...");
            String workerId = client.getWorkerContainerId();
            System.out.println("   当前 Worker ID: " + workerId);

            System.out.println("   手动删除容器...");
            ProcessBuilder pb = new ProcessBuilder("docker", "rm", "-f", workerId);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();

            System.out.println("   等待容器删除...");
            Thread.sleep(1000);

            System.out.println("   再次执行命令（应该自动恢复）...");
            result = client.execute(
                java.util.List.of("echo", "Recovered!"),
                ResourceLimits.defaults()
            );
            System.out.println("   退出码: " + result.getExitCode());
            System.out.println("   输出: " + result.getStdout().trim());
            System.out.println("   新 Worker ID: " + client.getWorkerContainerId());

            if (result.getExitCode() == 0 && "Recovered!".equals(result.getStdout().trim())) {
                System.out.println("\n✓ 自动恢复测试通过!");
            } else {
                System.out.println("\n✗ 自动恢复测试失败!");
                System.out.println("   错误: " + result.getStderr());
            }
        } else {
            System.out.println("\n✗ Docker 沙箱不可用!");
        }

        // 清理
        System.out.println("\n4. 清理...");
        client.shutdown();
        System.out.println("   完成");
    }
}
