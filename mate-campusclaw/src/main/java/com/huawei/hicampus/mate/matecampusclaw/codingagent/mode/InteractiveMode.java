package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.agent.event.*;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingLevel;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.CampusClawApplication;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandContext;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandRegistry;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction.Compactor;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui.*;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui.EditorContainer.CommandSuggestion;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.PromptTemplateEntry;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.AgentSession;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.skill.Skill;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashExecutionResult;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashExecutor;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.bash.BashExecutorOptions;
import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Tui;
import com.huawei.hicampus.mate.matecampusclaw.tui.component.Container;
import com.huawei.hicampus.mate.matecampusclaw.tui.component.Text;
import com.huawei.hicampus.mate.matecampusclaw.tui.terminal.Terminal;

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
 */
public class InteractiveMode {

    private final SlashCommandRegistry commandRegistry;
    private final BashExecutor bashExecutor;
    private final Compactor compactor;
    private final ModelRegistry modelRegistry;
    private final com.huawei.hicampus.mate.matecampusclaw.cron.CronService cronService;
    private final com.huawei.hicampus.mate.matecampusclaw.codingagent.loop.LoopManager loopManager;

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
     */
    public void setInitialPrompt(String prompt) {
        this.initialPrompt = prompt;
    }

    /**
     * Sets scoped models for Ctrl+P cycling (from --models flag).
     * When set, Ctrl+P cycles only through these models instead of all registered ones.
     */
    public void setScopedModels(List<Model> models) {
        this.scopedModels = models != null ? models : List.of();
    }

    public InteractiveMode(SlashCommandRegistry commandRegistry,
                           BashExecutor bashExecutor,
                           Compactor compactor,
                           ModelRegistry modelRegistry,
                           com.huawei.hicampus.mate.matecampusclaw.cron.CronService cronService,
                           com.huawei.hicampus.mate.matecampusclaw.codingagent.loop.LoopManager loopManager) {
        this.commandRegistry = Objects.requireNonNull(commandRegistry, "commandRegistry");
        this.bashExecutor = bashExecutor;
        this.compactor = compactor;
        this.modelRegistry = modelRegistry;
        this.cronService = cronService;
        this.loopManager = loopManager;
    }

    /**
     * Runs the full-screen interactive REPL.
     */
    public void run(AgentSession session, Terminal terminal) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(terminal, "terminal");
        this.currentSession = session;

        // Build component tree
        root = new Container();
        chatContainer = new Container();
        editorContainer = new EditorContainer();
        footer = new FooterComponent();

        // Welcome text with keybinding hints matching campusclaw style
        // Colors from campusclaw dark theme: dim=#666666, muted=#808080
        String DIM = "\033[38;2;102;102;102m";
        String MUTED = "\033[38;2;128;128;128m";
        String RST = "\033[0m";
        var wb = new StringBuilder();
        String version = CampusClawApplication.class.getPackage() != null
                && CampusClawApplication.class.getPackage().getImplementationVersion() != null
                ? CampusClawApplication.class.getPackage().getImplementationVersion() : "0.1.0";
        wb.append("\033[1m\033[38;2;138;190;183mCampusClaw\033[0m").append(DIM).append(" v").append(version).append(RST).append("\n");

