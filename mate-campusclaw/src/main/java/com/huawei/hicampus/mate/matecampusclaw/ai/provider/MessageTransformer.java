package com.huawei.hicampus.mate.matecampusclaw.ai.provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

/**
 * Transforms messages for cross-provider compatibility.
 * Handles tool call ID normalization, thinking block conversion,
 * and orphaned tool call result synthesis.
 *
 * <p>Aligned with TypeScript campusclaw transform-messages.ts.
 */
public class MessageTransformer {

    private static final int MAX_TOOL_CALL_ID_LENGTH = 64;

    /**
     * Transform messages for the target model.
     * Uses model's provider, api, and id to determine if thinking blocks can be preserved.
     *
     * @param messages  the conversation messages
     * @param model     the target model
     * @return transformed messages
     */
    public static List<Message> transform(List<Message> messages, Model model) {
        return transform(messages, model.api(), model.provider().value(), model.id());
    }

    /**
     * Transform messages for the target API.
     *
     * @param messages       the conversation messages
     * @param targetApi      the target API protocol
     * @param targetProvider the target provider value
     * @param targetModelId  the target model id
     * @return transformed messages
     */
    public static List<Message> transform(
            List<Message> messages, Api targetApi,
            String targetProvider, String targetModelId) {

        // First pass: transform content blocks
        var transformed = new ArrayList<Message>(messages.size());
        for (Message msg : messages) {
            if (msg instanceof UserMessage) {
                transformed.add(msg);
            } else if (msg instanceof ToolResultMessage tr) {
                String normalizedId = normalizeToolCallId(tr.toolCallId());
                if (!normalizedId.equals(tr.toolCallId())) {
                    transformed.add(new ToolResultMessage(normalizedId, tr.toolName(),
                        tr.content(), tr.details(), tr.isError(), tr.timestamp()));
                } else {
                    transformed.add(msg);
                }
            } else if (msg instanceof AssistantMessage am) {
                boolean isSameModel = targetProvider.equals(am.provider())
                    && targetApi.value().equals(am.api())
                    && targetModelId.equals(am.model());

                var transformedContent = new ArrayList<ContentBlock>();
                for (ContentBlock cb : am.content()) {
                    if (cb instanceof ThinkingContent tc) {
                        // Redacted thinking: only valid for same model
                        if (tc.redacted()) {
                            if (isSameModel) transformedContent.add(cb);
                            continue;
                        }
                        // Same model with signature: keep for replay
                        if (isSameModel && tc.thinkingSignature() != null
                                && !tc.thinkingSignature().isEmpty()) {
                            transformedContent.add(cb);
                            continue;
                        }
                        // Skip empty thinking blocks
                        if (tc.thinking() == null || tc.thinking().isBlank()) continue;
                        // Same model: keep as-is
                        if (isSameModel) {
                            transformedContent.add(cb);
                            continue;
                        }
                        // Cross-model: convert thinking to plain text
                        transformedContent.add(new TextContent(tc.thinking()));
                    } else if (cb instanceof ToolCall tc) {
                        String normalizedId = normalizeToolCallId(tc.id());
                        // Remove thoughtSignature when switching models
                        String sig = isSameModel ? tc.thoughtSignature() : null;
                        if (!normalizedId.equals(tc.id()) || sig != tc.thoughtSignature()) {
                            transformedContent.add(new ToolCall(normalizedId, tc.name(), tc.arguments(), sig));
                        } else {
                            transformedContent.add(cb);
                        }
                    } else if (cb instanceof TextContent tc) {
                        // Strip textSignature when switching models
                        if (!isSameModel && tc.textSignature() != null) {
                            transformedContent.add(new TextContent(tc.text()));
                        } else {
                            transformedContent.add(cb);
                        }
                    } else {
                        transformedContent.add(cb);
                    }
                }

                // Ensure assistant messages are never empty after filtering
                if (transformedContent.isEmpty()) {
                    transformedContent.add(new TextContent(""));
                }

                // Skip errored/aborted assistant messages
                if (am.stopReason() == StopReason.ERROR || am.stopReason() == StopReason.ABORTED) {
                    continue;
                }

                transformed.add(new AssistantMessage(
                    transformedContent, am.api(), am.provider(), am.model(),
                    am.responseId(), am.usage(), am.stopReason(), am.errorMessage(), am.timestamp()
                ));
            }
        }

        // Second pass: insert synthetic results for orphaned tool calls
        List<Message> result = new ArrayList<>();
        var pendingToolCalls = new ArrayList<ToolCall>();
        var existingToolResultIds = new HashSet<String>();

        for (Message msg : transformed) {
            if (msg instanceof AssistantMessage am) {
                // Insert synthetic results for orphans from previous assistant
                insertSyntheticResults(result, pendingToolCalls, existingToolResultIds);
                pendingToolCalls.clear();
                existingToolResultIds.clear();

                // Track tool calls
                for (ContentBlock cb : am.content()) {
                    if (cb instanceof ToolCall tc) {
                        pendingToolCalls.add(tc);
                    }
                }
                result.add(msg);
            } else if (msg instanceof ToolResultMessage tr) {
                existingToolResultIds.add(tr.toolCallId());
                result.add(msg);
            } else if (msg instanceof UserMessage) {
                // User message interrupts tool flow
                insertSyntheticResults(result, pendingToolCalls, existingToolResultIds);
                pendingToolCalls.clear();
                existingToolResultIds.clear();
                result.add(msg);
            } else {
                result.add(msg);
            }
        }

        return result;
    }

    /**
     * Legacy overload for backward compatibility.
     */
    public static List<Message> transform(List<Message> messages, Api targetApi, Api sourceApi) {
        return transform(messages, targetApi, "", "");
    }

    private static void insertSyntheticResults(
            List<Message> result,
            List<ToolCall> pendingToolCalls,
            Set<String> existingToolResultIds) {
        for (ToolCall tc : pendingToolCalls) {
            if (!existingToolResultIds.contains(tc.id())) {
                result.add(new ToolResultMessage(
                    tc.id(),
                    tc.name(),
                    List.of(new TextContent("No result provided")),
                    null,
                    true, // isError = true, matching TS behavior
                    System.currentTimeMillis()
                ));
            }
        }
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
