/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.ai.types.Provider;
import com.campusclaw.codingagent.auth.AuthStore;
import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;

/**
 * Removes a persisted API key. Usage: {@code /logout <provider>}.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class LogoutCommand implements SlashCommand {

    private final AuthStore authStore;

    public LogoutCommand(AuthStore authStore) {
        this.authStore = authStore;
    }

    @Override
    public String name() {
        return "logout";
    }

    @Override
    public String description() {
        return "Remove a persisted API key for a provider";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        String providerStr = arguments == null ? "" : arguments.trim();
        if (providerStr.isEmpty()) {
            context.output().println("Usage: /logout <provider>");
            return;
        }
        var providerOpt = Provider.tryFromValue(providerStr);
        if (providerOpt.isEmpty()) {
            context.output().println("Unknown provider: " + providerStr + ". Run /providers to list known ids.");
            return;
        }
        Provider provider = providerOpt.get();
        boolean removed = authStore.remove(provider);
        if (removed) {
            context.output().println("Removed credentials for " + provider.value() + ".");
        } else {
            context.output().println("No credentials stored for " + provider.value() + ".");
        }
    }
}
