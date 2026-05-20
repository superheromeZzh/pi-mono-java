/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.env;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Optional;

import com.campusclaw.ai.types.Provider;

import org.junit.jupiter.api.Test;

class EnvApiKeyResolverTest {

    private final EnvApiKeyResolver resolver = new EnvApiKeyResolver();

    @Test
    void resolveDoesNotThrowForAnyProvider() {
        // Environment is whatever the JVM has — we just verify no exception escapes.
        for (Provider p : Provider.values()) {
            Optional<String> result = resolver.resolve(p);
            assertNotNull(result);
        }
    }

    @Test
    void resolveCustomReturnsEmpty() {
        // CUSTOM and other unknown providers fall through the default branch.
        assertFalse(resolver.resolve(Provider.CUSTOM).isPresent());
    }
}
