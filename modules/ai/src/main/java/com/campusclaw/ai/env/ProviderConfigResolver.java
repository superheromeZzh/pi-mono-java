/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.ai.env;

import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.Provider;

/**
 * Resolves runtime configuration (API key, base URL, headers) for an LLM
 * provider. Implementations may layer multiple sources — typically a
 * user settings file overlaid on top of environment variables — to give
 * users a single, opencode-style place to manage credentials.
 *
 * <p>The default implementation in this module is {@link EnvProviderConfigResolver},
 * which only reads environment variables. Higher-level modules (e.g. the
 * coding-agent CLI) override this with a settings-aware variant.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public interface ProviderConfigResolver {

    /**
     * Resolves config for the given model. The {@code model} argument is
     * provided so implementations can fall back to the model's embedded
     * {@code apiKey} field when no other source is available.
     */
    ResolvedProviderConfig resolve(Provider provider, Model model);
}
