package com.campusclaw.codingagent.tool.sandbox;

import com.campusclaw.codingagent.config.ToolExecutionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Docker 沙箱客户端 - 使用系统 docker 命令
 * 简化版本，不依赖 docker-java 库
 */
@Slf4j
@Component
public class DockerSandboxClient {

    private final ToolExecutionProperties properties;
    private final SandboxSecurityPolicy securityPolicy;

    private String workerContainerId;
    private boolean dockerAvailable = false;

    @Autowired
    public DockerSandboxClient(ToolExecutionProperties properties,
                                SandboxSecurityPolicy securityPolicy) {
        this.properties = properties;
        this.securityPolicy = securityPolicy;
        initialize();
    }

    /**
     * 初始化 Docker 连接
     */
    private void initialize() {
        if (!properties.isSandboxExecutionEnabled()) {
            log.info("Sandbox execution is disabled");
            return;
        }

        try {
            // 检查 Docker 是否可用
            ProcessResult versionResult = executeDockerCommand(List.of("version", "--format", "{{.Server.Version}}"));
            if (versionResult.exitCode == 0) {
                log.info("Docker available, version: {}", versionResult.stdout.trim());
                dockerAvailable = true;
            } else {
                log.warn("Docker not available: {}", versionResult.stderr);
                return;
            }

            // 如果使用临时容器模式，不需要创建常驻 worker
            if (properties.isUseEphemeralContainers()) {
                log.info("Using ephemeral container mode - worker will be created on demand");
                return;
            }

            // 启动常驻工作容器
            startWorkerContainer();

        } catch (Exception e) {
            log.error("Failed to initialize Docker sandbox", e);
            dockerAvailable = false;
        }
    }

    /**
     * 启动常驻工作容器
     */
    private void startWorkerContainer() {
        try {
            // 先清理可能存在的旧容器
            cleanupOldWorkers();

            String containerName = "campusclaw-worker-" + UUID.randomUUID().toString().substring(0, 8);
            String workspace = properties.getSandboxWorkspacePath();

            List<String> runCmd = List.of(
                "run", "-d",
                "--name", containerName,
                "--network", "none",
                "--memory", properties.getSandboxWorkerMemory(),
                "--cpus", String.valueOf(properties.getSandboxWorkerCpu()),
                "--read-only",
                "--security-opt", "no-new-privileges:true",
                "--cap-drop", "ALL",
                "--cap-add", "SETUID", "--cap-add", "SETGID", "--cap-add", "DAC_OVERRIDE",
                "-v", workspace + ":/workspace",
                "-w", "/workspace",
                properties.getSandboxWorkerImage(),
                "sh", "-c", "while true; do sleep 3600; done"
            );

            ProcessResult result = executeDockerCommand(runCmd);
            if (result.exitCode == 0) {
                workerContainerId = result.stdout.trim();
                log.info("Sandbox worker started: {}", containerName);

                // 安装必要工具
                installWorkerTools();
            } else {
                log.error("Failed to start worker container: {}", result.stderr);
            }

        } catch (Exception e) {
            log.error("Failed to start worker container", e);
        }
    }

    /**
     * 在工作容器中安装必要工具
     */
    private void installWorkerTools() {
        List<String> installCmd = List.of(
            "exec", workerContainerId,
            "sh", "-c",
            "apk add --no-cache bash coreutils grep sed awk curl git 2>/dev/null || " +
            "apt-get update && apt-get install -y bash coreutils grep sed awk curl git 2>/dev/null || true"
        );

        ProcessResult result = executeDockerCommand(installCmd);
        if (result.exitCode != 0) {
            log.warn("Failed to install some tools: {}", result.stderr);
        }
    }

