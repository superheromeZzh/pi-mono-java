package com.campusclaw.codingagent.prompt;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.codingagent.context.ContextFileLoader.ContextFile;
import com.campusclaw.codingagent.skill.SkillPromptFormatter;

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
            你是 "CampusClaw"，一个智能园区管理助手。你帮助用户管理园区的人、物和事， \
            提供园区设施管理、人员管理、事务处理等智能化服务。

            核心职责：
            - 园区人员管理：员工、访客、权限管理等
            - 园区物资管理：设备、资产、库存管理等
            - 园区事务处理：报修、预约、通知、巡检等
            - 数据分析与报表：生成园区运营数据报表
            - 智能问答：解答园区相关问题和规章制度

            交流准则：
            - 使用中文回复用户
            - 称呼自己为 "CampusClaw"
            - 回应简洁友好，专业且有帮助
            - 主动询问细节以更好地理解用户需求
            - 涉及敏感操作时需确认用户身份和权限
            - 保持回复聚焦且可操作性强""";

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

        // 2. 工具说明
        if (!config.tools().isEmpty()) {
            sb.append("\n\n可用工具:\n");
            for (AgentTool tool : config.tools()) {
                sb.append("- ").append(tool.name()).append(": ").append(tool.description()).append('\n');
            }
        }

        // 3. 技能
        String skillsBlock = SkillPromptFormatter.format(config.skills());
        if (!skillsBlock.isEmpty()) {
            sb.append("\n\n# 技能\n\n");
            sb.append(skillsBlock);
        }

        // 4. 上下文文件 (AGENTS.md / CLAUDE.md)
        if (!config.contextFiles().isEmpty()) {
            sb.append("\n\n# 项目上下文\n\n");
            for (ContextFile cf : config.contextFiles()) {
                sb.append("## ").append(cf.path().getFileName()).append("\n\n");
                sb.append(cf.content().strip()).append("\n\n");
            }
        }

        // 5. 园区管理文档指引
        sb.append("\n\n园区管理知识库（当被问及园区管理制度、设施使用、人员规范时参考）:\n");
        sb.append("- 当被问及：园区规章制度、设施预约、人员管理、安全规范、报修流程\n");
        sb.append("- 处理园区事务时，查看 docs/ 目录中的相关管理文档\n");
        sb.append("- 阅读 .md 文件获取完整的园区管理规范，并遵循相关指引\n");

        // 6. 环境信息
        sb.append("\n\n# 环境信息\n\n");
        appendEnvironmentInfo(sb, config);

        // 6. APPEND_SYSTEM.md
        if (config.appendSystemPrompt() != null && !config.appendSystemPrompt().isBlank()) {
            sb.append("\n\n").append(config.appendSystemPrompt());
        }

        // 7. 用户自定义提示词 (来自 CLI --system-prompt / --append-system-prompt)
        if (config.customPrompt() != null && !config.customPrompt().isBlank()) {
            sb.append("\n\n# 用户指令\n\n");
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

        // 园区管理场景下的工具使用指南
        if (hasBash) {
            guidelines.append("\n- 使用 bash 工具执行系统命令进行数据处理或报表生成");
        }

        if (toolNames.contains("read")) {
            guidelines.append("\n- 读取配置文件和数据文件以了解园区当前状态");
        }

        if (toolNames.contains("edit") && toolNames.contains("write")) {
            guidelines.append("\n- 使用 edit 修改现有配置文件，使用 write 创建新的记录或报表");
        }

        if (hasGrep || hasGlob) {
            guidelines.append("\n- 使用 grep/glob 工具快速搜索园区数据和文档");
        }

        // 园区管理特殊提示
        guidelines.append("\n- 处理人员信息时注意保护隐私数据");
        guidelines.append("\n- 重要操作（如删除、修改权限）执行前需用户确认");

        return guidelines.toString();
    }

    private void appendEnvironmentInfo(StringBuilder sb, SystemPromptConfig config) {
        sb.append("- 当前日期: ").append(LocalDate.now()).append('\n');
        sb.append("- 工作目录: ").append(config.cwd()).append('\n');

        String os = config.env().getOrDefault("OS_NAME", System.getProperty("os.name", "unknown"));
        sb.append("- 操作系统: ").append(os).append('\n');

        String gitBranch = config.env().get("GIT_BRANCH");
        if (gitBranch != null && !gitBranch.isBlank()) {
            sb.append("- Git 分支: ").append(gitBranch).append('\n');
        }

        String javaVersion = config.env().getOrDefault("JAVA_VERSION",
                System.getProperty("java.version", "unknown"));
        sb.append("- Java 版本: ").append(javaVersion).append('\n');

        String shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            sb.append("- Shell: ").append(shell).append('\n');
        }

        String arch = System.getProperty("os.arch", "");
        if (!arch.isBlank()) {
            sb.append("- 系统架构: ").append(arch);
        }
    }
}
