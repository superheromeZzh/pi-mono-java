/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProviderTest {

    @Nested
    class Value {

        @Test
        void exposesUnderlyingString() {
            assertEquals("anthropic", Provider.ANTHROPIC.value());
            assertEquals("openai", Provider.OPENAI.value());
            assertEquals("amazon-bedrock", Provider.AMAZON_BEDROCK.value());
        }
    }

    @Nested
    class FromValue {

        @Test
        void exactMatch() {
            assertEquals(Provider.ANTHROPIC, Provider.fromValue("anthropic"));
            assertEquals(Provider.OPENAI, Provider.fromValue("openai"));
            assertEquals(Provider.AMAZON_BEDROCK, Provider.fromValue("amazon-bedrock"));
        }

        @Test
        void unknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> Provider.fromValue("not-a-real-provider"));
        }
    }

    @Nested
    class TryFromValue {

        @Test
        void exactMatch() {
            assertTrue(Provider.tryFromValue("anthropic").isPresent());
        }

        @Test
        void caseInsensitive() {
            assertEquals(Provider.ANTHROPIC, Provider.tryFromValue("ANTHROPIC").orElseThrow());
            assertEquals(Provider.OPENAI, Provider.tryFromValue("OpenAI").orElseThrow());
        }

        @Test
        void underscoreDashTolerant() {
            assertEquals(
                    Provider.AMAZON_BEDROCK,
                    Provider.tryFromValue("amazon_bedrock").orElseThrow());
            assertEquals(
                    Provider.AMAZON_BEDROCK,
                    Provider.tryFromValue("AMAZON_BEDROCK").orElseThrow());
        }

        @Test
        void nullAndBlankYieldEmpty() {
            assertTrue(Provider.tryFromValue(null).isEmpty());
            assertTrue(Provider.tryFromValue("").isEmpty());
            assertTrue(Provider.tryFromValue("   ").isEmpty());
        }

        @Test
        void unknownYieldsEmpty() {
            assertTrue(Provider.tryFromValue("mystery").isEmpty());
        }
    }
}