    /**
     * 清理旧的工作容器
     */
    private void cleanupOldWorkers() {
        try {
            ProcessResult listResult = executeDockerCommand(List.of(
                "ps", "-aq", "--filter", "name=campusclaw-worker-"
            ));

            if (listResult.exitCode == 0 && !listResult.stdout.isEmpty()) {
                String[] containers = listResult.stdout.trim().split("\\s+");
                for (String container : containers) {
                    if (!container.isEmpty()) {
                        executeDockerCommand(List.of("rm", "-f", container));
                        log.info("Cleaned up old worker: {}", container.substring(0, 12));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup old workers", e);
        }
    }

    /**
     * 检查 worker 容器是否健康运行
     */
    private boolean isWorkerHealthy() {
        if (workerContainerId == null) {
            return false;
        }
        // 检查容器是否存在且正在运行
        ProcessResult result = executeDockerCommand(List.of(
            "inspect", "-f", "{{.State.Running}}", workerContainerId
        ));
        return result.exitCode == 0 && "true".equals(result.stdout.trim());
    }

    /**
     * 确保 worker 容器可用（如果不存在则重新创建）
     */
    private synchronized void ensureWorkerAvailable() {
        if (!isWorkerHealthy()) {
            log.warn("Worker container not healthy or missing, recreating...");
            workerContainerId = null;
            startWorkerContainer();
        }
    }

    /**
     * 在沙箱中执行命令
     */
    public SandboxResult execute(List<String> command, ResourceLimits limits) {
        if (!dockerAvailable) {
            return SandboxResult.error("Docker sandbox not available", "");
        }

        // 如果使用临时容器模式，则创建临时容器
        if (properties.isUseEphemeralContainers()) {
            return executeWithEphemeralContainer(command, limits);
        }

        // 常驻 Worker 模式：确保 worker 可用
        ensureWorkerAvailable();

        if (workerContainerId == null) {
            return SandboxResult.error("Failed to create sandbox worker container", "");
        }

        return executeWithWorker(command, limits);
    }

    /**
     * 使用常驻 Worker 容器执行
     */
    private SandboxResult executeWithWorker(List<String> command, ResourceLimits limits) {
        long startTime = System.currentTimeMillis();

        try {
            List<String> execCmd = new java.util.ArrayList<>();
            execCmd.add("exec");
            execCmd.add(workerContainerId);
            execCmd.addAll(command);

            ProcessResult result = executeDockerCommand(execCmd, limits.getTimeoutSeconds());

            // 如果执行失败且容器不存在，尝试重新创建并再次执行
            if (result.exitCode != 0 &&
                (result.stderr.contains("No such container") ||
                 result.stderr.contains("is not running"))) {
                log.warn("Worker container lost during execution, attempting recovery...");

                // 重新创建 worker
                workerContainerId = null;
                startWorkerContainer();

                if (workerContainerId != null) {
                    // 重试执行
                    execCmd.set(1, workerContainerId);
                    result = executeDockerCommand(execCmd, limits.getTimeoutSeconds());
                } else {
                    return SandboxResult.error("Failed to recreate worker container", result.stderr);
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;

            if (result.timedOut) {
                return SandboxResult.timeout(limits.getTimeoutSeconds());
            }

            return SandboxResult.builder()
                .stdout(result.stdout)
                .stderr(result.stderr)
                .exitCode(result.exitCode)
                .executionTimeMs(executionTime)
                .build();

        } catch (Exception e) {
            log.error("Failed to execute command in worker", e);
            return SandboxResult.error("Execution failed: " + e.getMessage(), "");
        }
    }

    /**
     * 使用临时容器执行
     */
    private SandboxResult executeWithEphemeralContainer(List<String> command, ResourceLimits limits) {
        long startTime = System.currentTimeMillis();
        String containerName = "campusclaw-temp-" + UUID.randomUUID().toString().substring(0, 8);

        try {
            String workspace = properties.getSandboxWorkspacePath();
            String currentDir = System.getProperty("user.dir");

            // 构建 docker run 命令
            List<String> runCmd = new java.util.ArrayList<>();
            runCmd.add("run");
            runCmd.add("--rm");
            runCmd.add("--name");
            runCmd.add(containerName);
            runCmd.add("-v");
            runCmd.add(currentDir + ":" + workspace);
            runCmd.add("-w");
            runCmd.add(workspace);

            // 资源限制
            if (!properties.getSandboxWorkerMemory().isEmpty()) {
                runCmd.add("--memory");
                runCmd.add(properties.getSandboxWorkerMemory());
            }
            if (properties.getSandboxWorkerCpu() > 0) {
                runCmd.add("--cpus");
                runCmd.add(String.valueOf(properties.getSandboxWorkerCpu()));
            }

            runCmd.add(properties.getSandboxWorkerImage());
            runCmd.addAll(command);

            ProcessResult result = executeDockerCommand(runCmd, limits.getTimeoutSeconds());
            long executionTime = System.currentTimeMillis() - startTime;

            if (result.timedOut) {
                // 清理超时的容器
                executeDockerCommand(List.of("rm", "-f", containerName));
                return SandboxResult.timeout(limits.getTimeoutSeconds());
            }

            return SandboxResult.builder()
                .stdout(result.stdout)
                .stderr(result.stderr)
                .exitCode(result.exitCode)
                .executionTimeMs(executionTime)
                .build();

        } catch (Exception e) {
            log.error("Failed to execute command in ephemeral container", e);
            // 尝试清理
            executeDockerCommand(List.of("rm", "-f", containerName));
            return SandboxResult.error("Execution failed: " + e.getMessage(), "");
        }
    }

    /**
     * 执行 Docker 命令
     */
    private ProcessResult executeDockerCommand(List<String> args) {
        return executeDockerCommand(args, 60);
    }

    private ProcessResult executeDockerCommand(List<String> args, int timeoutSeconds) {
        List<String> fullCmd = new java.util.ArrayList<>();
        fullCmd.add("docker");

        // 添加 -H 参数如果配置了远程主机
        String dockerHost = properties.getDockerHost();
        if (dockerHost != null && !dockerHost.isEmpty() && !dockerHost.equals("unix:///var/run/docker.sock")) {
            fullCmd.add("-H");
            fullCmd.add(dockerHost);
        }

        fullCmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(fullCmd);
        pb.redirectErrorStream(false);

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        boolean timedOut = false;
        int exitCode = -1;

        try {
            Process process = pb.start();

            // 读取输出
            Thread stdoutReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.debug("Error reading stdout", e);
                }
            });

            Thread stderrReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                } catch (IOException e) {
                    log.debug("Error reading stderr", e);
                }
            });

            stdoutReader.start();
            stderrReader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                timedOut = true;
            }

            exitCode = process.exitValue();

            stdoutReader.join(5000);
            stderrReader.join(5000);

        } catch (IOException | InterruptedException e) {
            log.error("Failed to execute docker command", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ProcessResult(-1, "", e.getMessage(), false);
        }

        return new ProcessResult(exitCode, stdout.toString(), stderr.toString(), timedOut);
    }

    /**
     * 检查沙箱是否可用
     */
    public boolean isAvailable() {
        if (!dockerAvailable) {
            return false;
        }
        // 临时容器模式：只要 Docker 可用即可
        if (properties.isUseEphemeralContainers()) {
            return true;
        }
        // 常驻 Worker 模式：检查 worker 是否健康
        return isWorkerHealthy();
    }

    /**
     * 获取工作容器 ID
     */
    public String getWorkerContainerId() {
        return workerContainerId;
    }

    /**
     * 关闭沙箱客户端
     */
    public void shutdown() {
        if (workerContainerId != null) {
            try {
                executeDockerCommand(List.of("rm", "-f", workerContainerId));
                log.info("Sandbox worker stopped: {}", workerContainerId.substring(0, 12));
            } catch (Exception e) {
                log.error("Failed to stop sandbox worker", e);
            }
        }
    }

    private record ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {}
}
