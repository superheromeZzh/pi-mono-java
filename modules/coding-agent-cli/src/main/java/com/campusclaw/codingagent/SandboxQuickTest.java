/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent;

import com.campusclaw.codingagent.config.ToolExecutionProperties;
import com.campusclaw.codingagent.tool.sandbox.DockerSandboxClient;
import com.campusclaw.codingagent.tool.sandbox.ResourceLimits;
import com.campusclaw.codingagent.tool.sandbox.SandboxResult;
import com.campusclaw.codingagent.tool.sandbox.SandboxSecurityPolicy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stand-alone manual smoke test for the long-lived Docker sandbox worker. Configures
 * {@link DockerSandboxClient} with a persistent (non-ephemeral) Alpine worker, runs an
 * {@code echo} command, logs the result, and waits on stdin so the operator can inspect
 * the container before tearing it down. Intended for ad-hoc developer runs, not the CI suite.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SandboxQuickTest {

    private static final Logger log = LoggerFactory.getLogger(SandboxQuickTest.class);

    public static void main(String[] args) throws Exception {
        log.info("=== docker sandbox smoke test ===");

        ToolExecutionProperties props = new ToolExecutionProperties();
        props.setSandboxExecutionEnabled(true);
        props.setUseEphemeralContainers(false);
        props.setDockerHost("unix:///var/run/docker.sock");
        props.setSandboxWorkerImage("alpine:3.19");
        props.setSandboxWorkerMemory("512m");
        props.setSandboxWorkerCpu(1.0);

        SandboxSecurityPolicy policy = new SandboxSecurityPolicy();

        log.info("initializing docker client");
        log.info("config: useEphemeralContainers={}", props.isUseEphemeralContainers());

        DockerSandboxClient client = new DockerSandboxClient(props, policy);

        log.info("docker available: {}", client.isAvailable());
        log.info("worker container id: {}", client.getWorkerContainerId());

        if (client.isAvailable()) {
            log.info("executing test command: echo 'Hello from Sandbox'");
            SandboxResult result =
                    client.execute(java.util.List.of("echo", "Hello from Sandbox"), ResourceLimits.defaults());
            log.info("exit code: {}", result.getExitCode());
            log.info("stdout: {}", result.getStdout().trim());
        } else {
            log.warn("sandbox unavailable");
        }

        log.info("press Enter to shut down the sandbox and exit");
        System.in.read();

        client.shutdown();
        log.info("sandbox shut down");
    }
}
