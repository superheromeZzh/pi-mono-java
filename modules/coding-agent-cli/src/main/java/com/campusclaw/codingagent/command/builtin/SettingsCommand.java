package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;
import com.campusclaw.codingagent.settings.Settings;
import com.campusclaw.codingagent.settings.SettingsManager;

/**
 * Displays all current settings matching campusclaw TS /settings output.
 */
public class SettingsCommand implements SlashCommand {

    private final SettingsManager settingsManager;

    public SettingsCommand(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    @Override
    public String name() {
        return "settings";
    }

    @Override
    public String description() {
        return "Print current settings";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var state = context.session().getAgent().getState();
        var model = state.getModel();
        Settings settings = settingsManager.load();
        var out = context.output();

        out.println("Settings:");

        // Model info
        out.println("  Model: " + (model != null ? model.id() : nvl(settings.defaultModel(), "not set")));
        if (model != null && model.provider() != null) {
            out.println("  Provider: " + model.provider().name());
        } else if (settings.defaultProvider() != null) {
            out.println("  Provider: " + settings.defaultProvider());
        }

        // Thinking
        var thinkingLevel = state.getThinkingLevel();
        out.println("  Thinking: " + (thinkingLevel != null ? thinkingLevel.value()
                : nvl(settings.defaultThinkingLevel(), "medium")));

        // Transport & modes
        out.println("  Transport: " + nvl(settings.transport(), "auto"));
        out.println("  Steering mode: " + nvl(settings.steeringMode(), "one-at-a-time"));
        out.println("  Follow-up mode: " + nvl(settings.followUpMode(), "one-at-a-time"));

        // Theme
        out.println("  Theme: " + nvl(settings.theme(), "default"));

        // UI
        out.println("  Hide thinking block: " + (settings.hideThinkingBlock() != null ? settings.hideThinkingBlock() : false));
        out.println("  Skill commands: " + (settings.enableSkillCommands() != null ? settings.enableSkillCommands() : true));

        // Shell
        if (settings.shellPath() != null) {
            out.println("  Shell: " + settings.shellPath());
        }

        // Compaction
        if (settings.compaction() != null) {
            var c = settings.compaction();
            out.println("  Compaction: " + (c.enabled() != null ? c.enabled() : true));
            if (c.reserveTokens() != null) out.println("    Reserve tokens: " + c.reserveTokens());
            if (c.keepRecentTokens() != null) out.println("    Keep recent: " + c.keepRecentTokens());
        } else {
            out.println("  Compaction: true (default)");
        }

        // Retry
        if (settings.retry() != null) {
            var r = settings.retry();
            out.println("  Retry: " + (r.enabled() != null ? r.enabled() : false));
            if (r.maxRetries() != null) out.println("    Max retries: " + r.maxRetries());
        }

        // Context window
        if (model != null && model.contextWindow() > 0) {
            out.println("  Context window: " + model.contextWindow());
        }
    }

    private static String nvl(String value, String defaultValue) {
        return value != null ? value : defaultValue;
    }
}
