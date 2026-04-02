package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.TruncationUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent tool that executes bash commands via {@link BashExecutor},
 * truncates combined output, and returns results as {@link TextContent}.
 */
@Component
public class BashTool implements AgentTool {

    /** No default timeout — matches campusclaw behavior (timeout only if explicitly set). */
    static final int NO_TIMEOUT = 0;
    static final int MAX_OUTPUT_LINES = 2000;
    static final int MAX_OUTPUT_BYTES = 100_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BashExecutor bashExecutor;
    private final Path cwd;

    @Autowired
    public BashTool(BashExecutor bashExecutor) {
        this(bashExecutor, Path.of(System.getProperty("user.dir")));
    }

    public BashTool(BashExecutor bashExecutor, Path cwd) {
        this.bashExecutor = bashExecutor;
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String label() {
        return "Bash";
    }

    @Override
    public String description() {
        return "Execute a bash command in the working directory.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = MAPPER.createObjectNode();
        props.set("command", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "The bash command to execute"));
        props.set("timeout", MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Timeout in seconds (optional)"));

        return MAPPER.createObjectNode()
                .put("type", "object")
                .<ObjectNode>set("properties", props)
                .set("required", MAPPER.createArrayNode().add("command"));
    }

    @Override
    public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate
    ) throws Exception {
        String command = (String) params.get("command");
        if (command == null || command.isBlank()) {
            return new AgentToolResult(
                    List.<ContentBlock>of(new TextContent("Error: command is required")),
                    null
            );
        }

        int timeoutSeconds = NO_TIMEOUT;
        Object timeoutParam = params.get("timeout");
        if (timeoutParam instanceof Number n && n.intValue() > 0) {
            timeoutSeconds = n.intValue();
        }

        // Pass current system environment (matching campusclaw behavior)
        var env = System.getenv();

        var options = new BashExecutorOptions(
                timeoutSeconds > 0 ? Duration.ofSeconds(timeoutSeconds) : null,
                signal,
                env.isEmpty() ? null : new java.util.HashMap<>(env)
        );

        BashExecutionResult execResult;
        try {
            execResult = bashExecutor.execute(command, cwd, options);
        } catch (IOException e) {
            return new AgentToolResult(
                    List.<ContentBlock>of(new TextContent("Error executing command: " + e.getMessage())),
                    null
            );
        }

        // Combine stdout and stderr
        String combined = buildCombinedOutput(execResult);

        // Truncate the combined output (tail truncation: keep the first lines)
        TruncationUtils.TruncationResult truncationResult =
                TruncationUtils.truncateTail(combined, MAX_OUTPUT_LINES, MAX_OUTPUT_BYTES);

        String displayText;
        String fullOutputPath = null;

        if (truncationResult.truncated()) {
            displayText = truncateText(combined, truncationResult.outputLines());

            // Write full output to a temp file for later retrieval
            try {
                Path tempFile = Files.createTempFile("bash-output-", ".txt");
                Files.writeString(tempFile, combined, StandardCharsets.UTF_8);
                fullOutputPath = tempFile.toString();
            } catch (IOException ignored) {
                // If we can't write the temp file, just proceed without it
            }
        } else {
            displayText = combined;
        }

        var details = new BashToolDetails(
                truncationResult.truncated() ? truncationResult : null,
                fullOutputPath
        );

        return new AgentToolResult(
                List.<ContentBlock>of(new TextContent(displayText)),
                details
        );
    }

    private static String buildCombinedOutput(BashExecutionResult result) {
        var sb = new StringBuilder();

        if (result.stdout() != null && !result.stdout().isEmpty()) {
            sb.append(result.stdout());
        }

        if (result.stderr() != null && !result.stderr().isEmpty()) {
            if (sb.length() > 0 && !sb.toString().endsWith("\n")) {
                sb.append('\n');
            }
            sb.append(result.stderr());
        }

        if (result.exitCode() == null) {
            if (sb.length() > 0 && !sb.toString().endsWith("\n")) {
                sb.append('\n');
            }
            sb.append("[process timed out or was cancelled]");
        } else if (result.exitCode() != 0) {
            if (sb.length() > 0 && !sb.toString().endsWith("\n")) {
                sb.append('\n');
            }
            sb.append("[exit code: ").append(result.exitCode()).append(']');
        }

        return sb.toString();
    }

    /**
     * Truncates text to the first N lines.
     */
    private static String truncateText(String text, int maxLines) {
        String[] lines = text.split("\n", -1);
        if (lines.length <= maxLines) {
            return text;
        }
        var sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            if (i > 0) sb.append('\n');
            sb.append(lines[i]);
        }
        return sb.toString();
    }
}
