/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeCommandTest {

    @Mock
    AgentSession session;

    private final List<String> out = new ArrayList<>();

    @Nested
    class Metadata {

        @Test
        void identity() {
            ResumeCommand c = new ResumeCommand();
            assertThat(c.name()).isEqualTo("resume");
            assertThat(c.description()).isNotBlank();
        }
    }

    @Nested
    class DisabledPersistence {

        @Test
        void noSessionManagerReportsDisabled() {
            when(session.getSessionManager()).thenReturn(null);
            SlashCommandContext ctx = new SlashCommandContext(session, out::add);
            new ResumeCommand().execute(ctx, "");
            assertThat(out).anyMatch(s -> s.contains("Session persistence is disabled"));
        }
    }
}
