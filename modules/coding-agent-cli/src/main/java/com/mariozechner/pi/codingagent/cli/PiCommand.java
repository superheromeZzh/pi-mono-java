package com.mariozechner.pi.codingagent.cli;

import com.mariozechner.pi.agent.tool.AgentTool;
import com.mariozechner.pi.ai.PiAiService;
import com.mariozechner.pi.ai.model.ModelRegistry;
import com.mariozechner.pi.ai.types.*;
import com.mariozechner.pi.codingagent.command.SlashCommandRegistry;
import com.mariozechner.pi.codingagent.compaction.Compactor;
import com.mariozechner.pi.codingagent.mode.InteractiveMode;
import com.mariozechner.pi.codingagent.mode.OneShotMode;
import com.mariozechner.pi.codingagent.prompt.SystemPromptBuilder;
import com.mariozechner.pi.codingagent.session.AgentSession;
import com.mariozechner.pi.codingagent.session.SessionConfig;
import com.mariozechner.pi.codingagent.skill.SkillExpander;
import com.mariozechner.pi.codingagent.skill.SkillLoader;
import com.mariozechner.pi.codingagent.settings.Settings;
import com.mariozechner.pi.codingagent.settings.SettingsManager;
import com.mariozechner.pi.codingagent.tool.bash.BashExecutor;
import com.mariozechner.pi.tui.terminal.JLineTerminal;
import com.mariozechner.pi.tui.terminal.Terminal;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Main CLI command for Pi Coding Agent.
 * Parses command-line arguments and launches the agent in the requested mode.
 */
@Command(
        name = "pi",
        description = "Pi Coding Agent — an AI-powered software engineering assistant.",
        mixinStandardHelpOptions = true,
        version = "pi 0.1.0"
)
@Component
public class PiCommand implements Callable<Integer> {

    private final PiAiService piAiService;
    private final ModelRegistry modelRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final List<AgentTool> tools;
    private final SlashCommandRegistry commandRegistry;
    private final BashExecutor bashExecutor;
    private final SettingsManager settingsManager;

    public PiCommand(PiAiService piAiService, ModelRegistry modelRegistry,
                     SystemPromptBuilder promptBuilder, List<AgentTool> tools,
                     SlashCommandRegistry commandRegistry, BashExecutor bashExecutor,
                     SettingsManager settingsManager) {
        this.piAiService = piAiService;
        this.modelRegistry = modelRegistry;
        this.promptBuilder = promptBuilder;
        this.tools = tools;
        this.commandRegistry = commandRegistry;
        this.bashExecutor = bashExecutor;
        this.settingsManager = settingsManager;
    }

    @Option(names = {"-m", "--model"}, description = "AI model to use (e.g. claude-sonnet-4-20250514)")
    String model;

    @Option(names = {"-p", "--prompt"}, description = "Initial prompt to send to the agent")
    String prompt;

    @Option(names = {"--mode"}, description = "Execution mode: interactive, one-shot, or print",
            defaultValue = "interactive")
    String mode;

    @Option(names = {"--cwd"}, description = "Working directory (defaults to current directory)")
    Path cwd;

    @Option(names = {"--system-prompt"}, description = "Custom system prompt (replaces default)")
    String systemPrompt;

    @Option(names = {"--append-system-prompt"}, description = "Text appended to the system prompt")
    String appendSystemPrompt;

    @Option(names = {"--thinking"}, description = "Thinking level: off, minimal, low, medium, high, xhigh")
    String thinking;

    @Option(names = {"--tools"}, description = "Comma-separated list of tools to enable (e.g. read,bash,edit)")
    String toolsFilter;

    @Option(names = {"--verbose"}, description = "Enable verbose startup output")
    boolean verbose;

    @Parameters(description = "Prompt arguments (joined with spaces if no -p given). " +
            "Prefix with @ to include file contents.", arity = "0..*")
    List<String> promptArgs;

