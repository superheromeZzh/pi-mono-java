package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandRegistry;

public class HelpCommand implements SlashCommand {

    private final SlashCommandRegistry registry;

    public HelpCommand(SlashCommandRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "List all available commands";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var out = context.output();
        out.println("Available commands:");
        for (SlashCommand cmd : registry.getAll()) {
            out.println("  /" + cmd.name() + " - " + cmd.description());
        }

        // Skills
        var skills = context.session().getSkillRegistry().getAll();
        if (!skills.isEmpty()) {
            out.println("");
            out.println("Skills (invoke with /skill:<name>):");
            for (var skill : skills) {
                out.println("  /skill:" + skill.name() + " - " + skill.description());
            }
        }

        // Prompt templates
        var templates = context.session().getPromptTemplates();
        if (!templates.isEmpty()) {
            out.println("");
            out.println("Prompt templates:");
            for (var t : templates) {
                out.println("  /" + t.name() + " - " + t.description());
            }
        }
    }
}
