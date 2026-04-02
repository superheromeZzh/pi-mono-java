package com.huawei.hicampus.mate.matecampusclaw.codingagent.pkg;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

/**
 * Discovers and manages extension packages (skills, tools, commands).
 * Supports npm-style packages and git-based packages.
 */
public class PackageManager {
    private static final Logger log = LoggerFactory.getLogger(PackageManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PackageManifest(
        @JsonProperty("name") String name,
        @JsonProperty("version") @Nullable String version,
        @JsonProperty("description") @Nullable String description,
        @JsonProperty("skills") @Nullable List<String> skills,
        @JsonProperty("tools") @Nullable List<String> tools,
        @JsonProperty("commands") @Nullable List<String> commands,
        @JsonProperty("repository") @Nullable String repository
    ) {}

    public record InstalledPackage(
        String name,
        String version,
        Path location,
        PackageManifest manifest
    ) {}

    private final Path packagesDir;
    private final Map<String, InstalledPackage> installed = new LinkedHashMap<>();

    public PackageManager(Path packagesDir) {
        this.packagesDir = packagesDir;
    }

    /** Scan packages directory for installed packages. */
    public void scan() {
        installed.clear();
        if (!Files.isDirectory(packagesDir)) return;

        try (var dirs = Files.list(packagesDir)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path manifestPath = dir.resolve("package.json");
                if (Files.exists(manifestPath)) {
                    try {
                        PackageManifest manifest = MAPPER.readValue(manifestPath.toFile(), PackageManifest.class);
                        String name = manifest.name() != null ? manifest.name() : dir.getFileName().toString();
                        String version = manifest.version() != null ? manifest.version() : "0.0.0";
                        installed.put(name, new InstalledPackage(name, version, dir, manifest));
                        log.debug("Found package: {} v{}", name, version);
                    } catch (IOException e) {
                        log.warn("Failed to read package manifest: {}", manifestPath, e);
                    }
                }
            });
        } catch (IOException e) {
            log.warn("Failed to scan packages directory: {}", packagesDir, e);
        }
    }

    /** Get all installed packages. */
    public List<InstalledPackage> getInstalled() {
        return List.copyOf(installed.values());
    }

    /** Get an installed package by name. */
    public Optional<InstalledPackage> get(String name) {
        return Optional.ofNullable(installed.get(name));
    }

    /** Check if a package is installed. */
    public boolean isInstalled(String name) {
        return installed.containsKey(name);
    }

    /** Get all skill paths from all packages. */
    public List<Path> getAllSkillPaths() {
        List<Path> paths = new ArrayList<>();
        for (InstalledPackage pkg : installed.values()) {
            if (pkg.manifest().skills() != null) {
                for (String skill : pkg.manifest().skills()) {
                    paths.add(pkg.location().resolve(skill));
                }
            }
            // Also check for skills/ directory
            Path skillsDir = pkg.location().resolve("skills");
            if (Files.isDirectory(skillsDir)) {
                try (var stream = Files.list(skillsDir)) {
                    stream.filter(p -> p.toString().endsWith(".md"))
                        .forEach(paths::add);
                } catch (IOException e) {
                    log.debug("Failed to list skills dir for {}", pkg.name(), e);
                }
            }
        }
        return paths;
    }
}
