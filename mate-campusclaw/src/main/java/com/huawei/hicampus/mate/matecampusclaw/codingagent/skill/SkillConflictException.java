/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.skill;

import java.util.List;

/**
 * Thrown when an install operation would introduce one or more skill name
 * conflicts with already-installed skills.
 *
 * <p>Maps to HTTP 409 Conflict at the REST layer.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SkillConflictException extends SkillInstallException {

    private final List<Conflict> conflicts;

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public SkillConflictException(List<Conflict> conflicts) {
        super(buildMessage(conflicts));
        this.conflicts = List.copyOf(conflicts);
    }

    @SuppressWarnings("checkstyle:java_doc_format_missing")
    public List<Conflict> conflicts() {
        return conflicts;
    }

    private static String buildMessage(List<Conflict> conflicts) {
        StringBuilder sb = new StringBuilder("Skill name conflict: ");
        for (int i = 0; i < conflicts.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Conflict c = conflicts.get(i);
            sb.append(c.skill())
                    .append(" (already in package ")
                    .append(c.existingPackage())
                    .append(")");
        }
        return sb.toString();
    }

    /**
     * Describes a single conflicting skill name.
     *
     * @param skill           the skill name that collides
     * @param existingPackage the package directory that already contains a skill with this name
     */
    public record Conflict(String skill, String existingPackage) {}
}
