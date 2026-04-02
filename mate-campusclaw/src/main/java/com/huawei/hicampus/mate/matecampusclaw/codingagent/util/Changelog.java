package com.huawei.hicampus.mate.matecampusclaw.codingagent.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

/**
 * Manages application changelog entries.
 * Loads from bundled changelog.json and supports user-read tracking.
 */
public class Changelog {
    private static final Logger log = LoggerFactory.getLogger(Changelog.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChangelogEntry(
        @JsonProperty("version") String version,
        @JsonProperty("date") String date,
        @JsonProperty("title") @Nullable String title,
        @JsonProperty("changes") List<Change> changes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(
        @JsonProperty("type") String type,       // "feature", "fix", "improvement", "breaking"
        @JsonProperty("description") String description
    ) {}

    private final List<ChangelogEntry> entries = new ArrayList<>();
    private @Nullable String lastReadVersion;
    private final Path stateFile;

    public Changelog(Path stateDir) {
        this.stateFile = stateDir.resolve("changelog-read.txt");
        loadReadState();
    }

    /** Load changelog entries from a JSON file. */
    public void loadFromFile(Path path) {
        try {
            List<ChangelogEntry> loaded = MAPPER.readValue(path.toFile(),
                new TypeReference<List<ChangelogEntry>>() {});
            entries.clear();
            entries.addAll(loaded);
            entries.sort((a, b) -> b.date().compareTo(a.date())); // newest first
        } catch (IOException e) {
            log.warn("Failed to load changelog: {}", path, e);
        }
    }

    /** Load changelog from classpath resource. */
    public void loadFromResource(String resourcePath) {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.debug("Changelog resource not found: {}", resourcePath);
                return;
            }
            List<ChangelogEntry> loaded = MAPPER.readValue(is,
                new TypeReference<List<ChangelogEntry>>() {});
            entries.clear();
            entries.addAll(loaded);
            entries.sort((a, b) -> b.date().compareTo(a.date()));
        } catch (IOException e) {
            log.warn("Failed to load changelog resource: {}", resourcePath, e);
        }
    }

    /** Get all entries. */
    public List<ChangelogEntry> getAll() {
        return Collections.unmodifiableList(entries);
    }

    /** Get entries newer than the last read version. */
    public List<ChangelogEntry> getUnread() {
        if (lastReadVersion == null) return List.copyOf(entries);
        List<ChangelogEntry> unread = new ArrayList<>();
        for (ChangelogEntry entry : entries) {
            if (entry.version().equals(lastReadVersion)) break;
            unread.add(entry);
        }
        return unread;
    }

    /** Check if there are unread changes. */
    public boolean hasUnread() {
        return !getUnread().isEmpty();
    }

    /** Mark all entries as read up to the latest version. */
    public void markAllRead() {
        if (!entries.isEmpty()) {
            lastReadVersion = entries.get(0).version();
            saveReadState();
        }
    }

    /** Format a changelog entry for terminal display. */
    public static String format(ChangelogEntry entry) {
        var sb = new StringBuilder();
        sb.append("\033[1m").append(entry.version());
        if (entry.title() != null) {
            sb.append(" — ").append(entry.title());
        }
        sb.append("\033[0m");
        sb.append(" \033[2m(").append(entry.date()).append(")\033[0m\n");
        for (Change change : entry.changes()) {
            String icon = switch (change.type()) {
                case "feature" -> "\033[32m✦\033[0m";
                case "fix" -> "\033[31m✦\033[0m";
                case "improvement" -> "\033[33m✦\033[0m";
                case "breaking" -> "\033[91m⚠\033[0m";
                default -> "•";
            };
            sb.append("  ").append(icon).append(" ").append(change.description()).append('\n');
        }
        return sb.toString();
    }

    /** Format all entries. */
    public String formatAll() {
        var sb = new StringBuilder();
        for (ChangelogEntry entry : entries) {
            sb.append(format(entry)).append('\n');
        }
        return sb.toString();
    }

    private void loadReadState() {
        if (Files.exists(stateFile)) {
            try {
                lastReadVersion = Files.readString(stateFile).trim();
            } catch (IOException e) {
                log.debug("Failed to load changelog read state", e);
            }
        }
    }

    private void saveReadState() {
        if (lastReadVersion == null) return;
        try {
            Files.createDirectories(stateFile.getParent());
            Files.writeString(stateFile, lastReadVersion);
        } catch (IOException e) {
            log.debug("Failed to save changelog read state", e);
        }
    }
}
