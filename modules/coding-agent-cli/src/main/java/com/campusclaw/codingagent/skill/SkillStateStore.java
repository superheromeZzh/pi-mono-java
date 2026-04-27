package com.campusclaw.codingagent.skill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persists per-skill enabled/disabled state in {@code .disabled.json}.
 *
 * <p>Default semantics: a skill not listed in the file is considered enabled.
 * Only explicitly disabled skill names are tracked.
 */
public class SkillStateStore {

    private static final Logger log = LoggerFactory.getLogger(SkillStateStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String STATE_FILE = ".disabled.json";

    private final Path skillsDir;
    private final ReentrantLock lock = new ReentrantLock();

    public SkillStateStore(Path skillsDir) {
        this.skillsDir = skillsDir;
    }

    /**
     * Loads the set of disabled skill names.
     * Returns an empty set if the file is missing or unreadable.
     */
    public Set<String> loadDisabled() {
        Path file = skillsDir.resolve(STATE_FILE);
        if (!Files.exists(file)) {
            return Collections.emptySet();
        }
        try {
            String json = Files.readString(file);
            List<String> list = MAPPER.readValue(json, new TypeReference<>() {});
            return new LinkedHashSet<>(list);
        } catch (IOException e) {
            log.warn("Failed to read skill state file: {}", file, e);
            return Collections.emptySet();
        }
    }

    public boolean isDisabled(String name) {
        return loadDisabled().contains(name);
    }

    /** Idempotently marks the skill as disabled. */
    public void disable(String name) {
        mutate(set -> set.add(name));
    }

    /** Idempotently marks the skill as enabled. */
    public void enable(String name) {
        mutate(set -> set.remove(name));
    }

    private void mutate(Consumer<Set<String>> op) {
        lock.lock();
        try {
            Set<String> set = new LinkedHashSet<>(loadDisabled());
            op.accept(set);
            save(set);
        } finally {
            lock.unlock();
        }
    }

    private void save(Set<String> set) {
        Path file = skillsDir.resolve(STATE_FILE);
        Path tmp = file.resolveSibling(STATE_FILE + ".tmp");
        try {
            Files.createDirectories(skillsDir);
            Files.writeString(tmp, MAPPER.writeValueAsString(new ArrayList<>(set)));
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write skill state file: " + file, e);
        }
    }
}
