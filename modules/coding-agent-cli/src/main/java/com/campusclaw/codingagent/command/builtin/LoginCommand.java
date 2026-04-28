package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.ai.types.Provider;
import com.campusclaw.codingagent.auth.AuthStore;
import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;

/**
 * Persists an API key for a provider into {@code ~/.campusclaw/agent/auth.json}.
 *
 * <p>Usage: {@code /login <provider> <api-key>}
 *
 * <p>Once stored, the key is used in preference to env vars and to
 * {@code settings.json#provider.<id>.apiKey}, mirroring opencode's
 * {@code opencode auth login} flow.
 */
public class LoginCommand implements SlashCommand {

    private final AuthStore authStore;

    public LoginCommand(AuthStore authStore) {
        this.authStore = authStore;
    }

    @Override
    public String name() { return "login"; }

    @Override
    public String description() { return "Persist an API key for a provider"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        String args = arguments == null ? "" : arguments.trim();
        if (args.isEmpty()) {
            context.output().println("Usage: /login <provider> <api-key>");
            context.output().println("Providers: anthropic, openai, zai, google, mistral, xai, groq, openrouter, ...");
            return;
        }
        String[] parts = args.split("\\s+", 2);
        if (parts.length < 2) {
            context.output().println("Usage: /login <provider> <api-key>");
            return;
        }
        String providerStr = parts[0];
        String key = parts[1].trim();
        var providerOpt = Provider.tryFromValue(providerStr);
        if (providerOpt.isEmpty()) {
            context.output().println("Unknown provider: " + providerStr
                    + ". Run /providers to list known ids.");
            return;
        }
        Provider provider = providerOpt.get();
        authStore.setApiKey(provider, key);
        context.output().println("Saved API key for " + provider.value()
                + " to ~/.campusclaw/agent/auth.json (mode 0600).");
    }
}
