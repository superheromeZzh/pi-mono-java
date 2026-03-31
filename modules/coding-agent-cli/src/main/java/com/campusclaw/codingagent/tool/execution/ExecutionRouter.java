package com.campusclaw.codingagent.tool.execution;

import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.agent.tool.AgentToolUpdateCallback;
import com.campusclaw.codingagent.config.ToolExecutionProperties;
import com.campusclaw.codingagent.tool.bash.BashTool;
import com.campusclaw.codingagent.tool.edit.EditTool;
import com.campusclaw.codingagent.tool.glob.GlobTool;
import com.campusclaw.codingagent.tool.grep.GrepTool;
import com.campusclaw.codingagent.tool.read.ReadTool;
import com.campusclaw.codingagent.tool.sandbox.*;
import com.campusclaw.codingagent.tool.write.WriteTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 执行路由器 - 根据配置和策略路由到本地或沙箱执行
 */
@Slf4j
@Component
public class ExecutionRouter {

    private final ToolExecutionProperties properties;
    private final SandboxSecurityPolicy securityPolicy;
    private final DockerSandboxClient sandboxClient;

    // 本地工具（使用 Optional，因为 hybrid 模式下可能不存在）
    private final Optional<ReadTool> localReadTool;
    private final Optional<WriteTool> localWriteTool;
    private final Optional<EditTool> localEditTool;
    private final Optional<BashTool> localBashTool;
    private final Optional<GlobTool> localGlobTool;
    private final Optional<GrepTool> localGrepTool;

    // 编译好的正则模式
    private final List<Pattern> sandboxPatterns;
    private final List<Pattern> protectedPathPatterns;

    @Autowired
    public ExecutionRouter(
            ToolExecutionProperties properties,
            SandboxSecurityPolicy securityPolicy,
            DockerSandboxClient sandboxClient,
            Optional<ReadTool> localReadTool,
            Optional<WriteTool> localWriteTool,
            Optional<EditTool> localEditTool,
            Optional<BashTool> localBashTool,
            Optional<GlobTool> localGlobTool,
            Optional<GrepTool> localGrepTool) {

        this.properties = properties;
        this.securityPolicy = securityPolicy;
        this.sandboxClient = sandboxClient;

        this.localReadTool = localReadTool;
        this.localWriteTool = localWriteTool;
        this.localEditTool = localEditTool;
        this.localBashTool = localBashTool;
        this.localGlobTool = localGlobTool;
        this.localGrepTool = localGrepTool;

        // 预编译正则
        this.sandboxPatterns = properties.getSandboxRequiredPatterns().stream()
            .map(Pattern::compile)
            .toList();
        this.protectedPathPatterns = properties.getProtectedPathPatterns().stream()
            .map(Pattern::compile)
            .toList();

        log.info("ExecutionRouter initialized with default mode: {}", properties.getDefaultMode());
    }

    /**
     * 路由执行请求
     */
    public AgentToolResult route(
            String toolName,
            Map<String, Object> params,
            ExecutionMode explicitMode,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate) throws Exception {

        ExecutionMode mode = determineMode(toolName, params, explicitMode);

        if (properties.isExecutionLoggingEnabled()) {
            log.debug("Routing tool [{}] with mode: {}", toolName, mode);
        }

        return switch (mode) {
            case LOCAL -> executeLocal(toolName, params, signal, onUpdate);
            case SANDBOX -> executeSandbox(toolName, params, signal, onUpdate);
            case AUTO -> executeAuto(toolName, params, signal, onUpdate);
        };
    }

    /**
     * 本地执行
     */
    private AgentToolResult executeLocal(String toolName, Map<String, Object> params,
                                          CancellationToken signal,
                                          AgentToolUpdateCallback onUpdate) throws Exception {
        if (!properties.isLocalExecutionEnabled()) {
            throw new IllegalStateException("Local execution is disabled");
        }

        String callId = "local-" + System.currentTimeMillis();

        // 检查本地工具是否可用，不可用则回退到沙箱
        Optional<?> tool = switch (toolName) {
            case "read" -> localReadTool.map(t -> t);
            case "write" -> localWriteTool.map(t -> t);
            case "edit" -> localEditTool.map(t -> t);
            case "bash" -> localBashTool.map(t -> t);
            case "glob" -> localGlobTool.map(t -> t);
            case "grep" -> localGrepTool.map(t -> t);
            default -> Optional.empty();
        };

        if (tool.isEmpty()) {
            log.warn("Local tool {} not available, falling back to sandbox", toolName);
            if (properties.isSandboxExecutionEnabled() && sandboxClient.isAvailable()) {
                return executeSandbox(toolName, params, signal, onUpdate);
            }
            throw new IllegalStateException("Tool " + toolName + " not available locally and sandbox is disabled");
        }

        return switch (toolName) {
            case "read" -> localReadTool.get().execute(callId, params, signal, onUpdate);
            case "write" -> localWriteTool.get().execute(callId, params, signal, onUpdate);
            case "edit" -> localEditTool.get().execute(callId, params, signal, onUpdate);
            case "bash" -> localBashTool.get().execute(callId, params, signal, onUpdate);
            case "glob" -> localGlobTool.get().execute(callId, params, signal, onUpdate);
            case "grep" -> localGrepTool.get().execute(callId, params, signal, onUpdate);
            default -> throw new UnsupportedOperationException("Tool not supported: " + toolName);
        };
    }

