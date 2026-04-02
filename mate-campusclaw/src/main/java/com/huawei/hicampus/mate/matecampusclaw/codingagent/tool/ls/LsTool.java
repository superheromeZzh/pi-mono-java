package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ls;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.LsOperations;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.LsOperations.LsEntry;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.PathUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent tool that lists directory contents.
 * Output format: one entry per line with type indicator, size, date, and name.
 * Directories are listed first, then alphabetical within each group.
 */
@Component
public class LsTool implements AgentTool {

    static final int MAX_ENTRIES = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final LsOperations lsOperations;
    private final Path cwd;

    @Autowired
    public LsTool(LsOperations lsOperations) {
        this(lsOperations, Path.of(System.getProperty("user.dir")));
    }

    public LsTool(LsOperations lsOperations, Path cwd) {
        this.lsOperations = lsOperations;
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "ls";
    }

    @Override
    public String label() {
        return "Ls";
    }

    @Override
    public String description() {
        return "List directory contents with file type, size, and modification time.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = MAPPER.createObjectNode();
        props.set("path", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Directory path to list (relative or absolute)"));

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

        Path resolvedPath;
        try {
            resolvedPath = PathUtils.resolveReadPath(pathInput, cwd);
        } catch (SecurityException e) {
            return errorResult("Error: " + e.getMessage());
        }

        if (!Files.isDirectory(resolvedPath)) {
            return errorResult("Error: not a directory: " + pathInput);
        }

        List<LsEntry> entries;
        try {
            entries = lsOperations.list(resolvedPath);
        } catch (IOException e) {
            return errorResult("Error listing directory: " + e.getMessage());
        }

        if (entries.isEmpty()) {
            return textResult("(empty directory)");
        }

        // Sort: directories first, then alphabetical by name within each group
        entries.sort(Comparator
                .comparing((LsEntry e) -> !"directory".equals(e.type()))
                .thenComparing(LsEntry::name, String.CASE_INSENSITIVE_ORDER));

        // Limit results
        boolean truncated = entries.size() > MAX_ENTRIES;
        if (truncated) {
            entries = entries.subList(0, MAX_ENTRIES);
        }

        var sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) sb.append('\n');
            LsEntry entry = entries.get(i);
            String typeFlag = switch (entry.type()) {
                case "directory" -> "drw-";
                case "symlink" -> "lrw-";
                default -> "-rw-";
            };
            String name = entry.name();
            if ("directory".equals(entry.type())) {
                name = name + "/";
            }
            sb.append(String.format("%s %5d  %s  %s",
                    typeFlag,
                    entry.size(),
                    DATE_FORMAT.format(entry.lastModified()),
                    name));
        }

        if (truncated) {
            sb.append("\n... (truncated to ").append(MAX_ENTRIES).append(" entries)");
        }

        return textResult(sb.toString());
    }

    private static AgentToolResult textResult(String text) {
        return new AgentToolResult(
                List.<ContentBlock>of(new TextContent(text)),
                null
        );
    }

    private static AgentToolResult errorResult(String message) {
        return new AgentToolResult(
                List.<ContentBlock>of(new TextContent(message)),
                null
        );
    }
}
