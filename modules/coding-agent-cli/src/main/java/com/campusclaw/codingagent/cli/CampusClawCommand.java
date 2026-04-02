package com.campusclaw.codingagent.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.campusclaw.agent.proxy.ProxyConfig;
import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.ai.CampusClawAiService;
import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.*;
import com.campusclaw.codingagent.command.SlashCommandRegistry;
import com.campusclaw.codingagent.compaction.Compactor;
import com.campusclaw.codingagent.config.ConfigValueResolver;
import com.campusclaw.codingagent.mode.InteractiveMode;
import com.campusclaw.codingagent.mode.OneShotMode;
import com.campusclaw.codingagent.mode.rpc.RpcMode;
import com.campusclaw.codingagent.mode.server.ServerMode;
import com.campusclaw.codingagent.prompt.SystemPromptBuilder;
import com.campusclaw.codingagent.session.AgentSession;
import com.campusclaw.codingagent.session.SessionConfig;
import com.campusclaw.codingagent.session.SessionManager;
import com.campusclaw.codingagent.settings.Settings;
import com.campusclaw.codingagent.settings.SettingsManager;
import com.campusclaw.codingagent.skill.SkillExpander;
import com.campusclaw.codingagent.skill.SkillInstallException;
import com.campusclaw.codingagent.skill.SkillLoader;
import com.campusclaw.codingagent.skill.SkillManager;
import com.campusclaw.codingagent.tool.bash.BashExecutor;
import com.campusclaw.tui.terminal.JLineTerminal;
import com.campusclaw.tui.terminal.Terminal;

import org.springframework.stereotype.Component;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Main CLI command for CampusClaw.
 * Parses command-line arguments and launches the agent in the requested mode.
 */
@Command(
        name = "campusclaw",
        description = "CampusClaw — an AI-powered software engineering assistant.",
        mixinStandardHelpOptions = true,
        version = "pi 0.1.0"
)
@Component
public class CampusClawCommand implements Callable<Integer> {

    private final CampusClawAiService piAiService;
    private final ModelRegistry modelRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final List<AgentTool> tools;
    private final SlashCommandRegistry commandRegistry;
    private final BashExecutor bashExecutor;
    private final SettingsManager settingsManager;
    private final com.campusclaw.cron.CronService cronService;
    private final com.campusclaw.codingagent.loop.LoopManager loopManager;

    public CampusClawCommand(CampusClawAiService piAiService, ModelRegistry modelRegistry,
                     SystemPromptBuilder promptBuilder, List<AgentTool> tools,
                     SlashCommandRegistry commandRegistry, BashExecutor bashExecutor,
                     SettingsManager settingsManager,
                     @org.springframework.lang.Nullable com.campusclaw.cron.CronService cronService,
                     com.campusclaw.codingagent.loop.LoopManager loopManager) {
        this.piAiService = piAiService;
        this.modelRegistry = modelRegistry;
        this.promptBuilder = promptBuilder;
        this.tools = tools;
        this.commandRegistry = commandRegistry;
        this.bashExecutor = bashExecutor;
        this.settingsManager = settingsManager;
        this.cronService = cronService;
        this.loopManager = loopManager;
    }

    @Option(names = {"--provider"}, description = "Provider name (e.g. anthropic, openai, zai, google)")
    String provider;

    @Option(names = {"-m", "--model"}, description = "AI model to use (e.g. claude-sonnet-4-20250514)")
    String model;

    @Option(names = {"--api-key"}, description = "API key (overrides env vars)")
    String apiKey;

    @Option(names = {"--prompt"}, description = "Initial prompt to send to the agent (internal use)")
    String prompt;

    @Option(names = {"--mode"}, description = "Execution mode: interactive, one-shot, rpc, server, or print",
            defaultValue = "interactive")
    String mode;

    @Option(names = {"--port"}, description = "HTTP server port (for server mode)")
    Integer port;

    @Option(names = {"--proxy"}, description = "HTTP/SOCKS5 proxy URL (e.g. http://127.0.0.1:7890)")
    String proxy;

    @Option(names = {"--cwd"}, description = "Working directory (defaults to current directory)")
    Path cwd;

    @Option(names = {"--system-prompt"}, description = "Custom system prompt (replaces default)")
    String systemPrompt;

