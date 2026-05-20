/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.footer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.campusclaw.ai.types.Api;
import com.campusclaw.ai.types.Cost;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.ai.types.ThinkingLevel;
import com.campusclaw.ai.types.Usage;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FooterDataProviderTest {

    private static Model model() {
        return new Model(
                "m",
                "MyModel",
                Api.ANTHROPIC_MESSAGES,
                Provider.ANTHROPIC,
                null,
                false,
                List.of(),
                null,
                0,
                0,
                null,
                null,
                null);
    }

    @Nested
    class Defaults {

        @Test
        void defaultModelName() {
            FooterDataProvider p = new FooterDataProvider();
            assertThat(p.getFooterData().modelName()).isEqualTo("unknown");
            assertThat(p.getFooterData().isStreaming()).isFalse();
            assertThat(p.getFooterData().hints()).isNotEmpty();
        }
    }

    @Nested
    class Mutation {

        @Test
        void setModelUpdatesName() {
            FooterDataProvider p = new FooterDataProvider();
            p.setModel(model());
            assertThat(p.getFooterData().modelName()).isEqualTo("MyModel");
            assertThat(p.getFooterData().providerName()).isEqualTo("anthropic");
        }

        @Test
        void setThinkingLevel() {
            FooterDataProvider p = new FooterDataProvider();
            p.setThinkingLevel(ThinkingLevel.MEDIUM);
            assertThat(p.getFooterData().thinkingLevel()).isEqualTo(ThinkingLevel.MEDIUM);
        }

        @Test
        void updateUsage() {
            FooterDataProvider p = new FooterDataProvider();
            Usage u = new Usage(100, 200, 0, 0, 300, Cost.empty());
            p.updateUsage(u);
            assertThat(p.getFooterData().tokenStats().inputTokens()).isEqualTo(100);
            assertThat(p.getFooterData().tokenStats().outputTokens()).isEqualTo(200);
        }

        @Test
        void updateSession() {
            FooterDataProvider p = new FooterDataProvider();
            p.updateSession(3, 8, 12345L);
            assertThat(p.getFooterData().sessionStats().turnCount()).isEqualTo(3);
            assertThat(p.getFooterData().sessionStats().messageCount()).isEqualTo(8);
        }

        @Test
        void setStreaming() {
            FooterDataProvider p = new FooterDataProvider();
            p.setStreaming(true);
            assertThat(p.getFooterData().isStreaming()).isTrue();
        }
    }

    @Nested
    class FormatStatusBar {

        @Test
        void basicLine() {
            FooterDataProvider p = new FooterDataProvider();
            p.setModel(model());
            String line = p.formatStatusBar(80);
            assertThat(line).contains("MyModel").contains("anthropic");
        }

        @Test
        void includesThinkingTokenStreaming() {
            FooterDataProvider p = new FooterDataProvider();
            p.setModel(model());
            p.setThinkingLevel(ThinkingLevel.HIGH);
            p.updateUsage(new Usage(100, 200, 0, 0, 300, Cost.empty()));
            p.setStreaming(true);
            String line = p.formatStatusBar(80);
            assertThat(line).contains("high").contains("tok").contains("●");
        }

        @Test
        void offThinkingHidden() {
            FooterDataProvider p = new FooterDataProvider();
            p.setModel(model());
            p.setThinkingLevel(ThinkingLevel.OFF);
            String line = p.formatStatusBar(80);
            assertThat(line).doesNotContain("[off]");
        }
    }

    @Nested
    class TokenStatsFormatting {

        @Test
        void formatTokensTinyMediumLarge() {
            FooterDataProvider.TokenStats small = new FooterDataProvider.TokenStats(0, 0, 0, 500, 0);
            FooterDataProvider.TokenStats mid = new FooterDataProvider.TokenStats(0, 0, 0, 12_000, 0);
            FooterDataProvider.TokenStats big = new FooterDataProvider.TokenStats(0, 0, 0, 2_500_000, 0);
            assertThat(small.formatTokens()).isEqualTo("500 tok");
            assertThat(mid.formatTokens()).contains("K");
            assertThat(big.formatTokens()).contains("M");
        }

        @Test
        void formatCostTiers() {
            assertThat(new FooterDataProvider.TokenStats(0, 0, 0, 0, 0.001).formatCost())
                    .startsWith("$0.0010");
            assertThat(new FooterDataProvider.TokenStats(0, 0, 0, 0, 0.123).formatCost())
                    .startsWith("$0.123");
            assertThat(new FooterDataProvider.TokenStats(0, 0, 0, 0, 12.50).formatCost())
                    .startsWith("$12.50");
        }

        @Test
        void fromUsage() {
            Usage u = new Usage(50, 100, 10, 0, 160, Cost.empty());
            FooterDataProvider.TokenStats stats = FooterDataProvider.TokenStats.from(u);
            assertThat(stats.inputTokens()).isEqualTo(50);
            assertThat(stats.totalTokens()).isEqualTo(160);
        }
    }

    @Nested
    class SessionStatsFormatting {

        @Test
        void formatDuration() {
            assertThat(new FooterDataProvider.SessionStats(0, 0, 30 * 1000).formatDuration())
                    .isEqualTo("30s");
            assertThat(new FooterDataProvider.SessionStats(0, 0, 90 * 1000).formatDuration())
                    .isEqualTo("1m 30s");
            assertThat(new FooterDataProvider.SessionStats(0, 0, 60L * 60 * 1000 + 30 * 60 * 1000).formatDuration())
                    .isEqualTo("1h 30m");
        }
    }

    @Nested
    class FooterHint {

        @Test
        void format() {
            FooterDataProvider.FooterHint h = new FooterDataProvider.FooterHint("Ctrl+X", "exit");
            assertThat(h.format()).contains("Ctrl+X").contains("exit");
        }
    }
}
