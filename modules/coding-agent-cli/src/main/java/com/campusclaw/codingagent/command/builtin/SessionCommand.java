/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.command.builtin;

import java.util.List;
import java.util.Locale;

import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.UserMessage;
import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;

/**
 * Shows session info and stats matching campusclaw TS /session command.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
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

    /** Per-message-type counts and accumulated token/cost stats over a session's history. */
    private record SessionStats(
            int userCount,
            int assistantCount,
            int toolCount,
            long totalInput,
            long totalOutput,
            long totalCacheRead,
            long totalCacheWrite,
            double totalCost) {}

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var session = context.session();
        var state = session.getAgent().getState();
        var model = state.getModel();
        var out = context.output();
        out.println("Session Info:");
        printModelInfo(out, model, state);
        SessionStats stats = collectStats(state.getMessages());
        printMessageStats(out, state.getMessages().size(), stats);
        if (model != null && model.contextWindow() > 0) {
            long used = stats.totalInput() + stats.totalOutput();
            double pct = (double) used / model.contextWindow() * 100;
            out.println("  Context usage: " + String.format(Locale.ROOT, "%.1f%%", pct) + " of "
                    + formatTokens(model.contextWindow()));
        }
        int skillCount = session.getSkillRegistry().getAll().size();
        int templateCount = session.getPromptTemplates().size();
        if (skillCount > 0 || templateCount > 0) {
            out.println("  Resources: " + skillCount + " skill(s), " + templateCount + " template(s)");
        }
        out.println("  CWD: " + System.getProperty("user.dir"));
        var sm = session.getSessionManager();
        if (sm != null) {
            out.println("  Session ID: " + sm.getSessionId());
            if (sm.getSessionFile() != null) {
                out.println("  Session file: " + sm.getSessionFile());
            }
        }
    }

    private static void printModelInfo(
            com.campusclaw.codingagent.command.SlashCommandContext.OutputWriter out,
            com.campusclaw.ai.types.Model model,
            com.campusclaw.agent.state.AgentState state) {
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
    }

    private static SessionStats collectStats(List<Message> messages) {
        int userCount = 0;
        int assistantCount = 0;
        int toolCount = 0;
        long totalInput = 0L;
        long totalOutput = 0L;
        long totalCacheRead = 0L;
        long totalCacheWrite = 0L;
        double totalCost = 0;
        for (Message msg : messages) {
            if (msg instanceof UserMessage) {
                userCount++;
            } else if (msg instanceof AssistantMessage am) {
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
        return new SessionStats(
                userCount,
                assistantCount,
                toolCount,
                totalInput,
                totalOutput,
                totalCacheRead,
                totalCacheWrite,
                totalCost);
    }

    private static void printMessageStats(
            com.campusclaw.codingagent.command.SlashCommandContext.OutputWriter out, int total, SessionStats stats) {
        out.println("  Messages: " + total + " (" + stats.userCount() + " user, " + stats.assistantCount()
                + " assistant, " + stats.toolCount() + " tool)");
        out.println("  Tokens: ↑" + formatTokens((int) stats.totalInput()) + " ↓"
                + formatTokens((int) stats.totalOutput()));
        if (stats.totalCacheRead() > 0 || stats.totalCacheWrite() > 0) {
            out.println("  Cache: R" + formatTokens((int) stats.totalCacheRead()) + " W"
                    + formatTokens((int) stats.totalCacheWrite()));
        }
        if (stats.totalCost() > 0) {
            out.println("  Cost: $" + String.format(Locale.ROOT, "%.4f", stats.totalCost()));
        }
    }

    private static String formatTokens(int tokens) {
        if (tokens >= 10_000_000) {
            return String.format(Locale.ROOT, "%.0fM", tokens / 1_000_000.0);
        }
        if (tokens >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", tokens / 1_000_000.0);
        }
        if (tokens >= 10_000) {
            return String.format(Locale.ROOT, "%.0fk", tokens / 1000.0);
        }
        if (tokens >= 1000) {
            return String.format(Locale.ROOT, "%.1fk", tokens / 1000.0);
        }
        return String.valueOf(tokens);
    }
}
