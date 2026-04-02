package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.export.HtmlExporter;

/**
 * Share session as a secret GitHub Gist.
 * Requires the `gh` CLI to be installed and authenticated.
 */
public class ShareCommand implements SlashCommand {

    @Override
    public String name() { return "share"; }

    @Override
    public String description() { return "Share session as a secret GitHub gist"; }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var session = context.session();
        var messages = session.getHistory();

        if (messages.isEmpty()) {
            context.output().println("No messages to share.");
            return;
        }

        // Export to temp HTML file
        String modelId = session.getModelId();
        String html = HtmlExporter.export(messages, "CampusClaw Session", modelId);

        try {
            Path tmpFile = Files.createTempFile("campusclaw-share-", ".html");
            Files.writeString(tmpFile, html);

            // Use gh CLI to create a gist
            var process = new ProcessBuilder(
                    "gh", "gist", "create",
                    "--desc", "CampusClaw session (" + messages.size() + " messages)",
                    tmpFile.toString()
            ).redirectErrorStream(true).start();

            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exitCode = process.waitFor();

            Files.deleteIfExists(tmpFile);

            if (exitCode == 0 && !output.isEmpty()) {
                context.output().println("Session shared: " + output);
            } else {
                context.output().println("Failed to create gist. Is `gh` CLI installed and authenticated?");
                if (!output.isEmpty()) {
                    context.output().println(output);
                }
            }
        } catch (IOException | InterruptedException e) {
            context.output().println("Share failed: " + e.getMessage());
            context.output().println("Install GitHub CLI: https://cli.github.com");
        }
    }
}
