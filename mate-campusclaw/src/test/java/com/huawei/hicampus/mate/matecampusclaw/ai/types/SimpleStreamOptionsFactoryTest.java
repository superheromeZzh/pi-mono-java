/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SimpleStreamOptionsFactoryTest {

    private static Model model(boolean reasoning, int maxTokens) {
        return new Model(
                "id",
                "name",
                Api.ANTHROPIC_MESSAGES,
                Provider.ANTHROPIC,
                null,
                reasoning,
                List.of(),
                null,
                100000,
                maxTokens,
                null,
                null,
                null);
    }

    @Nested
    class FromModel {

        @Test
        void copiesMaxTokensWithoutReasoning() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.fromModel(model(false, 4096));
            assertEquals(4096, o.maxTokens());
            assertNull(o.reasoning());
        }

        @Test
        void enablesReasoningWhenSupported() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.fromModel(model(true, 8192));
            assertEquals(ThinkingLevel.MEDIUM, o.reasoning());
        }
    }

    @Nested
    class Fast {

        @Test
        void cappedAt4096() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.fast(model(false, 16384));
            assertEquals(4096, o.maxTokens());
            assertEquals(0.0, o.temperature());
        }

        @Test
        void respectsSmallerModelMax() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.fast(model(false, 1024));
            assertEquals(1024, o.maxTokens());
        }
    }

    @Nested
    class Thorough {

        @Test
        void copiesMaxTokens() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.thorough(model(false, 8192));
            assertEquals(8192, o.maxTokens());
            assertNull(o.reasoning());
        }

        @Test
        void enablesHighReasoningWhenSupported() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.thorough(model(true, 8192));
            assertEquals(ThinkingLevel.HIGH, o.reasoning());
        }
    }

    @Nested
    class Helpers {

        @Test
        void withApiKey() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.withApiKey("sk-x");
            assertEquals("sk-x", o.apiKey());
        }

        @Test
        void withHeaders() {
            Map<String, String> h = Map.of("X-Foo", "bar");
            SimpleStreamOptions o = SimpleStreamOptionsFactory.withHeaders(h);
            assertEquals(h, o.headers());
        }

        @Test
        void withThinkingEnabledOnSupportedModel() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.withThinking(model(true, 8192), ThinkingLevel.LOW);
            assertEquals(ThinkingLevel.LOW, o.reasoning());
            assertEquals(8192, o.maxTokens());
        }

        @Test
        void withThinkingIgnoredOnNonReasoningModel() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.withThinking(model(false, 8192), ThinkingLevel.HIGH);
            assertNull(o.reasoning());
        }

        @Test
        void withThinkingNullLevel() {
            SimpleStreamOptions o = SimpleStreamOptionsFactory.withThinking(model(true, 8192), null);
            assertNull(o.reasoning());
        }
    }

    @Nested
    class Merge {

        @Test
        void nullBaseReturnsOverrides() {
            SimpleStreamOptions ov =
                    SimpleStreamOptions.builder().maxTokens(100).build();
            assertSame(ov, SimpleStreamOptionsFactory.merge(null, ov));
        }

        @Test
        void nullOverridesReturnsBase() {
            SimpleStreamOptions base =
                    SimpleStreamOptions.builder().maxTokens(100).build();
            assertSame(base, SimpleStreamOptionsFactory.merge(base, null));
        }

        @Test
        void overridesWinPerField() {
            SimpleStreamOptions base = SimpleStreamOptions.builder()
                    .maxTokens(100)
                    .temperature(0.5)
                    .apiKey("base-key")
                    .build();
            SimpleStreamOptions ov =
                    SimpleStreamOptions.builder().maxTokens(200).build();
            SimpleStreamOptions merged = SimpleStreamOptionsFactory.merge(base, ov);
            assertEquals(200, merged.maxTokens()); // override wins
            assertEquals(0.5, merged.temperature()); // base preserved
            assertEquals("base-key", merged.apiKey());
        }

        @Test
        void allFieldsMerged() {
            SimpleStreamOptions base = SimpleStreamOptions.builder()
                    .temperature(0.5)
                    .maxTokens(100)
                    .apiKey("a")
                    .sessionId("s1")
                    .headers(Map.of("h", "1"))
                    .maxRetryDelayMs(1000L)
                    .metadata(Map.of("k", "v"))
                    .reasoning(ThinkingLevel.LOW)
                    .build();
            SimpleStreamOptions ov =
                    SimpleStreamOptions.builder().temperature(0.7).build();
            SimpleStreamOptions merged = SimpleStreamOptionsFactory.merge(base, ov);
            assertEquals(0.7, merged.temperature());
            assertEquals(100, merged.maxTokens());
            assertEquals("a", merged.apiKey());
            assertEquals("s1", merged.sessionId());
            assertNotNull(merged.headers());
            assertEquals(1000L, merged.maxRetryDelayMs());
        }
    }
}
