/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.mode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.campusclaw.agent.event.AgentEvent;
import com.campusclaw.agent.event.MessageEndEvent;
import com.campusclaw.agent.event.MessageUpdateEvent;
import com.campusclaw.agent.event.ToolExecutionEndEvent;
import com.campusclaw.agent.event.ToolExecutionStartEvent;
import com.campusclaw.agent.event.ToolExecutionUpdateEvent;
import com.campusclaw.agent.event.TurnStartEvent;
import com.campusclaw.agent.tool.CancellationToken;
import com.campusclaw.agent.util.LoggingUncaughtExceptionHandler;
import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.stream.AssistantMessageEvent;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.ThinkingLevel;
import com.campusclaw.ai.types.UserMessage;
import com.campusclaw.codingagent.CampusClawApplication;
import com.campusclaw.codingagent.command.SlashCommandContext;
import com.campusclaw.codingagent.command.SlashCommandRegistry;
import com.campusclaw.codingagent.compaction.Compactor;
import com.campusclaw.codingagent.mode.tui.AssistantMessageComponent;
import com.campusclaw.codingagent.mode.tui.BashExecutionComponent;
import com.campusclaw.codingagent.mode.tui.CommandOutputComponent;
import com.campusclaw.codingagent.mode.tui.EditorContainer;
import com.campusclaw.codingagent.mode.tui.EditorContainer.CommandSuggestion;
import com.campusclaw.codingagent.mode.tui.FooterComponent;
import com.campusclaw.codingagent.mode.tui.ModelSelectorOverlay;
import com.campusclaw.codingagent.mode.tui.SessionSelectorOverlay;
import com.campusclaw.codingagent.mode.tui.ToolStatusComponent;
import com.campusclaw.codingagent.mode.tui.TreeSelectorOverlay;
import com.campusclaw.codingagent.mode.tui.UserMessageComponent;
import com.campusclaw.codingagent.prompt.PromptTemplateEntry;
import com.campusclaw.codingagent.session.AgentSession;
import com.campusclaw.codingagent.skill.Skill;
import com.campusclaw.codingagent.tool.bash.BashExecutionResult;
import com.campusclaw.codingagent.tool.bash.BashExecutor;
import com.campusclaw.codingagent.tool.bash.BashExecutorOptions;
import com.campusclaw.tui.Component;
import com.campusclaw.tui.Tui;
import com.campusclaw.tui.component.Container;
import com.campusclaw.tui.component.Text;
import com.campusclaw.tui.terminal.Terminal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

