/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import com.campusclaw.agent.proxy.ProxyConfig;
import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.ai.CampusClawAiService;
import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.Api;
import com.campusclaw.ai.types.InputModality;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.ModelCost;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.ai.types.ThinkingLevel;
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
import com.campusclaw.codingagent.skill.SandboxSkillParser;
import com.campusclaw.codingagent.skill.SkillExpander;
import com.campusclaw.codingagent.skill.SkillInstallException;
import com.campusclaw.codingagent.skill.SkillLoader;
import com.campusclaw.codingagent.skill.SkillManager;
import com.campusclaw.codingagent.tool.bash.BashExecutor;
import com.campusclaw.tui.terminal.JLineTerminal;
import com.campusclaw.tui.terminal.Terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Main CLI command for CampusClaw.
 * Parses command-line arguments and launches the agent in the requested mode.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Command(
        name = "campusclaw",
        description = "CampusClaw — an AI-powered software engineering assistant.",
        mixinStandardHelpOptions = true,
        version = "pi 0.1.0")
@Component
public class CampusClawCommand implements Callable<Integer> {

    private static final Logger log = LoggerFactory.getLogger(CampusClawCommand.class);

    @Spec
    private CommandLine.Model.CommandSpec spec;

    private final CampusClawAiService piAiService;
    private final ModelRegistry modelRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final List<AgentTool> tools;
    private final SlashCommandRegistry commandRegistry;
    private final BashExecutor bashExecutor;
    private final SettingsManager settingsManager;
    private final com.campusclaw.cron.CronService cronService;
    private final com.campusclaw.codingagent.loop.LoopManager loopManager;
    private final org.springframework.context.ApplicationContext applicationContext;
    private final SandboxSkillParser sandboxSkillParser;
    private final com.campusclaw.codingagent.resolver.AgentModelResolver agentModelResolver;
    private final com.campusclaw.codingagent.model.ModelCatalogService modelCatalogService;
    private final com.campusclaw.agent.subagent.SubAgentRegistry subAgentRegistry;

    @org.springframework.beans.factory.annotation.Value("${server.session.persistence.enabled:true}")
    private boolean serverSessionPersistenceEnabled;

    public CampusClawCommand(
            CampusClawAiService piAiService,
            ModelRegistry modelRegistry,
            SystemPromptBuilder promptBuilder,
            List<AgentTool> tools,
            SlashCommandRegistry commandRegistry,
            BashExecutor bashExecutor,
            SettingsManager settingsManager,
            @org.springframework.lang.Nullable com.campusclaw.cron.CronService cronService,
            com.campusclaw.codingagent.loop.LoopManager loopManager,
            org.springframework.context.ApplicationContext applicationContext,
            @org.springframework.lang.Nullable SandboxSkillParser sandboxSkillParser,
            com.campusclaw.codingagent.resolver.AgentModelResolver agentModelResolver,
            com.campusclaw.codingagent.model.ModelCatalogService modelCatalogService,
            com.campusclaw.agent.subagent.SubAgentRegistry subAgentRegistry) {
        this.piAiService = piAiService;
        this.modelRegistry = modelRegistry;
        this.promptBuilder = promptBuilder;
        this.tools = tools;
        this.commandRegistry = commandRegistry;
        this.bashExecutor = bashExecutor;
        this.settingsManager = settingsManager;
        this.cronService = cronService;
        this.loopManager = loopManager;
        this.applicationContext = applicationContext;
        this.sandboxSkillParser = sandboxSkillParser;
        this.agentModelResolver = agentModelResolver;
        this.modelCatalogService = modelCatalogService;
        this.subAgentRegistry = subAgentRegistry;
    }

    private PrintWriter out() {
        return spec.commandLine().getOut();
    }

    private PrintWriter err() {
        return spec.commandLine().getErr();
    }

    @Option(
            names = {"--provider"},
            description = "Provider name (e.g. anthropic, openai, zai, google)")
    String provider;

    @Option(
            names = {"-m", "--model"},
            description = "AI model to use (e.g. claude-sonnet-4-20250514)")
    String model;

    @Option(
            names = {"--api-key"},
            description = "API key (overrides env vars)")
    String apiKey;

    @Option(
            names = {"--prompt"},
            description = "Initial prompt to send to the agent (internal use)")
    String prompt;

    @Option(
            names = {"--mode"},
            description = "Execution mode: interactive, one-shot, rpc, server, or print",
            defaultValue = "interactive")
    String mode;

