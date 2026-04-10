package com.huawei.hicampus.mate.matecampusclaw.codingagent.auth;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthStorage {
    private static final Logger log = LoggerFactory.getLogger(AuthStorage.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path AUTH_FILE = com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.AUTH_FILE;
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");

    /** Get credential for a provider. */
    public Optional<Credential> get(String provider) {
        return load().map(m -> m.get(provider));
    }

    /** Get API key string for a provider (from ApiKey credential or OAuth accessToken). */
    public Optional<String> getApiKey(String provider) {
        return get(provider).map(c -> switch (c) {
            case Credential.ApiKey ak -> ak.key();
            case Credential.OAuth oa -> oa.accessToken();
        });
    }

    /** Store a credential. */
    public void set(String provider, Credential credential) {
        var map = load().orElse(new LinkedHashMap<>());
        map.put(provider, credential);
        save(map);
    }

    /** Remove a credential. */
    public void remove(String provider) {
        var map = load().orElse(new LinkedHashMap<>());
        map.remove(provider);
        save(map);
    }

    /** Check if a provider has credentials stored. */
    public boolean has(String provider) {
        return load().map(m -> m.containsKey(provider)).orElse(false);
    }

    /** List all stored provider names. */
    public Set<String> list() {
        return load().map(Map::keySet).orElse(Set.of());
    }

    private Optional<Map<String, Credential>> load() {
        if (!Files.exists(AUTH_FILE)) return Optional.empty();
        try {
            String json = Files.readString(AUTH_FILE);
            Map<String, Credential> map = MAPPER.readValue(json, new TypeReference<>() {});
            return Optional.of(new LinkedHashMap<>(map));
        } catch (Exception e) {
            log.warn("Failed to read auth file: {}", AUTH_FILE, e);
            return Optional.empty();
        }
    }

    private void save(Map<String, Credential> map) {
        try {
            Files.createDirectories(AUTH_FILE.getParent());
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
            Files.writeString(AUTH_FILE, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // Set owner-only permissions (600)
            try {
                Files.setPosixFilePermissions(AUTH_FILE, OWNER_ONLY);
            } catch (UnsupportedOperationException e) {
                // Windows doesn't support POSIX permissions
            }
        } catch (IOException e) {
            log.error("Failed to save auth file: {}", AUTH_FILE, e);
        }
    }
}
