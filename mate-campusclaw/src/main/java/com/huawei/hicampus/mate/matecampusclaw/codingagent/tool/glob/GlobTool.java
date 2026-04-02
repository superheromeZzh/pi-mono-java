package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.glob;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.PathUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent tool for finding files by glob pattern.
 * Results are sorted by modification time (most recent first).
 */
@Component
public class GlobTool implements AgentTool {

    static final int MAX_RESULTS = 1000;

    static final Set<String> EXCLUDED_DIRS = Set.of(
            ".git", ".svn", ".hg",
            "node_modules",
            "build", "dist", "out", "target",
            ".gradle", ".idea", ".vscode",
            "__pycache__", ".tox", ".mypy_cache",
            "vendor", ".bundle"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path cwd;

    @Autowired
    public GlobTool() {
        this(Path.of(System.getProperty("user.dir")));
    }

    public GlobTool(Path cwd) {
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "glob";
    }

    @Override
    public String label() {
        return "Glob";
    }

    @Override
    public String description() {
        return "Find files by glob pattern. Results are sorted by modification time (most recent first).";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = MAPPER.createObjectNode();
        props.set("pattern", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Glob pattern to match files (e.g. \"**/*.java\")"));
        props.set("path", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Starting directory (optional, defaults to cwd)"));

        return MAPPER.createObjectNode()
                .put("type", "object")
                .<ObjectNode>set("properties", props)
                .set("required", MAPPER.createArrayNode().add("pattern"));
    }

    @Override
    public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate
    ) throws Exception {
        String pattern = (String) params.get("pattern");
        if (pattern == null || pattern.isEmpty()) {
            return errorResult("Error: pattern is required");
        }

        String pathInput = (String) params.get("path");
        Path searchRoot;
        if (pathInput != null && !pathInput.isBlank()) {
            try {
                searchRoot = PathUtils.resolveToCwd(pathInput, cwd);
            } catch (SecurityException e) {
                return errorResult("Error: " + e.getMessage());
            }
        } else {
            searchRoot = cwd;
        }

        if (!Files.isDirectory(searchRoot)) {
            return errorResult("Error: not a directory: " + pathInput);
        }

        FileSystem fs = searchRoot.getFileSystem();
        PathMatcher matcher = fs.getPathMatcher("glob:" + pattern);

        List<MatchedFile> matches = new ArrayList<>();

        try {
            Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(searchRoot)) {
                        String dirName = dir.getFileName().toString();
                        if (EXCLUDED_DIRS.contains(dirName) || dirName.startsWith(".")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                    }
                    return matches.size() >= MAX_RESULTS
                            ? FileVisitResult.TERMINATE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (matches.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }
                    Path relative = searchRoot.relativize(file);
                    if (matcher.matches(relative)) {
                        matches.add(new MatchedFile(relative, attrs.lastModifiedTime().toMillis()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return errorResult("Error walking directory: " + e.getMessage());
        }

        if (matches.isEmpty()) {
            return textResult("No files matched.");
        }

        // Sort by modification time, most recent first
        matches.sort(Comparator.comparingLong(MatchedFile::modifiedMillis).reversed());

        var sb = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(matches.get(i).relativePath());
        }

        return textResult(sb.toString());
    }

    private record MatchedFile(Path relativePath, long modifiedMillis) {
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
