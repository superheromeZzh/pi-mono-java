package com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.CacheRetention;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Cost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ImageContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.InputModality;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ModelCost;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptionsFactory;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingBudgets;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Tool;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Transport;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

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
