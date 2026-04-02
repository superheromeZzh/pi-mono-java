package com.huawei.hicampus.mate.matecampusclaw.codingagent.session;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persists and restores agent session message history in JSONL format
 * (one JSON object per line).
 *
 * <p>Each line is a polymorphic {@link Message} serialized via Jackson,
 * using the {@code role} discriminator for type resolution.
 */
public class SessionPersistence {

    private final ObjectMapper objectMapper;

    public SessionPersistence(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Saves the given messages to a JSONL file. Parent directories are created
     * if they don't exist.
     *
     * @param sessionId  identifier written as a comment on the first line
     * @param messages   the conversation messages to persist
     * @param outputPath the file path to write to
     * @throws SessionPersistenceException if writing fails
     */
    public void save(String sessionId, List<Message> messages, Path outputPath) {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(outputPath, "outputPath");

        try {
            Path parent = outputPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
                for (Message message : messages) {
                    writer.write(objectMapper.writeValueAsString(message));
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new SessionPersistenceException("Failed to save session " + sessionId + " to " + outputPath, e);
        }
    }

    /**
     * Loads messages from a JSONL file. Each non-blank line is deserialized as
     * a polymorphic {@link Message}.
     *
     * @param inputPath the file to read from
     * @return list of deserialized messages in file order
     * @throws SessionPersistenceException if reading or parsing fails
     */
    public List<Message> load(Path inputPath) {
        Objects.requireNonNull(inputPath, "inputPath");

        if (!Files.exists(inputPath)) {
            throw new SessionPersistenceException("Session file does not exist: " + inputPath);
        }

        List<Message> messages = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(inputPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                try {
                    messages.add(objectMapper.readValue(line, Message.class));
                } catch (JsonProcessingException e) {
                    throw new SessionPersistenceException(
                            "Failed to parse message at line " + lineNumber + " in " + inputPath, e);
                }
            }
        } catch (SessionPersistenceException e) {
            throw e;
        } catch (IOException e) {
            throw new SessionPersistenceException("Failed to read session file: " + inputPath, e);
        }

        return messages;
    }
}
