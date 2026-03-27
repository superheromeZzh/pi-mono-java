package com.mariozechner.pi.ai.provider;

import com.mariozechner.pi.ai.types.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Transforms messages for cross-provider compatibility.
 * Handles tool call ID normalization, thinking block conversion,
 * and orphaned tool call result synthesis.
 */
public class MessageTransformer {

    private static final int MAX_TOOL_CALL_ID_LENGTH = 64;

    /**
     * Transform messages for the target API.
     *
     * @param messages  the conversation messages
     * @param targetApi the target API protocol
     * @param sourceApi the source API protocol (from previous model), may be null
     * @return transformed messages
     */
    public static List<Message> transform(List<Message> messages, Api targetApi, Api sourceApi) {
        List<Message> result = new ArrayList<>(messages.size());

        // Track all tool call IDs and result IDs
        Set<String> toolResultIds = new HashSet<>();

        // Collect tool result IDs first so we know which tool calls are orphaned
        for (Message msg : messages) {
            if (msg instanceof ToolResultMessage tr) {
                toolResultIds.add(normalizeToolCallId(tr.toolCallId()));
            }
        }

        // Transform messages: normalize IDs, filter thinking blocks
        Set<String> normalizedToolResultIds = new HashSet<>();
        for (Message msg : messages) {
            if (msg instanceof AssistantMessage am) {
                var transformedContent = new ArrayList<ContentBlock>();
                for (ContentBlock cb : am.content()) {
                    if (cb instanceof ToolCall tc) {
                        String normalizedId = normalizeToolCallId(tc.id());
                        transformedContent.add(new ToolCall(normalizedId, tc.name(), tc.arguments(), tc.thoughtSignature()));
                    } else if (cb instanceof ThinkingContent tc) {
                        // If switching providers, drop thinking blocks with signatures
                        // (encrypted thinking is only valid for the same model)
                        if (sourceApi != targetApi && tc.thinkingSignature() != null && !tc.thinkingSignature().isEmpty()) {
                            continue;
                        }
                        transformedContent.add(cb);
                    } else {
                        transformedContent.add(cb);
                    }
                }

                // Ensure assistant messages are never empty after filtering
                if (transformedContent.isEmpty()) {
                    transformedContent.add(new TextContent(""));
                }

                result.add(new AssistantMessage(
                    transformedContent, am.api(), am.provider(), am.model(),
                    am.responseId(), am.usage(), am.stopReason(), am.errorMessage(), am.timestamp()
                ));
            } else if (msg instanceof ToolResultMessage tr) {
                String normalizedId = normalizeToolCallId(tr.toolCallId());
                normalizedToolResultIds.add(normalizedId);
                result.add(new ToolResultMessage(normalizedId, tr.toolName(), tr.content(), tr.details(), tr.isError(), tr.timestamp()));
            } else {
                result.add(msg);
            }
        }

        // Second pass: insert synthetic results for orphaned tool calls
        List<Message> finalResult = new ArrayList<>();
        for (Message msg : result) {
            finalResult.add(msg);
            if (msg instanceof AssistantMessage am) {
                for (ContentBlock cb : am.content()) {
                    if (cb instanceof ToolCall tc) {
                        if (!normalizedToolResultIds.contains(tc.id())) {
                            // Insert synthetic empty tool result
                            finalResult.add(new ToolResultMessage(
                                tc.id(),
                                tc.name(),
                                List.of(new TextContent("")),
                                null,
                                false,
                                System.currentTimeMillis()
                            ));
                            normalizedToolResultIds.add(tc.id());
                        }
                    }
                }
            }
        }

        return finalResult;
    }

    /**
     * Normalize tool call ID to fit within provider constraints.
     * OpenAI generates very long IDs; Anthropic requires < 64 chars.
     */
    static String normalizeToolCallId(String id) {
        if (id == null || id.length() <= MAX_TOOL_CALL_ID_LENGTH) {
            return id;
        }
        return id.substring(0, MAX_TOOL_CALL_ID_LENGTH);
    }
}
