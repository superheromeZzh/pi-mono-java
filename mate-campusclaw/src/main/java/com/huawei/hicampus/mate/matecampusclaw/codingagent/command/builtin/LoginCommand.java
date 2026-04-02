package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

/**
 * Login with an API key or OAuth provider.
 */
public class LoginCommand implements SlashCommand {

    @Override
    public String name() { return "login"; }

    @Override
    public String description() { return "Login with OAuth provider"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("OAuth login is not yet implemented in campusclaw.");
        context.output().println("Set API keys via environment variables or --api-key flag:");
        context.output().println("  ANTHROPIC_API_KEY, OPENAI_API_KEY, GEMINI_API_KEY,");
        context.output().println("  ZAI_API_KEY, XAI_API_KEY, GROQ_API_KEY, etc.");
    }
}
