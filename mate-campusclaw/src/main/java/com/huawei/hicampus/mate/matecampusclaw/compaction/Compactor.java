package com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction;

import java.util.*;

import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles context compaction when conversations exceed token limits.
 * Summarizes old messages via LLM and retains only recent context.
 */
public class Compactor {
    private static final Logger log = LoggerFactory.getLogger(Compactor.class);

    private static final String SUMMARIZATION_PROMPT = """
        You are a conversation summarizer. Summarize the conversation so far in a way that preserves:
        1. All important decisions and their rationale
        2. Current state of the task being worked on
        3. Files that have been read or modified and why
        4. Any errors encountered and how they were resolved
        5. Outstanding tasks or next steps

        Be concise but thorough. Focus on information that would be needed to continue the conversation.
        """;

    private final CampusClawAiService aiService;
    private final CompactionConfig config;

    public Compactor(CampusClawAiService aiService, CompactionConfig config) {
        this.aiService = aiService;
        this.config = config;
    }

    public Compactor(CampusClawAiService aiService) {
        this(aiService, CompactionConfig.defaults());
    }

    /**
     * Check if compaction is needed based on estimated token count.
     */
    public boolean needsCompaction(List<Message> messages, int contextWindow) {
        if (!config.enabled()) return false;
        int estimatedTokens = estimateTokens(messages);
        int threshold = contextWindow - config.reserveTokens();
        return estimatedTokens > threshold;
    }

    /**
     * Compact the conversation by summarizing old messages and keeping recent ones.
     */
    public CompactionResult compact(List<Message> messages, Model model) {
        // Extract file operations before compaction
        var fileOps = FileOperationTracker.extract(messages);

        // Split messages: old (to summarize) and recent (to keep)
        int recentTokens = 0;
        int splitIndex = messages.size();
        for (int i = messages.size() - 1; i >= 0; i--) {
            int msgTokens = estimateMessageTokens(messages.get(i));
            if (recentTokens + msgTokens > config.keepRecentTokens()) {
                splitIndex = i + 1;
                break;
            }
            recentTokens += msgTokens;
        }

        // Ensure we keep at least the last message
        if (splitIndex >= messages.size()) {
            splitIndex = Math.max(0, messages.size() - 1);
        }

        List<Message> oldMessages = messages.subList(0, splitIndex);
        List<Message> recentMessages = new ArrayList<>(messages.subList(splitIndex, messages.size()));

        if (oldMessages.isEmpty()) {
            return new CompactionResult("", recentMessages, fileOps.filesRead(), fileOps.filesModified());
        }

        // Generate summary of old messages
        String summary = generateSummary(oldMessages, fileOps, model);

        log.info("Compacted {} messages into summary ({} chars), keeping {} recent messages",
            oldMessages.size(), summary.length(), recentMessages.size());

        return new CompactionResult(summary, recentMessages, fileOps.filesRead(), fileOps.filesModified());
    }

    private String generateSummary(List<Message> messages, FileOperationTracker.FileOperations fileOps, Model model) {
        // Serialize messages to text for summarization
        var sb = new StringBuilder();
        sb.append("Files read: ").append(String.join(", ", fileOps.filesRead())).append("\n");
        sb.append("Files modified: ").append(String.join(", ", fileOps.filesModified())).append("\n\n");
        sb.append("Conversation to summarize:\n\n");

        for (Message msg : messages) {
            sb.append(serializeMessage(msg)).append("\n");
        }

        try {
            var context = new Context(
                SUMMARIZATION_PROMPT,
                List.of(new UserMessage(sb.toString(), System.currentTimeMillis())),
                null
            );

            var result = aiService.completeSimple(model, context, null).block();
            if (result != null) {
                for (var cb : result.content()) {
                    if (cb instanceof TextContent tc) {
                        return tc.text();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("LLM summarization failed, falling back to simple truncation", e);
        }

        // Fallback: simple text extraction
        return "Previous conversation summary (auto-generated):\n" + sb.substring(0, Math.min(sb.length(), 2000));
    }

    private String serializeMessage(Message msg) {
        if (msg instanceof UserMessage um) {
            var sb = new StringBuilder("User: ");
            for (var cb : um.content()) {
                if (cb instanceof TextContent tc) sb.append(tc.text());
            }
            return sb.toString();
        } else if (msg instanceof AssistantMessage am) {
            var sb = new StringBuilder("Assistant: ");
            for (var cb : am.content()) {
                if (cb instanceof TextContent tc) sb.append(tc.text());
                else if (cb instanceof ToolCall tc) sb.append("[Tool: ").append(tc.name()).append("]");
            }
            return sb.toString();
        } else if (msg instanceof ToolResultMessage tr) {
            var sb = new StringBuilder("ToolResult(").append(tr.toolCallId()).append("): ");
            for (var cb : tr.content()) {
                if (cb instanceof TextContent tc) {
                    String text = tc.text();
                    sb.append(text, 0, Math.min(text.length(), 200));
                    if (text.length() > 200) sb.append("...");
                }
            }
            return sb.toString();
        }
        return "";
    }

    /** Rough token estimate: ~4 chars per token. */
    static int estimateTokens(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            total += estimateMessageTokens(msg);
        }
        return total;
    }

    static int estimateMessageTokens(Message msg) {
        int chars = 0;
        List<ContentBlock> content;
        if (msg instanceof UserMessage um) content = um.content();
        else if (msg instanceof AssistantMessage am) content = am.content();
        else if (msg instanceof ToolResultMessage tr) content = tr.content();
        else return 0;

        for (ContentBlock cb : content) {
            if (cb instanceof TextContent tc) chars += tc.text().length();
            else if (cb instanceof ThinkingContent tc) chars += tc.thinking().length();
            else if (cb instanceof ToolCall tc) {
                chars += tc.name().length();
                if (tc.arguments() != null) chars += tc.arguments().toString().length();
            }
        }
        return Math.max(chars / 4, 1);
    }
}
