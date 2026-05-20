/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.command.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.campusclaw.codingagent.command.SlashCommandContext;
import com.campusclaw.codingagent.session.AgentSession;
import com.campusclaw.codingagent.skill.SkillRegistry;

import org.junit.jupiter.api.Test;

class ReloadCommandTest {

    @Test
    void identityMetadata() {
        ReloadCommand cmd = new ReloadCommand();
        assertThat(cmd.name()).isEqualTo("reload");
        assertThat(cmd.description()).isNotBlank();
    }

    @Test
    void reportsSkillAndTemplateCountsOnSuccess() {
        ReloadCommand cmd = new ReloadCommand();
        AgentSession session = mock(AgentSession.class);
        SkillRegistry registry = mock(SkillRegistry.class);
        when(session.getSkillRegistry()).thenReturn(registry);
        when(registry.getAll()).thenReturn(java.util.List.of());
        when(session.getPromptTemplates()).thenReturn(java.util.List.of());

        List<String> output = new ArrayList<>();
        SlashCommandContext ctx = new SlashCommandContext(session, output::add);

        cmd.execute(ctx, "");

        verify(session).reload();
        assertThat(output).anyMatch(s -> s.contains("Reloading"));
        assertThat(output).anyMatch(s -> s.contains("0 skill(s)"));
        assertThat(output).anyMatch(s -> s.contains("0 template(s)"));
    }

    @Test
    void reportsErrorWhenReloadThrows() {
        ReloadCommand cmd = new ReloadCommand();
        AgentSession session = mock(AgentSession.class);
        doThrow(new RuntimeException("disk full")).when(session).reload();

        List<String> output = new ArrayList<>();
        SlashCommandContext ctx = new SlashCommandContext(session, output::add);

        cmd.execute(ctx, "");

        assertThat(output).anyMatch(s -> s.contains("Reload failed: disk full"));
    }
}
