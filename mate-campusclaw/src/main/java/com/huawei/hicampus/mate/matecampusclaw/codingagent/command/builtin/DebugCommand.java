/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dumps agent state and messages to a debug log file for troubleshooting.
 * Matches campusclaw TS /debug command.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class DebugCommand implements SlashCommand {

    private static final Logger log = LoggerFactory.getLogger(DebugCommand.class);

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String name() {
        return "debug";
    }

    @Override
    public String description() {
        return "Dump agent state to debug log";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var session = context.session();
        var state = session.getAgent().getState();

        // Build debug output
        var sb = new StringBuilder();
        sb.append("=== CampusClaw Debug Dump ===\n");
        sb.append("Timestamp: ").append(LocalDateTime.now()).append("\n");

        var model = state.getModel();
        if (model != null) {
            sb.append("Model: ").append(model.id()).append("\n");
            sb.append("Provider: ").append(model.provider().name()).append("\n");
            sb.append("Context window: ").append(model.contextWindow()).append("\n");
        }

        var thinkingLevel = state.getThinkingLevel();
        sb.append("Thinking: ")
                .append(thinkingLevel != null ? thinkingLevel.value() : "off")
                .append("\n");
        sb.append("Streaming: ").append(state.isStreaming()).append("\n");

        String error = state.getError();
        if (error != null) {
            sb.append("Error: ").append(error).append("\n");
        }

        sb.append("\n=== Messages (").append(state.getMessages().size()).append(") ===\n");
        try {
            for (Message msg : state.getMessages()) {
                sb.append(MAPPER.writeValueAsString(msg)).append("\n");
            }
        } catch (Exception e) {
            sb.append("Error serializing messages: ").append(e.getMessage()).append("\n");
        }

        sb.append("\n=== System Prompt ===\n");
        String systemPrompt = state.getSystemPrompt();
        if (systemPrompt != null) {
            sb.append(systemPrompt).append("\n");
        }

        // Write to file
        Path debugDir = com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.USER_AGENT_DIR;
        try {
            Files.createDirectories(debugDir);
        } catch (IOException e) {
            // pre-create best-effort; the write below will surface the real failure
            log.debug("could not pre-create debug dir {}", debugDir, e);
        }

        String filename =
                "debug-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".log";
        Path debugFile = debugDir.resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(debugFile)) {
            writer.write(sb.toString());
            context.output().println("Debug dump written to " + debugFile);
        } catch (IOException e) {
            context.output().println("Failed to write debug dump: " + e.getMessage());
        }
    }
}
