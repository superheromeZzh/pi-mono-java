package com.huawei.hicampus.mate.matecampusclaw.codingagent.source;

import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import jakarta.annotation.Nullable;

/**
 * Tracks the origin and provenance of resources (skills, tools, commands, settings).
 * Enables users to understand where a resource came from and diagnose conflicts.
 */
public class SourceInfo {

    public enum SourceType {
        BUILTIN,       // Shipped with the application
        GLOBAL_CONFIG, // From ~/.campusclaw/agent/ directory
        PROJECT_CONFIG,// From .campusclaw/ in current project
        PACKAGE,       // From an installed package
        EXTENSION,     // From extension system
        CUSTOM         // User-provided custom resource
    }

    public record ResourceSource(
        String resourceId,
        String resourceType,   // "skill", "tool", "command", "setting", "keybinding"
        SourceType sourceType,
        @Nullable String packageName,
        @Nullable Path filePath,
        @Nullable Instant loadedAt,
        int priority           // Higher priority overrides lower
    ) implements Comparable<ResourceSource> {
        @Override
        public int compareTo(ResourceSource other) {
            return Integer.compare(other.priority, this.priority); // Higher first
        }
    }

    private final Map<String, List<ResourceSource>> registry = new LinkedHashMap<>();

    /** Register a resource source. */
    public void register(ResourceSource source) {
        registry.computeIfAbsent(source.resourceId(), k -> new ArrayList<>())
            .add(source);
    }

    /** Get the effective source (highest priority) for a resource. */
    public Optional<ResourceSource> getEffective(String resourceId) {
        List<ResourceSource> sources = registry.get(resourceId);
        if (sources == null || sources.isEmpty()) return Optional.empty();
        return sources.stream().min(Comparator.naturalOrder()); // highest priority first
    }

    /** Get all sources for a resource (shows overrides). */
    public List<ResourceSource> getSources(String resourceId) {
        List<ResourceSource> sources = registry.getOrDefault(resourceId, List.of());
        return sources.stream().sorted().toList();
    }

    /** Get all registered resource IDs. */
    public Set<String> getResourceIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /** Get all resources of a given type. */
    public List<ResourceSource> getByType(String resourceType) {
        return registry.values().stream()
            .flatMap(Collection::stream)
            .filter(s -> s.resourceType().equals(resourceType))
            .sorted()
            .toList();
    }

    /** Detect conflicts (multiple sources for same resource). */
    public Map<String, List<ResourceSource>> getConflicts() {
        Map<String, List<ResourceSource>> conflicts = new LinkedHashMap<>();
        for (var entry : registry.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.put(entry.getKey(), entry.getValue().stream().sorted().toList());
            }
        }
        return conflicts;
    }

    /** Format a resource source for display. */
    public static String format(ResourceSource source) {
        StringBuilder sb = new StringBuilder();
        sb.append(source.resourceId()).append(" (").append(source.resourceType()).append(")");
        sb.append(" from ").append(source.sourceType().name().toLowerCase().replace('_', ' '));
        if (source.packageName() != null) {
            sb.append(" [").append(source.packageName()).append("]");
        }
        if (source.filePath() != null) {
            sb.append(" @ ").append(source.filePath());
        }
        return sb.toString();
    }

    /** Clear all registered sources. */
    public void clear() {
        registry.clear();
    }
}
