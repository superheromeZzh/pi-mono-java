package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.grep;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashExecutionResult;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashExecutor;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashExecutorOptions;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.PathUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent tool for searching file contents using regular expressions.
 * Prefers system ripgrep (rg) when available, falls back to a Java implementation.
 */
@Component
public class GrepTool implements AgentTool {

    static final int MAX_RESULTS = 500;
    static final Duration TIMEOUT = Duration.ofSeconds(30);

    /** File type to extensions mapping, matching ripgrep's --type behavior. */
    static final Map<String, Set<String>> TYPE_EXTENSIONS = Map.ofEntries(
            Map.entry("js", Set.of(".js", ".mjs", ".cjs", ".jsx")),
            Map.entry("ts", Set.of(".ts", ".mts", ".cts", ".tsx")),
            Map.entry("py", Set.of(".py", ".pyi")),
            Map.entry("java", Set.of(".java")),
            Map.entry("rb", Set.of(".rb")),
            Map.entry("go", Set.of(".go")),
            Map.entry("rs", Set.of(".rs")),
            Map.entry("c", Set.of(".c", ".h")),
            Map.entry("cpp", Set.of(".cpp", ".cc", ".cxx", ".hpp", ".hh", ".hxx", ".h")),
            Map.entry("cs", Set.of(".cs")),
            Map.entry("swift", Set.of(".swift")),
            Map.entry("kt", Set.of(".kt", ".kts")),
            Map.entry("scala", Set.of(".scala")),
            Map.entry("html", Set.of(".html", ".htm")),
            Map.entry("css", Set.of(".css")),
            Map.entry("json", Set.of(".json")),
            Map.entry("xml", Set.of(".xml")),
            Map.entry("yaml", Set.of(".yaml", ".yml")),
            Map.entry("toml", Set.of(".toml")),
            Map.entry("md", Set.of(".md", ".markdown")),
            Map.entry("sh", Set.of(".sh", ".bash", ".zsh")),
            Map.entry("sql", Set.of(".sql")),
            Map.entry("php", Set.of(".php"))
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final BashExecutor bashExecutor;
    private final Path cwd;
    private volatile Boolean rgAvailable;

    @Autowired
    public GrepTool(BashExecutor bashExecutor) {
        this(bashExecutor, Path.of(System.getProperty("user.dir")));
    }

    public GrepTool(BashExecutor bashExecutor, Path cwd) {
        this.bashExecutor = bashExecutor;
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String label() {
        return "Grep";
    }

    @Override
    public String description() {
        return "Search file contents using regular expressions.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = MAPPER.createObjectNode();
        props.set("pattern", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Regular expression pattern to search for"));
        props.set("path", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "File or directory to search in (optional, defaults to cwd)"));
        props.set("glob", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "Glob pattern to filter files (e.g. \"*.ts\")"));
        props.set("type", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "File type filter (e.g. \"js\", \"py\", \"java\")"));

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
        String glob = (String) params.get("glob");
        String type = (String) params.get("type");

        Path searchPath;
        if (pathInput != null && !pathInput.isBlank()) {
            try {
                searchPath = PathUtils.resolveToCwd(pathInput, cwd);
            } catch (SecurityException e) {
                return errorResult("Error: " + e.getMessage());
            }
        } else {
            searchPath = cwd;
        }

        if (isRgAvailable()) {
            return executeWithRg(pattern, searchPath, glob, type, signal);
        }
        return executeWithJava(pattern, searchPath, glob, type);
    }

    // -------------------------------------------------------------------
    // ripgrep execution
    // -------------------------------------------------------------------

