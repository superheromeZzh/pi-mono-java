/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class EnumsTest {

    @Nested
    class ApiEnum {

        @Test
        void valueAndFromValue() {
            assertEquals("anthropic-messages", Api.ANTHROPIC_MESSAGES.value());
            assertEquals(Api.OPENAI_COMPLETIONS, Api.fromValue("openai-completions"));
            assertEquals(Api.GOOGLE_VERTEX, Api.fromValue("google-vertex"));
        }

        @Test
        void unknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> Api.fromValue("nope"));
        }
    }

    @Nested
    class StopReasonEnum {

        @Test
        void valueAndFromValue() {
            assertEquals("stop", StopReason.STOP.value());
            assertEquals(StopReason.TOOL_USE, StopReason.fromValue("toolUse"));
            assertEquals(StopReason.ABORTED, StopReason.fromValue("aborted"));
        }

        @Test
        void unknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> StopReason.fromValue("nope"));
        }
    }

    @Nested
    class ThinkingLevelEnum {

        @Test
        void valueAndFromValue() {
            assertEquals("off", ThinkingLevel.OFF.value());
            assertEquals(ThinkingLevel.MEDIUM, ThinkingLevel.fromValue("medium"));
            assertEquals(ThinkingLevel.XHIGH, ThinkingLevel.fromValue("xhigh"));
        }

        @Test
        void unknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> ThinkingLevel.fromValue("nope"));
        }
    }

    @Nested
    class CacheRetentionEnum {

        @Test
        void valueAndFromValue() {
            assertEquals("none", CacheRetention.NONE.value());
            assertEquals(CacheRetention.SHORT, CacheRetention.fromValue("short"));
            assertEquals(CacheRetention.LONG, CacheRetention.fromValue("long"));
        }

        @Test
        void unknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> CacheRetention.fromValue("nope"));
        }
    }

    @Nested
    class TransportEnum {

        @Test
        void valueAndFromValue() {
            assertEquals("sse", Transport.SSE.value());
            assertEquals(Transport.WEBSOCKET, Transport.fromValue("websocket"));
            assertEquals(Transport.AUTO, Transport.fromValue("auto"));
        }

        @Test
        void unknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> Transport.fromValue("nope"));
        }
    }
}
