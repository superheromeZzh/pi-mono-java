/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.command.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.campusclaw.codingagent.command.SlashCommandContext;
import com.campusclaw.codingagent.session.AgentSession;
import com.campusclaw.codingagent.session.SessionManager;

import org.junit.jupiter.api.Test;

class NameCommandTest {

    @Test
    void identityMetadata() {
        NameCommand cmd = new NameCommand();
        assertThat(cmd.name()).isEqualTo("name");
        assertThat(cmd.description()).isNotBlank();
    }

    @Test
    void emptyArgsWithoutSessionNameShowsUsageHint() {
        NameCommand cmd = new NameCommand();
        List<String> output = new ArrayList<>();
        SlashCommandContext ctx = new SlashCommandContext(mock(AgentSession.class), output::add);

        cmd.execute(ctx, "");

        assertThat(output).anyMatch(s -> s.contains("Usage: /name"));
        assertThat(cmd.getSessionName()).isNull();
    }

    @Test
    void emptyArgsAfterNamingShowsCurrentName() {
        NameCommand cmd = new NameCommand();
        AgentSession session = mock(AgentSession.class);
        when(session.getSessionManager()).thenReturn(null);
        List<String> output = new ArrayList<>();
        SlashCommandContext ctx = new SlashCommandContext(session, output::add);

        cmd.execute(ctx, "alpha");
        output.clear();
        cmd.execute(ctx, "");

        assertThat(output).anyMatch(s -> s.contains("Session name: alpha"));
    }

    @Test
    void settingNamePersistsToSessionManagerWhenPresent() {
        NameCommand cmd = new NameCommand();
        SessionManager sm = mock(SessionManager.class);
        AgentSession session = mock(AgentSession.class);
        when(session.getSessionManager()).thenReturn(sm);

        List<String> output = new ArrayList<>();
        SlashCommandContext ctx = new SlashCommandContext(session, output::add);

        cmd.execute(ctx, "  my-experiment  ");

        verify(sm).appendSessionName("my-experiment");
        assertThat(cmd.getSessionName()).isEqualTo("my-experiment");
        assertThat(output).anyMatch(s -> s.contains("Session name set to: my-experiment"));
    }

    @Test
    void settingNameSkipsPersistenceWhenSessionManagerNull() {
        NameCommand cmd = new NameCommand();
        AgentSession session = mock(AgentSession.class);
        when(session.getSessionManager()).thenReturn(null);

        List<String> output = new ArrayList<>();
        SlashCommandContext ctx = new SlashCommandContext(session, output::add);

        cmd.execute(ctx, "no-persist");

        assertThat(cmd.getSessionName()).isEqualTo("no-persist");
        assertThat(output).anyMatch(s -> s.contains("Session name set to: no-persist"));
    }
}
