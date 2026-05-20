/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.resolver.AgentModelResolver;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CompactorTest {

    @Mock
    CampusClawAiService aiService;

    @Mock
    AgentModelResolver agentModelResolver;

    private static Model model() {
        return new Model(
                "claude-3",
                "Claude",
                Api.ANTHROPIC_MESSAGES,
                Provider.fromValue("anthropic"),
                "https://api.example.com",
                false,
                List.of(),
                null,
                100,
                100,
                null,
                null,
                null);
    }

    private static AssistantMessage assistant(List<ContentBlock> blocks) {
        return new AssistantMessage(
                blocks, "anthropic", "anthropic", "claude", null, Usage.empty(), StopReason.STOP, null, 0L);
    }

    @Nested
    class NeedsCompaction {

        @Test
        void disabledReturnsFalseEvenWhenLarge() {
            CompactionConfig cfg = new CompactionConfig(false, 0, 0);
            Compactor c = new Compactor(aiService, cfg);
            List<Message> big = List.of(new UserMessage("x".repeat(1_000_000), 0));
            assertThat(c.needsCompaction(big, 100)).isFalse();
        }

        @Test
        void underThresholdReturnsFalse() {
            CompactionConfig cfg = new CompactionConfig(true, 0, 20_000);
            Compactor c = new Compactor(aiService, cfg);
            assertThat(c.needsCompaction(List.of(new UserMessage("hi", 0)), 10_000))
                    .isFalse();
        }

        @Test
        void overThresholdReturnsTrue() {
            CompactionConfig cfg = new CompactionConfig(true, 0, 20_000);
            Compactor c = new Compactor(aiService, cfg);
            String big = "a".repeat(4 * 200); // ~200 tokens
            assertThat(c.needsCompaction(List.of(new UserMessage(big, 0)), 100)).isTrue();
        }
    }

    @Nested
    class Compact {

        @Test
        void onlyRecentMessagesYieldsEmptySummary() {
            CompactionConfig cfg = new CompactionConfig(true, 0, 1_000_000);
            Compactor c = new Compactor(aiService, cfg, agentModelResolver);
            List<Message> recent = List.of(new UserMessage("hi", 0));
            CompactionResult result = c.compact(recent, model());
            assertThat(result.summary()).isEmpty();
            assertThat(result.retainedMessages()).hasSize(1);
        }

        @Test
        void summaryFromLlmReturnedWhenAvailable() {
            CompactionConfig cfg = new CompactionConfig(true, 0, 10);
            Compactor c = new Compactor(aiService, cfg, agentModelResolver);
            when(agentModelResolver.resolve(eq("summarizer"), any(Model.class))).thenReturn(model());
            when(aiService.completeSimple(any(Model.class), any(Context.class), any()))
                    .thenReturn(Mono.just(assistant(List.of(new TextContent("CONDENSED", null)))));

            List<Message> msgs = List.of(
                    new UserMessage("first user".repeat(50), 0),
                    new UserMessage("second user".repeat(50), 1),
                    new UserMessage("recent tiny", 2));
            CompactionResult result = c.compact(msgs, model());
            assertThat(result.summary()).contains("CONDENSED");
            assertThat(result.retainedMessages()).isNotEmpty();
        }

        @Test
        void llmFailureFallsBackToTruncatedDigest() {
            CompactionConfig cfg = new CompactionConfig(true, 0, 10);
            Compactor c = new Compactor(aiService, cfg);
            when(aiService.completeSimple(any(Model.class), any(Context.class), any()))
                    .thenThrow(new RuntimeException("network"));

            List<Message> msgs = List.of(new UserMessage("first".repeat(50), 0), new UserMessage("recent", 1));
            CompactionResult result = c.compact(msgs, model());
            assertThat(result.summary()).contains("Previous conversation summary");
        }

        @Test
        void atLeastLastMessageRetained() {
            // keepRecentTokens=0 so loop never accepts anything, splitIndex falls to size-1
            CompactionConfig cfg = new CompactionConfig(true, 0, 0);
            Compactor c = new Compactor(aiService, cfg);
            when(aiService.completeSimple(any(Model.class), any(Context.class), any()))
                    .thenReturn(Mono.just(assistant(List.of(new TextContent("S", null)))));
            List<Message> msgs =
                    List.of(new UserMessage("old1", 0), new UserMessage("old2", 1), new UserMessage("last", 2));
            CompactionResult result = c.compact(msgs, model());
            assertThat(result.retainedMessages()).hasSize(1);
        }
    }

    @Nested
    class TokenEstimation {

        @Test
        void emptyMessageGivesMinimumOne() {
            int tokens = Compactor.estimateMessageTokens(new UserMessage(List.of(), 0));
            assertThat(tokens).isEqualTo(1);
        }

        @Test
        void textTokensApproxCharsOverFour() {
            int tokens =
                    Compactor.estimateMessageTokens(new UserMessage(List.of(new TextContent("a".repeat(40), null)), 0));
            assertThat(tokens).isEqualTo(10);
        }

        @Test
        void assistantWithThinkingAndToolCallCounted() {
            ContentBlock think = new ThinkingContent("a".repeat(40), null, false);
            ContentBlock call = new ToolCall("c1", "search", Map.of("q", "x"));
            AssistantMessage am = new AssistantMessage(
                    List.of(think, call), "x", "x", "x", null, Usage.empty(), StopReason.STOP, null, 0L);
            int tokens = Compactor.estimateMessageTokens(am);
            assertThat(tokens).isGreaterThan(1);
        }

        @Test
        void toolResultCounted() {
            ToolResultMessage tr =
                    new ToolResultMessage("c1", "search", List.of(new TextContent("hello", null)), null, false, 0L);
            int tokens = Compactor.estimateMessageTokens(tr);
            assertThat(tokens).isGreaterThanOrEqualTo(1);
        }

        @Test
        void totalAcrossMessages() {
            int tokens = Compactor.estimateTokens(List.of(
                    new UserMessage(List.of(new TextContent("a".repeat(8), null)), 0),
                    new UserMessage(List.of(new TextContent("b".repeat(8), null)), 0)));
            assertThat(tokens).isGreaterThanOrEqualTo(4);
        }
    }
}
