/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.ToolExecutionProperties;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.DockerSandboxClient;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.ResourceLimits;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.SandboxResult;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.sandbox.SandboxSecurityPolicy;

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
        log.info("=== Docker 沙箱测试 ===");

        ToolExecutionProperties props = new ToolExecutionProperties();
        props.setSandboxExecutionEnabled(true);
        props.setUseEphemeralContainers(false);
        props.setDockerHost("unix:///var/run/docker.sock");
        props.setSandboxWorkerImage("alpine:3.19");
        props.setSandboxWorkerMemory("512m");
        props.setSandboxWorkerCpu(1.0);

        SandboxSecurityPolicy policy = new SandboxSecurityPolicy();

        log.info("初始化 Docker 客户端...");
        log.info("配置: useEphemeralContainers = {}", props.isUseEphemeralContainers());

        DockerSandboxClient client = new DockerSandboxClient(props, policy);

        log.info("Docker 可用: {}", client.isAvailable());
        log.info("Worker 容器 ID: {}", client.getWorkerContainerId());

        if (client.isAvailable()) {
            log.info("执行测试命令: echo 'Hello from Sandbox'");
            SandboxResult result =
                    client.execute(java.util.List.of("echo", "Hello from Sandbox"), ResourceLimits.defaults());
            log.info("退出码: {}", result.getExitCode());
            log.info("输出: {}", result.getStdout().trim());
        } else {
            log.warn("沙箱不可用！");
        }

        log.info("按 Enter 键关闭沙箱并退出...");
        System.in.read();

        client.shutdown();
        log.info("沙箱已关闭");
    }
}