    @Option(
            names = {"--port"},
            description = "HTTP server port (for server mode)")
    Integer port;

    @Option(
            names = {"--host"},
            description = "HTTP server bind address (for server mode, default: localhost)")
    String host;

    @Option(
            names = {"--proxy"},
            description = "HTTP/SOCKS5 proxy URL (e.g. http://127.0.0.1:7890)")
    String proxy;

    @Option(
            names = {"--cwd"},
            description = "Working directory (defaults to current directory)")
    Path cwd;

    @Option(
            names = {"--system-prompt"},
            description = "Custom system prompt (replaces default)")
    String systemPrompt;

    @Option(
            names = {"--append-system-prompt"},
            description = "Text appended to the system prompt")
    String appendSystemPrompt;

    @Option(
            names = {"--thinking"},
            description = "Thinking level: off, minimal, low, medium, high, xhigh")
    String thinking;

    @Option(
            names = {"--models"},
            description = "Comma-separated model patterns for Ctrl+P cycling")
    String modelsFilter;

    @Option(
            names = {"--tools"},
            description = "Comma-separated list of tools to enable (e.g. read,bash,edit)")
    String toolsFilter;

    @Option(
            names = {"--no-tools"},
            description = "Disable all built-in tools")
    boolean noTools;

    @Option(
            names = {"-p", "--print"},
            description = "Non-interactive mode: process prompt and exit")
    boolean printMode;

    @Option(
            names = {"-c", "--continue"},
            description = "Continue previous session")
    boolean continueSession;

    @Option(
            names = {"-r", "--resume"},
            description = "Select a session to resume")
    boolean resumeSession;

    @Option(
            names = {"--session"},
            description = "Use specific session file")
    Path sessionPath;

    @Option(
            names = {"--fork"},
            description = "Fork specific session file into a new session")
    Path forkPath;

    @Option(
            names = {"--no-session"},
            description = "Don't save session (ephemeral)")
    boolean noSession;

    @Option(
            names = {"--export"},
            description = "Export session file to HTML",
            arity = "1..2")
    List<String> exportArgs;

    @Option(
            names = {"--list-models"},
            description = "List available models and exit",
            arity = "0..1",
            fallbackValue = "")
    String listModels;

    @Option(
            names = {"--offline"},
            description = "Disable startup network operations")
    boolean offline;

    @Option(
            names = {"--verbose"},
            description = "Enable verbose startup output")
    boolean verbose;

    @Option(
            names = {"--cron-tick"},
            description = "Execute due cron jobs and exit (for OS scheduler)",
            hidden = true)
    boolean cronTick;

    @Parameters(
            description = "Prompt arguments (joined with spaces if no -p given). "
                    + "Prefix with @ to include file contents.",
            arity = "0..*")
    List<String> promptArgs;

    @Override
    public Integer call() {
        applyProxyOverride();
        if (cronTick) {
            return executeCronTick();
        }
        Integer subRc = dispatchSubcommand();
        if (subRc != null) {
            return subRc;
        }
        String effectivePrompt = resolveEffectivePrompt();
        Path effectiveCwd = cwd != null ? cwd : Path.of(System.getProperty("user.dir"));
        com.campusclaw.codingagent.config.AppPaths.ensureUserDirs();

        Settings settings = settingsManager != null ? settingsManager.load() : Settings.empty();
        String effectiveModel = (model != null) ? model : settings.resolvedDefaultModel();
        String effectiveThinking = (thinking != null) ? thinking : settings.defaultThinkingLevel();
        registerCustomModels(settings);

        if (exportArgs != null && !exportArgs.isEmpty()) {
            return runExportMode();
        }
        if (listModels != null) {
            return runListModelsMode();
        }
        Integer validateRc = validateSessionFlags();
        if (validateRc != null) {
            return validateRc;
        }
        if (printMode) {
            if (effectivePrompt == null) {
                err().println("Error: --print requires a prompt (positional args or piped stdin)");
                return 1;
            }
            mode = "one-shot";
        }
        if ("print".equals(mode)) {
            return runPrintMode(effectiveModel, effectiveCwd, effectivePrompt, effectiveThinking);
        }
        if ("one-shot".equals(mode) && effectivePrompt == null) {
            err().println("Error: --mode one-shot requires a prompt (-p or positional args)");
            return 1;
        }
        return runAgentMode(effectivePrompt, effectiveCwd, effectiveModel, effectiveThinking);
    }

