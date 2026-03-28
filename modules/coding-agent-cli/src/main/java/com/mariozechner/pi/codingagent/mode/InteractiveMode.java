package com.mariozechner.pi.codingagent.mode;

import com.mariozechner.pi.agent.event.*;
import com.mariozechner.pi.agent.tool.CancellationToken;
import com.mariozechner.pi.ai.PiAiService;
import com.mariozechner.pi.ai.model.ModelRegistry;
import com.mariozechner.pi.ai.stream.AssistantMessageEvent;
import com.mariozechner.pi.ai.types.AssistantMessage;
import com.mariozechner.pi.ai.types.Message;
import com.mariozechner.pi.ai.types.Model;
import com.mariozechner.pi.ai.types.ThinkingLevel;
import com.mariozechner.pi.ai.types.UserMessage;
import com.mariozechner.pi.codingagent.command.SlashCommand;
import com.mariozechner.pi.codingagent.command.SlashCommandContext;
import com.mariozechner.pi.codingagent.command.SlashCommandRegistry;
import com.mariozechner.pi.codingagent.compaction.Compactor;
import com.mariozechner.pi.codingagent.mode.tui.*;
import com.mariozechner.pi.codingagent.mode.tui.EditorContainer.CommandSuggestion;
import com.mariozechner.pi.codingagent.prompt.PromptTemplateEntry;
import com.mariozechner.pi.codingagent.skill.Skill;
import com.mariozechner.pi.codingagent.session.AgentSession;
import com.mariozechner.pi.codingagent.tool.bash.BashExecutionResult;
import com.mariozechner.pi.codingagent.tool.bash.BashExecutor;
import com.mariozechner.pi.codingagent.tool.bash.BashExecutorOptions;
import com.mariozechner.pi.tui.Component;
import com.mariozechner.pi.tui.Tui;
import com.mariozechner.pi.tui.component.Container;
import com.mariozechner.pi.tui.component.Text;
import com.mariozechner.pi.tui.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    // Cancellation token for the currently running bash command
    private volatile CancellationToken bashCancelToken;

    // Bash mode state
    private boolean bashMode;

    // Tool output expand/collapse state (toggled by Ctrl+O)
    private boolean toolsExpanded;

    // Thinking block visibility state (toggled by Ctrl+T)
    private boolean hideThinkingBlock;

    // Follow-up / steering queues during compaction
    private final List<QueuedMessage> compactionQueue = new ArrayList<>();

    private record QueuedMessage(String text, String mode) {}

    public InteractiveMode(SlashCommandRegistry commandRegistry,
                           BashExecutor bashExecutor,
                           Compactor compactor,
                           ModelRegistry modelRegistry) {
        this.commandRegistry = Objects.requireNonNull(commandRegistry, "commandRegistry");
        this.bashExecutor = bashExecutor;
        this.compactor = compactor;
        this.modelRegistry = modelRegistry;
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

        // Welcome text with keybinding hints matching pi-mono style
        // Colors from pi-mono dark theme: dim=#666666, muted=#808080
        String DIM = "\033[38;2;102;102;102m";
        String MUTED = "\033[38;2;128;128;128m";
        String RST = "\033[0m";
        var wb = new StringBuilder();
        wb.append("\033[1m\033[38;2;138;190;183mpi\033[0m").append(DIM).append(" v0.1.0").append(RST).append("\n");

        // Keybinding hints (aligned with pi-mono) — key in dim, description in muted
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
        wb.append(DIM).append(" Pi can explain its own features and look up its docs. Ask it how to use or extend Pi.").append(RST);
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
        // Set border color based on thinking level (matching pi-mono dynamic border)
        editorContainer.setBorderForThinkingLevel(thinkingLevelStr);

        // Register slash command autocomplete suggestions
        buildCommandSuggestions(session);

        root.addChild(chatContainer);
        root.addChild(editorContainer);
        root.addChild(footer);

        // Setup TUI
        tui = new Tui(terminal);
        tui.setRoot(root);

        // Set terminal title
        terminal.write("\033]0;pi — " + cwd + "\007");

        // Input routing — dispatch characters to editor, handle Ctrl+C/D globally
        BlockingQueue<String> submitQueue = new LinkedBlockingQueue<>();
        var eofFlag = new AtomicBoolean(false);
        var executingPrompt = new AtomicBoolean(false);
        var abortedFlag = new AtomicBoolean(false);
        var sessionRef = new AtomicReference<>(session);
        var followUpFlag = new AtomicBoolean(false);

        editorContainer.setOnSubmit(value -> {
            if (value != null) {
                submitQueue.add(value);
            }
        });

        tui.setInputHandler(data -> {
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

                // Global: Ctrl+L — select model (dispatch to /model)
                if (ch == 12) { // 0x0C = Ctrl+L
                    handleSlashCommand("/model", session);
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

                // Global: Ctrl+C
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
                    // Standalone Escape — abort streaming or cancel autocomplete
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

                    // Shift+Tab: \033[Z — cycle thinking level
                    if (data.startsWith("\033[Z", i)) {
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
            // Don't echo slash commands as user messages (matching pi-mono behavior)
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
            if (currentAssistantMessage != null && !currentAssistantMessage.hasContent()) {
                tui.render();
            }
        }, 80, 80, TimeUnit.MILLISECONDS);

        Runnable unsub = session.getAgent().subscribe(event -> {
            handleEvent(event);
            tui.render();
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

        var allModels = modelRegistry.getAllModels();
        if (allModels.size() <= 1) {
            showStatus("只有一个模型可用");
            return;
        }

        // Sort models by provider then id for stable ordering
        allModels.sort(Comparator.comparing((Model m) -> m.provider().value())
                .thenComparing(Model::id));

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
            Path tmpFile = Files.createTempFile("pi-editor-", ".pi.md");
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
            Path tmpFile = Files.createTempFile("pi-clipboard-", ".png");
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
     */
    private void showStatus(String message) {
        chatContainer.addChild(new Text(
                "\033[38;2;128;128;128m  " + message + "\033[0m", 1, 0));
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
     * with the file's content. Matches pi-mono TS @file behavior.
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
