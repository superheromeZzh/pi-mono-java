/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.auth.AuthStore;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginLogoutCommandTest {

    @Mock
    AuthStore authStore;

    private final List<String> out = new ArrayList<>();
    private final SlashCommandContext ctx = new SlashCommandContext(null, out::add);

    @Nested
    class Login {

        @Test
        void metadata() {
            LoginCommand c = new LoginCommand(authStore);
            assertThat(c.name()).isEqualTo("login");
            assertThat(c.description()).isNotBlank();
        }

        @Test
        void emptyArgsShowsUsage() {
            new LoginCommand(authStore).execute(ctx, "");
            assertThat(out).anyMatch(s -> s.contains("Usage"));
            assertThat(out).anyMatch(s -> s.contains("Providers:"));
        }

        @Test
        void onlyProviderShowsUsage() {
            new LoginCommand(authStore).execute(ctx, "anthropic");
            assertThat(out).first().asString().contains("Usage:");
        }

        @Test
        void unknownProviderRejected() {
            new LoginCommand(authStore).execute(ctx, "mystery sk-x");
            assertThat(out).first().asString().contains("Unknown provider");
        }

        @Test
        void validProviderSavesKey() {
            new LoginCommand(authStore).execute(ctx, "anthropic sk-test");
            verify(authStore).setApiKey(Provider.ANTHROPIC, "sk-test");
            assertThat(out).first().asString().contains("Saved API key");
        }

        @Test
        void caseInsensitiveProvider() {
            new LoginCommand(authStore).execute(ctx, "OpenAI sk-y");
            verify(authStore).setApiKey(Provider.OPENAI, "sk-y");
        }
    }

    @Nested
    class Logout {

        @Test
        void metadata() {
            LogoutCommand c = new LogoutCommand(authStore);
            assertThat(c.name()).isEqualTo("logout");
        }

        @Test
        void emptyArgsShowsUsage() {
            new LogoutCommand(authStore).execute(ctx, "");
            assertThat(out).first().asString().contains("Usage:");
        }

        @Test
        void unknownProviderRejected() {
            new LogoutCommand(authStore).execute(ctx, "mystery");
            assertThat(out).first().asString().contains("Unknown provider");
        }

        @Test
        void existingProviderRemoved() {
            when(authStore.remove(Provider.ANTHROPIC)).thenReturn(true);
            new LogoutCommand(authStore).execute(ctx, "anthropic");
            verify(authStore).remove(Provider.ANTHROPIC);
        }

        @Test
        void missingProvider() {
            when(authStore.remove(Provider.ANTHROPIC)).thenReturn(false);
            new LogoutCommand(authStore).execute(ctx, "anthropic");
            assertThat(out).isNotEmpty();
        }
    }
}