    @Option(names = {"--append-system-prompt"}, description = "Text appended to the system prompt")
    String appendSystemPrompt;

    @Option(names = {"--thinking"}, description = "Thinking level: off, minimal, low, medium, high, xhigh")
    String thinking;

    @Option(names = {"--models"}, description = "Comma-separated model patterns for Ctrl+P cycling")
    String modelsFilter;

    @Option(names = {"--tools"}, description = "Comma-separated list of tools to enable (e.g. read,bash,edit)")
    String toolsFilter;

    @Option(names = {"--no-tools"}, description = "Disable all built-in tools")
    boolean noTools;

    @Option(names = {"-p", "--print"}, description = "Non-interactive mode: process prompt and exit")
    boolean printMode;

    @Option(names = {"-c", "--continue"}, description = "Continue previous session")
    boolean continueSession;

    @Option(names = {"-r", "--resume"}, description = "Select a session to resume")
    boolean resumeSession;

    @Option(names = {"--session"}, description = "Use specific session file")
    Path sessionPath;

    @Option(names = {"--fork"}, description = "Fork specific session file into a new session")
    Path forkPath;

    @Option(names = {"--no-session"}, description = "Don't save session (ephemeral)")
    boolean noSession;

    @Option(names = {"--export"}, description = "Export session file to HTML", arity = "1..2")
    List<String> exportArgs;

    @Option(names = {"--list-models"}, description = "List available models and exit", arity = "0..1",
            fallbackValue = "")
    String listModels;

    @Option(names = {"--offline"}, description = "Disable startup network operations")
    boolean offline;

    @Option(names = {"--verbose"}, description = "Enable verbose startup output")
    boolean verbose;

    @Option(names = {"--cron-tick"}, description = "Execute due cron jobs and exit (for OS scheduler)", hidden = true)
    boolean cronTick;

    @Parameters(description = "Prompt arguments (joined with spaces if no -p given). " +
            "Prefix with @ to include file contents.", arity = "0..*")
    List<String> promptArgs;