    /**
     * 沙箱执行
     */
    private AgentToolResult executeSandbox(String toolName, Map<String, Object> params,
                                            CancellationToken signal,
                                            AgentToolUpdateCallback onUpdate) throws Exception {
        if (!properties.isSandboxExecutionEnabled()) {
            throw new IllegalStateException("Sandbox execution is disabled");
        }

        if (!sandboxClient.isAvailable()) {
            log.warn("Sandbox not available, falling back to local execution");
            return executeLocal(toolName, params, signal, onUpdate);
        }

        // 构建沙箱命令
        List<String> command = buildSandboxCommand(toolName, params);

        ResourceLimits limits = determineResourceLimits(toolName, params);

        SandboxResult result = sandboxClient.execute(command, limits);

        return convertToToolResult(result, toolName);
    }

    /**
     * 自动模式执行
     */
    private AgentToolResult executeAuto(String toolName, Map<String, Object> params,
                                         CancellationToken signal,
                                         AgentToolUpdateCallback onUpdate) throws Exception {
        RiskLevel risk = assessRisk(toolName, params);

        if (properties.isExecutionLoggingEnabled()) {
            log.debug("Risk assessment for [{}]: {}", toolName, risk);
        }

        // 高风险强制沙箱
        if (risk == RiskLevel.HIGH || risk == RiskLevel.CRITICAL) {
            if (properties.isSandboxExecutionEnabled() && sandboxClient.isAvailable()) {
                log.info("High risk operation, using sandbox: {} {}", toolName, params);
                return executeSandbox(toolName, params, signal, onUpdate);
            } else {
                log.warn("High risk operation but sandbox unavailable: {}", toolName);
                // 安全检查
                performSafetyCheck(toolName, params);
            }
        }

        // 低风险使用本地
        if (risk == RiskLevel.LOW) {
            return executeLocal(toolName, params, signal, onUpdate);
        }

        // 中等风险：沙箱可用就用沙箱，否则本地+安全检查
        if (properties.isSandboxExecutionEnabled() && sandboxClient.isAvailable()) {
            return executeSandbox(toolName, params, signal, onUpdate);
        } else {
            performSafetyCheck(toolName, params);
            return executeLocal(toolName, params, signal, onUpdate);
        }
    }

    /**
     * 构建沙箱命令
     */
    private List<String> buildSandboxCommand(String toolName, Map<String, Object> params) {
        return switch (toolName) {
            case "read" -> buildReadCommand(params);
            case "write" -> buildWriteCommand(params);
            case "edit" -> buildEditCommand(params);
            case "bash" -> buildBashCommand(params);
            case "glob" -> buildGlobCommand(params);
            case "grep" -> buildGrepCommand(params);
            default -> throw new UnsupportedOperationException("Tool not supported in sandbox: " + toolName);
        };
    }

    private List<String> buildReadCommand(Map<String, Object> params) {
        String path = (String) params.get("path");
        Object offset = params.get("offset");
        Object limit = params.get("limit");

        String fullPath = "/workspace/" + path;

        if (limit != null) {
            int endLine = (offset != null ? ((Number) offset).intValue() : 1) + ((Number) limit).intValue() - 1;
            return List.of("sed", "-n", offset + "," + endLine + "p", fullPath);
        } else if (offset != null && ((Number) offset).intValue() > 1) {
            return List.of("sed", "-n", offset + ",$p", fullPath);
        }
        return List.of("cat", fullPath);
    }

    private List<String> buildWriteCommand(Map<String, Object> params) {
        String path = (String) params.get("path");
        String content = (String) params.get("content");

        String fullPath = "/workspace/" + path;
        String parentDir = fullPath.contains("/") ?
            fullPath.substring(0, fullPath.lastIndexOf('/')) : "/workspace";

        // 使用 printf 写入，处理特殊字符
        String escaped = content.replace("'", "'\\''");
        return List.of("sh", "-c",
            "mkdir -p '" + parentDir + "' && printf '%s' '" + escaped + "' > '" + fullPath + "'");
    }

