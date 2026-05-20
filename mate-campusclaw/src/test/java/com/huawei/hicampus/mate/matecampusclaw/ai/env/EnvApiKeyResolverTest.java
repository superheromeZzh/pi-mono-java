/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.env;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;

import org.junit.jupiter.api.Test;

class EnvApiKeyResolverTest {

    private final EnvApiKeyResolver resolver = new EnvApiKeyResolver();

    @Test
    void resolveDoesNotThrowForAnyProvider() {
        // Environment is whatever the JVM has — we just verify every Provider value is
        // dispatchable without an exception escaping (e.g. no missing switch branch).
        for (Provider p : Provider.values()) {
            assertDoesNotThrow(() -> resolver.resolve(p), "resolver must handle provider: " + p);
        }
    }

    @Test
    void resolveCustomReturnsEmpty() {
        // CUSTOM and other unknown providers fall through the default branch.
        assertFalse(resolver.resolve(Provider.CUSTOM).isPresent());
    }
}
