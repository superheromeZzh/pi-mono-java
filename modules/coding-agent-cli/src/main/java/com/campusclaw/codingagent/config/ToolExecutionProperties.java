package com.campusclaw.codingagent.config;

import java.util.List;
import java.util.Set;

import com.campusclaw.codingagent.tool.execution.ExecutionMode;

import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 工具执行配置属性
 */
@Slf4j
@Data
@ConfigurationProperties(prefix = "tool.execution")
public class ToolExecutionProperties {

    @PostConstruct
    public void init() {
        // Check for system property override
        String modeOverride = System.getProperty("TOOL_EXECUTION_DEFAULT_MODE");
        if (modeOverride != null && !modeOverride.isEmpty()) {
            try {
                ExecutionMode mode = ExecutionMode.valueOf(modeOverride.toUpperCase());
                this.defaultMode = mode;
                log.info("Tool execution mode overridden by system property: {}", mode);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid execution mode in system property '{}', using default: {}",
                        modeOverride, this.defaultMode);
            }
        }
    }

    /**
     * 全局默认执行模式
     */
    private ExecutionMode defaultMode = ExecutionMode.LOCAL;

    /**
     * 是否允许本地执行（安全开关）
     */
    private boolean localExecutionEnabled = true;

    /**
     * 是否允许沙箱执行
     */
    private boolean sandboxExecutionEnabled = false;

    /**
     * Docker 守护进程地址
     */
    private String dockerHost = "tcp://localhost:2375";

    /**
     * 沙箱工作目录
     */
    private String sandboxWorkspacePath = "/workspace";

    /**
     * 沙箱工作镜像
     * 使用 alpine 镜像，通过启动脚本安装 bash
     */
    private String sandboxWorkerImage = "alpine:3.19";

    /**
     * 沙箱工作容器内存限制
     */
    private String sandboxWorkerMemory = "512m";

    /**
     * 沙箱工作容器 CPU 限制
     */
    private double sandboxWorkerCpu = 1.0;

    /**
     * 强制使用沙箱的命令模式（正则）
     */
    private List<String> sandboxRequiredPatterns = List.of(
        "rm\\s+-rf\\s+/",
        "mkfs\\.",
        "dd\\s+if=/dev/zero",
        ":\\(\\)\\{\\s*:|:&\\s*\\};:",
        "curl\\s+.*\\|.*sh",
        "wget\\s+.*\\|.*sh",
        "eval\\s+.*\\$"
    );

    /**
     * 强制使用沙箱的文件路径模式
     */
    private List<String> protectedPathPatterns = List.of(
        "/etc/.*",
        "/usr/.*",
        "/bin/.*",
        "/sbin/.*",
        "\\.\\./.*",
        "/root/.*",
        "/sys/.*",
        "/proc/.*"
    );

    /**
     * 本地执行的命令白名单（当 mode=AUTO 时）
     */
    private Set<String> localSafeCommands = Set.of(
        "cat", "head", "tail", "grep", "awk", "sed",
        "ls", "pwd", "echo", "wc", "sort", "uniq",
        "find", "which", "whoami", "id",
        "git", "git-status", "git-log", "git-diff", "git-show"
    );

    /**
     * 文件操作大小阈值（超过则使用本地执行更高效）
     * 默认 10MB
     */
    private long localExecutionSizeThreshold = 10 * 1024 * 1024;

    /**
     * 沙箱执行的超时时间（秒）
     */
    private int sandboxTimeoutSeconds = 120;

    /**
     * 本地执行的超时时间（秒）
     */
    private int localTimeoutSeconds = 60;

    /**
     * 是否启用执行日志
     */
    private boolean executionLoggingEnabled = true;

    /**
     * 使用临时容器模式（不创建常驻 worker 容器）
     * 适用于无法拉取镜像的场景
     */
    private boolean useEphemeralContainers = false;
}
