/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.sandbox;

import com.campusclaw.codingagent.config.ToolExecutionProperties;

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
        log.info("=== persistent container smoke test ===");

        ToolExecutionProperties props = new ToolExecutionProperties();
        props.setSandboxExecutionEnabled(true);
        props.setUseEphemeralContainers(false);
        props.setDockerHost("unix:///var/run/docker.sock");
        props.setSandboxWorkerImage("alpine:3.19");
        props.setSandboxWorkerMemory("512m");
        props.setSandboxWorkerCpu(1.0);

        SandboxSecurityPolicy policy = new SandboxSecurityPolicy();

        log.info("1. initializing DockerSandboxClient");
        log.info("   useEphemeralContainers={}", props.isUseEphemeralContainers());

        DockerSandboxClient client = new DockerSandboxClient(props, policy);

        log.info("   docker available: {}", client.isAvailable());
        log.info("   worker id: {}", client.getWorkerContainerId());

        if (client.isAvailable()) {
            log.info("2. executing test command");
            SandboxResult result = client.execute(
                    java.util.List.of("echo", "Hello from persistent container"), ResourceLimits.defaults());
            log.info("   exit code: {}", result.getExitCode());
            log.info("   stdout: {}", result.getStdout().trim());

            log.info("3. testing auto-recovery after container removal");
            String workerId = client.getWorkerContainerId();
            log.info("   current worker id: {}", workerId);

            log.info("   manually removing container");
            ProcessBuilder pb = new ProcessBuilder("docker", "rm", "-f", workerId);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();

            log.info("   waiting for container removal");
            Thread.sleep(1000);

            log.info("   re-executing command (should auto-recover)");
            result = client.execute(java.util.List.of("echo", "Recovered!"), ResourceLimits.defaults());
            log.info("   exit code: {}", result.getExitCode());
            log.info("   stdout: {}", result.getStdout().trim());
            log.info("   new worker id: {}", client.getWorkerContainerId());

            if (result.getExitCode() == 0
                    && "Recovered!".equals(result.getStdout().trim())) {
                log.info("auto-recovery test passed");
            } else {
                log.error("auto-recovery test failed");
                log.error("   stderr: {}", result.getStderr());
            }
        } else {
            log.error("docker sandbox unavailable");
        }

        log.info("4. cleanup");
        client.shutdown();
        log.info("   done");
    }
}
