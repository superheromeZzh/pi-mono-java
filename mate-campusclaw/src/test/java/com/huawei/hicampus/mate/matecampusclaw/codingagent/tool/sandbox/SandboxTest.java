/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.ToolExecutionProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stand-alone manual smoke test for {@link DockerSandboxClient} in long-lived worker mode.
 * Boots a persistent Alpine worker, runs {@code ls -la}, logs stdout/stderr, and shuts the
 * sandbox down. Runs via {@code main} rather than JUnit so it can be invoked outside the
 * regular test suite when verifying Docker integration locally.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SandboxTest {

    private static final Logger log = LoggerFactory.getLogger(SandboxTest.class);

    public static void main(String[] args) {
        ToolExecutionProperties props = new ToolExecutionProperties();
        props.setSandboxExecutionEnabled(true);
        props.setUseEphemeralContainers(false);
        props.setDockerHost("unix:///var/run/docker.sock");
        props.setSandboxWorkerImage("alpine:3.19");
        props.setSandboxWorkerMemory("512m");
        props.setSandboxWorkerCpu(1.0);

        SandboxSecurityPolicy policy = new SandboxSecurityPolicy();

        DockerSandboxClient client = new DockerSandboxClient(props, policy);

        log.info("docker available: {}", client.isAvailable());
        log.info("worker container id: {}", client.getWorkerContainerId());

        if (client.isAvailable()) {
            log.info("executing test command: ls -la");
            SandboxResult result = client.execute(java.util.List.of("ls", "-la"), ResourceLimits.defaults());
            log.info("exit code: {}", result.getExitCode());
            log.info("stdout:\n{}", result.getStdout());
            if (!result.getStderr().isEmpty()) {
                log.warn("stderr: {}", result.getStderr());
            }
        }

        client.shutdown();
    }
}
