package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.read;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ImageContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.ReadOperations;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.PathUtils;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.TruncationUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent tool that reads file contents with optional line offset and limit.
 * Detects image files and returns them as {@link ImageContent}.
 * Text files are returned with line numbers and truncated if they exceed limits.
 */
@Component
public class ReadTool implements AgentTool {

    static final int DEFAULT_MAX_BYTES = 32_768;  // 32KB
    static final int DEFAULT_MAX_LINES = 500;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReadOperations readOperations;
    private final Path cwd;

    @Autowired
    public ReadTool(ReadOperations readOperations) {
        this(readOperations, Path.of(System.getProperty("user.dir")));
    }

    public ReadTool(ReadOperations readOperations, Path cwd) {
        this.readOperations = readOperations;
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "read";
    }

    @Override
    public String label() {
        return "Read";
    }

    @Override
    public String description() {
        return "Read the contents of a file. For image files, the content is returned as an image.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = MAPPER.createObjectNode();
        props.set("path", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "The file path to read (relative or absolute)"));
        props.set("offset", MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Starting line number, 1-indexed (optional)"));
        props.set("limit", MAPPER.createObjectNode()
                .put("type", "integer")
                .put("description", "Maximum number of lines to read (optional)"));

        return MAPPER.createObjectNode()
                .put("type", "object")
                .<ObjectNode>set("properties", props)
                .set("required", MAPPER.createArrayNode().add("path"));
    }

    @Override
    public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate
    ) throws Exception {
        String pathInput = (String) params.get("path");
        if (pathInput == null || pathInput.isBlank()) {
            return errorResult("Error: path is required");
        }

        // Resolve and validate the path
        Path resolvedPath;
        try {
            resolvedPath = PathUtils.resolveReadPath(pathInput, cwd);
        } catch (SecurityException e) {
            return errorResult("Error: " + e.getMessage());
        }

        // Check file exists
        if (!readOperations.exists(resolvedPath)) {
            return errorResult("Error: file not found: " + pathInput);
        }

        // Detect image files
        try {
            String mimeType = readOperations.detectMimeType(resolvedPath);
            if (mimeType != null && mimeType.startsWith("image/")) {
                return readImage(resolvedPath, mimeType);
            }
        } catch (IOException ignored) {
            // If MIME detection fails, treat as text
        }

        return readText(resolvedPath, params);
    }

    private AgentToolResult readImage(Path path, String mimeType) throws IOException {
        byte[] data = readOperations.readFile(path);
        String base64 = Base64.getEncoder().encodeToString(data);
        return new AgentToolResult(
                List.<ContentBlock>of(new ImageContent(base64, mimeType)),
                null
        );
    }

    private AgentToolResult readText(Path path, Map<String, Object> params) throws IOException {
        byte[] rawBytes = readOperations.readFile(path);
        String content = new String(rawBytes, StandardCharsets.UTF_8);

        String[] allLines = content.split("\n", -1);
        // Remove trailing empty line from split if content ends with newline
        int totalLines = allLines.length;
        if (content.endsWith("\n") && totalLines > 0) {
            totalLines = totalLines - 1;
        }

        // Parse offset (1-indexed) and limit
        int offset = 1;
        Object offsetParam = params.get("offset");
        if (offsetParam instanceof Number n) {
            offset = Math.max(1, n.intValue());
        }

        Integer limit = null;
        Object limitParam = params.get("limit");
        if (limitParam instanceof Number n) {
            limit = n.intValue();
        }

        // Apply offset and limit to select a window of lines
        int startIdx = offset - 1; // convert to 0-indexed
        int endIdx = limit != null
                ? Math.min(startIdx + limit, totalLines)
                : totalLines;

        if (startIdx >= totalLines) {
            return new AgentToolResult(
                    List.<ContentBlock>of(new TextContent("")),
                    new ReadToolDetails(null)
            );
        }

        // Build numbered output
        var sb = new StringBuilder();
        for (int i = startIdx; i < endIdx; i++) {
            int lineNum = i + 1;
            sb.append(String.format("%6d\t%s", lineNum, allLines[i]));
            if (i < endIdx - 1) {
                sb.append('\n');
            }
        }
        String numberedOutput = sb.toString();

        // Truncate if needed
        TruncationUtils.TruncationResult truncationResult =
                TruncationUtils.truncateTail(numberedOutput, DEFAULT_MAX_LINES, DEFAULT_MAX_BYTES);

        String displayText;
        if (truncationResult.truncated()) {
            displayText = truncateFirstNLines(numberedOutput, truncationResult.outputLines());
        } else {
            displayText = numberedOutput;
        }

        var details = new ReadToolDetails(
                truncationResult.truncated() ? truncationResult : null
        );

        return new AgentToolResult(
                List.<ContentBlock>of(new TextContent(displayText)),
                details
        );
    }

    private static String truncateFirstNLines(String text, int maxLines) {
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

    private static AgentToolResult errorResult(String message) {
        return new AgentToolResult(
                List.<ContentBlock>of(new TextContent(message)),
                null
        );
    }
}
