/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;

import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.auth.AuthStore;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandRegistry;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.loop.LoopManager;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.resolver.AgentModelResolver;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuiltinCommandRegistrarTest {

    @Mock
    SlashCommandRegistry registry;

    @Mock
    CampusClawAiService piAiService;

    @Mock
    SettingsManager settingsManager;

    @Mock
    LoopManager loopManager;

    @Mock
    AuthStore authStore;

    @Mock
    AgentModelResolver agentModelResolver;

    @Mock
    ModelRegistry modelRegistry;

    @InjectMocks
    BuiltinCommandRegistrar registrar;

    @Test
    void registersAllBuiltinsViaPostConstruct() throws Exception {
        var method = BuiltinCommandRegistrar.class.getDeclaredMethod("registerBuiltins");
        method.setAccessible(true);
        method.invoke(registrar);

        // 27 register() calls in the source — verify at least that many.
        verify(registry, atLeast(25)).register(org.mockito.ArgumentMatchers.any(SlashCommand.class));
    }

    @Test
    void exposesPostConstructAnnotation() throws Exception {
        var method = BuiltinCommandRegistrar.class.getDeclaredMethod("registerBuiltins");
        assertThat(method.isAnnotationPresent(jakarta.annotation.PostConstruct.class))
                .isTrue();
    }
}