    @Override
    public Integer call() {
        // --proxy: explicit proxy overrides auto-detection from main()
        if (proxy != null && !proxy.isBlank()) {
            ProxyConfig proxyConfig = ProxyConfig.fromUrl(proxy);
            if (proxyConfig.isConfigured()) {
                proxyConfig.installAsDefault();
            } else {
                System.err.println("Warning: invalid proxy URL: " + proxy);
            }
        }

        // --cron-tick: execute due cron jobs and exit (for launchd/crontab)
        if (cronTick) {
            return executeCronTick();
        }

        // Handle subcommands: skill, install, remove, uninstall, update, list, config
        if (promptArgs != null && !promptArgs.isEmpty()) {
            String first = promptArgs.get(0);
            if ("skill".equals(first)) {
                return handleSkillCommand(promptArgs.subList(1, promptArgs.size()));
            }
            if ("install".equals(first) || "remove".equals(first) || "uninstall".equals(first)
                    || "update".equals(first) || "list".equals(first) || "config".equals(first)) {
                return handlePackageCommand(first, promptArgs.subList(1, promptArgs.size()));
            }
        }

        String effectivePrompt = resolvePrompt();

        // Read piped stdin if not a TTY
        if (effectivePrompt == null && System.console() == null) {
            try {
                String piped = new String(System.in.readAllBytes()).trim();
                if (!piped.isEmpty()) {
                    effectivePrompt = piped;
                }
            } catch (IOException e) {
                // Ignore — stdin not available
            }
        }

        Path effectiveCwd = cwd != null ? cwd : Path.of(System.getProperty("user.dir"));

        // Ensure user-level config directories exist
        com.campusclaw.codingagent.config.AppPaths.ensureUserDirs();

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
                String resolvedApiKey = ConfigValueResolver.resolve(cm.apiKey());
                String resolvedBaseUrl = ConfigValueResolver.resolve(cm.baseUrl());
                Model customModel = new Model(
                        cm.id(),
                        cm.name() != null ? cm.name() : cm.id(),
                        Api.fromValue(cm.api()),
                        Provider.CUSTOM,
                        resolvedBaseUrl,
                        cm.reasoning() != null && cm.reasoning(),
                        modalities,
                        new ModelCost(0, 0, 0, 0),
                        cm.contextWindow() != null ? cm.contextWindow() : 128000,
                        cm.maxTokens() != null ? cm.maxTokens() : 8192,
                        null,
                        cm.thinkingFormat(),
                        resolvedApiKey
                );
                modelRegistry.register(customModel);
            }
        }

        // --export: export session file to HTML and exit
        if (exportArgs != null && !exportArgs.isEmpty()) {
            Path inputFile = Path.of(exportArgs.get(0));
            if (!Files.exists(inputFile)) {
                System.err.println("Session file not found: " + inputFile);
                return 1;
            }
            // Determine output path
            Path outputFile;
            if (exportArgs.size() > 1) {
                outputFile = Path.of(exportArgs.get(1));
            } else {
                String name = inputFile.getFileName().toString().replaceAll("\\.jsonl$", ".html");
                outputFile = inputFile.resolveSibling(name);
            }

            var sm = new SessionManager();
            var messages = sm.loadSession(inputFile);
            if (messages.isEmpty()) {
                System.err.println("No messages found in session file.");
                return 1;
            }

            String html = com.campusclaw.codingagent.export.HtmlExporter.export(
                    messages, "CampusClaw Session", sm.getSessionId());
            try {
                Files.writeString(outputFile, html);
                System.out.println("Exported " + messages.size() + " messages to " + outputFile);
            } catch (IOException e) {
                System.err.println("Failed to write HTML: " + e.getMessage());
                return 1;
            }
            return 0;
        }

        // --list-models: print available models and exit
        if (listModels != null) {
            String search = listModels.isBlank() ? null : listModels;
            var allModels = modelRegistry.getAllModels();
            allModels.sort(Comparator.comparing((Model m) -> m.provider().value())
                    .thenComparing(Model::id));
            for (var m : allModels) {
                if (search != null && !m.id().toLowerCase().contains(search.toLowerCase())
                        && !m.name().toLowerCase().contains(search.toLowerCase())) {
                    continue;
                }
                System.out.printf("  %-15s %-40s %s%n",
                        m.provider().value(), m.id(), m.name());
            }
            return 0;
        }

        // Validate conflicting session flags (matching campusclaw)
        int sessionFlagCount = 0;
        var conflicting = new ArrayList<String>();
        if (sessionPath != null) { sessionFlagCount++; conflicting.add("--session"); }
        if (continueSession) { sessionFlagCount++; conflicting.add("--continue"); }
        if (resumeSession) { sessionFlagCount++; conflicting.add("--resume"); }
        if (noSession) { sessionFlagCount++; conflicting.add("--no-session"); }
        if (sessionFlagCount > 1) {
            System.err.println("Error: conflicting flags: " + String.join(", ", conflicting));
            return 1;
        }

        // --print/-p flag makes this non-interactive (like campusclaw -p)
        if (printMode) {
            if (effectivePrompt == null) {
                System.err.println("Error: --print requires a prompt (positional args or piped stdin)");
                return 1;
            }
            mode = "one-shot";
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

        // Filter tools
        List<AgentTool> effectiveTools;
        if (noTools) {
            effectiveTools = List.of();
        } else if (toolsFilter != null && !toolsFilter.isBlank()) {
            var allowed = List.of(toolsFilter.split(","));
            effectiveTools = tools.stream()
                    .filter(t -> allowed.contains(t.name()))
                    .collect(Collectors.toList());
        } else {
            effectiveTools = tools;
        }

        AgentSession session = new AgentSession(
                piAiService, modelRegistry, promptBuilder,
                new SkillLoader(), new SkillExpander(), effectiveTools
        );

        // Session persistence (skip if --no-session)
        SessionManager sessionManager = noSession ? null : new SessionManager();
        if (sessionManager != null) {
            session.setSessionManager(sessionManager);
        }

        SessionConfig config = new SessionConfig(effectiveModel, effectiveCwd, effectiveSystemPrompt, mode);
        session.initialize(config);

        // Handle session flags: --session, --fork, --continue, --resume
        if (sessionManager != null) {
            if (sessionPath != null) {
                // --session: load specific session file
                var messages = sessionManager.loadSession(sessionPath);
                if (!messages.isEmpty()) {
                    for (var msg : messages) {
                        session.getAgent().getState().appendMessage(msg);
                    }
                    System.err.println("Loaded session " + sessionManager.getSessionId()
                            + " (" + messages.size() + " messages)");
                } else {
                    System.err.println("Warning: session file empty or invalid: " + sessionPath);
                    sessionManager.createSession(effectiveCwd.toString());
                }
            } else if (forkPath != null) {
                // --fork: load session, then create new file (fork)
                var messages = sessionManager.loadSession(forkPath);
                String originalId = sessionManager.getSessionId();
                sessionManager.createSession(effectiveCwd.toString());
                if (!messages.isEmpty()) {
                    for (var msg : messages) {
                        session.getAgent().getState().appendMessage(msg);
                        sessionManager.appendMessage(msg);
                    }
                    System.err.println("Forked session " + originalId + " → "
                            + sessionManager.getSessionId() + " (" + messages.size() + " messages)");
                }
            } else if (continueSession) {
                // --continue: resume latest session
                var messages = sessionManager.resumeLatestSession(effectiveCwd.toString());
                if (!messages.isEmpty()) {
                    for (var msg : messages) {
                        session.getAgent().getState().appendMessage(msg);
                    }
                    System.err.println("Resumed session " + sessionManager.getSessionId()
                            + " (" + messages.size() + " messages)");
                } else {
                    sessionManager.createSession(effectiveCwd.toString());
                }
            } else if (resumeSession) {
                // --resume: show list and let user pick (in non-interactive context, just list)
                System.err.println("Use /resume command in interactive mode to select a session.");
                sessionManager.createSession(effectiveCwd.toString());
            } else {
                sessionManager.createSession(effectiveCwd.toString());
            }
        }

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

        if ("rpc".equals(mode)) {
            new RpcMode(session).run();
            return 0;
        }

        if ("server".equals(mode)) {
            new ServerMode(piAiService, modelRegistry, promptBuilder,
                    effectiveTools, config, port != null ? port : 3000).run();
            return 0;
        }

        // Interactive mode (default)
        Terminal terminal = new JLineTerminal();
        try {
            var interactiveMode = new InteractiveMode(commandRegistry, bashExecutor, new Compactor(piAiService), modelRegistry, cronService, loopManager);

            // Resolve --models scoped models for Ctrl+P cycling
            if (modelsFilter != null && !modelsFilter.isBlank()) {
                var patterns = List.of(modelsFilter.split(","));
                var scoped = new ArrayList<Model>();
                var allRegistered = modelRegistry.getAllModels();
                for (String pattern : patterns) {
                    String p = pattern.trim().toLowerCase();
                    for (var m : allRegistered) {
                        if (matchesModelPattern(p, m) && scoped.stream().noneMatch(s -> ModelRegistry.modelsAreEqual(s, m))) {
                            scoped.add(m);
                        }
                    }
                }
                if (!scoped.isEmpty()) {
                    interactiveMode.setScopedModels(scoped);
                }
            } else if (settings.enabledModels() != null && !settings.enabledModels().isEmpty()) {
                // Also support enabledModels from settings
                var scoped = new ArrayList<Model>();
                var allRegistered = modelRegistry.getAllModels();
                for (String pattern : settings.enabledModels()) {
                    String p = pattern.trim().toLowerCase();
                    for (var m : allRegistered) {
                        if (matchesModelPattern(p, m) && scoped.stream().noneMatch(s -> ModelRegistry.modelsAreEqual(s, m))) {
                            scoped.add(m);
                        }
                    }
                }
                if (!scoped.isEmpty()) {
                    interactiveMode.setScopedModels(scoped);
                }
            }

            // Pass initial prompt to interactive mode if provided
            if (effectivePrompt != null) {
                interactiveMode.setInitialPrompt(effectivePrompt);
            }

            interactiveMode.run(session, terminal);
        } finally {
            terminal.close();
            if (sessionManager != null) sessionManager.close();
        }
        return 0;
    }

    /**
     * Matches a model against a pattern. Supports:
     * - Exact match: "glm-5"
     * - Glob-style: "glm-*", "*sonnet*"
     * - Provider prefix: "zai/*"
     * - Fuzzy substring: "sonnet" matches "claude-sonnet-4-20250514"
     */
    /**
     * Execute due cron jobs synchronously and exit.
     * Called by OS scheduler (launchd/crontab) via --cron-tick flag.
     */
    private Integer executeCronTick() {
        if (cronService == null) {
            System.err.println("Error: Cron service not available");
            return 1;
        }
        var store = new com.campusclaw.cron.store.CronStore();
        var processLock = store.acquireProcessLock();
        if (processLock == null) {
            System.err.println("Another cron-tick is already running, skipping");
            return 0;
        }
        try {
            var results = cronService.tickOnce();
            for (var r : results) {
                System.out.printf("[cron-tick] %s → %s%n", r.jobId(), r.status());
            }
            if (results.isEmpty()) {
                System.out.println("[cron-tick] No jobs due");
            }
            return 0;
        } catch (Exception e) {
            System.err.println("[cron-tick] Error: " + e.getMessage());
            return 1;
        } finally {
            store.releaseProcessLock(processLock);
        }
    }

    /**
     * Handles package management subcommands (install, remove, update, list, config).
     * Aligned with campusclaw's package management system.
     */
    private Integer handlePackageCommand(String command, List<String> args) {
        com.campusclaw.codingagent.config.AppPaths.ensureUserDirs();

        // Normalize "uninstall" → "remove"
        if ("uninstall".equals(command)) command = "remove";

        boolean local = args.contains("-l") || args.contains("--local");
        var filteredArgs = args.stream()
                .filter(a -> !"-l".equals(a) && !"--local".equals(a) && !"--help".equals(a))
                .toList();

        if (args.contains("--help")) {
            printPackageCommandHelp(command);
            return 0;
        }

        switch (command) {
            case "install" -> {
                if (filteredArgs.isEmpty()) {
                    System.err.println("Usage: pi install <source> [-l]");
                    return 1;
                }
                String source = filteredArgs.get(0);
                System.out.println("Installing package: " + source + (local ? " (local)" : " (global)"));
                // TODO: implement actual package installation (npm/git clone)
                System.out.println("Package installation is not yet fully implemented.");
                System.out.println("Add the source to your settings.json packages array manually:");
                System.out.println("  \"packages\": [\"" + source + "\"]");
                return 0;
            }
            case "remove" -> {
                if (filteredArgs.isEmpty()) {
                    System.err.println("Usage: pi remove <source> [-l]");
                    return 1;
                }
                String source = filteredArgs.get(0);
                System.out.println("Removing package: " + source);
                System.out.println("Remove the source from your settings.json packages array manually.");
                return 0;
            }
            case "update" -> {
                String source = filteredArgs.isEmpty() ? null : filteredArgs.get(0);
                if (source != null) {
                    System.out.println("Updating package: " + source);
                } else {
                    System.out.println("Updating all packages...");
                }
                System.out.println("Package update is not yet fully implemented.");
                return 0;
            }
            case "list" -> {
                System.out.println("Installed packages:");
                Settings settings = settingsManager != null ? settingsManager.load() : Settings.empty();
                if (settings.packages() != null && !settings.packages().isEmpty()) {
                    for (String pkg : settings.packages()) {
                        System.out.println("  " + pkg);
                    }
                } else {
                    System.out.println("  (none)");
                }
                return 0;
            }
            case "config" -> {
                System.out.println("Package config TUI is not yet implemented.");
                System.out.println("Edit settings.json manually to configure packages.");
                return 0;
            }
            default -> {
                System.err.println("Unknown package command: " + command);
                return 1;
            }
        }
    }

    /**
     * Handles skill management subcommands: install, list, remove, link, update.
     */
    private Integer handleSkillCommand(List<String> args) {
        com.campusclaw.codingagent.config.AppPaths.ensureUserDirs();

        if (args.isEmpty() || "--help".equals(args.get(0)) || "-h".equals(args.get(0))) {
            printSkillHelp(null);
            return 0;
        }

        String action = args.get(0);
        var actionArgs = args.subList(1, args.size());

        if (actionArgs.contains("--help") || actionArgs.contains("-h")) {
            printSkillHelp(action);
            return 0;
        }

        var manager = new SkillManager(com.campusclaw.codingagent.config.AppPaths.USER_SKILLS_DIR);

        switch (action) {
            case "install" -> {
                if (actionArgs.isEmpty()) {
                    System.err.println("Usage: campusclaw skill install <git-url>");
                    return 1;
                }
                String gitUrl = actionArgs.get(0);
                try {
                    System.out.println("Installing skill from: " + gitUrl);
                    String name = manager.install(gitUrl);
                    System.out.println("Skill installed: " + name);
                    // Show what was installed
                    var skills = new SkillLoader().loadFromDirectory(
                            com.campusclaw.codingagent.config.AppPaths.USER_SKILLS_DIR.resolve(name), "user");
                    for (var skill : skills) {
                        System.out.println("  - " + skill.name() + ": " + skill.description());
                    }
                    return 0;
                } catch (SkillInstallException e) {
                    System.err.println("Error: " + e.getMessage());
                    return 1;
                }
            }
            case "list", "ls" -> {
                var infos = manager.list();
                if (infos.isEmpty()) {
                    System.out.println("No skills installed.");
                    System.out.println("Install skills with: campusclaw skill install <git-url>");
                    return 0;
                }
                // Calculate column widths
                int maxName = Math.max(4, infos.stream().mapToInt(i -> i.name().length()).max().orElse(4));
                int maxType = Math.max(6, infos.stream().mapToInt(i -> i.sourceType().length()).max().orElse(6));
                String fmt = "  %-" + maxName + "s  %-" + maxType + "s  %s%n";
                System.out.printf(fmt, "NAME", "SOURCE", "DESCRIPTION");
                System.out.printf(fmt, "-".repeat(maxName), "-".repeat(maxType), "-".repeat(30));
                for (var info : infos) {
                    System.out.printf(fmt, info.name(), info.sourceType(), info.description());
                }
                return 0;
            }
            case "remove", "rm", "uninstall" -> {
                if (actionArgs.isEmpty()) {
                    System.err.println("Usage: campusclaw skill remove <name>");
                    return 1;
                }
                String name = actionArgs.get(0);
                try {
                    manager.remove(name);
                    System.out.println("Removed skill: " + name);
                    return 0;
                } catch (SkillInstallException e) {
                    System.err.println("Error: " + e.getMessage());
                    return 1;
                }
            }
            case "link" -> {
                if (actionArgs.isEmpty()) {
                    System.err.println("Usage: campusclaw skill link <path>");
                    return 1;
                }
                Path localPath = Path.of(actionArgs.get(0));
                try {
                    String name = manager.link(localPath);
                    System.out.println("Linked skill: " + name + " → " + localPath.toAbsolutePath().normalize());
                    return 0;
                } catch (SkillInstallException e) {
                    System.err.println("Error: " + e.getMessage());
                    return 1;
                }
            }
            case "import" -> {
                if (actionArgs.isEmpty()) {
                    System.err.println("Usage: campusclaw skill import <archive-path>");
                    return 1;
                }
                Path archivePath = Path.of(actionArgs.get(0));
                try {
                    System.out.println("Importing skill from: " + archivePath);
                    String name = manager.importArchive(archivePath);
                    System.out.println("Skill imported: " + name);
                    var skills = new SkillLoader().loadFromDirectory(
                            com.campusclaw.codingagent.config.AppPaths.USER_SKILLS_DIR.resolve(name), "user");
                    for (var skill : skills) {
                        System.out.println("  - " + skill.name() + ": " + skill.description());
                    }
                    return 0;
                } catch (SkillInstallException e) {
                    System.err.println("Error: " + e.getMessage());
                    return 1;
                }
            }
            case "update" -> {
                if (actionArgs.isEmpty()) {
                    System.err.println("Usage: campusclaw skill update <name>");
                    return 1;
                }
                String name = actionArgs.get(0);
                try {
                    System.out.println("Updating skill: " + name);
                    manager.update(name);
                    System.out.println("Updated: " + name);
                    return 0;
                } catch (SkillInstallException e) {
                    System.err.println("Error: " + e.getMessage());
                    return 1;
                }
            }
            default -> {
                System.err.println("Unknown skill command: " + action);
                printSkillHelp(null);
                return 1;
            }
        }
    }

    private void printSkillHelp(String action) {
        if (action == null) {
            System.out.println("""
                    Usage: campusclaw skill <command> [args]

                    Commands:
                      install <git-url>    Install a skill from a git repository
                      import <archive>     Import a skill from a .zip or .tar.gz archive
                      list                 List installed skills
                      remove <name>        Remove an installed skill
                      link <path>          Symlink a local skill directory (for development)
                      update <name>        Update a git-installed skill (git pull)

                    Examples:
                      campusclaw skill install https://github.com/user/my-skill
                      campusclaw skill import ./my-skill.zip
                      campusclaw skill import ~/Downloads/skill-pack.tar.gz
                      campusclaw skill link ./my-local-skill
                      campusclaw skill list
                      campusclaw skill remove my-skill
                      campusclaw skill update my-skill""");
            return;
        }
        switch (action) {
            case "install" -> System.out.println("""
                    Usage: campusclaw skill install <git-url>

                    Clone a git repository into ~/.campusclaw/agent/skills/.
                    The repository must contain at least one SKILL.md file.

                    Examples:
                      campusclaw skill install https://github.com/user/my-skill
                      campusclaw skill install git@github.com:user/skill-collection.git""");
            case "import" -> System.out.println("""
                    Usage: campusclaw skill import <archive-path>

                    Extract a .zip or .tar.gz archive into ~/.campusclaw/agent/skills/.
                    The archive must contain at least one SKILL.md file.
                    If the archive contains a single top-level directory, it will be unwrapped.

                    Supported formats: .zip, .tar.gz, .tgz

                    Examples:
                      campusclaw skill import ./my-skill.zip
                      campusclaw skill import ~/Downloads/skill-collection.tar.gz""");
            case "list", "ls" -> System.out.println("""
                    Usage: campusclaw skill list

                    List all skills in ~/.campusclaw/agent/skills/ with their source and description.""");
            case "remove", "rm", "uninstall" -> System.out.println("""
                    Usage: campusclaw skill remove <name>

                    Remove an installed skill by its directory name.
                    For git-installed skills, deletes the cloned directory.
                    For linked skills, removes the symlink (does not delete the original).""");
            case "link" -> System.out.println("""
                    Usage: campusclaw skill link <path>

                    Create a symbolic link in ~/.campusclaw/agent/skills/ pointing to a local directory.
                    Useful for developing and testing skills without copying files.

                    Example:
                      campusclaw skill link ./my-skill-in-progress""");
            case "update" -> System.out.println("""
                    Usage: campusclaw skill update <name>

                    Run 'git pull --ff-only' in the skill directory.
                    Only works for git-installed skills.""");
            default -> printSkillHelp(null);
        }
    }

    private void printPackageCommandHelp(String command) {
        switch (command) {
            case "install" -> System.out.println("""
                    Usage: pi install <source> [-l]

                    Install a package and add it to settings.

                    Options:
                      -l, --local    Install project-locally (.campusclaw/settings.json)

                    Examples:
                      pi install npm:@foo/bar
                      pi install git:github.com/user/repo
                      pi install ./local/path""");
            case "remove" -> System.out.println("""
                    Usage: pi remove <source> [-l]

                    Remove a package source from settings.
                    Alias: pi uninstall <source> [-l]""");
            case "update" -> System.out.println("""
                    Usage: pi update [source]

                    Update installed packages.
                    If <source> is provided, only that package is updated.""");
            case "list" -> System.out.println("""
                    Usage: pi list

                    List installed packages from user and project settings.""");
            default -> System.out.println("No help available for: " + command);
        }
    }

    static boolean matchesModelPattern(String pattern, Model model) {
        String id = model.id().toLowerCase();
        String name = model.name().toLowerCase();
        String provider = model.provider().value().toLowerCase();

        // Strip thinking level suffix (e.g., "sonnet:high" → "sonnet")
        int colonIdx = pattern.indexOf(':');
        String p = colonIdx >= 0 ? pattern.substring(0, colonIdx) : pattern;

        // Provider/id format: "zai/glm-5"
        if (p.contains("/")) {
            String[] parts = p.split("/", 2);
            if (!provider.contains(parts[0])) return false;
            p = parts[1];
        }

        // Glob-style matching
        if (p.contains("*")) {
            String regex = p.replace("*", ".*");
            return id.matches(regex) || name.matches(regex);
        }

        // Substring match
        return id.contains(p) || name.contains(p);
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
    boolean isPrintMode() { return printMode; }
    String getAppendSystemPrompt() { return appendSystemPrompt; }
}
