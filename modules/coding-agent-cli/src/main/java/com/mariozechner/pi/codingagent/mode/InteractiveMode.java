package com.mariozechner.pi.codingagent.mode;

import com.mariozechner.pi.agent.event.*;
import com.mariozechner.pi.ai.PiAiService;
import com.mariozechner.pi.ai.stream.AssistantMessageEvent;
import com.mariozechner.pi.ai.types.AssistantMessage;
import com.mariozechner.pi.ai.types.Message;
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
import com.mariozechner.pi.tui.Tui;
import com.mariozechner.pi.tui.component.Container;
import com.mariozechner.pi.tui.component.Text;
import com.mariozechner.pi.tui.terminal.Terminal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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

    // TUI components
    private Tui tui;
    private Container root;
    private Container chatContainer;
    private EditorContainer editorContainer;
    private FooterComponent footer;

    // Streaming state
    private AssistantMessageComponent currentAssistantMessage;
    private final Map<String, ToolStatusComponent> pendingTools = new LinkedHashMap<>();

    // Bash mode state
    private boolean bashMode;

    // Follow-up / steering queues during compaction
    private final List<QueuedMessage> compactionQueue = new ArrayList<>();

    private record QueuedMessage(String text, String mode) {}

    public InteractiveMode(SlashCommandRegistry commandRegistry,
                           BashExecutor bashExecutor,
                           Compactor compactor) {
        this.commandRegistry = Objects.requireNonNull(commandRegistry, "commandRegistry");
        this.bashExecutor = bashExecutor;
        this.compactor = compactor;
    }

    /**
     * Runs the full-screen interactive REPL.
     */
    public void run(AgentSession session, Terminal terminal) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(terminal, "terminal");

        // Build component tree
        root = new Container();
        chatContainer = new Container();
        editorContainer = new EditorContainer();
        footer = new FooterComponent();

        // Welcome text with keybinding hints and resource info
        var wb = new StringBuilder();
        wb.append("\033[1m\033[36mPi Coding Agent\033[0m").append(modelInfo(session)).append("\n");

        // Show loaded resources
        int skillCount = session.getSkillRegistry().getAll().size();
        int templateCount = session.getPromptTemplates().size();
        if (skillCount > 0 || templateCount > 0) {
            wb.append("\033[2m  Loaded: ");
            if (skillCount > 0) wb.append(skillCount).append(" skill(s)");
            if (skillCount > 0 && templateCount > 0) wb.append(", ");
            if (templateCount > 0) wb.append(templateCount).append(" template(s)");
            wb.append("\033[0m\n");
        }

        wb.append("\n");
        wb.append("\033[2m  Tips:\033[0m\n");
        wb.append("\033[2m    Enter           submit message\033[0m\n");
        wb.append("\033[2m    Shift+Enter     new line (or type \\ before Enter)\033[0m\n");
        wb.append("\033[2m    Alt+Enter       queue follow-up message\033[0m\n");
        wb.append("\033[2m    ↑/↓             navigate command history\033[0m\n");
        wb.append("\033[2m    Ctrl+C          interrupt / clear input\033[0m\n");
        wb.append("\033[2m    Ctrl+D          exit\033[0m\n");
        wb.append("\033[2m    !               run bash command\033[0m\n");
        wb.append("\033[2m    !!              run bash (excluded from context)\033[0m\n");
        wb.append("\033[2m    /               slash commands (/help for list)\033[0m");
        chatContainer.addChild(new Text(wb.toString()));

        // Set model/footer info
        var model = session.getAgent().getState().getModel();
        if (model != null) {
            footer.setModel(
                    model.provider().name().toLowerCase(),
                    model.id(),
                    model.contextWindow() > 0 ? model.contextWindow() : 200000);
        }
        // Set cwd and thinking level
        String cwd = System.getProperty("user.dir", "");
        footer.setCwd(cwd);

        var thinkingLevel = session.getAgent().getState().getThinkingLevel();
        footer.setThinkingLevel(thinkingLevel != null ? thinkingLevel.value() : "medium");

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
        var submitLatch = new AtomicReference<CountDownLatch>();
        var submitValue = new AtomicReference<String>();
        var eofFlag = new AtomicBoolean(false);
        var executingPrompt = new AtomicBoolean(false);
        var abortedFlag = new AtomicBoolean(false);
        var sessionRef = new AtomicReference<>(session);
        var followUpFlag = new AtomicBoolean(false);

        editorContainer.setOnSubmit(value -> {
            var latch = submitLatch.get();
            if (latch != null) {
                submitValue.set(value);
                latch.countDown();
            }
        });

        tui.setInputHandler(data -> {
            int i = 0;
            while (i < data.length()) {
                char ch = data.charAt(i);

                // Global: Ctrl+D = exit
                if (ch == 4) {
                    eofFlag.set(true);
                    var latch = submitLatch.get();
                    if (latch != null) latch.countDown();
                    return;
                }

                // Global: Ctrl+C
                if (ch == 3) {
                    if (executingPrompt.get()) {
                        abortedFlag.set(true);
                        sessionRef.get().abort();
                    } else {
                        // Clear input
                        editorContainer.clear();
                        bashMode = false;
                        editorContainer.setBorderColor(EditorContainer.CYAN);
                        tui.render();
                    }
                    i++;
                    continue;
                }

                // Detect Alt+Enter for follow-up: ESC [ 1 3 ; 2 u (Kitty) or ESC CR
                if (ch == '\033' && i + 1 < data.length()) {
                    // Kitty protocol: \033[13;2u
                    if (data.startsWith("\033[13;2u", i)) {
                        followUpFlag.set(true);
                        var latch = submitLatch.get();
                        String text = editorContainer.getEditor().getText();
                        if (latch != null && text != null && !text.trim().isEmpty()) {
                            submitValue.set(text);
                            latch.countDown();
                        }
                        i += 7;
                        continue;
                    }
                    // Alt+Enter: ESC CR
                    if (data.charAt(i + 1) == '\r') {
                        followUpFlag.set(true);
                        var latch = submitLatch.get();
                        String text = editorContainer.getEditor().getText();
                        if (latch != null && text != null && !text.trim().isEmpty()) {
                            submitValue.set(text);
                            latch.countDown();
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
                editorContainer.setBorderColor(bashMode ? EditorContainer.YELLOW : EditorContainer.CYAN);
            }

            tui.render();
        });

        tui.start();
        tui.render();

        // REPL loop
        try {
            while (!eofFlag.get()) {
                // Wait for user input
                var latch = new CountDownLatch(1);
                submitLatch.set(latch);
                submitValue.set(null);
                abortedFlag.set(false);
                followUpFlag.set(false);

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (eofFlag.get()) break;

                String input = submitValue.get();
                if (input == null || input.trim().isEmpty()) {
                    tui.render();
                    continue;
                }

                String trimmed = input.trim();
                editorContainer.addToHistory(trimmed);
                editorContainer.clear();
                bashMode = false;
                editorContainer.setBorderColor(EditorContainer.CYAN);

                // Slash commands — skip if it's a /skill: invocation or prompt template
                if (trimmed.startsWith("/") && !isSkillOrTemplate(trimmed, session)) {
                    if (handleSlashCommand(trimmed, session)) {
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
            chatContainer.addChild(new UserMessageComponent(input));
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

        try {
            var options = new BashExecutorOptions(Duration.ofSeconds(120), null, Map.of());
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
        }
    }

    private void executePrompt(AgentSession session, String input, AtomicBoolean aborted) {
        chatContainer.addChild(new UserMessageComponent(input));

        currentAssistantMessage = new AssistantMessageComponent();
        chatContainer.addChild(currentAssistantMessage);
        pendingTools.clear();

        tui.render();

        Runnable unsub = session.getAgent().subscribe(event -> {
            handleEvent(event);
            tui.render();
        });

        CompletableFuture<Void> future = session.prompt(input);

        try {
            future.join();
        } catch (Exception e) {
            String error = session.getAgent().getState().getError();
            if (aborted.get()) {
                chatContainer.addChild(new Text("\033[2m  Aborted.\033[0m"));
            } else {
                chatContainer.addChild(new Text(
                        "\033[31m  Error: " + (error != null ? error : e.getMessage()) + "\033[0m"));
            }
        }

        String error = session.getAgent().getState().getError();
        if (error != null && !aborted.get()) {
            chatContainer.addChild(new Text("\033[31m  Error: " + error + "\033[0m"));
        }

        if (currentAssistantMessage != null) {
            currentAssistantMessage.setComplete(true);
        }
        currentAssistantMessage = null;

        if (unsub != null) unsub.run();
        tui.render();
    }

    void handleEvent(AgentEvent event) {
        switch (event) {
            case MessageUpdateEvent e -> {
                if (currentAssistantMessage == null) return;
                if (e.assistantMessageEvent() instanceof AssistantMessageEvent.TextDeltaEvent delta) {
                    currentAssistantMessage.appendText(delta.delta());
                } else if (e.assistantMessageEvent() instanceof AssistantMessageEvent.ThinkingDeltaEvent thinking) {
                    currentAssistantMessage.appendThinking(thinking.delta());
                }
            }
            case MessageEndEvent e -> {
                if (e.message() instanceof AssistantMessage msg && msg.usage() != null) {
                    double cost = msg.usage().cost() != null ? msg.usage().cost().total() : 0;
                    footer.updateUsage(msg.usage().input(), msg.usage().output(),
                            msg.usage().cacheRead(), msg.usage().cacheWrite(), cost);
                }
            }
            case ToolExecutionStartEvent e -> {
                var tool = new ToolStatusComponent(e.toolName());
                tool.setArgs(e.args());
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
