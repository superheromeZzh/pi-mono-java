/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ls;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.agent.tool.AgentToolResult;
import com.campusclaw.agent.tool.AgentToolUpdateCallback;
import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.codingagent.tool.ops.LsOperations;
import com.campusclaw.codingagent.tool.ops.LsOperations.LsEntry;
import com.campusclaw.codingagent.util.PathUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent tool that lists directory contents.
 * Output format: one entry per line with type indicator, size, date, and name.
 * Directories are listed first, then alphabetical within each group.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
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
        props.set(
                "path",
                MAPPER.createObjectNode()
                        .put("type", "string")
                        .put("description", "Directory path to list (relative or absolute)"));

        return MAPPER.createObjectNode()
                .put("type", "object")
                .<ObjectNode>set("properties", props)
                .set("required", MAPPER.createArrayNode().add("path"));
    }

    @Override
    public AgentToolResult execute(
            String toolCallId, Map<String, Object> params, CancellationToken signal, AgentToolUpdateCallback onUpdate)
            throws Exception {
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
        entries.sort(Comparator.comparing((LsEntry e) -> !"directory".equals(e.type()))
                .thenComparing(LsEntry::name, String.CASE_INSENSITIVE_ORDER));
        boolean truncated = entries.size() > MAX_ENTRIES;
        if (truncated) {
            entries = entries.subList(0, MAX_ENTRIES);
        }
        return textResult(formatEntries(entries, truncated));
    }

    private static String formatEntries(List<LsEntry> entries, boolean truncated) {
        var sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            LsEntry entry = entries.get(i);
            String typeFlag =
                    switch (entry.type()) {
                        case "directory" -> "drw-";
                        case "symlink" -> "lrw-";
                        default -> "-rw-";
                    };
            String name = "directory".equals(entry.type()) ? entry.name() + "/" : entry.name();
            sb.append(String.format(
                    Locale.ROOT,
                    "%s %5d  %s  %s",
                    typeFlag,
                    entry.size(),
                    DATE_FORMAT.format(entry.lastModified()),
                    name));
        }
        if (truncated) {
            sb.append("\n... (truncated to ").append(MAX_ENTRIES).append(" entries)");
        }
        return sb.toString();
    }

    private static AgentToolResult textResult(String text) {
        return new AgentToolResult(List.<ContentBlock>of(new TextContent(text)), null);
    }

    private static AgentToolResult errorResult(String message) {
        return new AgentToolResult(List.<ContentBlock>of(new TextContent(message)), null);
    }
}
