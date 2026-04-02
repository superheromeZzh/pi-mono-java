package com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.context.ContextFileLoader.ContextFile;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.SkillPromptFormatter;

import org.springframework.stereotype.Service;

/**
 * Builds the system prompt from base instructions, tool descriptions,
 * skill listings, environment info, context files, and user customizations.
 *
 * <p>Generates conditional guidelines based on available tools,
 * matching the campusclaw TS system prompt builder.
 */
@Service
public class SystemPromptBuilder {

    static final String BASE_PROMPT = """
            You are an expert coding assistant operating inside CampusClaw, a coding agent harness. \
            You help users by reading files, executing commands, editing code, and writing new files.

            Key guidelines:
            - Be concise in your responses
            - Show file paths clearly when working with files
            - Prefer editing existing files over creating new ones
            - Understand existing code before suggesting modifications
            - Do not introduce security vulnerabilities
            - Do not list available tools or commands in your response — \
            the user already has access to /help for that
            - Keep responses focused and actionable""";

    /**
     * Builds the complete system prompt from the given configuration.
     *
     * <p>If {@code config.systemPromptOverride()} is set (from SYSTEM.md),
     * it replaces the default base prompt entirely.
     *
     * @param config the prompt configuration
     * @return the assembled system prompt string
     */
    public String build(SystemPromptConfig config) {
        var sb = new StringBuilder();

        // 1. Base role definition (or SYSTEM.md override)
        if (config.systemPromptOverride() != null && !config.systemPromptOverride().isBlank()) {
            sb.append(config.systemPromptOverride());
        } else {
            sb.append(BASE_PROMPT);

            // Conditional guidelines only apply to default prompt
            String conditionalGuidelines = buildConditionalGuidelines(config);
            if (!conditionalGuidelines.isEmpty()) {
                sb.append('\n').append(conditionalGuidelines);
            }
        }

        // 2. Tool descriptions (one-line snippets, matching campusclaw TS)
        if (!config.tools().isEmpty()) {
            sb.append("\n\nAvailable tools:\n");
            for (AgentTool tool : config.tools()) {
                sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append('\n');
            }
        }

        // 3. Skills
        String skillsBlock = SkillPromptFormatter.format(config.skills());
        if (!skillsBlock.isEmpty()) {
            sb.append("\n\n# Skills\n\n");
            sb.append(skillsBlock);
        }

        // 4. Context files (AGENTS.md / CLAUDE.md)
        if (!config.contextFiles().isEmpty()) {
            sb.append("\n\n# Project Context\n\n");
            for (ContextFile cf : config.contextFiles()) {
                sb.append("## ").append(cf.path().getFileName()).append("\n\n");
                sb.append(cf.content().strip()).append("\n\n");
            }
        }

        // 5. Pi documentation guidance (matching campusclaw system prompt)
        sb.append("\n\nCampusClaw documentation (read only when asked about CampusClaw itself, extensions, themes, skills, or TUI):\n");
        sb.append("- When asked about: extensions, themes, skills, prompt templates, TUI, keybindings, SDK, custom providers, models, packages\n");
        sb.append("- When working on CampusClaw topics, check for docs/ and examples/ directories in the project before implementing\n");
        sb.append("- Always read .md files completely and follow links to related docs\n");

        // 6. Environment info
        sb.append("\n\n# Environment\n\n");
        appendEnvironmentInfo(sb, config);

        // 6. APPEND_SYSTEM.md
        if (config.appendSystemPrompt() != null && !config.appendSystemPrompt().isBlank()) {
            sb.append("\n\n").append(config.appendSystemPrompt());
        }

        // 7. User custom prompt (from CLI --system-prompt / --append-system-prompt)
        if (config.customPrompt() != null && !config.customPrompt().isBlank()) {
            sb.append("\n\n# User Instructions\n\n");
            sb.append(config.customPrompt());
        }

        return sb.toString();
    }

    /**
     * Generates conditional guidelines based on which tools are available.
     * Matches campusclaw TS conditional guideline generation.
     */
    String buildConditionalGuidelines(SystemPromptConfig config) {
        Set<String> toolNames = new LinkedHashSet<>();
        for (var tool : config.tools()) {
            toolNames.add(tool.name());
        }

        var guidelines = new StringBuilder();

        boolean hasBash = toolNames.contains("bash");
        boolean hasGrep = toolNames.contains("grep");
        boolean hasGlob = toolNames.contains("glob");
        boolean hasLs = toolNames.contains("ls");
        boolean hasFind = hasGlob; // glob serves as find equivalent

        if (hasBash && (hasGrep || hasFind || hasLs)) {
            guidelines.append("\n- Prefer grep/glob/ls tools over bash for file exploration (faster, respects .gitignore)");
        } else if (hasBash) {
            guidelines.append("\n- Use bash for file operations like ls, rg, find");
        }

        if (toolNames.contains("edit") && toolNames.contains("write")) {
            guidelines.append("\n- Use edit for modifying existing files, write for creating new ones");
        }

        if (toolNames.contains("read")) {
            guidelines.append("\n- Read files before making changes to understand existing code");
        }

        return guidelines.toString();
    }

    private void appendEnvironmentInfo(StringBuilder sb, SystemPromptConfig config) {
        sb.append("- Current date: ").append(LocalDate.now()).append('\n');
        sb.append("- Working directory: ").append(config.cwd()).append('\n');

        String os = config.env().getOrDefault("OS_NAME", System.getProperty("os.name", "unknown"));
        sb.append("- Operating system: ").append(os).append('\n');

        String gitBranch = config.env().get("GIT_BRANCH");
        if (gitBranch != null && !gitBranch.isBlank()) {
            sb.append("- Git branch: ").append(gitBranch).append('\n');
        }

        String javaVersion = config.env().getOrDefault("JAVA_VERSION",
                System.getProperty("java.version", "unknown"));
        sb.append("- Java version: ").append(javaVersion).append('\n');

        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            sb.append("- Shell: ").append(shell).append('\n');
        }

        String arch = System.getProperty("os.arch", "");
        if (!arch.isBlank()) {
            sb.append("- Architecture: ").append(arch);
        }
    }
}