    private void applyProxyOverride() {
        if (proxy == null || proxy.isBlank()) {
            return;
        }
        ProxyConfig proxyConfig = ProxyConfig.fromUrl(proxy);
        if (proxyConfig.isConfigured()) {
            proxyConfig.installAsDefault();
        } else {
            err().println("Warning: invalid proxy URL: " + proxy);
        }
    }

    // Returns null when no subcommand applies; otherwise the subcommand's exit code.
    private Integer dispatchSubcommand() {
        if (promptArgs == null || promptArgs.isEmpty()) {
            return null;
        }
        String first = promptArgs.get(0);
        if ("skill".equals(first)) {
            return handleSkillCommand(promptArgs.subList(1, promptArgs.size()));
        }
        if ("install".equals(first)
                || "remove".equals(first)
                || "uninstall".equals(first)
                || "update".equals(first)
                || "list".equals(first)
                || "config".equals(first)) {
            return handlePackageCommand(first, promptArgs.subList(1, promptArgs.size()));
        }
        return null;
    }

    private String resolveEffectivePrompt() {
        String effectivePrompt = resolvePrompt();
        if (effectivePrompt != null
                || System.console() != null
                || "server".equals(mode)
                || "rpc".equals(mode)
                || "print".equals(mode)) {
            return effectivePrompt;
        }
        try {
            String piped = new String(System.in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!piped.isEmpty()) {
                return piped;
            }
        } catch (IOException e) {
            // stdin not available — fall through with null
        }
        return null;
    }

    private void registerCustomModels(Settings settings) {
        if (settings.customModels() == null) {
            return;
        }
        for (Settings.CustomModelConfig cm : settings.customModels()) {
            if (cm.id() == null || cm.api() == null || cm.baseUrl() == null || cm.apiKey() == null) {
                err().println("Warning: Skipping custom model with missing required fields (id, api, baseUrl, apiKey)");
                continue;
            }
            List<InputModality> modalities = (cm.inputModalities() == null)
                    ? List.of(InputModality.TEXT)
                    : cm.inputModalities().stream()
                            .map(InputModality::fromValue)
                            .collect(Collectors.toList());
            modelRegistry.register(new Model(
                    cm.id(),
                    cm.name() != null ? cm.name() : cm.id(),
                    Api.fromValue(cm.api()),
                    Provider.CUSTOM,
                    ConfigValueResolver.resolve(cm.baseUrl()),
                    cm.reasoning() != null && cm.reasoning(),
                    modalities,
                    new ModelCost(0, 0, 0, 0),
                    cm.contextWindow() != null ? cm.contextWindow() : 128000,
                    cm.maxTokens() != null ? cm.maxTokens() : 8192,
                    null,
                    cm.thinkingFormat(),
                    ConfigValueResolver.resolve(cm.apiKey())));
        }
    }

