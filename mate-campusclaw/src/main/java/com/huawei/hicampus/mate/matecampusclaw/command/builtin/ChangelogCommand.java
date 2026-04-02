package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.Changelog;

/**
 * Show changelog entries.
 */
public class ChangelogCommand implements SlashCommand {

    @Override
    public String name() { return "changelog"; }

    @Override
    public String description() { return "Show changelog entries"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var changelog = new Changelog(AppPaths.USER_AGENT_DIR);
        changelog.loadFromResource("/changelog.json");

        var entries = changelog.getAll();
        if (entries.isEmpty()) {
            context.output().println("No changelog entries available.");
            return;
        }

        context.output().println(changelog.formatAll());
        changelog.markAllRead();
    }
}
