/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.Settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelResolverTest {

    @Mock
    ModelRegistry modelRegistry;

    private static Model model(String id) {
        return new Model(
                id,
                id,
                Api.ANTHROPIC_MESSAGES,
                Provider.ANTHROPIC,
                "https://api.example.com",
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
    class Resolve {

        @Test
        void exactMatchReturned() {
            Model m = model("gpt-4");
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.OPENAI));
            when(modelRegistry.getModel(Provider.OPENAI, "gpt-4")).thenReturn(Optional.of(m));
            ModelResolver r = new ModelResolver(modelRegistry);
            assertThat(r.resolve("gpt-4", null)).isSameAs(m);
        }

        @Test
        void blankIdSkipsMatchPhase() {
            // Will fall through to safe default
            Model safe = model("claude-sonnet-4-20250514");
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.ANTHROPIC));
            when(modelRegistry.getModel(Provider.ANTHROPIC, "claude-sonnet-4-20250514"))
                    .thenReturn(Optional.of(safe));
            ModelResolver r = new ModelResolver(modelRegistry);
            assertThat(r.resolve("  ", null)).isSameAs(safe);
        }

        @Test
        void fallsBackToScopedOverride() {
            Model target = model("real-model");
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.OPENAI));
            when(modelRegistry.getModel(any(Provider.class), any(String.class))).thenAnswer(invocation -> {
                String id = invocation.getArgument(1);
                return "real-model".equals(id) ? Optional.of(target) : Optional.empty();
            });
            ModelResolver r = new ModelResolver(modelRegistry);
            r.addScopedOverride("alias", "real-model");
            assertThat(r.resolve("alias", null)).isSameAs(target);
        }

        @Test
        void fallsBackToSettingsDefault() {
            Model defaultModel = model("default-x");
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.OPENAI));
            when(modelRegistry.getModel(any(Provider.class), any(String.class))).thenAnswer(invocation -> {
                String id = invocation.getArgument(1);
                return "default-x".equals(id) ? Optional.of(defaultModel) : Optional.empty();
            });
            Settings settings = new Settings(
                    "openai",
                    "default-x",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
            ModelResolver r = new ModelResolver(modelRegistry);
            assertThat(r.resolve("missing", settings)).isSameAs(defaultModel);
        }

        @Test
        void fallsBackToSafeDefault() {
            Model safe = model("claude-sonnet-4-20250514");
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.ANTHROPIC));
            when(modelRegistry.getModel(any(Provider.class), any(String.class))).thenAnswer(invocation -> {
                String id = invocation.getArgument(1);
                return "claude-sonnet-4-20250514".equals(id) ? Optional.of(safe) : Optional.empty();
            });
            ModelResolver r = new ModelResolver(modelRegistry);
            assertThat(r.resolve("nothing", null)).isSameAs(safe);
        }

        @Test
        void noResolutionThrows() {
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.ANTHROPIC));
            when(modelRegistry.getModel(any(Provider.class), any(String.class))).thenReturn(Optional.empty());
            ModelResolver r = new ModelResolver(modelRegistry);
            assertThatThrownBy(() -> r.resolve("x", null)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ScopedOverrides {

        @Test
        void addAndRemove() {
            ModelResolver r = new ModelResolver(modelRegistry);
            r.addScopedOverride("a1", "real");
            assertThat(r.getScopedOverrides()).containsEntry("a1", "real");
            r.removeScopedOverride("a1");
            assertThat(r.getScopedOverrides()).isEmpty();
        }

        @Test
        void scopedOverridesIsImmutable() {
            ModelResolver r = new ModelResolver(modelRegistry);
            r.addScopedOverride("a", "b");
            org.junit.jupiter.api.Assertions.assertThrows(
                    UnsupportedOperationException.class,
                    () -> r.getScopedOverrides().put("x", "y"));
        }
    }

    @Nested
    class FindModelAndAllIds {

        @Test
        void findModelEmptyWhenAbsent() {
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.ANTHROPIC));
            when(modelRegistry.getModel(Provider.ANTHROPIC, "x")).thenReturn(Optional.empty());
            assertThat(new ModelResolver(modelRegistry).findModel("x")).isEmpty();
        }

        @Test
        void getAllModelIdsAggregates() {
            when(modelRegistry.getProviders()).thenReturn(List.of(Provider.OPENAI, Provider.ANTHROPIC));
            when(modelRegistry.getModels(Provider.OPENAI)).thenReturn(List.of(model("gpt-4"), model("gpt-3.5")));
            when(modelRegistry.getModels(Provider.ANTHROPIC)).thenReturn(List.of(model("claude")));
            assertThat(new ModelResolver(modelRegistry).getAllModelIds()).containsExactly("gpt-4", "gpt-3.5", "claude");
        }
    }
}