    private AgentToolResult executeWithRg(
            String pattern, Path searchPath, String glob, String type, CancellationToken signal
    ) throws IOException {
        var cmd = new StringBuilder("rg --line-number --no-heading");
        cmd.append(" --max-count ").append(MAX_RESULTS);

        if (glob != null && !glob.isBlank()) {
            cmd.append(" --glob ").append(shellQuote(glob));
        }
        if (type != null && !type.isBlank()) {
            cmd.append(" --type ").append(shellQuote(type));
        }

        cmd.append(' ').append(shellQuote(pattern));
        cmd.append(' ').append(shellQuote(searchPath.toString()));

        var options = new BashExecutorOptions(TIMEOUT, signal, null);
        BashExecutionResult result = bashExecutor.execute(cmd.toString(), cwd, options);

        String output = result.stdout() != null ? result.stdout() : "";

        // rg returns exit code 1 for "no matches" — not an error
        if (result.exitCode() != null && result.exitCode() > 1) {
            String stderr = result.stderr() != null ? result.stderr() : "";
            return errorResult("Grep error: " + stderr.trim());
        }

        if (output.isBlank()) {
            return textResult("No matches found.");
        }

        return textResult(output.strip());
    }

    // -------------------------------------------------------------------
    // Java fallback
    // -------------------------------------------------------------------

    AgentToolResult executeWithJava(
            String patternStr, Path searchPath, String glob, String type
    ) {
        Pattern regex;
        try {
            regex = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            return errorResult("Error: invalid regex pattern: " + e.getMessage());
        }

        Set<String> typeExtensions = null;
        if (type != null && !type.isBlank()) {
            typeExtensions = TYPE_EXTENSIONS.get(type);
            if (typeExtensions == null) {
                return errorResult("Error: unknown file type: " + type);
            }
        }

        PathMatcher globMatcher = null;
        if (glob != null && !glob.isBlank()) {
            FileSystem fs = searchPath.getFileSystem();
            globMatcher = fs.getPathMatcher("glob:" + glob);
        }

        List<String> results = new ArrayList<>();

        if (Files.isRegularFile(searchPath)) {
            searchFile(searchPath, regex, results);
        } else {
            walkAndSearch(searchPath, regex, globMatcher, typeExtensions, results);
        }

        if (results.isEmpty()) {
            return textResult("No matches found.");
        }

        return textResult(String.join("\n", results));
    }

    private void walkAndSearch(
            Path dir, Pattern regex, PathMatcher globMatcher,
            Set<String> typeExtensions, List<String> results
    ) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    String name = d.getFileName().toString();
                    if (name.startsWith(".") || name.equals("node_modules")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return results.size() >= MAX_RESULTS
                            ? FileVisitResult.TERMINATE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= MAX_RESULTS) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (!matchesFilters(file, globMatcher, typeExtensions)) {
                        return FileVisitResult.CONTINUE;
                    }
                    searchFile(file, regex, results);
                    return results.size() >= MAX_RESULTS
                            ? FileVisitResult.TERMINATE
                            : FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best-effort walk
        }
    }

    private static boolean matchesFilters(Path file, PathMatcher globMatcher, Set<String> typeExtensions) {
        if (globMatcher != null && !globMatcher.matches(file.getFileName())) {
            return false;
        }
        if (typeExtensions != null) {
            String fileName = file.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            if (dot < 0) return false;
            String ext = fileName.substring(dot);
            return typeExtensions.contains(ext);
        }
        return true;
    }

    private void searchFile(Path file, Pattern regex, List<String> results) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            Path relative = cwd.relativize(file);
            for (int i = 0; i < lines.size() && results.size() < MAX_RESULTS; i++) {
                Matcher m = regex.matcher(lines.get(i));
                if (m.find()) {
                    results.add(relative + ":" + (i + 1) + ":" + lines.get(i));
                }
            }
        } catch (IOException ignored) {
            // Skip unreadable files (binary, permission issues, etc.)
        }
    }

    // -------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------

    boolean isRgAvailable() {
        if (rgAvailable == null) {
            synchronized (this) {
                if (rgAvailable == null) {
                    rgAvailable = checkRgAvailable();
                }
            }
        }
        return rgAvailable;
    }

    // Visible for testing
    void setRgAvailable(boolean available) {
        this.rgAvailable = available;
    }

    private boolean checkRgAvailable() {
        try {
            BashExecutionResult result = bashExecutor.execute(
                    "command -v rg", cwd,
                    new BashExecutorOptions(Duration.ofSeconds(5), null, null));
            return result.exitCode() != null && result.exitCode() == 0;
        } catch (IOException e) {
            return false;
        }
    }

    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
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
