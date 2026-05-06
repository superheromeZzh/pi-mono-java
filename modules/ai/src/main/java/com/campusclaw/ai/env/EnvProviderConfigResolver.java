/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.env;

import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.Provider;

import org.springframework.stereotype.Service;

/**
 * Default {@link ProviderConfigResolver} that consults only environment
 * variables, plus any API key embedded directly on the {@link Model}.
 *
 * <p>Higher-level modules can register a {@code @Primary} bean of the same
 * interface to layer additional sources (e.g. a user settings file).
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Service
public class EnvProviderConfigResolver implements ProviderConfigResolver {

    private final EnvApiKeyResolver envApiKeyResolver;

    public EnvProviderConfigResolver(EnvApiKeyResolver envApiKeyResolver) {
        this.envApiKeyResolver = envApiKeyResolver;
    }

    @Override
    public ResolvedProviderConfig resolve(Provider provider, Model model) {
        if (model != null && model.apiKey() != null && !model.apiKey().isBlank()) {
            return new ResolvedProviderConfig(model.apiKey(), null, null);
        }
        String key = envApiKeyResolver.resolve(provider).orElse(null);
        return new ResolvedProviderConfig(key, null, null);
    }
}