    private Integer runExportMode() {
        Path inputFile = Path.of(exportArgs.get(0));
        if (!Files.exists(inputFile)) {
            err().println("Session file not found: " + inputFile);
            return 1;
        }
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
            err().println("No messages found in session file.");
            return 1;
        }
        String html = com.campusclaw.codingagent.export.HtmlExporter.export(
                messages, "CampusClaw Session", sm.getSessionId());
        try {
            Files.writeString(outputFile, html);
            out().println("Exported " + messages.size() + " messages to " + outputFile);
            return 0;
        } catch (IOException e) {
            log.error("Failed to write HTML export to {}", outputFile, e);
            err().println("Failed to write HTML: " + e.getMessage());
            return 1;
        }
    }

    private Integer runListModelsMode() {
        String search = listModels.isBlank() ? null : listModels;
        var allModels = modelRegistry.getAllModels();
        allModels.sort(Comparator.comparing((Model m) -> m.provider().value()).thenComparing(Model::id));
        for (var m : allModels) {
            if (search != null
                    && !m.id().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))
                    && !m.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))) {
                continue;
            }
            out().printf("  %-15s %-40s %s%n", m.provider().value(), m.id(), m.name());
        }
        return 0;
    }

    // Returns 1 when conflicting session flags are present, null otherwise.
    private Integer validateSessionFlags() {
        var conflicting = new ArrayList<String>();
        if (sessionPath != null) {
            conflicting.add("--session");
        }
        if (continueSession) {
            conflicting.add("--continue");
        }
        if (resumeSession) {
            conflicting.add("--resume");
        }
        if (noSession) {
            conflicting.add("--no-session");
        }
        if (conflicting.size() > 1) {
            err().println("Error: conflicting flags: " + String.join(", ", conflicting));
            return 1;
        }
        return null;
    }

    private Integer runPrintMode(
            String effectiveModel, Path effectiveCwd, String effectivePrompt, String effectiveThinking) {
        out().println("Model: " + effectiveModel);
        out().println("Mode: " + mode);
        out().println("CWD: " + effectiveCwd);
        out().println("Prompt: " + effectivePrompt);
        if (effectiveThinking != null) {
            out().println("Thinking: " + effectiveThinking);
        }
        if (toolsFilter != null) {
            out().println("Tools: " + toolsFilter);
        }
        return 0;
    }

    private Integer runAgentMode(
            String effectivePrompt, Path effectiveCwd, String effectiveModel, String effectiveThinking) {
        String effectiveSystemPrompt = mergeSystemPrompts();
        List<AgentTool> effectiveTools = resolveEffectiveTools();
        boolean useSandbox = Boolean.parseBoolean(System.getenv("SKILL_SANDBOX_PARSING"));

        AgentSession session = new AgentSession(
                piAiService,
                modelRegistry,
                promptBuilder,
                new SkillLoader(sandboxSkillParser, useSandbox),
                new SkillExpander(sandboxSkillParser, useSandbox),
                effectiveTools);
        session.setSubAgentRegistry(subAgentRegistry);
        SessionManager sessionManager = noSession ? null : new SessionManager();
        if (sessionManager != null) {
            session.setSessionManager(sessionManager);
        }
        SessionConfig config = new SessionConfig(effectiveModel, effectiveCwd, effectiveSystemPrompt, mode);
        session.initialize(config);
        if (sessionManager != null) {
            applySessionLoading(sessionManager, session, effectiveCwd);
        }
        applyThinkingLevel(session, effectiveThinking);

        if ("one-shot".equals(mode)) {
            return new OneShotMode().run(session, effectivePrompt);
        }
        if ("rpc".equals(mode)) {
            new RpcMode(session).run();
            return 0;
        }
        if ("server".equals(mode)) {
            new ServerMode(
                            piAiService,
                            modelRegistry,
                            promptBuilder,
                            effectiveTools,
                            config,
                            port != null ? port : 3000,
                            host != null ? host : "localhost",
                            sandboxSkillParser,
                            useSandbox,
                            modelCatalogService,
                            serverSessionPersistenceEnabled)
                    .run();
            return 0;
        }
        return runInteractiveMode(session, sessionManager, effectivePrompt);
    }

    private String mergeSystemPrompts() {
        String effective = systemPrompt;
        if (appendSystemPrompt != null && !appendSystemPrompt.isBlank()) {
            effective = (effective == null) ? appendSystemPrompt : effective + "\n\n" + appendSystemPrompt;
        }
        return effective;
    }

    private List<AgentTool> resolveEffectiveTools() {
        if (noTools) {
            return List.of();
        }
        if (toolsFilter != null && !toolsFilter.isBlank()) {
            var allowed = List.of(toolsFilter.split(","));
            return tools.stream().filter(t -> allowed.contains(t.name())).collect(Collectors.toList());
        }
        return tools;
    }

    private void applySessionLoading(SessionManager sessionManager, AgentSession session, Path effectiveCwd) {
        if (sessionPath != null) {
            loadExplicitSession(sessionManager, session, effectiveCwd);
        } else if (forkPath != null) {
            forkExistingSession(sessionManager, session, effectiveCwd);
        } else if (continueSession) {
            continueLatestSession(sessionManager, session, effectiveCwd);
        } else if (resumeSession) {
            err().println("Use /resume command in interactive mode to select a session.");
            sessionManager.createSession(effectiveCwd.toString());
        } else {
            sessionManager.createSession(effectiveCwd.toString());
        }
    }

    private void loadExplicitSession(SessionManager sessionManager, AgentSession session, Path effectiveCwd) {
        var messages = sessionManager.loadSession(sessionPath);
        if (messages.isEmpty()) {
            err().println("Warning: session file empty or invalid: " + sessionPath);
            sessionManager.createSession(effectiveCwd.toString());
            return;
        }
        for (var msg : messages) {
            session.getAgent().getState().appendMessage(msg);
        }
        err().println("Loaded session " + sessionManager.getSessionId() + " (" + messages.size() + " messages)");
    }

    private void forkExistingSession(SessionManager sessionManager, AgentSession session, Path effectiveCwd) {
        var messages = sessionManager.loadSession(forkPath);
        String originalId = sessionManager.getSessionId();
        sessionManager.createSession(effectiveCwd.toString());
        if (messages.isEmpty()) {
            return;
        }
        for (var msg : messages) {
            session.getAgent().getState().appendMessage(msg);
            sessionManager.appendMessage(msg);
        }
        err().println("Forked session " + originalId + " → " + sessionManager.getSessionId() + " (" + messages.size()
                + " messages)");
    }

    private void continueLatestSession(SessionManager sessionManager, AgentSession session, Path effectiveCwd) {
        List<com.campusclaw.ai.types.Message> messages = loadMessagesFromChatMemory(sessionManager);
        if (messages.isEmpty()) {
            messages = sessionManager.resumeLatestSession(effectiveCwd.toString());
        }
        if (messages.isEmpty()) {
            sessionManager.createSession(effectiveCwd.toString());
            return;
        }
        for (var msg : messages) {
            session.getAgent().getState().appendMessage(msg);
        }
        err().println("Resumed session " + sessionManager.getSessionId() + " (" + messages.size() + " messages)");
    }

    private List<com.campusclaw.ai.types.Message> loadMessagesFromChatMemory(SessionManager sessionManager) {
        if (applicationContext == null) {
            return List.of();
        }
        try {
            var store = applicationContext.getBean(com.campusclaw.assistant.memory.ChatMemoryStore.class);
            return store.load(sessionManager.getSessionId());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private void applyThinkingLevel(AgentSession session, String effectiveThinking) {
        if (effectiveThinking == null) {
            return;
        }
        try {
            session.getAgent().setThinkingLevel(ThinkingLevel.fromValue(effectiveThinking));
        } catch (IllegalArgumentException e) {
            err().println("Warning: Unknown thinking level '" + effectiveThinking
                    + "'. Valid: off, minimal, low, medium, high, xhigh");
        }
    }

    private Integer runInteractiveMode(AgentSession session, SessionManager sessionManager, String effectivePrompt) {
        Terminal terminal = new JLineTerminal();
        try {
            var interactiveMode = new InteractiveMode(
                    commandRegistry,
                    bashExecutor,
                    new Compactor(
                            piAiService,
                            com.campusclaw.codingagent.compaction.CompactionConfig.defaults(),
                            agentModelResolver),
                    modelRegistry,
                    cronService,
                    loopManager,
                    applicationContext);
            applyScopedModels(interactiveMode);
            if (effectivePrompt != null) {
                interactiveMode.setInitialPrompt(effectivePrompt);
            }
            interactiveMode.run(session, terminal);
        } finally {
            // Note: do NOT call terminal.close() — the system terminal is owned
            // by the OS and closing it can exit the parent terminal window.
            if (sessionManager != null) {
                sessionManager.close();
            }
        }
        return 0;
    }

    private void applyScopedModels(InteractiveMode interactiveMode) {
        if (modelsFilter != null && !modelsFilter.isBlank()) {
            var scoped = new ArrayList<Model>();
            var allRegistered = modelRegistry.getAllModels();
            for (String pattern : modelsFilter.split(",")) {
                String p = pattern.trim().toLowerCase(Locale.ROOT);
                if (p.isEmpty()) {
                    continue;
                }
                for (var m : allRegistered) {
                    if (com.campusclaw.codingagent.model.ModelCatalogService.matchesPattern(p, m)
                            && scoped.stream().noneMatch(s -> ModelRegistry.modelsAreEqual(s, m))) {
                        scoped.add(m);
                    }
                }
            }
            if (!scoped.isEmpty()) {
                interactiveMode.setScopedModels(scoped);
            }
        } else if (modelCatalogService != null && modelCatalogService.isFiltered()) {
            var scoped = modelCatalogService.getAvailableModels();
            if (!scoped.isEmpty()) {
                interactiveMode.setScopedModels(scoped);
            }
        }
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
     *
     * @return the result
     */
    private Integer executeCronTick() {
        if (cronService == null) {
            err().println("Error: Cron service not available");
            return 1;
        }
        var store = new com.campusclaw.cron.store.CronStore();
        var processLock = store.acquireProcessLock();
        if (processLock == null) {
            err().println("Another cron-tick is already running, skipping");
            return 0;
        }
        try {
            var results = cronService.tickOnce();
            for (var r : results) {
                out().printf("[cron-tick] %s → %s%n", r.jobId(), r.status());
            }
            if (results.isEmpty()) {
                out().println("[cron-tick] No jobs due");
            }
            return 0;
        } catch (Exception e) {
            log.error("cron-tick execution failed", e);
            err().println("[cron-tick] Error: " + e.getMessage());
            return 1;
        } finally {
            store.releaseProcessLock(processLock);
        }
    }

    /**
     * Handles package management subcommands (install, remove, update, list, config).
     * Aligned with campusclaw's package management system.
     *
     * @param command the command
     * @param args the args
     *
     * @return the result
     */
    private Integer handlePackageCommand(String command, List<String> args) {
        com.campusclaw.codingagent.config.AppPaths.ensureUserDirs();
        String normalized = "uninstall".equals(command) ? "remove" : command;
        if (args.contains("--help")) {
            printPackageCommandHelp(normalized);
            return 0;
        }
        boolean local = args.contains("-l") || args.contains("--local");
        var filteredArgs = args.stream()
                .filter(a -> !"-l".equals(a) && !"--local".equals(a) && !"--help".equals(a))
                .toList();
        return switch (normalized) {
            case "install" -> packageInstall(filteredArgs, local);
            case "remove" -> packageRemove(filteredArgs);
            case "update" -> packageUpdate(filteredArgs);
            case "list" -> packageList();
            case "config" -> packageConfig();
            default -> {
                err().println("Unknown package command: " + normalized);
                yield 1;
            }
        };
    }

    private Integer packageInstall(List<String> filteredArgs, boolean local) {
        if (filteredArgs.isEmpty()) {
            err().println("Usage: pi install <source> [-l]");
            return 1;
        }
        String source = filteredArgs.get(0);
        out().println("Installing package: " + source + (local ? " (local)" : " (global)"));
        out().println("Package installation is not yet fully implemented.");
        out().println("Add the source to your settings.json packages array manually:");
        out().println("  \"packages\": [\"" + source + "\"]");
        return 0;
    }

    private Integer packageRemove(List<String> filteredArgs) {
        if (filteredArgs.isEmpty()) {
            err().println("Usage: pi remove <source> [-l]");
            return 1;
        }
        out().println("Removing package: " + filteredArgs.get(0));
        out().println("Remove the source from your settings.json packages array manually.");
        return 0;
    }

    private Integer packageUpdate(List<String> filteredArgs) {
        if (filteredArgs.isEmpty()) {
            out().println("Updating all packages...");
        } else {
            out().println("Updating package: " + filteredArgs.get(0));
        }
        out().println("Package update is not yet fully implemented.");
        return 0;
    }

    private Integer packageList() {
        out().println("Installed packages:");
        Settings settings = settingsManager != null ? settingsManager.load() : Settings.empty();
        if (settings.packages() == null || settings.packages().isEmpty()) {
            out().println("  (none)");
            return 0;
        }
        for (String pkg : settings.packages()) {
            out().println("  " + pkg);
        }
        return 0;
    }

    private Integer packageConfig() {
        out().println("Package config TUI is not yet implemented.");
        out().println("Edit settings.json manually to configure packages.");
        return 0;
    }

    /**
     * Handles skill management subcommands: install, list, remove, link, update.
     *
     * @param args the args
     *
     * @return the result
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
        boolean useSandbox = Boolean.parseBoolean(System.getenv("SKILL_SANDBOX_PARSING"));
        var manager = new SkillManager(
                com.campusclaw.codingagent.config.AppPaths.USER_SKILLS_DIR, sandboxSkillParser, useSandbox);
        return switch (action) {
            case "install" -> skillInstall(manager, useSandbox, actionArgs);
            case "list", "ls" -> skillList(manager);
            case "remove", "rm", "uninstall" -> skillRemove(manager, actionArgs);
            case "link" -> skillLink(manager, actionArgs);
            case "import" -> skillImport(manager, actionArgs);
            case "update" -> skillUpdate(manager, actionArgs);
            default -> {
                err().println("Unknown skill command: " + action);
                printSkillHelp(null);
                yield 1;
            }
        };
    }

    private Integer skillInstall(SkillManager manager, boolean useSandbox, List<String> actionArgs) {
        if (actionArgs.isEmpty()) {
            err().println("Usage: campusclaw skill install <git-url>");
            return 1;
        }
        String gitUrl = actionArgs.get(0);
        try {
            out().println("Installing skill from: " + gitUrl);
            String name = manager.install(gitUrl);
            out().println("Skill installed: " + name);
            var skillLoader = new SkillLoader(sandboxSkillParser, useSandbox);
            var skills = skillLoader.loadFromDirectory(
                    com.campusclaw.codingagent.config.AppPaths.USER_SKILLS_DIR.resolve(name), "user");
            for (var skill : skills) {
                out().println("  - " + skill.name() + ": " + skill.description());
            }
            return 0;
        } catch (SkillInstallException e) {
            err().println("Error: " + e.getMessage());
            return 1;
        }
    }

    private Integer skillList(SkillManager manager) {
        var infos = manager.list();
        if (infos.isEmpty()) {
            out().println("No skills installed.");
            out().println("Install skills with: campusclaw skill install <git-url>");
            return 0;
        }
        int maxName = Math.max(
                4, infos.stream().mapToInt(i -> i.name().length()).max().orElse(4));
        int maxType = Math.max(
                6, infos.stream().mapToInt(i -> i.sourceType().length()).max().orElse(6));
        String fmt = "  %-" + maxName + "s  %-" + maxType + "s  %s%n";
        out().printf(fmt, "NAME", "SOURCE", "DESCRIPTION");
        out().printf(fmt, "-".repeat(maxName), "-".repeat(maxType), "-".repeat(30));
        for (var info : infos) {
            out().printf(fmt, info.name(), info.sourceType(), info.description());
        }
        return 0;
    }

    private Integer skillRemove(SkillManager manager, List<String> actionArgs) {
        if (actionArgs.isEmpty()) {
            err().println("Usage: campusclaw skill remove <name>");
            return 1;
        }
        String name = actionArgs.get(0);
        try {
            manager.remove(name);
            out().println("Removed skill: " + name);
            return 0;
        } catch (SkillInstallException e) {
            err().println("Error: " + e.getMessage());
            return 1;
        }
    }

    private Integer skillLink(SkillManager manager, List<String> actionArgs) {
        if (actionArgs.isEmpty()) {
            err().println("Usage: campusclaw skill link <path>");
            return 1;
        }
        Path localPath = Path.of(actionArgs.get(0));
        try {
            String name = manager.link(localPath);
            out().println("Linked skill: " + name + " → "
                    + localPath.toAbsolutePath().normalize());
            return 0;
        } catch (SkillInstallException e) {
            err().println("Error: " + e.getMessage());
            return 1;
        }
    }

    private Integer skillImport(SkillManager manager, List<String> actionArgs) {
        if (actionArgs.isEmpty()) {
            err().println("Usage: campusclaw skill import <archive-path>");
            return 1;
        }
        Path archivePath = Path.of(actionArgs.get(0));
        try {
            out().println("Importing skill from: " + archivePath);
            String name = manager.importArchive(archivePath);
            out().println("Skill imported: " + name);
            var skills = new SkillLoader()
                    .loadFromDirectory(
                            com.campusclaw.codingagent.config.AppPaths.USER_SKILLS_DIR.resolve(name), "user");
            for (var skill : skills) {
                out().println("  - " + skill.name() + ": " + skill.description());
            }
            return 0;
        } catch (SkillInstallException e) {
            err().println("Error: " + e.getMessage());
            return 1;
        }
    }

    private Integer skillUpdate(SkillManager manager, List<String> actionArgs) {
        if (actionArgs.isEmpty()) {
            err().println("Usage: campusclaw skill update <name>");
            return 1;
        }
        String name = actionArgs.get(0);
        try {
            out().println("Updating skill: " + name);
            manager.update(name);
            out().println("Updated: " + name);
            return 0;
        } catch (SkillInstallException e) {
            err().println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static final String SKILL_HELP_OVERVIEW =
            """
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
                campusclaw skill update my-skill""";

    private static final java.util.Map<String, String> SKILL_HELP_PER_ACTION = java.util.Map.ofEntries(
            java.util.Map.entry(
                    "install",
                    """
                    Usage: campusclaw skill install <git-url>

                    Clone a git repository into ~/.campusclaw/agent/skills/.
                    The repository must contain at least one SKILL.md file.

                    Examples:
                        campusclaw skill install https://github.com/user/my-skill
                        campusclaw skill install git@github.com:user/skill-collection.git"""),
            java.util.Map.entry(
                    "import",
                    """
                    Usage: campusclaw skill import <archive-path>

                    Extract a .zip or .tar.gz archive into ~/.campusclaw/agent/skills/.
                    The archive must contain at least one SKILL.md file.
                    If the archive contains a single top-level directory, it will be unwrapped.

                    Supported formats: .zip, .tar.gz, .tgz

                    Examples:
                        campusclaw skill import ./my-skill.zip
                        campusclaw skill import ~/Downloads/skill-collection.tar.gz"""),
            java.util.Map.entry(
                    "list",
                    """
                    Usage: campusclaw skill list

                    List all skills in ~/.campusclaw/agent/skills/ with their source and description."""),
            java.util.Map.entry(
                    "remove",
                    """
                    Usage: campusclaw skill remove <name>

                    Remove an installed skill by its directory name.
                    For git-installed skills, deletes the cloned directory.
                    For linked skills, removes the symlink (does not delete the original)."""),
            java.util.Map.entry(
                    "link",
                    """
                    Usage: campusclaw skill link <path>

                    Create a symbolic link in ~/.campusclaw/agent/skills/ pointing to a local directory.
                    Useful for developing and testing skills without copying files.

                    Example:
                        campusclaw skill link ./my-skill-in-progress"""),
            java.util.Map.entry(
                    "update",
                    """
                    Usage: campusclaw skill update <name>

                    Run 'git pull --ff-only' in the skill directory.
                    Only works for git-installed skills."""));

    private void printSkillHelp(String action) {
        if (action == null) {
            out().println(SKILL_HELP_OVERVIEW);
            return;
        }

        // Normalize alias forms ("ls" → "list", "rm"/"uninstall" → "remove") to the canonical key.
        String key =
                switch (action) {
                    case "ls" -> "list";
                    case "rm", "uninstall" -> "remove";
                    default -> action;
                };
        String text = SKILL_HELP_PER_ACTION.get(key);
        if (text == null) {
            printSkillHelp(null);
            return;
        }
        out().println(text);
    }

    private void printPackageCommandHelp(String command) {
        switch (command) {
            case "install" ->
                out().println(
                                """
        Usage: pi install <source> [-l]

        Install a package and add it to settings.

        Options:
            -l, --local    Install project-locally (.campusclaw/settings.json)

        Examples:
            pi install npm:@foo/bar
            pi install git:github.com/user/repo
            pi install ./local/path""");
            case "remove" ->
                out().println(
                                """
        Usage: pi remove <source> [-l]

        Remove a package source from settings.
        Alias: pi uninstall <source> [-l]""");
            case "update" ->
                out().println(
                                """
        Usage: pi update [source]

        Update installed packages.
        If <source> is provided, only that package is updated.""");
            case "list" ->
                out().println(
                                """
        Usage: pi list

        List installed packages from user and project settings.""");
            default -> out().println("No help available for: " + command);
        }
    }

    static boolean matchesModelPattern(String pattern, Model model) {
        String id = model.id().toLowerCase(Locale.ROOT);
        String name = model.name().toLowerCase(Locale.ROOT);
        String provider = model.provider().value().toLowerCase(Locale.ROOT);

        // Strip thinking level suffix (e.g., "sonnet:high" → "sonnet")
        int colonIdx = pattern.indexOf(':');
        String p = colonIdx >= 0 ? pattern.substring(0, colonIdx) : pattern;

        // Provider/id format: "zai/glm-5"
        if (p.contains("/")) {
            String[] parts = p.split("/", 2);
            if (!provider.contains(parts[0])) {
                return false;
            }
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
     *
     * @return the result
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

    String getModel() {
        return model;
    }

    String getPrompt() {
        return prompt;
    }

    String getMode() {
        return mode;
    }

    Path getCwd() {
        return cwd;
    }

    String getSystemPrompt() {
        return systemPrompt;
    }

    List<String> getPromptArgs() {
        return promptArgs;
    }

    String getThinking() {
        return thinking;
    }

    String getToolsFilter() {
        return toolsFilter;
    }

    boolean isVerbose() {
        return verbose;
    }

    boolean isPrintMode() {
        return printMode;
    }

    String getAppendSystemPrompt() {
        return appendSystemPrompt;
    }
}
