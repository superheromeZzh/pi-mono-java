package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

/**
 * Shows session info and stats matching campusclaw TS /session command.
 */
public class SessionCommand implements SlashCommand {

    @Override
    public String name() {
        return "session";
    }

    @Override
    public String description() {
        return "Show session info and stats";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var session = context.session();
        var state = session.getAgent().getState();
        var model = state.getModel();
        var out = context.output();

        out.println("Session Info:");
        out.println("  Model: " + (model != null ? model.id() : "unknown"));
        if (model != null && model.provider() != null) {
            out.println("  Provider: " + model.provider().name());
        }
        if (model != null && model.contextWindow() > 0) {
            out.println("  Context window: " + formatTokens(model.contextWindow()));
        }

        var thinkingLevel = state.getThinkingLevel();
        if (thinkingLevel != null) {
            out.println("  Thinking: " + thinkingLevel.value());
        }

        // Message stats
        var messages = state.getMessages();
        int userCount = 0, assistantCount = 0, toolCount = 0;
        long totalInput = 0, totalOutput = 0, totalCacheRead = 0, totalCacheWrite = 0;
        double totalCost = 0;

        for (Message msg : messages) {
            if (msg instanceof UserMessage) userCount++;
            else if (msg instanceof AssistantMessage am) {
                assistantCount++;
                if (am.usage() != null) {
                    totalInput += am.usage().input();
                    totalOutput += am.usage().output();
                    totalCacheRead += am.usage().cacheRead();
                    totalCacheWrite += am.usage().cacheWrite();
                    if (am.usage().cost() != null) {
                        totalCost += am.usage().cost().total();
                    }
                }
            } else {
                toolCount++;
            }
        }

        out.println("  Messages: " + messages.size()
                + " (" + userCount + " user, " + assistantCount + " assistant, " + toolCount + " tool)");
        out.println("  Tokens: ↑" + formatTokens((int) totalInput)
                + " ↓" + formatTokens((int) totalOutput));
        if (totalCacheRead > 0 || totalCacheWrite > 0) {
            out.println("  Cache: R" + formatTokens((int) totalCacheRead)
                    + " W" + formatTokens((int) totalCacheWrite));
        }
        if (totalCost > 0) {
            out.println("  Cost: $" + String.format("%.4f", totalCost));
        }

        // Context usage
        if (model != null && model.contextWindow() > 0) {
            long used = totalInput + totalOutput;
            double pct = (double) used / model.contextWindow() * 100;
            out.println("  Context usage: " + String.format("%.1f%%", pct)
                    + " of " + formatTokens(model.contextWindow()));
        }

        // Skills and templates
        int skillCount = session.getSkillRegistry().getAll().size();
        int templateCount = session.getPromptTemplates().size();
        if (skillCount > 0 || templateCount > 0) {
            out.println("  Resources: " + skillCount + " skill(s), " + templateCount + " template(s)");
        }

        out.println("  CWD: " + System.getProperty("user.dir"));

        // Session file info
        var sm = session.getSessionManager();
        if (sm != null) {
            out.println("  Session ID: " + sm.getSessionId());
            if (sm.getSessionFile() != null) {
                out.println("  Session file: " + sm.getSessionFile());
            }
        }
    }

    private static String formatTokens(int tokens) {
        if (tokens >= 10_000_000) return String.format("%.0fM", tokens / 1_000_000.0);
        if (tokens >= 1_000_000) return String.format("%.1fM", tokens / 1_000_000.0);
        if (tokens >= 10_000) return String.format("%.0fk", tokens / 1000.0);
        if (tokens >= 1000) return String.format("%.1fk", tokens / 1000.0);
        return String.valueOf(tokens);
    }
}
