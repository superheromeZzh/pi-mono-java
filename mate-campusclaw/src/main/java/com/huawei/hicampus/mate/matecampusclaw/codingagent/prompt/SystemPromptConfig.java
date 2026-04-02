package com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.context.ContextFileLoader.ContextFile;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.Skill;

/**
 * Configuration for building the system prompt.
 *
 * @param tools              registered agent tools
 * @param skills             available skills
 * @param cwd                current working directory
 * @param customPrompt       user-supplied additional prompt text (may be null)
 * @param env                environment variables snapshot (may be null or empty)
 * @param contextFiles       AGENTS.md/CLAUDE.md context files (may be null or empty)
 * @param systemPromptOverride  content of SYSTEM.md if found (replaces base prompt; may be null)
 * @param appendSystemPrompt content of APPEND_SYSTEM.md if found (may be null)
 */
public record SystemPromptConfig(
        List<AgentTool> tools,
        List<Skill> skills,
        Path cwd,
        String customPrompt,
        Map<String, String> env,
        List<ContextFile> contextFiles,
        String systemPromptOverride,
        String appendSystemPrompt
) {
    public SystemPromptConfig {
        tools = tools != null ? List.copyOf(tools) : List.of();
        skills = skills != null ? List.copyOf(skills) : List.of();
        env = env != null ? Map.copyOf(env) : Map.of();
        contextFiles = contextFiles != null ? List.copyOf(contextFiles) : List.of();
    }

    /** Backwards-compatible constructor without context files, system override, or append. */
    public SystemPromptConfig(
            List<AgentTool> tools,
            List<Skill> skills,
            Path cwd,
            String customPrompt,
            Map<String, String> env
    ) {
        this(tools, skills, cwd, customPrompt, env, List.of(), null, null);
    }
}
