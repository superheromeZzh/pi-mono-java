/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.export.HtmlExporter;

/**
 * Share session as a secret GitHub Gist.
 * Requires the `gh` CLI to be installed and authenticated.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class ShareCommand implements SlashCommand {

    private static final long GH_TIMEOUT_SECONDS = 30L;

    @Override
    public String name() {
        return "share";
    }

    @Override
    public String description() {
        return "Share session as a secret GitHub gist";
    }

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
                            "gh",
                            "gist",
                            "create",
                            "--desc",
                            "CampusClaw session (" + messages.size() + " messages)",
                            tmpFile.toString())
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(GH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                Files.deleteIfExists(tmpFile);
                context.output().println("gh gist create timed out after " + GH_TIMEOUT_SECONDS + "s");
                return;
            }
            int exitCode = process.exitValue();

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
