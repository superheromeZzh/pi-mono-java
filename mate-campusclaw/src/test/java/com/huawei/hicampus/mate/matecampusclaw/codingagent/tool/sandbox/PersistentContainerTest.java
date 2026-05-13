/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.ToolExecutionProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 简单测试：验证常驻容器模式和自动恢复
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class PersistentContainerTest {

    private static final Logger log = LoggerFactory.getLogger(PersistentContainerTest.class);

    public static void main(String[] args) throws Exception {
        log.info("=== 常驻容器模式测试 ===");

        ToolExecutionProperties props = new ToolExecutionProperties();
        props.setSandboxExecutionEnabled(true);
        props.setUseEphemeralContainers(false);
        props.setDockerHost("unix:///var/run/docker.sock");
        props.setSandboxWorkerImage("alpine:3.19");
        props.setSandboxWorkerMemory("512m");
        props.setSandboxWorkerCpu(1.0);

        SandboxSecurityPolicy policy = new SandboxSecurityPolicy();

        log.info("1. 初始化 DockerSandboxClient...");
        log.info("   useEphemeralContainers = {}", props.isUseEphemeralContainers());

        DockerSandboxClient client = new DockerSandboxClient(props, policy);

        log.info("   Docker 可用: {}", client.isAvailable());
        log.info("   Worker ID: {}", client.getWorkerContainerId());

        if (client.isAvailable()) {
            log.info("2. 测试执行命令...");
            SandboxResult result = client.execute(
                    java.util.List.of("echo", "Hello from persistent container"), ResourceLimits.defaults());
            log.info("   退出码: {}", result.getExitCode());
            log.info("   输出: {}", result.getStdout().trim());

            log.info("3. 测试容器删除后自动恢复...");
            String workerId = client.getWorkerContainerId();
            log.info("   当前 Worker ID: {}", workerId);

            log.info("   手动删除容器...");
            ProcessBuilder pb = new ProcessBuilder("docker", "rm", "-f", workerId);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();

            log.info("   等待容器删除...");
            Thread.sleep(1000);

            log.info("   再次执行命令（应该自动恢复）...");
            result = client.execute(java.util.List.of("echo", "Recovered!"), ResourceLimits.defaults());
            log.info("   退出码: {}", result.getExitCode());
            log.info("   输出: {}", result.getStdout().trim());
            log.info("   新 Worker ID: {}", client.getWorkerContainerId());

            if (result.getExitCode() == 0
                    && "Recovered!".equals(result.getStdout().trim())) {
                log.info("✓ 自动恢复测试通过!");
            } else {
                log.error("✗ 自动恢复测试失败!");
                log.error("   错误: {}", result.getStderr());
            }
        } else {
            log.error("✗ Docker 沙箱不可用!");
        }

        log.info("4. 清理...");
        client.shutdown();
        log.info("   完成");
    }
}
