package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;

/**
 * Reloads skills and settings matching campusclaw TS /reload command.
 */
public class ReloadCommand implements SlashCommand {

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String description() {
        return "Reload skills and settings";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var session = context.session();
        context.output().println("Reloading skills, templates, and system prompt...");
        try {
            session.reload();
            int skillCount = session.getSkillRegistry().getAll().size();
            int templateCount = session.getPromptTemplates().size();
            context.output().println("Reloaded: " + skillCount + " skill(s), "
                    + templateCount + " template(s). System prompt rebuilt.");
        } catch (Exception e) {
            context.output().println("Reload failed: " + e.getMessage());
        }
    }
}
