/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.skill;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single installed skill entry in {@code .installed.json}.
 *
 * @param name        skill directory name
 * @param sourceType  how the skill was installed: "git" or "link"
 * @param gitUrl      the git clone URL (null for link-type)
 * @param localPath   the original local path (null for git-type)
 * @param installedAt ISO-8601 timestamp of installation
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstalledSkillRecord(
        @JsonProperty("name") String name,
        @JsonProperty("sourceType") String sourceType,
        @JsonProperty("gitUrl") String gitUrl,
        @JsonProperty("localPath") String localPath,
        @JsonProperty("installedAt") String installedAt) {
    public static final String SOURCE_GIT = "git";
    public static final String SOURCE_LINK = "link";
    public static final String SOURCE_ARCHIVE = "archive";
}
