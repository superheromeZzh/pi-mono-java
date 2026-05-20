/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.campusclaw.codingagent.config.AppPaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PromptTemplateLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsTemplatesWithFrontmatterDescription() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path globalPrompts = agentDir.resolve("prompts");
        Files.createDirectories(globalPrompts);
        Files.writeString(
                globalPrompts.resolve("greet.md"),
                """
                ---
                description: Friendly greeting template
                ---
                Hello, world!
                """,
                StandardCharsets.UTF_8);

        PromptTemplateLoader loader = new PromptTemplateLoader();
        List<PromptTemplateEntry> entries = loader.load(tempDir.resolve("cwd-without-prompts"), agentDir);

        assertThat(entries).hasSize(1);
        PromptTemplateEntry entry = entries.get(0);
        assertThat(entry.name()).isEqualTo("greet");
        assertThat(entry.description()).isEqualTo("Friendly greeting template");
        assertThat(entry.source()).isEqualTo("user");
        assertThat(entry.content()).contains("Hello, world!");
    }

    @Test
    void fallsBackToFirstNonEmptyLineWhenNoDescription() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path globalPrompts = agentDir.resolve("prompts");
        Files.createDirectories(globalPrompts);
        Files.writeString(
                globalPrompts.resolve("plain.md"),
                "\n\nFirst real line is the fallback description.\nSecond line.\n",
                StandardCharsets.UTF_8);

        PromptTemplateLoader loader = new PromptTemplateLoader();
        List<PromptTemplateEntry> entries = loader.load(tempDir.resolve("nothing-here"), agentDir);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).description()).isEqualTo("First real line is the fallback description.");
    }

    @Test
    void truncatesLongFallbackDescription() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path globalPrompts = agentDir.resolve("prompts");
        Files.createDirectories(globalPrompts);
        String longLine = "a".repeat(120);
        Files.writeString(globalPrompts.resolve("long.md"), longLine, StandardCharsets.UTF_8);

        PromptTemplateLoader loader = new PromptTemplateLoader();
        List<PromptTemplateEntry> entries = loader.load(tempDir.resolve("nope"), agentDir);

        assertThat(entries).hasSize(1);
        String desc = entries.get(0).description();
        assertThat(desc).endsWith("...");
        assertThat(desc).hasSize(63);
    }

    @Test
    void projectPromptsOverrideGlobalsWithSameName() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path globalPrompts = agentDir.resolve("prompts");
        Files.createDirectories(globalPrompts);
        Files.writeString(
                globalPrompts.resolve("review.md"),
                "---\ndescription: Global review\n---\nbody-global\n",
                StandardCharsets.UTF_8);

        Path cwd = tempDir.resolve("cwd");
        Path projectPrompts = cwd.resolve(AppPaths.CONFIG_DIR_NAME).resolve("prompts");
        Files.createDirectories(projectPrompts);
        Files.writeString(
                projectPrompts.resolve("review.md"),
                "---\ndescription: Project review override\n---\nbody-project\n",
                StandardCharsets.UTF_8);

        PromptTemplateLoader loader = new PromptTemplateLoader();
        List<PromptTemplateEntry> entries = loader.load(cwd, agentDir);

        assertThat(entries).hasSize(1);
        PromptTemplateEntry entry = entries.get(0);
        assertThat(entry.description()).isEqualTo("Project review override");
        assertThat(entry.source()).isEqualTo("project");
    }

    @Test
    void missingGlobalDirYieldsEmpty() throws Exception {
        PromptTemplateLoader loader = new PromptTemplateLoader();
        List<PromptTemplateEntry> entries = loader.load(tempDir.resolve("nope-cwd"), tempDir.resolve("nope-agent"));
        assertThat(entries).isEmpty();
    }

    @Test
    void ignoresNonMarkdownFiles() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path globalPrompts = agentDir.resolve("prompts");
        Files.createDirectories(globalPrompts);
        Files.writeString(globalPrompts.resolve("README.txt"), "ignored", StandardCharsets.UTF_8);
        Files.writeString(globalPrompts.resolve("ok.md"), "ok body", StandardCharsets.UTF_8);

        PromptTemplateLoader loader = new PromptTemplateLoader();
        List<PromptTemplateEntry> entries = loader.load(tempDir.resolve("nope"), agentDir);
        assertThat(entries).extracting(PromptTemplateEntry::name).containsExactly("ok");
    }

    @Test
    void loadFromFileSkipsUnreadableFile() throws Exception {
        PromptTemplateLoader loader = new PromptTemplateLoader();
        PromptTemplateEntry entry = loader.loadFromFile(tempDir.resolve("does-not-exist.md"), "user");
        assertThat(entry).isNull();
    }

    @Test
    void recursivelyDiscoversNestedTemplates() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path nested = agentDir.resolve("prompts").resolve("nested");
        Files.createDirectories(nested);
        Files.writeString(
                nested.resolve("deep.md"), "---\ndescription: Deeply nested\n---\nbody\n", StandardCharsets.UTF_8);

        PromptTemplateLoader loader = new PromptTemplateLoader();
        List<PromptTemplateEntry> entries = loader.load(tempDir.resolve("nope"), agentDir);
        assertThat(entries).extracting(PromptTemplateEntry::name).containsExactly("deep");
    }
}