/**
 * Full-screen interactive REPL using TUI component tree rendering.
 *
 * <p>Layout (top to bottom):
 * <ul>
 *   <li>Chat area — scrollable list of user messages, assistant messages, tool statuses</li>
 *   <li>Editor — input area with cyan separator, always at bottom</li>
 *   <li>Footer — token stats and model info</li>
 * </ul>
 *
 * <p>Components are rendered by the {@link Tui} engine which handles differential
 * updates and synchronized output for flicker-free display.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class InteractiveMode {

    private static final Logger log = LoggerFactory.getLogger(InteractiveMode.class);

    private final SlashCommandRegistry commandRegistry;
    private final BashExecutor bashExecutor;
    private final Compactor compactor;
    private final ModelRegistry modelRegistry;
    private final com.campusclaw.cron.CronService cronService;
    private final com.campusclaw.codingagent.loop.LoopManager loopManager;
    private final ApplicationContext applicationContext;
    private com.campusclaw.assistant.memory.ChatMemoryStore chatMemoryStore;

    // Scoped models for Ctrl+P cycling (from --models flag)
    private List<Model> scopedModels = List.of();

    // TUI components
    private Tui tui;
    private Container root;
    private Container chatContainer;
    private EditorContainer editorContainer;
    private FooterComponent footer;
    private Component welcomeComponent;

    // Streaming state
    private AssistantMessageComponent currentAssistantMessage;
    private final Map<String, ToolStatusComponent> pendingTools = new LinkedHashMap<>();

    // Session reference for persistence in event handler
    private AgentSession currentSession;

    // Lock protecting concurrent TUI component mutations and render calls.
    // Must be held when modifying chat components (addChild, StringBuilder append, etc.)
    // from background threads (spinner, agent events, cron listener) to prevent races
    // with the render cycle that traverses the component tree.
    private final Object tuiLock = new Object();

    // Cancellation token for the currently running bash command
    private volatile CancellationToken bashCancelToken;

    // Bash mode state
    private boolean bashMode;

    // Tool output expand/collapse state (toggled by Ctrl+O)
    private boolean toolsExpanded;

    // Thinking block visibility state (toggled by Ctrl+T)
    private boolean hideThinkingBlock;

    // Overlay state — when non-null, input is routed to the overlay component
    private Component activeOverlay;

    // Initial prompt to send on startup
    private String initialPrompt;

    // Status message reuse (matches campusclaw behavior)
    private Component lastStatusComponent;

    // Double-tap tracking for Ctrl+C exit and double-escape
    private long lastCtrlCTime;
    private long lastEscapeTime;

    // Follow-up / steering queues during compaction
    private final List<QueuedMessage> compactionQueue = new ArrayList<>();

    private record QueuedMessage(String text, String mode) {}

    /**
     * Sets the initial prompt to send automatically when the TUI starts.
     *
     * @param prompt the prompt
     */
    public void setInitialPrompt(String prompt) {
        this.initialPrompt = prompt;
    }

    /**
     * Sets scoped models for Ctrl+P cycling (from --models flag).
     * When set, Ctrl+P cycles only through these models instead of all registered ones.
     *
     * @param models the models
     */
    public void setScopedModels(List<Model> models) {
        this.scopedModels = models != null ? models : List.of();
    }

    public InteractiveMode(
            SlashCommandRegistry commandRegistry,
            BashExecutor bashExecutor,
            Compactor compactor,
            ModelRegistry modelRegistry,
            com.campusclaw.cron.CronService cronService,
            com.campusclaw.codingagent.loop.LoopManager loopManager,
            ApplicationContext applicationContext) {
        this.commandRegistry = Objects.requireNonNull(commandRegistry, "commandRegistry");
        this.bashExecutor = bashExecutor;
        this.compactor = compactor;
        this.modelRegistry = modelRegistry;
        this.cronService = cronService;
        this.loopManager = loopManager;
        this.applicationContext = applicationContext;
    }

    /**
     * Runs the full-screen interactive REPL.
     *
     * @param session the session
     * @param terminal the terminal
     */
    public void run(AgentSession session, Terminal terminal) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(terminal, "terminal");
        this.currentSession = session;
        resolveChatMemoryStore();
        buildComponentTree(session);
        String cwd = System.getProperty("user.dir", "");
        applyInitialFooterState(session, cwd);
        buildCommandSuggestions(session);
        tui = new Tui(terminal);
        tui.setRoot(root);
        startCronEngineWithTuiNotifications(session);
        terminal.write("\033]0;pi — " + cwd + "\007");

        BlockingQueue<String> submitQueue = new LinkedBlockingQueue<>();
        var eofFlag = new AtomicBoolean(false);
        var executingPrompt = new AtomicBoolean(false);
        var abortedFlag = new AtomicBoolean(false);
        var sessionRef = new AtomicReference<>(session);
        var followUpFlag = new AtomicBoolean(false);
        if (loopManager != null) {
            loopManager.init(submitQueue, executingPrompt);
        }
        editorContainer.setOnSubmit(value -> {
            if (value != null) {
                submitQueue.add(value);
            }
        });
        tui.setInputHandler(data -> dispatchInput(
                data, session, submitQueue, eofFlag, executingPrompt, abortedFlag, sessionRef, followUpFlag));

        tui.start();
        tui.render();
        if (initialPrompt != null && !initialPrompt.isBlank()) {
            executingPrompt.set(true);
            executePrompt(session, expandFileReferences(initialPrompt), abortedFlag);
            executingPrompt.set(false);
            checkAutoCompaction(session);
        }
        try {
            runReplLoop(session, cwd, submitQueue, eofFlag, executingPrompt, abortedFlag, followUpFlag);
        } finally {
            if (loopManager != null) {
                loopManager.shutdown();
            }
            if (cronService != null) {
                cronService.stop();
            }
            tui.stop();
        }
    }

    // ChatMemoryStore is optional — silently no-op when the DB isn't configured.
    private void resolveChatMemoryStore() {
        if (applicationContext == null) {
            return;
        }
        try {
            chatMemoryStore = applicationContext.getBean(com.campusclaw.assistant.memory.ChatMemoryStore.class);
        } catch (Exception e) {
            chatMemoryStore = null;
        }
    }

    private void buildComponentTree(AgentSession session) {
        root = new Container();
        chatContainer = new Container();
        editorContainer = new EditorContainer();
        footer = new FooterComponent();
        welcomeComponent = new Text(buildWelcomeBanner());
        chatContainer.addChild(welcomeComponent);
        root.addChild(chatContainer);
        root.addChild(editorContainer);
        root.addChild(footer);
    }

    private void applyInitialFooterState(AgentSession session, String cwd) {
        var model = session.getAgent().getState().getModel();
        if (model != null) {
            footer.setModel(
                    model.provider().name().toLowerCase(Locale.ROOT),
                    model.id(),
                    model.contextWindow() > 0 ? model.contextWindow() : 200000,
                    model.reasoning());
        }
        footer.setCwd(cwd);
        String level = currentThinkingLevel(session);
        footer.setThinkingLevel(level);
        editorContainer.setBorderForThinkingLevel(level);
    }

    // Colors from campusclaw dark theme: dim=#666666, muted=#808080.
    private static final String BANNER_DIM = "\033[38;2;102;102;102m";
    private static final String BANNER_MUTED = "\033[38;2;128;128;128m";
    private static final String BANNER_RESET = "\033[0m";

    private static String buildWelcomeBanner() {
        var wb = new StringBuilder();
        String version = CampusClawApplication.class.getPackage() != null
                        && CampusClawApplication.class.getPackage().getImplementationVersion() != null
                ? CampusClawApplication.class.getPackage().getImplementationVersion()
                : "0.1.0";
        wb.append("\033[1m\033[38;2;138;190;183mCampusClaw\033[0m")
                .append(BANNER_DIM)
                .append(" v")
                .append(version)
                .append(BANNER_RESET)
                .append("\n");

        // Keybinding hints (aligned with campusclaw) — key in dim, description in muted.
        appendHint(wb, " escape", " to interrupt");
        appendHint(wb, " ctrl+c", " to clear");
        appendHint(wb, " ctrl+c twice", " to exit");
        appendHint(wb, " ctrl+d", " to exit (empty)");
        appendHint(wb, " ctrl+z", " to suspend");
        appendHint(wb, " ctrl+k", " to delete to end");
        appendHint(wb, " shift+tab", " to cycle thinking level");
        appendHint(wb, " ctrl+p/shift+ctrl+p", " to cycle models");
        appendHint(wb, " ctrl+l", " to select model");
        appendHint(wb, " ctrl+o", " to expand tools");
        appendHint(wb, " ctrl+t", " to expand thinking");
        appendHint(wb, " ctrl+g", " for external editor");
        appendHint(wb, " /", " for commands");
        appendHint(wb, " !", " to run bash");
        appendHint(wb, " !!", " to run bash (no context)");
        appendHint(wb, " alt+enter", " to queue follow-up");
        appendHint(wb, " alt+up", " to edit all queued messages");
        appendHint(wb, " ctrl+v", " to paste image");
        appendHint(wb, " drop files", " to attach");
        wb.append("\n");
        wb.append(BANNER_DIM)
                .append(
                        " CampusClaw can explain its own features and look up its docs. Ask it how to use or extend CampusClaw.")
                .append(BANNER_RESET);
        return wb.toString();
    }

    private static void appendHint(StringBuilder wb, String key, String description) {
        wb.append(BANNER_DIM)
                .append(key)
                .append(BANNER_RESET)
                .append(BANNER_MUTED)
                .append(description)
                .append(BANNER_RESET)
                .append("\n");
    }

    private void dispatchInput(
            String data,
            AgentSession session,
            BlockingQueue<String> submitQueue,
            AtomicBoolean eofFlag,
            AtomicBoolean executingPrompt,
            AtomicBoolean abortedFlag,
            AtomicReference<AgentSession> sessionRef,
            AtomicBoolean followUpFlag) {
        if (activeOverlay != null) {
            activeOverlay.handleInput(data);
            tui.render();
            return;
        }
        int i = 0;
        while (i < data.length()) {
            char ch = data.charAt(i);
            int advance = handleControlChar(
                    ch, data, i, session, submitQueue, eofFlag, executingPrompt, abortedFlag, sessionRef, followUpFlag);
            if (advance == EOF_RETURN) {
                return;
            }
            if (advance > 0) {
                i += advance;
                continue;
            }
            if (ch == '\033') {
                i = handleEscapeSequence(
                        data, i, session, submitQueue, executingPrompt, abortedFlag, sessionRef, followUpFlag);
            } else {
                editorContainer.handleInput(String.valueOf(ch));
                i++;
            }
        }
        refreshBashModeBorder(session);
        tui.render();
    }

    private static final int EOF_RETURN = -1;

    // Handles single-char globals (Ctrl+D/O/T/P/L/G/V/Z/C). Returns:
    //   EOF_RETURN to exit dispatchInput entirely (Ctrl+D, double Ctrl+C),
    //   >0  to advance i by that many chars,
    //   0   when ch wasn't a global and caller should fall through.
    private int handleControlChar(
            char ch,
            String data,
            int i,
            AgentSession session,
            BlockingQueue<String> submitQueue,
            AtomicBoolean eofFlag,
            AtomicBoolean executingPrompt,
            AtomicBoolean abortedFlag,
            AtomicReference<AgentSession> sessionRef,
            AtomicBoolean followUpFlag) {
        if (ch == 4) {
            eofFlag.set(true);
            submitQueue.add("");
            return EOF_RETURN;
        }
        if (ch == 3) {
            return handleCtrlC(session, submitQueue, eofFlag, executingPrompt, abortedFlag, sessionRef);
        }
        if (handleSimpleControlChar(ch, session)) {
            return 1;
        }
        return 0;
    }

    // Single-shot control characters that don't need access to the input loop
    // state. Returns true if {@code ch} was handled.
    private boolean handleSimpleControlChar(char ch, AgentSession session) {
        switch (ch) {
            case 15 -> { // Ctrl+O — toggle tool output expand/collapse
                toolsExpanded = !toolsExpanded;
                for (var child : chatContainer.getChildren()) {
                    if (child instanceof ToolStatusComponent tool) {
                        tool.setExpanded(toolsExpanded);
                    }
                }
            }
            case 20 -> toggleThinkingBlockVisibility(); // Ctrl+T
            case 16 -> cycleModel(session, true); // Ctrl+P
            case 12 -> showModelSelector(session); // Ctrl+L
            case 7 -> openExternalEditor(); // Ctrl+G
            case 22 -> pasteClipboardImage(session); // Ctrl+V
            case 26 -> {
                // Ctrl+Z — suspendSelf already does its own render
                suspendSelf();
                return true;
            }
            default -> {
                return false;
            }
        }
        tui.render();
        return true;
    }

    private void suspendSelf() {
        tui.stop();
        try {
            new ProcessBuilder(
                            "kill",
                            "-TSTP",
                            String.valueOf(ProcessHandle.current().pid()))
                    .inheritIO()
                    .start()
                    .waitFor();
        } catch (Exception ignored) {
            // SIGTSTP self-send is a convenience; fall through and re-render.
        }
        tui.start();
        tui.render();
    }

    private int handleCtrlC(
            AgentSession session,
            BlockingQueue<String> submitQueue,
            AtomicBoolean eofFlag,
            AtomicBoolean executingPrompt,
            AtomicBoolean abortedFlag,
            AtomicReference<AgentSession> sessionRef) {
        if (executingPrompt.get()) {
            abortedFlag.set(true);
            sessionRef.get().abort();
            return 1;
        }
        var token = bashCancelToken;
        if (token != null) {
            token.cancel();
        }
        long now = System.currentTimeMillis();
        if (now - lastCtrlCTime < 500) {
            eofFlag.set(true);
            submitQueue.add("");
            return EOF_RETURN;
        }
        lastCtrlCTime = now;
        editorContainer.clear();
        bashMode = false;
        editorContainer.setBorderForThinkingLevel(currentThinkingLevel(session));
        tui.render();
        return 1;
    }

    // Dispatches `\033` sequences. Returns the new i (after consuming the sequence).
    private int handleEscapeSequence(
            String data,
            int i,
            AgentSession session,
            BlockingQueue<String> submitQueue,
            AtomicBoolean executingPrompt,
            AtomicBoolean abortedFlag,
            AtomicReference<AgentSession> sessionRef,
            AtomicBoolean followUpFlag) {
        // Standalone Escape — abort streaming, or double-escape → tree.
        if (i + 1 >= data.length()) {
            handleStandaloneEscape(session, executingPrompt, abortedFlag, sessionRef);
            return i + 1;
        }

        // Kitty protocol: Alt+Enter \033[13;2u
        if (data.startsWith("\033[13;2u", i)) {
            queueFollowUp(submitQueue, followUpFlag);
            return i + 7;
        }

        // Shift+Tab: \033[Z — cycle thinking level (unless slash menu is showing).
        if (data.startsWith("\033[Z", i) && !editorContainer.isShowingSuggestions()) {
            cycleThinkingLevel(session);
            tui.render();
            return i + 3;
        }

        // Shift+Ctrl+P (Kitty variant).
        if (data.startsWith("\033[112;6u", i)) {
            cycleModel(session, false);
            tui.render();
            return i + 8;
        }

        // Alt+Enter: ESC CR.
        if (data.charAt(i + 1) == '\r') {
            queueFollowUp(submitQueue, followUpFlag);
            return i + 2;
        }
        int seqEnd = findEscapeSequenceEnd(data, i);
        editorContainer.handleInput(data.substring(i, seqEnd));
        return seqEnd;
    }

    private void handleStandaloneEscape(
            AgentSession session,
            AtomicBoolean executingPrompt,
            AtomicBoolean abortedFlag,
            AtomicReference<AgentSession> sessionRef) {
        if (executingPrompt.get()) {
            abortedFlag.set(true);
            sessionRef.get().abort();
            return;
        }
        var token = bashCancelToken;
        if (token != null) {
            token.cancel();
        }
        long now = System.currentTimeMillis();
        if (now - lastEscapeTime < 500) {
            showTreeSelector(session);
            lastEscapeTime = 0;
        } else {
            lastEscapeTime = now;
        }
    }

    private void queueFollowUp(BlockingQueue<String> submitQueue, AtomicBoolean followUpFlag) {
        followUpFlag.set(true);
        String text = editorContainer.getEditor().getText();
        if (text != null && !text.trim().isEmpty()) {
            submitQueue.add(text);
        }
    }

    private void refreshBashModeBorder(AgentSession session) {
        String editorText = editorContainer.getEditor().getText();
        boolean wasBashMode = bashMode;
        bashMode = editorText != null && editorText.stripLeading().startsWith("!");
        if (wasBashMode == bashMode) {
            return;
        }
        if (bashMode) {
            editorContainer.setBorderColor(EditorContainer.BORDER_BASH);
        } else {
            editorContainer.setBorderForThinkingLevel(currentThinkingLevel(session));
        }
    }

    private static String currentThinkingLevel(AgentSession session) {
        var level = session.getAgent().getState().getThinkingLevel();
        return level != null ? level.value() : "medium";
    }

    private void runReplLoop(
            AgentSession session,
            String cwd,
            BlockingQueue<String> submitQueue,
            AtomicBoolean eofFlag,
            AtomicBoolean executingPrompt,
            AtomicBoolean abortedFlag,
            AtomicBoolean followUpFlag) {
        while (!eofFlag.get()) {
            abortedFlag.set(false);
            followUpFlag.set(false);
            String input;
            try {
                input = submitQueue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (eofFlag.get()) {
                return;
            }
            if (input == null || input.trim().isEmpty()) {
                tui.render();
                continue;
            }
            try {
                processReplInput(input.trim(), session, cwd, executingPrompt, abortedFlag, followUpFlag);
            } catch (com.campusclaw.codingagent.command.QuitException e) {
                return;
            }
        }
    }

    private void processReplInput(
            String trimmed,
            AgentSession session,
            String cwd,
            AtomicBoolean executingPrompt,
            AtomicBoolean abortedFlag,
            AtomicBoolean followUpFlag) {
        editorContainer.addToHistory(trimmed);
        editorContainer.clear();
        bashMode = false;
        editorContainer.setBorderForThinkingLevel(currentThinkingLevel(session));
        if (handleSpecialOverlayCommand(trimmed, session)) {
            return;
        }
        if (trimmed.startsWith("/")
                && !isSkillOrTemplate(trimmed, session)
                && handleSlashCommandWithSideEffects(trimmed, session, cwd)) {
            return;
        }
        if (trimmed.startsWith("!") && handleBashPrefix(trimmed, cwd)) {
            return;
        }
        if (followUpFlag.get() && session.isStreaming()) {
            session.getAgent().followUp(new UserMessage(trimmed, System.currentTimeMillis()));
            chatContainer.addChild(
                    new Text("\033[2m  ↳ Follow-up queued: " + truncateDisplay(trimmed, 60) + "\033[0m"));
            tui.render();
            return;
        }
        if (session.isStreaming()) {
            session.steer(trimmed);
            chatContainer.addChild(new UserMessageComponent(trimmed));
            tui.render();
            return;
        }
        executingPrompt.set(true);
        executePrompt(session, expandFileReferences(trimmed), abortedFlag);
        executingPrompt.set(false);
        checkAutoCompaction(session);
    }

    private boolean handleSpecialOverlayCommand(String trimmed, AgentSession session) {
        if ("/resume".equals(trimmed)) {
            showSessionSelector(session);
            tui.render();
            return true;
        }
        if ("/tree".equals(trimmed)) {
            showTreeSelector(session);
            tui.render();
            return true;
        }
        if ("/model".equals(trimmed) || "/models".equals(trimmed)) {
            showModelSelector(session);
            tui.render();
            return true;
        }
        return false;
    }

    private boolean handleSlashCommandWithSideEffects(String trimmed, AgentSession session, String cwd) {
        if (!handleSlashCommand(trimmed, session)) {
            return false;
        }
        if (trimmed.startsWith("/new") || trimmed.startsWith("/reload")) {
            buildCommandSuggestions(session);
        }
        if (trimmed.equals("/new")) {
            chatContainer.clear();
            chatContainer.addChild(new Text("\033[38;2;138;190;183m✓ New session started\033[0m", 1, 1));
            footer.resetUsage();
            lastStatusComponent = null;
            var sm = session.getSessionManager();
            if (sm != null) {
                sm.close();
                sm.createSession(cwd);
            }
        }
        if (trimmed.startsWith("/model ")) {
            var newModel = session.getAgent().getState().getModel();
            if (newModel != null) {
                footer.setModel(
                        newModel.provider().name().toLowerCase(Locale.ROOT),
                        newModel.id(),
                        newModel.contextWindow() > 0 ? newModel.contextWindow() : 200000,
                        newModel.reasoning());
            }
        }
        if (trimmed.startsWith("/name ")) {
            footer.setSessionName(trimmed.substring(6).trim());
        }
        tui.render();
        return true;
    }

    private boolean handleBashPrefix(String trimmed, String cwd) {
        boolean excluded = trimmed.startsWith("!!");
        String command =
                excluded ? trimmed.substring(2).trim() : trimmed.substring(1).trim();
        if (command.isEmpty()) {
            return false;
        }
        handleBashCommand(command, excluded, cwd);
        tui.render();
        return true;
    }

    private void startCronEngineWithTuiNotifications(AgentSession session) {
        if (cronService == null) {
            return;
        }
        var currentModel = session.getAgent().getState().getModel();
        if (currentModel != null) {
            cronService.setDefaultModelId(currentModel.id());
        }
        cronService.addListener(event -> {
            String cronTag = "\033[38;2;102;178;178m[cron]\033[0m ";
            String msg =
                    switch (event) {
                        case com.campusclaw.cron.model.CronEvent.JobStarted e -> cronTag + "Running: " + e.jobName();
                        case com.campusclaw.cron.model.CronEvent.JobCompleted e -> {
                            String line = cronTag + "Completed: " + e.jobName();
                            if (e.output() != null && !e.output().isBlank()) {
                                line += "\n" + e.output();
                            }
                            yield line;
                        }
                        case com.campusclaw.cron.model.CronEvent.JobFailed e ->
                            cronTag + "Failed: " + e.jobName() + " — " + e.error();
                    };
            synchronized (tuiLock) {
                chatContainer.addChild(new Text(msg));
                tui.render();
            }
        });
        cronService.start();
    }

    private boolean handleSlashCommand(String input, AgentSession session) {
        var outputLines = new ArrayList<String>();
        SlashCommandContext context = new SlashCommandContext(session, outputLines::add);
        boolean handled = commandRegistry.execute(input, context);
        if (handled) {
            // Don't echo slash commands as user messages (matching campusclaw behavior)
            if (!outputLines.isEmpty()) {
                chatContainer.addChild(new CommandOutputComponent(String.join("\n", outputLines)));
            }
        }
        return handled;
    }

    private void handleBashCommand(String command, boolean excluded, String cwd) {
        var component = new BashExecutionComponent(command, excluded);
        chatContainer.addChild(component);
        tui.render();

        var signal = new CancellationToken();
        bashCancelToken = signal;
        try {
            var options = new BashExecutorOptions(Duration.ofSeconds(120), signal, Map.of());
            BashExecutionResult result = bashExecutor.execute(command, Path.of(cwd), options);

            // Combine stdout and stderr
            var output = new StringBuilder();
            if (result.stdout() != null && !result.stdout().isEmpty()) {
                output.append(result.stdout());
            }
            if (result.stderr() != null && !result.stderr().isEmpty()) {
                if (!output.isEmpty()) {
                    output.append("\n");
                }
                output.append(result.stderr());
            }

            component.setResult(output.toString(), result.exitCode());
        } catch (IOException e) {
            component.setResult("Error: " + e.getMessage(), 1);
        } finally {
            bashCancelToken = null;
        }
    }

    private void executePrompt(AgentSession session, String input, AtomicBoolean aborted) {
        var userMessageComponent = new UserMessageComponent(input);
        chatContainer.addChild(userMessageComponent);
        int agentMessageCountBefore =
                session.getAgent().getState().getMessages().size();
        persistUserMessage(session, input);
        currentAssistantMessage = new AssistantMessageComponent();
        chatContainer.addChild(currentAssistantMessage);
        pendingTools.clear();
        tui.render();

        var spinnerTimer = startSpinnerTimer();
        Runnable unsub = session.getAgent().subscribe(event -> {
            synchronized (tuiLock) {
                handleEvent(event);
                tui.render();
            }
        });

        try {
            session.prompt(input).join();
        } catch (Exception e) {
            if (!aborted.get()) {
                String error = session.getAgent().getState().getError();
                chatContainer.addChild(new Text(
                        "\033[38;2;204;102;102m Error: " + (error != null ? error : e.getMessage()) + "\033[0m"));
            }
        }

        if (aborted.get()) {
            rollbackAbortedTurn(session, input, userMessageComponent, agentMessageCountBefore);
        }
        String error = session.getAgent().getState().getError();
        if (error != null && !aborted.get()) {
            chatContainer.addChild(new Text("\033[38;2;204;102;102m Error: " + error + "\033[0m"));
        }
        spinnerTimer.shutdownNow();

        finalizeAssistantBubble();
        if (unsub != null) {
            unsub.run();
        }
        tui.render();
    }

    private void persistUserMessage(AgentSession session, String input) {
        var sm = session.getSessionManager();
        if (sm != null) {
            sm.appendMessage(new UserMessage(input, System.currentTimeMillis()));
        }
        if (chatMemoryStore != null && sm != null) {
            try {
                chatMemoryStore.append(sm.getSessionId(), List.of(new UserMessage(input, System.currentTimeMillis())));
            } catch (Exception e) {
                log.debug("Failed to persist user message to ChatMemory (DB unavailable?): {}", e.getMessage());
            }
        }
    }

    // Drives re-renders at 80ms so the "Working..." spinner animates while
    // the assistant bubble has no content yet.
    private java.util.concurrent.ScheduledExecutorService startSpinnerTimer() {
        var timer = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "spinner-timer");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler(LoggingUncaughtExceptionHandler.INSTANCE);
            return t;
        });
        timer.scheduleAtFixedRate(
                () -> {
                    synchronized (tuiLock) {
                        if (currentAssistantMessage != null && !currentAssistantMessage.hasContent()) {
                            tui.render();
                        }
                    }
                },
                80,
                80,
                TimeUnit.MILLISECONDS);
        return timer;
    }

    // Remove the turn's chat display, roll back agent history, and restore the
    // user's input into the editor so they can amend and resubmit.
    private void rollbackAbortedTurn(
            AgentSession session,
            String input,
            UserMessageComponent userMessageComponent,
            int agentMessageCountBefore) {
        chatContainer.removeChild(userMessageComponent);
        if (currentAssistantMessage != null) {
            chatContainer.removeChild(currentAssistantMessage);
            currentAssistantMessage = null;
        }
        var agentState = session.getAgent().getState();
        var trimmed = new ArrayList<>(agentState.getMessages());
        while (trimmed.size() > agentMessageCountBefore) {
            trimmed.remove(trimmed.size() - 1);
        }
        agentState.replaceMessages(trimmed);
        editorContainer.getEditor().setText(input);
    }

    private void finalizeAssistantBubble() {
        if (currentAssistantMessage == null) {
            currentAssistantMessage = null;
            return;
        }
        currentAssistantMessage.setComplete(true);
        if (applicationContext != null && currentAssistantMessage.hasContent()) {
            String replyText = currentAssistantMessage.getTextContent();
            if (replyText != null && !replyText.isEmpty()) {
                try {
                    applicationContext.publishEvent(
                            new com.campusclaw.assistant.channel.gateway.AgentResponseEvent(this, replyText));
                } catch (Exception e) {
                    log.error("Failed to publish AgentResponseEvent", e);
                }
            }
        }
        currentAssistantMessage = null;
    }

    /**
     * Cycles thinking level: off → minimal → low → medium → high → xhigh → off.
     * Skips xhigh if model doesn't support it. Shows status message.
     *
     * @param session the session
     */
    private void cycleThinkingLevel(AgentSession session) {
        var model = session.getAgent().getState().getModel();
        if (model == null || !model.reasoning()) {
            showStatus("当前模型不支持 thinking");
            return;
        }

        ThinkingLevel[] levels = ModelRegistry.supportsXhigh(model)
                ? ThinkingLevel.values()
                : new ThinkingLevel[] {
                    ThinkingLevel.OFF,
                    ThinkingLevel.MINIMAL,
                    ThinkingLevel.LOW,
                    ThinkingLevel.MEDIUM,
                    ThinkingLevel.HIGH
                };

        var current = session.getAgent().getState().getThinkingLevel();
        int idx = 0;
        for (int j = 0; j < levels.length; j++) {
            if (levels[j] == current) {
                idx = j;
                break;
            }
        }
        var next = levels[(idx + 1) % levels.length];
        session.getAgent().setThinkingLevel(next);
        footer.setThinkingLevel(next.value());
        editorContainer.setBorderForThinkingLevel(next.value());

        // Persist thinking level change
        var sm = session.getSessionManager();
        if (sm != null) {
            sm.appendThinkingLevelChange(next.value());
        }
        showStatus("Thinking: " + next.value());
    }

    /**
     * Cycles through available models with configured auth.
     * @param forward true for next, false for previous
     *
     * @param session the session
     */
    private void cycleModel(AgentSession session, boolean forward) {
        if (modelRegistry == null) {
            return;
        }
        List<Model> candidates = resolveCycleCandidates();
        if (candidates.size() <= 1) {
            showStatus(scopedModels.isEmpty() ? "只有一个模型可用" : "只有一个 scoped 模型");
            return;
        }
        var currentModel = session.getAgent().getState().getModel();
        int currentIdx = indexOfModel(candidates, currentModel);
        int nextIdx = forward
                ? (currentIdx + 1) % candidates.size()
                : (currentIdx - 1 + candidates.size()) % candidates.size();
        var newModel = candidates.get(nextIdx);
        applyModelSwitch(session, newModel);
    }

    private List<Model> resolveCycleCandidates() {
        if (!scopedModels.isEmpty()) {
            return new ArrayList<>(scopedModels);
        }
        List<Model> all = modelRegistry.getAllModels();
        all.sort(Comparator.comparing((Model m) -> m.provider().value()).thenComparing(Model::id));
        return all;
    }

    private static int indexOfModel(List<Model> models, Model target) {
        if (target == null) {
            return 0;
        }
        for (int j = 0; j < models.size(); j++) {
            if (ModelRegistry.modelsAreEqual(models.get(j), target)) {
                return j;
            }
        }
        return 0;
    }

    private void applyModelSwitch(AgentSession session, Model newModel) {
        session.getAgent().setModel(newModel);
        footer.setModel(
                newModel.provider().name().toLowerCase(Locale.ROOT),
                newModel.id(),
                newModel.contextWindow() > 0 ? newModel.contextWindow() : 200000,
                newModel.reasoning());
        if (!newModel.reasoning()) {
            session.getAgent().setThinkingLevel(ThinkingLevel.OFF);
            footer.setThinkingLevel("off");
        }
        editorContainer.setBorderForThinkingLevel(
                session.getAgent().getState().getThinkingLevel() != null
                        ? session.getAgent().getState().getThinkingLevel().value()
                        : "off");
        var sm = session.getSessionManager();
        if (sm != null) {
            sm.appendModelChange(newModel.provider().value(), newModel.id());
        }
        String thinkingStr = newModel.reasoning()
                ? " • " + session.getAgent().getState().getThinkingLevel().value()
                : "";
        showStatus("切换到 " + newModel.name() + thinkingStr);
    }

    /**
     * Toggles thinking block visibility on all assistant messages.
     */
    private void toggleThinkingBlockVisibility() {
        hideThinkingBlock = !hideThinkingBlock;
        for (var child : chatContainer.getChildren()) {
            if (child instanceof AssistantMessageComponent msg) {
                msg.setHideThinking(hideThinkingBlock);
            }
        }
        showStatus("Thinking: " + (hideThinkingBlock ? "隐藏" : "可见"));
    }

    /**
     * Shows the model selector overlay. Replaces the chat area until dismissed.
     *
     * @param session the session
     */
    private void showModelSelector(AgentSession session) {
        if (modelRegistry == null) {
            return;
        }
        var currentModel = session.getAgent().getState().getModel();
        var overlay = new ModelSelectorOverlay(modelRegistry, currentModel);

        overlay.setOnSelect(model -> {
            // Apply model change
            session.getAgent().setModel(model);
            footer.setModel(
                    model.provider().name().toLowerCase(Locale.ROOT),
                    model.id(),
                    model.contextWindow() > 0 ? model.contextWindow() : 200000,
                    model.reasoning());
            if (!model.reasoning()) {
                session.getAgent().setThinkingLevel(ThinkingLevel.OFF);
                footer.setThinkingLevel("off");
            }
            editorContainer.setBorderForThinkingLevel(
                    session.getAgent().getState().getThinkingLevel() != null
                            ? session.getAgent().getState().getThinkingLevel().value()
                            : "off");

            // Persist model change
            var sm = session.getSessionManager();
            if (sm != null) {
                sm.appendModelChange(model.provider().value(), model.id());
            }

            dismissOverlay();
            showStatus("切换到 " + model.name());
        });
        overlay.setOnCancel(this::dismissOverlay);

        showOverlay(overlay);
    }

    /**
     * Shows the session selector overlay for /resume.
     *
     * @param session the session
     */
    private void showSessionSelector(AgentSession session) {
        String cwd = System.getProperty("user.dir", "");
        var overlay = new SessionSelectorOverlay(cwd);

        if (overlay.isEmpty()) {
            showStatus("当前目录没有历史会话");
            return;
        }

        overlay.setOnSelect(item -> {
            var sm = session.getSessionManager();
            if (sm != null) {
                var messages = sm.loadSession(item.path());
                if (!messages.isEmpty()) {
                    session.getAgent().clearMessages();
                    for (var msg : messages) {
                        session.getAgent().getState().appendMessage(msg);
                    }
                    dismissOverlay();
                    showStatus("已恢复会话 " + item.id() + " (" + messages.size() + " 条消息)");
                    return;
                }
            }
            dismissOverlay();
            showStatus("会话为空或加载失败");
        });
        overlay.setOnCancel(this::dismissOverlay);

        showOverlay(overlay);
    }

    /**
     * Shows the tree selector overlay for /tree command.
     *
     * @param session the session
     */
    private void showTreeSelector(AgentSession session) {
        var messages = session.getHistory();
        var overlay = new TreeSelectorOverlay(messages);

        if (overlay.isEmpty()) {
            showStatus("当前会话没有消息");
            return;
        }

        overlay.setOnSelect(item -> {
            // Navigate to selected message — trim messages after it
            var allMessages = session.getHistory();
            if (item.index() < allMessages.size()) {
                var trimmed = new ArrayList<>(allMessages.subList(0, item.index() + 1));
                session.getAgent().getState().setMessages(trimmed);
                dismissOverlay();

                // Rebuild chat display
                chatContainer.clear();
                for (var msg : trimmed) {
                    if (msg instanceof UserMessage um) {
                        String text = "";
                        for (var block : um.content()) {
                            if (block instanceof com.campusclaw.ai.types.TextContent tc) {
                                text = tc.text();
                                break;
                            }
                        }
                        chatContainer.addChild(new UserMessageComponent(text));
                    } else if (msg instanceof com.campusclaw.ai.types.AssistantMessage am) {
                        var comp = new AssistantMessageComponent();
                        comp.setHideThinking(hideThinkingBlock);
                        for (var block : am.content()) {
                            if (block instanceof com.campusclaw.ai.types.TextContent tc) {
                                comp.appendText(tc.text());
                            } else if (block instanceof com.campusclaw.ai.types.ThinkingContent tc) {
                                comp.appendThinking(tc.thinking());
                            }
                        }
                        comp.setComplete(true);
                        chatContainer.addChild(comp);
                    }
                }
                showStatus("跳转到消息 #" + (item.index() + 1));
            } else {
                dismissOverlay();
            }
        });
        overlay.setOnCancel(this::dismissOverlay);

        showOverlay(overlay);
    }

    /**
     * Shows an overlay component, replacing the chat area.
     *
     * @param overlay the overlay
     */
    private void showOverlay(Component overlay) {
        activeOverlay = overlay;
        root.clear();
        root.addChild(overlay);
        root.addChild(footer);
        tui.render();
    }

    /**
     * Dismisses the active overlay and restores the normal chat view.
     */
    private void dismissOverlay() {
        activeOverlay = null;
        lastStatusComponent = null;
        root.clear();
        root.addChild(chatContainer);
        root.addChild(editorContainer);
        root.addChild(footer);
        tui.render();
    }

    /**
     * Opens an external editor ($VISUAL or $EDITOR) with the current editor content.
     * On save, replaces the editor content with the file contents.
     */
    private void openExternalEditor() {
        String editorCmd = System.getenv("VISUAL");
        if (editorCmd == null || editorCmd.isBlank()) {
            editorCmd = System.getenv("EDITOR");
        }
        if (editorCmd == null || editorCmd.isBlank()) {
            showStatus("未配置编辑器。请设置 $VISUAL 或 $EDITOR 环境变量");
            return;
        }

        String currentText = editorContainer.getEditor().getText();
        if (currentText == null) {
            currentText = "";
        }

        try {
            Path tmpFile = Files.createTempFile("campusclaw-editor-", ".pi.md");
            Files.writeString(tmpFile, currentText);

            // Stop TUI to release terminal
            tui.stop();

            // Split command to support editor args (e.g., "code --wait")
            String[] parts = editorCmd.split("\\s+");
            var cmd = new ArrayList<>(List.of(parts));
            cmd.add(tmpFile.toString());

            var process = new ProcessBuilder(cmd).inheritIO().start();
            process.waitFor();

            if (process.exitValue() == 0) {
                String newContent = Files.readString(tmpFile).replaceAll("\\n$", "");
                editorContainer.getEditor().setText(newContent);
            }

            Files.deleteIfExists(tmpFile);

            // Restart TUI
            tui.start();
        } catch (Exception e) {
            tui.start();
            showStatus("编辑器错误: " + e.getMessage());
        }
    }

    /**
     * Pastes an image from the system clipboard into the conversation.
     * Uses osascript on macOS to detect clipboard image.
     *
     * @param session the session
     */
    private void pasteClipboardImage(AgentSession session) {
        try {
            // macOS: use osascript to check clipboard for image
            var check = new ProcessBuilder("osascript", "-e", "the clipboard info for (class PNGf)")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(check.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exit = check.waitFor();

            if (exit != 0 || output.isEmpty()) {
                showStatus("剪贴板中没有图片");
                return;
            }

            // Save clipboard image to temp file
            Path tmpFile = Files.createTempFile("campusclaw-clipboard-", ".png");
            var save = new ProcessBuilder(
                            "osascript",
                            "-e",
                            "set imgData to the clipboard as «class PNGf»\n" + "set fp to open for access POSIX file \""
                                    + tmpFile + "\" with write permission\n" + "write imgData to fp\n"
                                    + "close access fp")
                    .redirectErrorStream(true)
                    .start();
            save.waitFor();

            if (Files.exists(tmpFile) && Files.size(tmpFile) > 0) {
                showStatus("已粘贴图片: " + tmpFile.getFileName());
            } else {
                showStatus("读取剪贴板图片失败");
                Files.deleteIfExists(tmpFile);
            }
        } catch (Exception e) {
            showStatus("粘贴图片错误: " + e.getMessage());
        }
    }

    /**
     * Shows a temporary status message in the chat area.
     * Reuses the last status line if it's at the bottom (matches campusclaw behavior).
     *
     * @param message the message
     */
    private void showStatus(String message) {
        var children = chatContainer.getChildren();

        // Reuse last status component if it's the last child
        if (lastStatusComponent != null
                && !children.isEmpty()
                && children.get(children.size() - 1) == lastStatusComponent) {
            // Replace with new text
            chatContainer.removeChild(lastStatusComponent);
        }
        var text = new Text("\033[38;2;128;128;128m  " + message + "\033[0m", 1, 0);
        chatContainer.addChild(text);
        lastStatusComponent = text;
    }

    void handleEvent(AgentEvent event) {
        switch (event) {
            case TurnStartEvent e -> onTurnStart();
            case MessageUpdateEvent e -> onMessageUpdate(e);
            case MessageEndEvent e -> onMessageEnd(e);
            case ToolExecutionStartEvent e -> onToolStart(e);
            case ToolExecutionUpdateEvent e -> onToolUpdate(e);
            case ToolExecutionEndEvent e -> onToolEnd(e);
            default -> {
                // other events ignored by the interactive TUI
            }
        }
    }

    // Continuation turn after a non-tool finish: close the previous bubble so
    // the next text delta lazily opens a fresh one.
    private void onTurnStart() {
        if (currentAssistantMessage != null && currentAssistantMessage.hasContent()) {
            currentAssistantMessage.setComplete(true);
            currentAssistantMessage = null;
        }
    }

    private void onMessageUpdate(MessageUpdateEvent e) {
        if (currentAssistantMessage == null) {
            currentAssistantMessage = new AssistantMessageComponent();
            chatContainer.addChild(currentAssistantMessage);
        }
        if (e.assistantMessageEvent() instanceof AssistantMessageEvent.TextDeltaEvent delta) {
            currentAssistantMessage.appendText(delta.delta());
        } else if (e.assistantMessageEvent() instanceof AssistantMessageEvent.ThinkingDeltaEvent thinking) {
            currentAssistantMessage.appendThinking(thinking.delta());
        }
    }

    private void onMessageEnd(MessageEndEvent e) {
        if (!(e.message() instanceof AssistantMessage msg)) {
            return;
        }
        if (msg.usage() != null) {
            double cost = msg.usage().cost() != null ? msg.usage().cost().total() : 0;
            footer.updateUsage(
                    msg.usage().input(),
                    msg.usage().output(),
                    msg.usage().cacheRead(),
                    msg.usage().cacheWrite(),
                    cost);
        }
        persistAssistantMessage(msg);
    }

    private void persistAssistantMessage(AssistantMessage msg) {
        if (currentSession == null) {
            return;
        }
        var sm = currentSession.getSessionManager();
        if (sm != null) {
            sm.appendMessage(msg);
        }
        if (chatMemoryStore != null && sm != null) {
            try {
                chatMemoryStore.append(sm.getSessionId(), List.of(msg));
            } catch (Exception ex) {
                log.debug("Failed to persist assistant message to ChatMemory (DB unavailable?): {}", ex.getMessage());
            }
        }
    }

    // Close the current assistant bubble before the tool block so any text that
    // arrives in the next turn opens a fresh bubble below the tool. Drop the
    // bubble entirely if it never received any thinking/text — an empty
    // pre-created placeholder shouldn't leave a gap above the tool.
    private void onToolStart(ToolExecutionStartEvent e) {
        if (currentAssistantMessage != null) {
            currentAssistantMessage.setComplete(true);
            if (!currentAssistantMessage.hasContent()) {
                chatContainer.removeChild(currentAssistantMessage);
            }
            currentAssistantMessage = null;
        }
        var tool = new ToolStatusComponent(e.toolName());
        tool.setArgs(e.args());
        tool.setExpanded(toolsExpanded);
        pendingTools.put(e.toolCallId(), tool);
        chatContainer.addChild(tool);
    }

    private void onToolUpdate(ToolExecutionUpdateEvent e) {
        var tool = pendingTools.get(e.toolCallId());
        if (tool != null) {
            tool.updatePartialResult(e.partialResult());
        }
    }

    private void onToolEnd(ToolExecutionEndEvent e) {
        var tool = pendingTools.get(e.toolCallId());
        if (tool != null) {
            tool.setComplete(e.isError(), e.result());
        }
    }

    private void checkAutoCompaction(AgentSession session) {
        if (compactor == null) {
            return;
        }

        var model = session.getAgent().getState().getModel();
        if (model == null || model.contextWindow() <= 0) {
            return;
        }

        var messages = session.getHistory();
        if (compactor.needsCompaction(messages, model.contextWindow())) {
            chatContainer.addChild(new Text("\033[2m  Auto-compacting context...\033[0m"));
            tui.render();

            try {
                var result = compactor.compact(new ArrayList<>(messages), model);
                var newMessages = new ArrayList<Message>();
                if (!result.summary().isEmpty()) {
                    newMessages.add(new UserMessage(
                            "[Context compaction summary]\n" + result.summary(), System.currentTimeMillis()));
                }
                newMessages.addAll(result.retainedMessages());
                session.getAgent().replaceMessages(newMessages);

                int removed = messages.size() - result.retainedMessages().size();
                chatContainer.addChild(new Text("\033[2m  Compacted " + removed + " messages.\033[0m"));
            } catch (Exception e) {
                chatContainer.addChild(new Text("\033[31m  Auto-compaction failed: " + e.getMessage() + "\033[0m"));
            }
            tui.render();
        }
    }

    private String modelInfo(AgentSession session) {
        var model = session.getAgent().getState().getModel();
        if (model != null) {
            return "\033[2m (" + model.id() + ")\033[0m";
        }
        return "";
    }

    private static String truncateDisplay(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 3) + "...";
    }

    /**
     * Expands @file references in the input text. Each @filepath token is replaced
     * with the file's content. Matches campusclaw TS @file behavior.
     *
     * @param input the input
     * @return the result
     */
    static String expandFileReferences(String input) {
        if (input == null || !input.contains("@")) {
            return input;
        }

        var sb = new StringBuilder();
        var tokens = input.split("\\s+");
        boolean first = true;
        for (String token : tokens) {
            if (!first) {
                sb.append(' ');
            }
            first = false;

            if (token.startsWith("@") && token.length() > 1) {
                String filePath = token.substring(1);
                Path path = Path.of(filePath);
                if (Files.isRegularFile(path)) {
                    try {
                        sb.append(Files.readString(path));
                    } catch (IOException e) {
                        sb.append("[Error reading ")
                                .append(filePath)
                                .append(": ")
                                .append(e.getMessage())
                                .append("]");
                    }
                } else {
                    sb.append(token); // Not a valid file, keep as-is
                }
            } else {
                sb.append(token);
            }
        }
        return sb.toString();
    }

    /**
     * Checks if the input is a /skill:name invocation or a prompt template command,
     * both of which should be sent to AgentSession.prompt() for expansion instead
     * of being processed as slash commands.
     *
     * @param input the input
     * @param session the session
     * @return the result
     */
    private boolean isSkillOrTemplate(String input, AgentSession session) {
        if (input.startsWith("/skill:")) {
            return true;
        }

        // Check if it matches a prompt template name
        int spaceIdx = input.indexOf(' ');
        String name = spaceIdx >= 0 ? input.substring(1, spaceIdx) : input.substring(1);
        for (PromptTemplateEntry template : session.getPromptTemplates()) {
            if (template.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the full list of slash command suggestions for autocomplete,
     * combining built-in commands, skills, and prompt templates.
     *
     * @param session the session
     */
    private void buildCommandSuggestions(AgentSession session) {
        var suggestions = new ArrayList<CommandSuggestion>();

        // 1. Built-in slash commands
        for (var cmd : commandRegistry.getAll()) {
            suggestions.add(new CommandSuggestion(cmd.name(), cmd.description()));
        }

        // 2. Skills as /skill:name commands
        for (Skill skill : session.getSkillRegistry().getAll()) {
            suggestions.add(
                    new CommandSuggestion("skill:" + skill.name(), "[" + skill.source() + "] " + skill.description()));
        }

        // 3. Prompt templates as /templatename commands
        for (PromptTemplateEntry template : session.getPromptTemplates()) {
            suggestions.add(
                    new CommandSuggestion(template.name(), "[" + template.source() + "] " + template.description()));
        }

        // Sort alphabetically
        suggestions.sort(Comparator.comparing(CommandSuggestion::name, String.CASE_INSENSITIVE_ORDER));
        editorContainer.setCommands(suggestions);
    }

    static int findEscapeSequenceEnd(String data, int start) {
        if (start + 1 >= data.length()) {
            return start + 1;
        }
        char second = data.charAt(start + 1);
        if (second == '[') {
            int j = start + 2;
            while (j < data.length()) {
                if (data.charAt(j) >= 0x40 && data.charAt(j) <= 0x7E) {
                    return j + 1;
                }
                j++;
            }
            return data.length();
        }
        return start + 2;
    }
}
