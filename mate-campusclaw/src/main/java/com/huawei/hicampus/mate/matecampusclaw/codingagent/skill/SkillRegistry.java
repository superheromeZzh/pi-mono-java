package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Central registry that manages loaded {@link Skill}s.
 * Skills are keyed by name; registering a duplicate name overwrites the previous entry.
 */
public class SkillRegistry {

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    /**
     * Registers a single skill. Overwrites any existing skill with the same name.
     */
    public void register(Skill skill) {
        skills.put(skill.name(), skill);
    }

    /**
     * Registers all skills in the given list.
     */
    public void registerAll(List<Skill> skillList) {
        for (Skill skill : skillList) {
            register(skill);
        }
    }

    /**
     * Looks up a skill by name.
     */
    public Optional<Skill> getByName(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    /**
     * Returns all registered skills in registration order.
     */
    public List<Skill> getAll() {
        return new ArrayList<>(skills.values());
    }

    /**
     * Returns skills that should be visible in the system prompt
     * (i.e. those with {@code disableModelInvocation == false}).
     */
    public List<Skill> getVisibleSkills() {
        return skills.values().stream()
                .filter(s -> !s.disableModelInvocation())
                .toList();
    }

    /**
     * Removes all registered skills.
     */
    public void clear() {
        skills.clear();
    }
}
