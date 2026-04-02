package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.editdiff;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.EditOperations;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool that applies edits using unified diff format.
 *
 * <p>Accepts a unified diff (similar to {@code git diff} output) and applies
 * the changes to the target file.
 */
public class EditDiffTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(EditDiffTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@\\s+-([0-9]+)(?:,([0-9]+))?\\s+\\+([0-9]+)(?:,([0-9]+))?\\s+@@");

    private final EditOperations editOps;
    private final JsonNode parametersSchema;

    public EditDiffTool(EditOperations editOps) {
        this.editOps = editOps;
        this.parametersSchema = buildSchema();
    }

    @Override
    public String name() {
        return "EditDiff";
    }

    @Override
    public String label() {
        return "EditDiff";
    }

    @Override
    public String description() {
        return "Apply changes to a file using unified diff format. Provide the file path and the diff content.";
    }

    @Override
    public JsonNode parameters() {
        return parametersSchema;
    }

    @Override
    public AgentToolResult execute(
        String toolCallId,
        Map<String, Object> params,
        CancellationToken signal,
        AgentToolUpdateCallback onUpdate
    ) throws Exception {
        String filePath = (String) params.get("file_path");
        String diff = (String) params.get("diff");

        if (filePath == null || filePath.isBlank()) {
            return new AgentToolResult(List.of(new TextContent("Error: file_path is required")), null);
        }
        if (diff == null || diff.isBlank()) {
            return new AgentToolResult(List.of(new TextContent("Error: diff content is required")), null);
        }

        Path path = Path.of(filePath);
        byte[] bytes = editOps.readFile(path);
        String original = new String(bytes, StandardCharsets.UTF_8);

        List<String> lines = new ArrayList<>(original.lines().toList());
        List<Hunk> hunks = parseHunks(diff);

        if (hunks.isEmpty()) {
            return new AgentToolResult(List.of(new TextContent("Error: No valid hunks found in diff")), null);
        }

        // Apply hunks in reverse order to preserve line numbers
        for (int i = hunks.size() - 1; i >= 0; i--) {
            applyHunk(lines, hunks.get(i));
        }

        String result = String.join("\n", lines);
        if (original.endsWith("\n")) result += "\n";

        editOps.writeFile(path, result);

        String msg = "Applied " + hunks.size() + " hunk(s) to " + filePath;
        return new AgentToolResult(List.of(new TextContent(msg)), null);
    }

    static List<Hunk> parseHunks(String diff) {
        var hunks = new ArrayList<Hunk>();
        var lines = diff.lines().toList();
        int i = 0;

        while (i < lines.size()) {
            String line = lines.get(i);
            Matcher m = HUNK_HEADER.matcher(line);
            if (m.find()) {
                int startLine = Integer.parseInt(m.group(1));
                var removals = new ArrayList<String>();
                var additions = new ArrayList<String>();
                var contextBefore = new ArrayList<String>();
                i++;

                while (i < lines.size()) {
                    String l = lines.get(i);
                    if (l.startsWith("@@") || l.startsWith("---") || l.startsWith("+++")) break;
                    if (l.startsWith("-")) {
                        removals.add(l.substring(1));
                    } else if (l.startsWith("+")) {
                        additions.add(l.substring(1));
                    } else if (l.startsWith(" ") || l.isEmpty()) {
                        if (removals.isEmpty() && additions.isEmpty()) {
                            contextBefore.add(l.startsWith(" ") ? l.substring(1) : l);
                        }
                    }
                    i++;
                }

                hunks.add(new Hunk(startLine - 1, removals, additions, contextBefore));
            } else {
                i++;
            }
        }

        return hunks;
    }

    private void applyHunk(List<String> lines, Hunk hunk) {
        int start = hunk.startLine + hunk.contextBefore.size();
        int removeCount = hunk.removals.size();

        for (int i = 0; i < removeCount && start < lines.size(); i++) {
            lines.remove(start);
        }

        for (int i = hunk.additions.size() - 1; i >= 0; i--) {
            lines.add(start, hunk.additions.get(i));
        }
    }

    private static JsonNode buildSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "file_path": {
                            "type": "string",
                            "description": "The absolute path to the file to modify"
                        },
                        "diff": {
                            "type": "string",
                            "description": "The unified diff content to apply"
                        }
                    },
                    "required": ["file_path", "diff"]
                }
                """);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build EditDiff schema", e);
        }
    }

    record Hunk(int startLine, List<String> removals, List<String> additions, List<String> contextBefore) {}
}