        // Keybinding hints (aligned with campusclaw) — key in dim, description in muted
        wb.append(DIM).append(" escape").append(RST).append(MUTED).append(" to interrupt").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+c").append(RST).append(MUTED).append(" to clear").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+c twice").append(RST).append(MUTED).append(" to exit").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+d").append(RST).append(MUTED).append(" to exit (empty)").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+z").append(RST).append(MUTED).append(" to suspend").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+k").append(RST).append(MUTED).append(" to delete to end").append(RST).append("\n");
        wb.append(DIM).append(" shift+tab").append(RST).append(MUTED).append(" to cycle thinking level").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+p/shift+ctrl+p").append(RST).append(MUTED).append(" to cycle models").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+l").append(RST).append(MUTED).append(" to select model").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+o").append(RST).append(MUTED).append(" to expand tools").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+t").append(RST).append(MUTED).append(" to expand thinking").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+g").append(RST).append(MUTED).append(" for external editor").append(RST).append("\n");
        wb.append(DIM).append(" /").append(RST).append(MUTED).append(" for commands").append(RST).append("\n");
        wb.append(DIM).append(" !").append(RST).append(MUTED).append(" to run bash").append(RST).append("\n");
        wb.append(DIM).append(" !!").append(RST).append(MUTED).append(" to run bash (no context)").append(RST).append("\n");
        wb.append(DIM).append(" alt+enter").append(RST).append(MUTED).append(" to queue follow-up").append(RST).append("\n");
        wb.append(DIM).append(" alt+up").append(RST).append(MUTED).append(" to edit all queued messages").append(RST).append("\n");
        wb.append(DIM).append(" ctrl+v").append(RST).append(MUTED).append(" to paste image").append(RST).append("\n");
        wb.append(DIM).append(" drop files").append(RST).append(MUTED).append(" to attach").append(RST).append("\n");
        wb.append("\n");
        wb.append(DIM).append(" CampusClaw can explain its own features and look up its docs. Ask it how to use or extend CampusClaw.").append(RST);
        welcomeComponent = new Text(wb.toString());
        chatContainer.addChild(welcomeComponent);

        // Set model/footer info
        var model = session.getAgent().getState().getModel();
        if (model != null) {
            footer.setModel(
                    model.provider().name().toLowerCase(),
                    model.id(),
                    model.contextWindow() > 0 ? model.contextWindow() : 200000,
                    model.reasoning());
        }
        // Set cwd and thinking level
        String cwd = System.getProperty("user.dir", "");
        footer.setCwd(cwd);

        var thinkingLevel = session.getAgent().getState().getThinkingLevel();
        String thinkingLevelStr = thinkingLevel != null ? thinkingLevel.value() : "medium";
        footer.setThinkingLevel(thinkingLevelStr);
        // Set border color based on thinking level (matching campusclaw dynamic border)
        editorContainer.setBorderForThinkingLevel(thinkingLevelStr);

        // Register slash command autocomplete suggestions
        buildCommandSuggestions(session);

        root.addChild(chatContainer);
        root.addChild(editorContainer);
        root.addChild(footer);

        // Setup TUI
        tui = new Tui(terminal);
        tui.setRoot(root);

        // Start cron engine with TUI notifications
        if (cronService != null) {
            // Set default model for cron jobs based on current session model
            var currentModel = session.getAgent().getState().getModel();
            if (currentModel != null) {
                cronService.setDefaultModelId(currentModel.id());
            }
            cronService.addListener(event -> {
                String cronTag = "\033[38;2;102;178;178m[cron]\033[0m ";
                String msg = switch (event) {
                    case com.huawei.hicampus.mate.matecampusclaw.cron.model.CronEvent.JobStarted e ->
                        cronTag + "Running: " + e.jobName();
                    case com.huawei.hicampus.mate.matecampusclaw.cron.model.CronEvent.JobCompleted e -> {
                        String line = cronTag + "Completed: " + e.jobName();
                        if (e.output() != null && !e.output().isBlank()) {
                            line += "\n" + e.output();
                        }
                        yield line;
                    }
                    case com.huawei.hicampus.mate.matecampusclaw.cron.model.CronEvent.JobFailed e ->
                        cronTag + "Failed: " + e.jobName() + " — " + e.error();
                };
                synchronized (tuiLock) {
                    chatContainer.addChild(new Text(msg));
                    tui.render();
                }
            });
            cronService.start();
        }

        // Set terminal title
        terminal.write("\033]0;pi — " + cwd + "\007");

        // Input routing — dispatch characters to editor, handle Ctrl+C/D globally
        BlockingQueue<String> submitQueue = new LinkedBlockingQueue<>();
        var eofFlag = new AtomicBoolean(false);
        var executingPrompt = new AtomicBoolean(false);
        var abortedFlag = new AtomicBoolean(false);
        var sessionRef = new AtomicReference<>(session);
        var followUpFlag = new AtomicBoolean(false);

        // Initialize loop manager for in-session recurring prompts
        if (loopManager != null) {
            loopManager.init(submitQueue, executingPrompt);
        }

        editorContainer.setOnSubmit(value -> {
            if (value != null) {
                submitQueue.add(value);
            }
        });

        tui.setInputHandler(data -> {
            // Route input to overlay when active
            if (activeOverlay != null) {
                activeOverlay.handleInput(data);
                tui.render();
                return;
            }

            int i = 0;
            while (i < data.length()) {
                char ch = data.charAt(i);

                // Global: Ctrl+D = exit
                if (ch == 4) {
                    eofFlag.set(true);
                    submitQueue.add(""); // unblock the queue
                    return;
                }

                // Global: Ctrl+O — toggle tool output expand/collapse
                if (ch == 15) { // 0x0F = Ctrl+O
                    toolsExpanded = !toolsExpanded;
                    for (var child : chatContainer.getChildren()) {
                        if (child instanceof ToolStatusComponent tool) {
                            tool.setExpanded(toolsExpanded);
                        }
                    }
                    tui.render();
                    i++;
                    continue;
                }

                // Global: Ctrl+T — toggle thinking block visibility
                if (ch == 20) { // 0x14 = Ctrl+T
                    toggleThinkingBlockVisibility();
                    tui.render();
                    i++;
                    continue;
                }

                // Global: Ctrl+P — cycle model forward
                if (ch == 16) { // 0x10 = Ctrl+P
                    cycleModel(session, true);
                    tui.render();
                    i++;
                    continue;
                }

                // Global: Ctrl+L — open model selector overlay
                if (ch == 12) { // 0x0C = Ctrl+L
                    showModelSelector(session);
                    tui.render();
                    i++;
                    continue;
                }

                // Global: Ctrl+G — open external editor
                if (ch == 7) { // 0x07 = Ctrl+G
                    openExternalEditor();
                    tui.render();
                    i++;
                    continue;
                }

                // Global: Ctrl+V — paste image from clipboard
                if (ch == 22) { // 0x16 = Ctrl+V
                    pasteClipboardImage(session);
                    tui.render();
                    i++;
                    continue;
                }

                // Global: Ctrl+Z — suspend process
                if (ch == 26) { // 0x1A = Ctrl+Z
                    tui.stop();
                    try {
                        // Send SIGTSTP to self
                        ProcessHandle.current().pid();
                        new ProcessBuilder("kill", "-TSTP", String.valueOf(ProcessHandle.current().pid()))
                                .inheritIO().start().waitFor();
                    } catch (Exception ignored) {}
                    tui.start();
                    tui.render();
                    i++;
                    continue;
                }

                // Global: Ctrl+C — clear input, or double-tap to exit
                if (ch == 3) {
                    if (executingPrompt.get()) {
                        abortedFlag.set(true);
                        sessionRef.get().abort();
                    } else {
                        // Cancel running bash command if any
                        var token = bashCancelToken;
                        if (token != null) {
                            token.cancel();
                        }

                        // Double Ctrl+C within 500ms → exit
                        long now = System.currentTimeMillis();
                        if (now - lastCtrlCTime < 500) {
                            eofFlag.set(true);
                            submitQueue.add("");
                            return;
                        }
                        lastCtrlCTime = now;

                        // Clear input
                        editorContainer.clear();
                        bashMode = false;
                        editorContainer.setBorderForThinkingLevel(
                                session.getAgent().getState().getThinkingLevel() != null
                                        ? session.getAgent().getState().getThinkingLevel().value() : "medium");
                        tui.render();
                    }
                    i++;
                    continue;
                }

                // Escape and escape sequences
                if (ch == '\033') {
                    // Standalone Escape — abort streaming, or double-escape → tree
                    if (i + 1 >= data.length()) {
                        if (executingPrompt.get()) {
                            abortedFlag.set(true);
                            sessionRef.get().abort();
                        } else {
                            // Cancel running bash command if any
                            var token = bashCancelToken;
                            if (token != null) {
                                token.cancel();
                            }

                            // Double-escape within 500ms → show tree
                            long now = System.currentTimeMillis();
                            if (now - lastEscapeTime < 500) {
                                showTreeSelector(session);
                                lastEscapeTime = 0;
                            } else {
                                lastEscapeTime = now;
                            }
                        }
                        i++;
                        continue;
                    }

                    // Kitty protocol: Alt+Enter \033[13;2u
                    if (data.startsWith("\033[13;2u", i)) {
                        followUpFlag.set(true);
                        String text = editorContainer.getEditor().getText();
                        if (text != null && !text.trim().isEmpty()) {
                            submitQueue.add(text);
                        }
                        i += 7;
                        continue;
                    }

                    // Shift+Tab: \033[Z — cycle thinking level (unless slash menu is showing)
                    if (data.startsWith("\033[Z", i) && !editorContainer.isShowingSuggestions()) {
                        cycleThinkingLevel(session);
                        tui.render();
                        i += 3;
                        continue;
                    }

                    // Shift+Ctrl+P: various terminal encodings
                    // Kitty: \033[112;6u   xterm: \033[1;6P   etc.
                    if (data.startsWith("\033[112;6u", i)) {
                        cycleModel(session, false);
                        tui.render();
                        i += 8;
                        continue;
                    }

                    // Alt+Enter: ESC CR
                    if (data.charAt(i + 1) == '\r') {
                        followUpFlag.set(true);
                        String text = editorContainer.getEditor().getText();
                        if (text != null && !text.trim().isEmpty()) {
                            submitQueue.add(text);
                        }
                        i += 2;
                        continue;
                    }

                    // Route escape sequence to editor
                    int seqEnd = findEscapeSequenceEnd(data, i);
                    editorContainer.handleInput(data.substring(i, seqEnd));
                    i = seqEnd;
                } else {
                    // Route to editor
                    editorContainer.handleInput(String.valueOf(ch));
                    i++;
                }
            }

            // Detect bash mode from editor content
            String editorText = editorContainer.getEditor().getText();
            boolean wasBashMode = bashMode;
            bashMode = editorText != null && editorText.stripLeading().startsWith("!");
            if (wasBashMode != bashMode) {
                if (bashMode) {
                    editorContainer.setBorderColor(EditorContainer.BORDER_BASH);
                } else {
                    editorContainer.setBorderForThinkingLevel(
                            session.getAgent().getState().getThinkingLevel() != null
                                    ? session.getAgent().getState().getThinkingLevel().value() : "medium");
                }
            }

            tui.render();
        });

        tui.start();
        tui.render();

        // Send initial prompt if provided (from CLI positional args)
        if (initialPrompt != null && !initialPrompt.isBlank()) {
            String expanded = expandFileReferences(initialPrompt);
            executingPrompt.set(true);
            executePrompt(session, expanded, abortedFlag);
            executingPrompt.set(false);
            checkAutoCompaction(session);
        }

        // REPL loop
        try {
            while (!eofFlag.get()) {
                // Wait for user input
                abortedFlag.set(false);
                followUpFlag.set(false);

                String input;
                try {
                    input = submitQueue.take(); // blocks until input available
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (eofFlag.get()) break;
                if (input == null || input.trim().isEmpty()) {
                    tui.render();
                    continue;
                }

                String trimmed = input.trim();
                editorContainer.addToHistory(trimmed);
                editorContainer.clear();
                bashMode = false;
                editorContainer.setBorderForThinkingLevel(
                                session.getAgent().getState().getThinkingLevel() != null
                                        ? session.getAgent().getState().getThinkingLevel().value() : "medium");

                // Special overlay commands
                if ("/resume".equals(trimmed)) {
                    showSessionSelector(session);
                    tui.render();
                    continue;
                }
                if ("/tree".equals(trimmed)) {
                    showTreeSelector(session);
                    tui.render();
                    continue;
                }

                // Slash commands — skip if it's a /skill: invocation or prompt template
                if (trimmed.startsWith("/") && !isSkillOrTemplate(trimmed, session)) {
                    if (handleSlashCommand(trimmed, session)) {
                        // Refresh state after commands that change it
                        if (trimmed.startsWith("/new") || trimmed.startsWith("/reload")) {
                            buildCommandSuggestions(session);
                        }
                        // Clear chat display and reset tokens on /new
                        if (trimmed.equals("/new")) {
                            chatContainer.clear();
                            chatContainer.addChild(new Text(
                                    "\033[38;2;138;190;183m\u2713 New session started\033[0m", 1, 1));
                            footer.resetUsage();
                            lastStatusComponent = null;
                            // Create a new session file
                            var sm = session.getSessionManager();
                            if (sm != null) {
                                sm.close();
                                sm.createSession(cwd);
                            }
                        }
                        // Update footer after model switch
                        if (trimmed.startsWith("/model ")) {
                            var newModel = session.getAgent().getState().getModel();
                            if (newModel != null) {
                                footer.setModel(
                                        newModel.provider().name().toLowerCase(),
                                        newModel.id(),
                                        newModel.contextWindow() > 0 ? newModel.contextWindow() : 200000,
                                        newModel.reasoning());
                            }
                        }
                        // Update footer session name after /name command
                        if (trimmed.startsWith("/name ")) {
                            footer.setSessionName(trimmed.substring(6).trim());
                        }
                        tui.render();
                        continue;
                    }
                }

                // Bash mode: ! or !! prefix
                if (trimmed.startsWith("!")) {
                    boolean excluded = trimmed.startsWith("!!");
                    String command = excluded ? trimmed.substring(2).trim() : trimmed.substring(1).trim();
                    if (!command.isEmpty()) {
                        handleBashCommand(command, excluded, cwd);
                        tui.render();
                        continue;
                    }
                }

                // Follow-up: queue message while streaming
                if (followUpFlag.get() && session.isStreaming()) {
                    session.getAgent().followUp(new UserMessage(trimmed, System.currentTimeMillis()));
                    chatContainer.addChild(new Text(
                            "\033[2m  ↳ Follow-up queued: " + truncateDisplay(trimmed, 60) + "\033[0m"));
                    tui.render();
                    continue;
                }

                // Steering: if agent is streaming, steer instead of new prompt
                if (session.isStreaming()) {
                    session.steer(trimmed);
                    chatContainer.addChild(new UserMessageComponent(trimmed));
                    tui.render();
                    continue;
                }

                // Expand @file references before sending
                String expandedInput = expandFileReferences(trimmed);

                // Execute prompt
                executingPrompt.set(true);
                executePrompt(session, expandedInput, abortedFlag);
                executingPrompt.set(false);

                // Auto-compaction check after prompt completes
                checkAutoCompaction(session);
            }
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

    private boolean handleSlashCommand(String input, AgentSession session) {
        var outputLines = new ArrayList<String>();
        SlashCommandContext context = new SlashCommandContext(
                session,
                outputLines::add
        );
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
                if (!output.isEmpty()) output.append("\n");
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
        chatContainer.addChild(new UserMessageComponent(input));

        // Persist user message to session file
        var sm = session.getSessionManager();
        if (sm != null) {
            sm.appendMessage(new UserMessage(input, System.currentTimeMillis()));
        }

        currentAssistantMessage = new AssistantMessageComponent();
        chatContainer.addChild(currentAssistantMessage);
        pendingTools.clear();

        tui.render();

        // Spinner animation timer — drives re-renders at 80ms for the "Working..." spinner
        var spinnerTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "spinner-timer");
            t.setDaemon(true);
            return t;
        });
        spinnerTimer.scheduleAtFixedRate(() -> {
            synchronized (tuiLock) {
                if (currentAssistantMessage != null && !currentAssistantMessage.hasContent()) {
                    tui.render();
                }
            }
        }, 80, 80, TimeUnit.MILLISECONDS);

        Runnable unsub = session.getAgent().subscribe(event -> {
            synchronized (tuiLock) {
                handleEvent(event);
                tui.render();
            }
        });

        CompletableFuture<Void> future = session.prompt(input);

        boolean showedAbort = false;
        try {
            future.join();
        } catch (Exception e) {
            String error = session.getAgent().getState().getError();
            if (aborted.get()) {
                chatContainer.addChild(new Text("\033[38;2;204;102;102m Operation aborted\033[0m"));
                showedAbort = true;
            } else {
                chatContainer.addChild(new Text(
                        "\033[38;2;204;102;102m Error: " + (error != null ? error : e.getMessage()) + "\033[0m"));
            }
        }

        // Show abort message if cancellation completed without exception
        if (aborted.get() && !showedAbort) {
            chatContainer.addChild(new Text("\033[38;2;204;102;102m Operation aborted\033[0m"));
        }

        String error = session.getAgent().getState().getError();
        if (error != null && !aborted.get()) {
            chatContainer.addChild(new Text("\033[38;2;204;102;102m Error: " + error + "\033[0m"));
        }

        // Stop spinner animation timer
        spinnerTimer.shutdownNow();

        if (currentAssistantMessage != null) {
            currentAssistantMessage.setComplete(true);
        }
        currentAssistantMessage = null;

        if (unsub != null) unsub.run();
        tui.render();
    }

    /**
     * Cycles thinking level: off → minimal → low → medium → high → xhigh → off.
     * Skips xhigh if model doesn't support it. Shows status message.
     */
    private void cycleThinkingLevel(AgentSession session) {
        var model = session.getAgent().getState().getModel();
        if (model == null || !model.reasoning()) {
            showStatus("当前模型不支持 thinking");
            return;
        }

        ThinkingLevel[] levels = ModelRegistry.supportsXhigh(model)
                ? ThinkingLevel.values()
                : new ThinkingLevel[]{ThinkingLevel.OFF, ThinkingLevel.MINIMAL, ThinkingLevel.LOW,
                        ThinkingLevel.MEDIUM, ThinkingLevel.HIGH};

        var current = session.getAgent().getState().getThinkingLevel();
        int idx = 0;
        for (int j = 0; j < levels.length; j++) {
            if (levels[j] == current) { idx = j; break; }
        }
        var next = levels[(idx + 1) % levels.length];
        session.getAgent().setThinkingLevel(next);
        footer.setThinkingLevel(next.value());
        editorContainer.setBorderForThinkingLevel(next.value());
        // Persist thinking level change
        var sm = session.getSessionManager();
        if (sm != null) sm.appendThinkingLevelChange(next.value());
        showStatus("Thinking: " + next.value());
    }

    /**
     * Cycles through available models with configured auth.
     * @param forward true for next, false for previous
     */
    private void cycleModel(AgentSession session, boolean forward) {
        if (modelRegistry == null) return;

        // Use scoped models if available, otherwise all models
        List<Model> candidates;
        if (!scopedModels.isEmpty()) {
            candidates = new ArrayList<>(scopedModels);
        } else {
            candidates = modelRegistry.getAllModels();
            // Sort models by provider then id for stable ordering
            candidates.sort(Comparator.comparing((Model m) -> m.provider().value())
                    .thenComparing(Model::id));
        }

        if (candidates.size() <= 1) {
            showStatus(scopedModels.isEmpty() ? "只有一个模型可用" : "只有一个 scoped 模型");
            return;
        }

        var allModels = candidates;

        var currentModel = session.getAgent().getState().getModel();
        int currentIdx = -1;
        if (currentModel != null) {
            for (int j = 0; j < allModels.size(); j++) {
                if (ModelRegistry.modelsAreEqual(allModels.get(j), currentModel)) {
                    currentIdx = j;
                    break;
                }
            }
        }
        if (currentIdx == -1) currentIdx = 0;

        int nextIdx = forward
                ? (currentIdx + 1) % allModels.size()
                : (currentIdx - 1 + allModels.size()) % allModels.size();
        var newModel = allModels.get(nextIdx);
        session.getAgent().setModel(newModel);

        footer.setModel(
                newModel.provider().name().toLowerCase(),
                newModel.id(),
                newModel.contextWindow() > 0 ? newModel.contextWindow() : 200000,
                newModel.reasoning());

        // Re-clamp thinking level for new model
        if (!newModel.reasoning()) {
            session.getAgent().setThinkingLevel(ThinkingLevel.OFF);
            footer.setThinkingLevel("off");
        }
        editorContainer.setBorderForThinkingLevel(
                session.getAgent().getState().getThinkingLevel() != null
                        ? session.getAgent().getState().getThinkingLevel().value() : "off");

        // Persist model change
        var sm = session.getSessionManager();
        if (sm != null) sm.appendModelChange(newModel.provider().value(), newModel.id());

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
     */
    private void showModelSelector(AgentSession session) {
        if (modelRegistry == null) return;
        var currentModel = session.getAgent().getState().getModel();
        var overlay = new ModelSelectorOverlay(modelRegistry, currentModel);

        overlay.setOnSelect(model -> {
            // Apply model change
            session.getAgent().setModel(model);
            footer.setModel(
                    model.provider().name().toLowerCase(),
                    model.id(),
                    model.contextWindow() > 0 ? model.contextWindow() : 200000,
                    model.reasoning());
            if (!model.reasoning()) {
                session.getAgent().setThinkingLevel(ThinkingLevel.OFF);
                footer.setThinkingLevel("off");
            }
            editorContainer.setBorderForThinkingLevel(
                    session.getAgent().getState().getThinkingLevel() != null
                            ? session.getAgent().getState().getThinkingLevel().value() : "off");
            // Persist model change
            var sm = session.getSessionManager();
            if (sm != null) sm.appendModelChange(model.provider().value(), model.id());

            dismissOverlay();
            showStatus("切换到 " + model.name());
        });
        overlay.setOnCancel(this::dismissOverlay);

        showOverlay(overlay);
    }

    /**
     * Shows the session selector overlay for /resume.
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
                            if (block instanceof com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent tc) {
                                text = tc.text();
                                break;
                            }
                        }
                        chatContainer.addChild(new UserMessageComponent(text));
                    } else if (msg instanceof com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage am) {
                        var comp = new AssistantMessageComponent();
                        comp.setHideThinking(hideThinkingBlock);
                        for (var block : am.content()) {
                            if (block instanceof com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent tc) {
                                comp.appendText(tc.text());
                            } else if (block instanceof com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingContent tc) {
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
        if (currentText == null) currentText = "";

        try {
            Path tmpFile = Files.createTempFile("campusclaw-editor-", ".pi.md");
            Files.writeString(tmpFile, currentText);

            // Stop TUI to release terminal
            tui.stop();

            // Split command to support editor args (e.g., "code --wait")
            String[] parts = editorCmd.split("\\s+");
            var cmd = new ArrayList<>(List.of(parts));
            cmd.add(tmpFile.toString());

            var process = new ProcessBuilder(cmd)
                    .inheritIO()
                    .start();
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
     */
    private void pasteClipboardImage(AgentSession session) {
        try {
            // macOS: use osascript to check clipboard for image
            var check = new ProcessBuilder("osascript", "-e",
                    "the clipboard info for (class PNGf)")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(check.getInputStream().readAllBytes()).trim();
            int exit = check.waitFor();

            if (exit != 0 || output.isEmpty()) {
                showStatus("剪贴板中没有图片");
                return;
            }

            // Save clipboard image to temp file
            Path tmpFile = Files.createTempFile("campusclaw-clipboard-", ".png");
            var save = new ProcessBuilder("osascript", "-e",
                    "set imgData to the clipboard as «class PNGf»\n" +
                    "set fp to open for access POSIX file \"" + tmpFile + "\" with write permission\n" +
                    "write imgData to fp\n" +
                    "close access fp")
                    .redirectErrorStream(true)
                    .start();
            save.waitFor();

            if (Files.exists(tmpFile) && Files.size(tmpFile) > 0) {
                // TODO: Attach image to message when image support is implemented
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
     */
    private void showStatus(String message) {
        var children = chatContainer.getChildren();
        // Reuse last status component if it's the last child
        if (lastStatusComponent != null && !children.isEmpty()
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
            case TurnStartEvent e -> {
                // Create a new AssistantMessageComponent for continuation turns
                // (after tool calls) so thinking/text from each turn are separate.
                if (currentAssistantMessage != null && currentAssistantMessage.hasContent()) {
                    currentAssistantMessage.setComplete(true);
                    currentAssistantMessage = new AssistantMessageComponent();
                    chatContainer.addChild(currentAssistantMessage);
                }
            }
            case MessageUpdateEvent e -> {
                if (currentAssistantMessage == null) return;
                if (e.assistantMessageEvent() instanceof AssistantMessageEvent.TextDeltaEvent delta) {
                    currentAssistantMessage.appendText(delta.delta());
                } else if (e.assistantMessageEvent() instanceof AssistantMessageEvent.ThinkingDeltaEvent thinking) {
                    currentAssistantMessage.appendThinking(thinking.delta());
                }
            }
            case MessageEndEvent e -> {
                if (e.message() instanceof AssistantMessage msg) {
                    if (msg.usage() != null) {
                        double cost = msg.usage().cost() != null ? msg.usage().cost().total() : 0;
                        footer.updateUsage(msg.usage().input(), msg.usage().output(),
                                msg.usage().cacheRead(), msg.usage().cacheWrite(), cost);
                    }
                    // Persist assistant message to session file
                    if (currentSession != null) {
                        var sm = currentSession.getSessionManager();
                        if (sm != null) sm.appendMessage(msg);
                    }
                }
            }
            case ToolExecutionStartEvent e -> {
                var tool = new ToolStatusComponent(e.toolName());
                tool.setArgs(e.args());
                tool.setExpanded(toolsExpanded);
                pendingTools.put(e.toolCallId(), tool);
                chatContainer.addChild(tool);
            }
            case ToolExecutionUpdateEvent e -> {
                var tool = pendingTools.get(e.toolCallId());
                if (tool != null) {
                    tool.updatePartialResult(e.partialResult());
                }
            }
            case ToolExecutionEndEvent e -> {
                var tool = pendingTools.get(e.toolCallId());
                if (tool != null) {
                    tool.setComplete(e.isError(), e.result());
                }
            }
            default -> { }
        }
    }

    private void checkAutoCompaction(AgentSession session) {
        if (compactor == null) return;

        var model = session.getAgent().getState().getModel();
        if (model == null || model.contextWindow() <= 0) return;

        var messages = session.getHistory();
        if (compactor.needsCompaction(messages, model.contextWindow())) {
            chatContainer.addChild(new Text("\033[2m  Auto-compacting context...\033[0m"));
            tui.render();

            try {
                var result = compactor.compact(new ArrayList<>(messages), model);
                var newMessages = new ArrayList<Message>();
                if (!result.summary().isEmpty()) {
                    newMessages.add(new UserMessage(
                            "[Context compaction summary]\n" + result.summary(),
                            System.currentTimeMillis()));
                }
                newMessages.addAll(result.retainedMessages());
                session.getAgent().replaceMessages(newMessages);

                int removed = messages.size() - result.retainedMessages().size();
                chatContainer.addChild(new Text(
                        "\033[2m  Compacted " + removed + " messages.\033[0m"));
            } catch (Exception e) {
                chatContainer.addChild(new Text(
                        "\033[31m  Auto-compaction failed: " + e.getMessage() + "\033[0m"));
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
        if (s.length() <= max) return s;
        return s.substring(0, max - 3) + "...";
    }

    /**
     * Expands @file references in the input text. Each @filepath token is replaced
     * with the file's content. Matches campusclaw TS @file behavior.
     */
    static String expandFileReferences(String input) {
        if (input == null || !input.contains("@")) return input;

        var sb = new StringBuilder();
        var tokens = input.split("\\s+");
        boolean first = true;
        for (String token : tokens) {
            if (!first) sb.append(' ');
            first = false;

            if (token.startsWith("@") && token.length() > 1) {
                String filePath = token.substring(1);
                Path path = Path.of(filePath);
                if (Files.isRegularFile(path)) {
                    try {
                        sb.append(Files.readString(path));
                    } catch (IOException e) {
                        sb.append("[Error reading ").append(filePath).append(": ").append(e.getMessage()).append("]");
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
     */
    private boolean isSkillOrTemplate(String input, AgentSession session) {
        if (input.startsWith("/skill:")) return true;

        // Check if it matches a prompt template name
        int spaceIdx = input.indexOf(' ');
        String name = spaceIdx >= 0 ? input.substring(1, spaceIdx) : input.substring(1);
        for (PromptTemplateEntry template : session.getPromptTemplates()) {
            if (template.name().equals(name)) return true;
        }
        return false;
    }

    /**
     * Builds the full list of slash command suggestions for autocomplete,
     * combining built-in commands, skills, and prompt templates.
     */
    private void buildCommandSuggestions(AgentSession session) {
        var suggestions = new ArrayList<CommandSuggestion>();

        // 1. Built-in slash commands
        for (var cmd : commandRegistry.getAll()) {
            suggestions.add(new CommandSuggestion(cmd.name(), cmd.description()));
        }

        // 2. Skills as /skill:name commands
        for (Skill skill : session.getSkillRegistry().getAll()) {
            suggestions.add(new CommandSuggestion(
                    "skill:" + skill.name(),
                    "[" + skill.source() + "] " + skill.description()));
        }

        // 3. Prompt templates as /templatename commands
        for (PromptTemplateEntry template : session.getPromptTemplates()) {
            suggestions.add(new CommandSuggestion(
                    template.name(),
                    "[" + template.source() + "] " + template.description()));
        }

        // Sort alphabetically
        suggestions.sort(Comparator.comparing(CommandSuggestion::name, String.CASE_INSENSITIVE_ORDER));
        editorContainer.setCommands(suggestions);
    }

    static int findEscapeSequenceEnd(String data, int start) {
        if (start + 1 >= data.length()) return start + 1;
        char second = data.charAt(start + 1);
        if (second == '[') {
            int j = start + 2;
            while (j < data.length()) {
                if (data.charAt(j) >= 0x40 && data.charAt(j) <= 0x7E) return j + 1;
                j++;
            }
            return data.length();
        }
        return start + 2;
    }
}
