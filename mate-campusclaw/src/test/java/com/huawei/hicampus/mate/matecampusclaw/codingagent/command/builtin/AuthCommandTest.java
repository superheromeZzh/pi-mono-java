/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
class AuthCommandTest {

    @Mock
    AuthStore authStore;

    private final List<String> out = new ArrayList<>();
    private final SlashCommandContext ctx = new SlashCommandContext(null, out::add);

    @Nested
    class Metadata {

        @Test
        void identity() {
            AuthCommand c = new AuthCommand(authStore);
            assertThat(c.name()).isEqualTo("auth");
            assertThat(c.description()).isNotBlank();
        }
    }

    @Nested
    class Usage {

        @Test
        void emptyArgsShowsUsage() {
            new AuthCommand(authStore).execute(ctx, "");
            assertThat(out).anyMatch(s -> s.contains("Usage"));
        }

        @Test
        void nullArgsShowsUsage() {
            new AuthCommand(authStore).execute(ctx, null);
            assertThat(out).anyMatch(s -> s.contains("Usage"));
        }

        @Test
        void unknownSubShowsUsage() {
            new AuthCommand(authStore).execute(ctx, "wha");
            assertThat(out).anyMatch(s -> s.contains("Usage"));
        }
    }

    @Nested
    class Login {

        @Test
        void noProviderShowsUsage() {
            new AuthCommand(authStore).execute(ctx, "login");
            assertThat(out).first().asString().contains("Usage: /auth login");
        }

        @Test
        void onlyProviderShowsUsage() {
            new AuthCommand(authStore).execute(ctx, "login anthropic");
            assertThat(out).first().asString().contains("Usage: /auth login");
        }

        @Test
        void unknownProviderRejected() {
            new AuthCommand(authStore).execute(ctx, "login mystery-provider sk-x");
            assertThat(out).first().asString().contains("Unknown provider");
        }

        @Test
        void validProviderSavesKey() {
            new AuthCommand(authStore).execute(ctx, "login anthropic sk-x");
            verify(authStore).setApiKey(Provider.ANTHROPIC, "sk-x");
            assertThat(out).first().asString().contains("Saved API key");
        }
    }

    @Nested
    class ListAndAliasLs {

        @Test
        void emptyReportsNoCredentials() {
            when(authStore.listSummary()).thenReturn(Map.of());
            new AuthCommand(authStore).execute(ctx, "list");
            assertThat(out).first().asString().contains("No stored credentials");
        }

        @Test
        void lsAliasWorks() {
            when(authStore.listSummary()).thenReturn(Map.of());
            new AuthCommand(authStore).execute(ctx, "ls");
            assertThat(out).first().asString().contains("No stored credentials");
        }

        @Test
        void rendersEntries() {
            when(authStore.listSummary()).thenReturn(Map.of("anthropic", "api", "openai", "api"));
            new AuthCommand(authStore).execute(ctx, "list");
            String joined = String.join("\n", out);
            assertThat(joined)
                    .contains("Stored credentials")
                    .contains("anthropic")
                    .contains("openai");
        }
    }

    @Nested
    class Logout {

        @Test
        void noProviderShowsUsage() {
            new AuthCommand(authStore).execute(ctx, "logout");
            assertThat(out).first().asString().contains("Usage: /auth logout");
        }

        @Test
        void unknownProviderRejected() {
            new AuthCommand(authStore).execute(ctx, "logout mystery");
            assertThat(out).first().asString().contains("Unknown provider");
        }

        @Test
        void successPath() {
            when(authStore.remove(Provider.ANTHROPIC)).thenReturn(true);
            new AuthCommand(authStore).execute(ctx, "logout anthropic");
            assertThat(out).first().asString().contains("Removed credentials");
        }

        @Test
        void noCredentialPath() {
            when(authStore.remove(Provider.ANTHROPIC)).thenReturn(false);
            new AuthCommand(authStore).execute(ctx, "logout anthropic");
            assertThat(out).first().asString().contains("No credentials stored");
        }
    }
}
