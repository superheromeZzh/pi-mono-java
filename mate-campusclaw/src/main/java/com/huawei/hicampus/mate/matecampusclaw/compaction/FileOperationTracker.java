package com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction;

import java.util.*;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

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
