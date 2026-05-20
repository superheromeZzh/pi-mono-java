/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MessageTransformerTest {

    private static AssistantMessage assistant(List<ContentBlock> content, String provider, Api api, String model) {
        return new AssistantMessage(
                content, api.value(), provider, model, null, Usage.empty(), StopReason.STOP, null, 0L);
    }

    private static AssistantMessage assistantWith(
            List<ContentBlock> content, String provider, Api api, String model, StopReason stopReason) {
        return new AssistantMessage(content, api.value(), provider, model, null, Usage.empty(), stopReason, null, 0L);
    }

    @Nested
    class NormalizeToolCallId {

        @Test
        void nullReturnsNull() {
            assertNull(MessageTransformer.normalizeToolCallId(null));
        }

        @Test
        void shortIdReturnsAsIs() {
            assertEquals("short_id", MessageTransformer.normalizeToolCallId("short_id"));
        }

        @Test
        void exactly64CharsReturnsAsIs() {
            String id = "a".repeat(64);
            assertEquals(id, MessageTransformer.normalizeToolCallId(id));
        }

        @Test
        void longIdTruncatedTo64() {
            String id = "a".repeat(100);
            String result = MessageTransformer.normalizeToolCallId(id);
            assertEquals(64, result.length());
            assertEquals("a".repeat(64), result);
        }
    }

    @Nested
    class TransformUserMessage {

        @Test
        void userMessagePassesThrough() {
            UserMessage user = new UserMessage(List.of(new TextContent("hi", null)), 1L);
            List<Message> result =
                    MessageTransformer.transform(List.of(user), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            assertEquals(1, result.size());
            assertSame(user, result.get(0));
        }
    }

    @Nested
    class TransformToolResult {

        @Test
        void shortIdPassesThroughUnchanged() {
            ToolResultMessage tr =
                    new ToolResultMessage("call_1", "tool", List.of(new TextContent("ok", null)), null, false, 1L);
            List<Message> result =
                    MessageTransformer.transform(List.of(tr), Api.ANTHROPIC_MESSAGES, "anthropic", "claude");
            assertEquals(1, result.size());
            assertSame(tr, result.get(0));
        }

        @Test
        void longIdNormalizedAndReplaced() {
            String longId = "x".repeat(100);
            ToolResultMessage tr =
                    new ToolResultMessage(longId, "tool", List.of(new TextContent("ok", null)), null, false, 1L);
            List<Message> result =
                    MessageTransformer.transform(List.of(tr), Api.ANTHROPIC_MESSAGES, "anthropic", "claude");
            assertEquals(1, result.size());
            ToolResultMessage out = (ToolResultMessage) result.get(0);
            assertEquals(64, out.toolCallId().length());
            assertNotSame(tr, out);
        }
    }

    @Nested
    class TransformAssistantMessage {

        @Test
        void errorAssistantSkipped() {
            AssistantMessage am = assistantWith(
                    List.of(new TextContent("err", null)),
                    "anthropic",
                    Api.ANTHROPIC_MESSAGES,
                    "claude",
                    StopReason.ERROR);
            List<Message> result =
                    MessageTransformer.transform(List.of(am), Api.ANTHROPIC_MESSAGES, "anthropic", "claude");
            assertTrue(result.isEmpty());
        }

        @Test
        void abortedAssistantSkipped() {
            AssistantMessage am = assistantWith(
                    List.of(new TextContent("a", null)),
                    "anthropic",
                    Api.ANTHROPIC_MESSAGES,
                    "claude",
                    StopReason.ABORTED);
            List<Message> result =
                    MessageTransformer.transform(List.of(am), Api.ANTHROPIC_MESSAGES, "anthropic", "claude");
            assertTrue(result.isEmpty());
        }

        @Test
        void emptyContentBecomesEmptyText() {
            AssistantMessage am = assistant(List.of(), "anthropic", Api.ANTHROPIC_MESSAGES, "claude");
            List<Message> result =
                    MessageTransformer.transform(List.of(am), Api.ANTHROPIC_MESSAGES, "anthropic", "claude");
            assertEquals(1, result.size());
            AssistantMessage out = (AssistantMessage) result.get(0);
            assertEquals(1, out.content().size());
            TextContent tc = (TextContent) out.content().get(0);
            assertEquals("", tc.text());
        }

        @Test
        void textSignatureStrippedOnCrossModel() {
            TextContent withSig = new TextContent("hi", "sig123");
            AssistantMessage am = assistant(List.of(withSig), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result = MessageTransformer.transform(List.of(am), Api.OPENAI_RESPONSES, "openai", "gpt-4");
            AssistantMessage out = (AssistantMessage) result.get(0);
            TextContent tc = (TextContent) out.content().get(0);
            assertEquals("hi", tc.text());
            assertNull(tc.textSignature());
        }

        @Test
        void textSignaturePreservedOnSameModel() {
            TextContent withSig = new TextContent("hi", "sig123");
            AssistantMessage am = assistant(List.of(withSig), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result =
                    MessageTransformer.transform(List.of(am), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            AssistantMessage out = (AssistantMessage) result.get(0);
            TextContent tc = (TextContent) out.content().get(0);
            assertEquals("sig123", tc.textSignature());
        }
    }

    @Nested
    class TransformThinkingBlock {

        @Test
        void redactedDroppedOnCrossModel() {
            ThinkingContent thinking = new ThinkingContent("secret", null, true);
            AssistantMessage am = assistant(List.of(thinking), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result = MessageTransformer.transform(List.of(am), Api.OPENAI_RESPONSES, "openai", "gpt-4");
            AssistantMessage out = (AssistantMessage) result.get(0);
            assertEquals(1, out.content().size());
            assertInstanceOf(TextContent.class, out.content().get(0));
            assertEquals("", ((TextContent) out.content().get(0)).text());
        }

        @Test
        void redactedKeptOnSameModel() {
            ThinkingContent thinking = new ThinkingContent("secret", null, true);
            AssistantMessage am = assistant(List.of(thinking), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result =
                    MessageTransformer.transform(List.of(am), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            AssistantMessage out = (AssistantMessage) result.get(0);
            assertEquals(1, out.content().size());
            assertInstanceOf(ThinkingContent.class, out.content().get(0));
            assertTrue(((ThinkingContent) out.content().get(0)).redacted());
        }

        @Test
        void sameModelWithSignaturePreserved() {
            ThinkingContent thinking = new ThinkingContent("ponder", "sig", false);
            AssistantMessage am = assistant(List.of(thinking), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result =
                    MessageTransformer.transform(List.of(am), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            AssistantMessage out = (AssistantMessage) result.get(0);
            assertInstanceOf(ThinkingContent.class, out.content().get(0));
            assertEquals("sig", ((ThinkingContent) out.content().get(0)).thinkingSignature());
        }

        @Test
        void emptyThinkingDropped() {
            ThinkingContent thinking = new ThinkingContent("   ", null, false);
            AssistantMessage am = assistant(List.of(thinking), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result =
                    MessageTransformer.transform(List.of(am), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            AssistantMessage out = (AssistantMessage) result.get(0);
            assertEquals(1, out.content().size());
            assertInstanceOf(TextContent.class, out.content().get(0));
        }

        @Test
        void sameModelWithoutSignatureKeptAsThinking() {
            ThinkingContent thinking = new ThinkingContent("ponder", null, false);
            AssistantMessage am = assistant(List.of(thinking), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result =
                    MessageTransformer.transform(List.of(am), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            AssistantMessage out = (AssistantMessage) result.get(0);
            assertInstanceOf(ThinkingContent.class, out.content().get(0));
        }

        @Test
        void crossModelThinkingConvertedToText() {
            ThinkingContent thinking = new ThinkingContent("ponder", null, false);
            AssistantMessage am = assistant(List.of(thinking), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result = MessageTransformer.transform(List.of(am), Api.OPENAI_RESPONSES, "openai", "gpt-4");
            AssistantMessage out = (AssistantMessage) result.get(0);
            assertEquals(1, out.content().size());
            assertInstanceOf(TextContent.class, out.content().get(0));
            assertEquals("ponder", ((TextContent) out.content().get(0)).text());
        }
    }

    @Nested
    class TransformToolCall {

        @Test
        void longIdNormalized() {
            String longId = "y".repeat(100);
            ToolCall tc = new ToolCall(longId, "search", Map.of("q", "x"), null);
            AssistantMessage am = assistant(List.of(tc), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            UserMessage user = new UserMessage(List.of(new TextContent("next", null)), 2L);
            List<Message> result =
                    MessageTransformer.transform(List.of(am, user), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            AssistantMessage out = (AssistantMessage) result.get(0);
            ToolCall normalized = (ToolCall) out.content().get(0);
            assertEquals(64, normalized.id().length());
            ToolResultMessage synthetic = (ToolResultMessage) result.get(1);
            assertEquals("y".repeat(64), synthetic.toolCallId());
        }

        @Test
        void thoughtSignatureStrippedOnCrossModel() {
            ToolCall tc = new ToolCall("c1", "search", Map.of(), "sig");
            AssistantMessage am = assistant(List.of(tc), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result = MessageTransformer.transform(List.of(am), Api.OPENAI_RESPONSES, "openai", "gpt-4");
            AssistantMessage out = (AssistantMessage) result.get(0);
            ToolCall outCall = (ToolCall) out.content().get(0);
            assertNull(outCall.thoughtSignature());
        }

        @Test
        void thoughtSignatureKeptOnSameModel() {
            ToolCall tc = new ToolCall("c1", "search", Map.of(), "sig");
            AssistantMessage am = assistant(List.of(tc), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result =
                    MessageTransformer.transform(List.of(am), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            AssistantMessage out = (AssistantMessage) result.get(0);
            ToolCall outCall = (ToolCall) out.content().get(0);
            assertEquals("sig", outCall.thoughtSignature());
            assertSame(tc, outCall);
        }
    }

    @Nested
    class FillOrphanToolResults {

        @Test
        void toolCallWithoutResultGetsSyntheticError() {
            ToolCall tc = new ToolCall("c1", "search", Map.of(), null);
            AssistantMessage am = assistant(List.of(tc), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            UserMessage user = new UserMessage(List.of(new TextContent("next", null)), 2L);
            List<Message> result =
                    MessageTransformer.transform(List.of(am, user), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            assertEquals(3, result.size());
            assertInstanceOf(AssistantMessage.class, result.get(0));
            ToolResultMessage synth = (ToolResultMessage) result.get(1);
            assertEquals("c1", synth.toolCallId());
            assertTrue(synth.isError());
            assertInstanceOf(UserMessage.class, result.get(2));
        }

        @Test
        void toolCallWithExistingResultNotDuplicated() {
            ToolCall tc = new ToolCall("c1", "search", Map.of(), null);
            AssistantMessage am = assistant(List.of(tc), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            ToolResultMessage existing =
                    new ToolResultMessage("c1", "search", List.of(new TextContent("res", null)), null, false, 2L);
            List<Message> result = MessageTransformer.transform(
                    List.of(am, existing), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            assertEquals(2, result.size());
            ToolResultMessage out = (ToolResultMessage) result.get(1);
            assertFalse(out.isError());
        }

        @Test
        void syntheticInsertedAtEndIfNoTrailingUser() {
            ToolCall tc = new ToolCall("c1", "search", Map.of(), null);
            AssistantMessage am = assistant(List.of(tc), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            AssistantMessage am2 =
                    assistant(List.of(new TextContent("hello", null)), "anthropic", Api.ANTHROPIC_MESSAGES, "claude-3");
            List<Message> result =
                    MessageTransformer.transform(List.of(am, am2), Api.ANTHROPIC_MESSAGES, "anthropic", "claude-3");
            assertEquals(3, result.size());
            assertInstanceOf(ToolResultMessage.class, result.get(1));
        }
    }

    @Nested
    class ConvenienceOverloads {

        @Test
        void modelOverload() {
            com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider provider = com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider.fromValue("anthropic");
            com.huawei.hicampus.mate.matecampusclaw.ai.types.Model model = new com.huawei.hicampus.mate.matecampusclaw.ai.types.Model(
                    "claude-3",
                    "Claude 3",
                    Api.ANTHROPIC_MESSAGES,
                    provider,
                    "https://api.anthropic.com",
                    false,
                    List.of(),
                    null,
                    0,
                    0,
                    null,
                    null,
                    null);
            UserMessage user = new UserMessage(List.of(new TextContent("hi", null)), 1L);
            List<Message> result = MessageTransformer.transform(List.of(user), model);
            assertEquals(1, result.size());
        }

        @Test
        void legacyApiOnlyOverload() {
            UserMessage user = new UserMessage(List.of(new TextContent("hi", null)), 1L);
            List<Message> result =
                    MessageTransformer.transform(List.of(user), Api.OPENAI_RESPONSES, Api.ANTHROPIC_MESSAGES);
            assertEquals(1, result.size());
        }
    }
}
