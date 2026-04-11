package com.campusclaw.codingagent.compaction;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.ToolCall;
import java.util.LinkedHashSet;

/**
 * Extracts file read/write operations from tool call history.
 */
public class FileOperationTracker {

    /** Extract file operations from message history. */
    public static FileOperations extract(List<Message> messages) {
        Set<String> filesRead = new LinkedHashSet<>();
        Set<String> filesModified = new LinkedHashSet<>();

        for (Message msg : messages) {
            if (msg instanceof AssistantMessage am) {
                for (ContentBlock cb : am.content()) {
                    if (cb instanceof ToolCall tc) {
                        String path = getStringArg(tc.arguments(), "path");
                        if (path == null) path = getStringArg(tc.arguments(), "file_path");
                        if (path == null) continue;

                        switch (tc.name()) {
                            case "Read", "read" -> filesRead.add(path);
                            case "Write", "write", "Edit", "edit" -> filesModified.add(path);
                            case "Bash", "bash" -> {
                                // Simple heuristic: bash commands that write to files
                                // can't reliably extract file paths from bash commands
                            }
                            default -> { }
                        }
                    }
                }
            }
        }

        return new FileOperations(filesRead, filesModified);
    }

    private static String getStringArg(Map<String, Object> args, String key) {
        if (args == null) return null;
        Object val = args.get(key);
        return val instanceof String s ? s : null;
    }

    public record FileOperations(Set<String> filesRead, Set<String> filesModified) {}
}