    @Override
    public Integer call() {
        String effectivePrompt = resolvePrompt();
        Path effectiveCwd = cwd != null ? cwd : Path.of(System.getProperty("user.dir"));

        // Load settings and apply defaults for model and thinking level
        Settings settings = settingsManager != null ? settingsManager.load() : Settings.empty();
        String effectiveModel = model;
        if (effectiveModel == null && settings.defaultModel() != null) {
            effectiveModel = settings.defaultModel();
        }
        String effectiveThinking = thinking;
        if (effectiveThinking == null && settings.defaultThinkingLevel() != null) {
            effectiveThinking = settings.defaultThinkingLevel();
        }

        // Register custom models from settings
        if (settings.customModels() != null) {
            for (Settings.CustomModelConfig cm : settings.customModels()) {
                if (cm.id() == null || cm.api() == null || cm.baseUrl() == null || cm.apiKey() == null) {
                    System.err.println("Warning: Skipping custom model with missing required fields (id, api, baseUrl, apiKey)");
                    continue;
                }
                List<InputModality> modalities = List.of(InputModality.TEXT);
                if (cm.inputModalities() != null) {
                    modalities = cm.inputModalities().stream()
                            .map(InputModality::fromValue)
                            .collect(Collectors.toList());
                }
                Model customModel = new Model(
                        cm.id(),
                        cm.name() != null ? cm.name() : cm.id(),
                        Api.fromValue(cm.api()),
                        Provider.CUSTOM,
                        cm.baseUrl(),
                        cm.reasoning() != null && cm.reasoning(),
                        modalities,
                        new ModelCost(0, 0, 0, 0),
                        cm.contextWindow() != null ? cm.contextWindow() : 128000,
                        cm.maxTokens() != null ? cm.maxTokens() : 8192,
                        null,
                        cm.thinkingFormat(),
                        cm.apiKey()
                );
                modelRegistry.register(customModel);
            }
        }

        if ("print".equals(mode)) {
            System.out.println("Model: " + effectiveModel);
            System.out.println("Mode: " + mode);
            System.out.println("CWD: " + effectiveCwd);
            System.out.println("Prompt: " + effectivePrompt);
            if (effectiveThinking != null) System.out.println("Thinking: " + effectiveThinking);
            if (toolsFilter != null) System.out.println("Tools: " + toolsFilter);
            return 0;
        }

        if ("one-shot".equals(mode) && effectivePrompt == null) {
            System.err.println("Error: --mode one-shot requires a prompt (-p or positional args)");
            return 1;
        }

        // Build session
        String effectiveSystemPrompt = systemPrompt;
        if (appendSystemPrompt != null && !appendSystemPrompt.isBlank()) {
            if (effectiveSystemPrompt == null) {
                effectiveSystemPrompt = appendSystemPrompt;
            } else {
                effectiveSystemPrompt = effectiveSystemPrompt + "\n\n" + appendSystemPrompt;
            }
        }

        // Filter tools if --tools specified
        List<AgentTool> effectiveTools = tools;
        if (toolsFilter != null && !toolsFilter.isBlank()) {
            var allowed = List.of(toolsFilter.split(","));
            effectiveTools = tools.stream()
                    .filter(t -> allowed.contains(t.name()))
                    .collect(Collectors.toList());
        }

        AgentSession session = new AgentSession(
                piAiService, modelRegistry, promptBuilder,
                new SkillLoader(), new SkillExpander(), effectiveTools
        );
        SessionConfig config = new SessionConfig(effectiveModel, effectiveCwd, effectiveSystemPrompt, mode);
        session.initialize(config);

        // Apply thinking level from CLI flag or settings default
        if (effectiveThinking != null) {
            try {
                ThinkingLevel level = ThinkingLevel.fromValue(effectiveThinking);
                session.getAgent().setThinkingLevel(level);
            } catch (IllegalArgumentException e) {
                System.err.println("Warning: Unknown thinking level '" + effectiveThinking
                        + "'. Valid: off, minimal, low, medium, high, xhigh");
            }
        }

        if ("one-shot".equals(mode)) {
            return new OneShotMode().run(session, effectivePrompt);
        }

        // Interactive mode (default)
        Terminal terminal = new JLineTerminal();
        try {
            new InteractiveMode(commandRegistry, bashExecutor, new Compactor(piAiService))
                    .run(session, terminal);
        } finally {
            terminal.close();
        }
        return 0;
    }

    /**
     * Resolves the effective prompt from the -p flag or positional arguments.
     * Supports @file syntax to include file contents.
     */
    String resolvePrompt() {
        if (prompt != null && !prompt.isBlank()) {
            return prompt;
        }
        if (promptArgs != null && !promptArgs.isEmpty()) {
            var parts = new ArrayList<String>();
            for (String arg : promptArgs) {
                if (arg.startsWith("@") && arg.length() > 1) {
                    // @file syntax: read file content
                    Path filePath = Path.of(arg.substring(1));
                    try {
                        parts.add(Files.readString(filePath));
                    } catch (IOException e) {
                        parts.add("[Error reading " + arg + ": " + e.getMessage() + "]");
                    }
                } else {
                    parts.add(arg);
                }
            }
            return String.join(" ", parts);
        }
        return null;
    }

    // --- Accessors for testing ---

    String getModel() { return model; }
    String getPrompt() { return prompt; }
    String getMode() { return mode; }
    Path getCwd() { return cwd; }
    String getSystemPrompt() { return systemPrompt; }
    List<String> getPromptArgs() { return promptArgs; }
    String getThinking() { return thinking; }
    String getToolsFilter() { return toolsFilter; }
    boolean isVerbose() { return verbose; }
    String getAppendSystemPrompt() { return appendSystemPrompt; }
}
