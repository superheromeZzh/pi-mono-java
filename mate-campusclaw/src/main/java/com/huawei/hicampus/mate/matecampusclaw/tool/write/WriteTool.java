package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.write;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops.WriteOperations;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.FileMutationQueue;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.PathUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent tool that creates or overwrites files.
 * Automatically creates parent directories and serializes writes
 * through {@link FileMutationQueue}.
 */
@Component
public class WriteTool implements AgentTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final WriteOperations writeOperations;
    private final FileMutationQueue mutationQueue;
    private final Path cwd;

    @Autowired
    public WriteTool(WriteOperations writeOperations, FileMutationQueue mutationQueue) {
        this(writeOperations, mutationQueue, Path.of(System.getProperty("user.dir")));
    }

    public WriteTool(WriteOperations writeOperations, FileMutationQueue mutationQueue, Path cwd) {
        this.writeOperations = writeOperations;
        this.mutationQueue = mutationQueue;
        this.cwd = cwd;
    }

    @Override
    public String name() {
        return "write";
    }

    @Override
    public String label() {
        return "Write";
    }

    @Override
    public String description() {
        return "Create or overwrite a file with the given content.";
    }

    @Override
    public JsonNode parameters() {
        ObjectNode props = MAPPER.createObjectNode();
        props.set("path", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "The file path to write"));
        props.set("content", MAPPER.createObjectNode()
                .put("type", "string")
                .put("description", "The content to write to the file"));

        return MAPPER.createObjectNode()
                .put("type", "object")
                .<ObjectNode>set("properties", props)
                .set("required", MAPPER.createArrayNode().add("path").add("content"));
    }

    @Override
    public AgentToolResult execute(
            String toolCallId,
            Map<String, Object> params,
            CancellationToken signal,
            AgentToolUpdateCallback onUpdate
    ) throws Exception {
        String pathInput = (String) params.get("path");
        String content = (String) params.get("content");

        if (pathInput == null || pathInput.isBlank()) {
            return errorResult("Error: path is required");
        }
        if (content == null) {
            return errorResult("Error: content is required");
        }

        Path resolvedPath;
        try {
            resolvedPath = PathUtils.resolveToCwd(pathInput, cwd);
        } catch (SecurityException e) {
            return errorResult("Error: " + e.getMessage());
        }

        return mutationQueue.withLock(resolvedPath, () -> {
            Path parent = resolvedPath.getParent();
            if (parent != null) {
                writeOperations.mkdir(parent);
            }
            writeOperations.writeFile(resolvedPath, content);

            return new AgentToolResult(
                    List.<ContentBlock>of(new TextContent("Wrote " + pathInput)),
                    null
            );
        });
    }

    private static AgentToolResult errorResult(String message) {
        return new AgentToolResult(
                List.<ContentBlock>of(new TextContent(message)),
                null
        );
    }
}
