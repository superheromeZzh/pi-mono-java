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

/**
 * Exports conversation history as JSONL (one JSON object per line).
 * Usage: /export [filename]
 * Default filename: pi-export-{timestamp}.jsonl
 */
public class ExportCommand implements SlashCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .disable(SerializationFeature.INDENT_OUTPUT);

    @Override
    public String name() {
        return "export";
    }

    @Override
    public String description() {
        return "Export conversation as JSONL";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        var messages = context.session().getHistory();
        if (messages.isEmpty()) {
            context.output().println("No messages to export.");
            return;
        }

        String filename = arguments != null && !arguments.isBlank()
                ? arguments.trim()
                : "campusclaw-export-" + LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".jsonl";

        Path outputPath = Path.of(System.getProperty("user.dir")).resolve(filename);

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            for (Message msg : messages) {
                writer.write(MAPPER.writeValueAsString(msg));
                writer.newLine();
            }
            context.output().println("Exported " + messages.size() + " messages to " + outputPath);
        } catch (IOException e) {
            context.output().println("Export failed: " + e.getMessage());
        }
    }
}