    private List<String> buildEditCommand(Map<String, Object> params) {
        String path = (String) params.get("path");
        String oldText = (String) params.get("oldText");
        String newText = (String) params.get("newText");

        String fullPath = "/workspace/" + path;

        // 使用 sed 进行替换（简化版本）
        String escapedOld = oldText.replace("/", "\\/").replace("&", "\\&");
        String escapedNew = newText.replace("/", "\\/").replace("&", "\\&");

        return List.of("sed", "-i", "s/" + escapedOld + "/" + escapedNew + "/g", fullPath);
    }

    private List<String> buildBashCommand(Map<String, Object> params) {
        String command = (String) params.get("command");
        return List.of("sh", "-c", command);
    }

    private List<String> buildGlobCommand(Map<String, Object> params) {
        String pattern = (String) params.get("pattern");
        String path = (String) params.getOrDefault("path", ".");
        return List.of("sh", "-c",
            "cd /workspace/" + path + " && find . -type f -name '" + pattern + "' 2>/dev/null | head -1000");
    }

    private List<String> buildGrepCommand(Map<String, Object> params) {
        String pattern = (String) params.get("pattern");
        String path = (String) params.getOrDefault("path", ".");
        return List.of("sh", "-c",
            "cd /workspace/" + path + " && grep -r -n '" + pattern + "' . 2>/dev/null | head -500");
    }

    /**
     * 确定资源限制
     */
    private ResourceLimits determineResourceLimits(String toolName, Map<String, Object> params) {
        return switch (toolName) {
            case "read", "glob" -> ResourceLimits.readonly();
            case "bash" -> ResourceLimits.highPerformance();
            default -> ResourceLimits.defaults();
        };
    }

    /**
     * 风险评估
     */
    private RiskLevel assessRisk(String toolName, Map<String, Object> params) {
        // 检查命令内容
        if (params.containsKey("command")) {
            String command = (String) params.get("command");

            if (securityPolicy.isDangerousCommand(command)) {
                return RiskLevel.CRITICAL;
            }

            String baseCmd = command.trim().split("\\s+")[0].toLowerCase();
            if (!properties.getLocalSafeCommands().contains(baseCmd)) {
                return RiskLevel.MEDIUM;
            }
        }

        // 检查文件路径
        if (params.containsKey("path")) {
            String path = (String) params.get("path");
            if (securityPolicy.isProtectedPath(path)) {
                return RiskLevel.HIGH;
            }
        }

        // 根据工具类型
        return switch (toolName) {
            case "read", "glob", "grep" -> RiskLevel.LOW;
            case "write", "edit" -> RiskLevel.MEDIUM;
            case "bash", "exec", "process" -> RiskLevel.MEDIUM;
            default -> RiskLevel.HIGH;
        };
    }

    /**
     * 安全检查（本地执行时）
     */
    private void performSafetyCheck(String toolName, Map<String, Object> params) {
        if (params.containsKey("command")) {
            String command = (String) params.get("command");
            securityPolicy.validateCommand(command);
        }
        if (params.containsKey("path")) {
            String path = (String) params.get("path");
            securityPolicy.validatePath(path);
        }
    }

    /**
     * 确定最终执行模式
     */
    private ExecutionMode determineMode(String toolName, Map<String, Object> params,
                                        ExecutionMode explicitMode) {
        // 1. 优先使用显式指定的模式
        if (explicitMode != null && explicitMode != ExecutionMode.AUTO) {
            return explicitMode;
        }

        // 2. 检查参数中是否指定了模式
        Object modeParam = params.get("_executionMode");
        if (modeParam != null) {
            try {
                return ExecutionMode.valueOf(modeParam.toString().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid execution mode: {}", modeParam);
            }
        }

        // 3. 使用全局配置
        return properties.getDefaultMode();
    }

    /**
     * 转换沙箱结果为工具结果
     */
    private AgentToolResult convertToToolResult(SandboxResult result, String toolName) {
        String output = result.getStdout();
        if (!result.getStderr().isEmpty()) {
            output += "\n[stderr]\n" + result.getStderr();
        }
        if (result.getExitCode() != null && result.getExitCode() != 0) {
            output += "\n[exit code: " + result.getExitCode() + "]";
        }
        if (result.getErrorMessage() != null) {
            output = "Error: " + result.getErrorMessage() + "\n" + output;
        }

        return new AgentToolResult(
            List.of(new com.campusclaw.ai.types.TextContent(output)),
            null
        );
    }

    private enum RiskLevel {
        LOW,        // 低风险：读取操作
        MEDIUM,     // 中风险：写入操作、未知命令
        HIGH,       // 高风险：系统路径、网络操作
        CRITICAL    // 极高风险：匹配危险模式
    }
}
