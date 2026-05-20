/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthStoreTest {

    @Nested
    class GetApiKey {

        @Test
        void missingFileReturnsEmpty(@TempDir Path tmp) {
            AuthStore store = new AuthStore(tmp.resolve("auth.json"));
            assertThat(store.getApiKey(Provider.ANTHROPIC)).isEmpty();
        }

        @Test
        void existingKeyRoundtrip(@TempDir Path tmp) {
            AuthStore store = new AuthStore(tmp.resolve("auth.json"));
            store.setApiKey(Provider.ANTHROPIC, "sk-test");
            assertThat(store.getApiKey(Provider.ANTHROPIC)).contains("sk-test");
        }

        @Test
        void blankKeyTreatedAsAbsent(@TempDir Path tmp) {
            AuthStore store = new AuthStore(tmp.resolve("auth.json"));
            store.setApiKey(Provider.ANTHROPIC, "");
            assertThat(store.getApiKey(Provider.ANTHROPIC)).isEmpty();
        }

        @Test
        void malformedFileReturnsEmpty(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("auth.json");
            Files.writeString(file, "{ not json");
            AuthStore store = new AuthStore(file);
            assertThat(store.getApiKey(Provider.ANTHROPIC)).isEmpty();
        }
    }

    @Nested
    class Remove {

        @Test
        void removesExistingKey(@TempDir Path tmp) {
            AuthStore store = new AuthStore(tmp.resolve("auth.json"));
            store.setApiKey(Provider.ANTHROPIC, "sk-test");
            assertThat(store.remove(Provider.ANTHROPIC)).isTrue();
            assertThat(store.getApiKey(Provider.ANTHROPIC)).isEmpty();
        }

        @Test
        void missingKeyReturnsFalse(@TempDir Path tmp) {
            AuthStore store = new AuthStore(tmp.resolve("auth.json"));
            assertThat(store.remove(Provider.ANTHROPIC)).isFalse();
        }
    }

    @Nested
    class ListSummary {

        @Test
        void emptyStoreEmptyMap(@TempDir Path tmp) {
            AuthStore store = new AuthStore(tmp.resolve("auth.json"));
            assertThat(store.listSummary()).isEmpty();
        }

        @Test
        void multipleProvidersListed(@TempDir Path tmp) {
            AuthStore store = new AuthStore(tmp.resolve("auth.json"));
            store.setApiKey(Provider.ANTHROPIC, "a");
            store.setApiKey(Provider.OPENAI, "b");
            assertThat(store.listSummary())
                    .containsKeys(Provider.ANTHROPIC.value(), Provider.OPENAI.value())
                    .containsValue("api");
        }
    }
}
