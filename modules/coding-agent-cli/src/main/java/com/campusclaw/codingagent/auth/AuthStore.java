package com.campusclaw.codingagent.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import com.campusclaw.ai.types.Provider;
import com.campusclaw.codingagent.config.AppPaths;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Persists per-provider API keys (and other auth artefacts) in a separate
 * file from {@code settings.json} so it can be excluded from version control
 * and locked down to {@code 0600}.
 *
 * <p>File: {@code ~/.campusclaw/agent/auth.json}.
 *
 * <p>Schema mirrors opencode's auth.json:
 * <pre>{@code
 * {
 *   "anthropic": {"type": "api", "key": "sk-ant-..."},
 *   "zai":       {"type": "api", "key": "..."}
 * }
 * }</pre>
 *
 * <p>Today only the {@code "api"} type is implemented; {@code "oauth"} is left
 * as a stub for future provider-specific OAuth flows.
 */
@Service
public class AuthStore {

    private static final Logger log = LoggerFactory.getLogger(AuthStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path authFile;

    public AuthStore() {
        this(AppPaths.AUTH_FILE);
    }

    public AuthStore(Path authFile) {
        this.authFile = authFile;
    }

    /** Reads the API key persisted for {@code provider}, or empty if none. */
    public Optional<String> getApiKey(Provider provider) {
        var entry = read().get(provider.value());
        if (entry == null) { return Optional.empty(); }
        Object key = entry.get("key");
        if (key instanceof String s && !s.isBlank()) { return Optional.of(s); }
        return Optional.empty();
    }

    /** Persists an API key for {@code provider}, writing {@code 0600}. */
    public void setApiKey(Provider provider, String apiKey) {
        var all = read();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", "api");
        entry.put("key", apiKey);
        all.put(provider.value(), entry);
        write(all);
    }

    /** Removes any persisted credential for {@code provider}. */
    public boolean remove(Provider provider) {
        var all = read();
        if (all.remove(provider.value()) == null) { return false; }
        write(all);
        return true;
    }

    /** Lists provider ids that currently have a credential persisted. */
    public Map<String, String> listSummary() {
        var summary = new TreeMap<String, String>();
        for (var e : read().entrySet()) {
            Object type = e.getValue().get("type");
            summary.put(e.getKey(), type instanceof String s ? s : "api");
        }
        return summary;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> read() {
        if (!Files.exists(authFile)) { return new LinkedHashMap<>(); }
        try {
            return MAPPER.readValue(Files.readString(authFile),
                    new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("Failed to read auth file {}: {}", authFile, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void write(Map<String, Map<String, Object>> all) {
        try {
            Files.createDirectories(authFile.getParent());
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(all);
            Files.writeString(authFile, json);
            tightenPermissions(authFile);
        } catch (IOException e) {
            log.error("Failed to write auth file {}", authFile, e);
        }
    }

    private static void tightenPermissions(Path file) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Non-POSIX FS (e.g. Windows) — silently skip.
        }
    }
}
