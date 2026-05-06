/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt;

import java.nio.file.Path;

/**
 * A reusable prompt template loaded from a markdown file.
 *
 * @param name        template name (derived from filename, without .md extension)
 * @param description short description (from frontmatter or first line)
 * @param content     template body (after frontmatter)
 * @param filePath    absolute path to the template file
 * @param source      where the template was found ("user" or "project")
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public record PromptTemplateEntry(String name, String description, String content, Path filePath, String source) {}
