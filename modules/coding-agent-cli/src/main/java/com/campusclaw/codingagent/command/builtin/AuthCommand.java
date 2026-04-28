package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.ai.types.Provider;
import com.campusclaw.codingagent.auth.AuthStore;
import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;

/**
 * opencode-style {@code /auth} command. Subcommands:
 *
 * <ul>
 *   <li>{@code /auth login <provider> <api-key>} — persist a key</li>
 *   <li>{@code /auth list} — show which providers have stored credentials</li>
 *   <li>{@code /auth logout <provider>} — remove a stored credential</li>
 * </ul>
 */
public class AuthCommand implements SlashCommand {

    private final AuthStore authStore;

    public AuthCommand(AuthStore authStore) {
        this.authStore = authStore;
    }

    @Override
    public String name() { return "auth"; }

    @Override
    public String description() { return "Manage stored API keys (login / list / logout)"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        String args = arguments == null ? "" : arguments.trim();
        if (args.isEmpty()) {
            printUsage(context);
            return;
        }
        String[] parts = args.split("\\s+", 2);
        String sub = parts[0].toLowerCase();
        String rest = parts.length > 1 ? parts[1].trim() : "";
        switch (sub) {
            case "login" -> doLogin(context, rest);
            case "list", "ls" -> doList(context);
            case "logout" -> doLogout(context, rest);
            default -> printUsage(context);
        }
    }

    private void doLogin(SlashCommandContext context, String rest) {
        if (rest.isEmpty()) {
            context.output().println("Usage: /auth login <provider> <api-key>");
            return;
        }
        String[] p = rest.split("\\s+", 2);
        if (p.length < 2) {
            context.output().println("Usage: /auth login <provider> <api-key>");
            return;
        }
        var providerOpt = Provider.tryFromValue(p[0]);
        if (providerOpt.isEmpty()) {
            context.output().println("Unknown provider: " + p[0]
                    + ". Run /providers to list known ids.");
            return;
        }
        Provider provider = providerOpt.get();
        authStore.setApiKey(provider, p[1].trim());
        context.output().println("Saved API key for " + provider.value()
                + " to ~/.campusclaw/agent/auth.json (mode 0600).");
    }

    private void doList(SlashCommandContext context) {
        var summary = authStore.listSummary();
        if (summary.isEmpty()) {
            context.output().println("No stored credentials. Use /auth login <provider> <key>.");
            return;
        }
        context.output().println("Stored credentials:");
        for (var e : summary.entrySet()) {
            context.output().println("  " + e.getKey() + "  (" + e.getValue() + ")");
        }
    }

    private void doLogout(SlashCommandContext context, String rest) {
        if (rest.isEmpty()) {
            context.output().println("Usage: /auth logout <provider>");
            return;
        }
        var providerOpt = Provider.tryFromValue(rest);
        if (providerOpt.isEmpty()) {
            context.output().println("Unknown provider: " + rest
                    + ". Run /providers to list known ids.");
            return;
        }
        Provider provider = providerOpt.get();
        boolean removed = authStore.remove(provider);
        context.output().println(removed
                ? "Removed credentials for " + provider.value() + "."
                : "No credentials stored for " + provider.value() + ".");
    }

    private void printUsage(SlashCommandContext context) {
        context.output().println("Usage:");
        context.output().println("  /auth login <provider> <api-key>");
        context.output().println("  /auth list");
        context.output().println("  /auth logout <provider>");
    }
}
