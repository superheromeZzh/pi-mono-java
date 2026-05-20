/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.Cost;
import com.campusclaw.ai.types.StopReason;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.ai.types.Usage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContextOverflowDetectorTest {

    private static AssistantMessage error(String errorMessage) {
        return new AssistantMessage(
                List.of(new TextContent("", null)),
                "x",
                "x",
                "x",
                null,
                Usage.empty(),
                StopReason.ERROR,
                errorMessage,
                0L);
    }

    private static AssistantMessage stopped(int input, int cacheRead) {
        Usage u = new Usage(input, 0, cacheRead, 0, input + cacheRead, Cost.empty());
        return new AssistantMessage(
                List.of(new TextContent("ok", null)), "x", "x", "x", null, u, StopReason.STOP, null, 0L);
    }

    @Nested
    class ErrorPatterns {

        @Test
        void anthropicPromptTooLong() {
            assertTrue(ContextOverflowDetector.isContextOverflow(error("prompt is too long: 200000 tokens")));
        }

        @Test
        void openAIExceedsContextWindow() {
            assertTrue(ContextOverflowDetector.isContextOverflow(error("This model exceeds the context window")));
        }

        @Test
        void bedrockInputTooLong() {
            assertTrue(ContextOverflowDetector.isContextOverflow(error("Input is too long for requested model")));
        }

        @Test
        void googleInputTokenExceeds() {
            assertTrue(ContextOverflowDetector.isContextOverflow(
                    error("Your input token count of 500000 exceeds the maximum allowed")));
        }

        @Test
        void zaiSilentOverflowDetected() {
            AssistantMessage msg = stopped(10000, 0);
            assertTrue(ContextOverflowDetector.isContextOverflow(msg, 8000));
        }

        @Test
        void zaiPattern() {
            assertTrue(ContextOverflowDetector.isContextOverflow(error("model_context_window_exceeded")));
        }

        @Test
        void mistralPattern() {
            assertTrue(ContextOverflowDetector.isContextOverflow(
                    error("too large for model with 8192 maximum context length")));
        }

        @Test
        void cerebrasBareStatus() {
            assertTrue(ContextOverflowDetector.isContextOverflow(error("400 (no body)")));
            assertTrue(ContextOverflowDetector.isContextOverflow(error("413 status code (no body)")));
        }

        @Test
        void genericTokenLimit() {
            assertTrue(ContextOverflowDetector.isContextOverflow(error("token limit exceeded")));
            assertTrue(ContextOverflowDetector.isContextOverflow(error("Too many tokens")));
        }
    }

    @Nested
    class NonOverflow {

        @Test
        void otherErrorNotOverflow() {
            assertFalse(ContextOverflowDetector.isContextOverflow(error("Rate limit exceeded")));
        }

        @Test
        void nullErrorNotOverflow() {
            assertFalse(ContextOverflowDetector.isContextOverflow(error(null)));
        }

        @Test
        void successWithinWindowNotOverflow() {
            assertFalse(ContextOverflowDetector.isContextOverflow(stopped(1000, 0), 8000));
        }

        @Test
        void successWithoutContextWindow() {
            assertFalse(ContextOverflowDetector.isContextOverflow(stopped(1000, 0), 0));
        }

        @Test
        void noArgConvenienceDefaultsToNoSilentCheck() {
            assertFalse(ContextOverflowDetector.isContextOverflow(stopped(99999, 0)));
        }
    }
}
