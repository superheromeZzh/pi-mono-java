package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui.*;
import com.huawei.hicampus.mate.matecampusclaw.tui.Tui;
import com.huawei.hicampus.mate.matecampusclaw.tui.component.*;
import com.huawei.hicampus.mate.matecampusclaw.tui.terminal.TestTerminal;

import org.junit.jupiter.api.Test;

/**
 * Visual rendering test — validates the full TUI output matches expectations.
 * Run this test and inspect the output to verify visual correctness.
 */
class TuiVisualTest {

    @Test
    void fullConversationRendersCorrectly() {
        var root = new Container();
        var chatContainer = new Container();
        var editorContainer = new EditorContainer();
        var footer = new FooterComponent();

        // Welcome
        chatContainer.addChild(new Text(
                "\033[1m\033[36mCampusClaw\033[0m\033[2m (glm-5)\033[0m\n"
                        + "\033[2mType /help for commands, /quit to exit\033[0m"));

        // User message
        chatContainer.addChild(new UserMessageComponent("你好，你是哪个大模型"));

        // Assistant with thinking + text
        var assistant = new AssistantMessageComponent();
        assistant.appendThinking("The user is asking which model I am.");
        assistant.appendText("你好！我是一个 AI 编码助手。\n\n目前运行在 **pi** 中。");
        assistant.setComplete(true);
        chatContainer.addChild(assistant);

        // Footer
        footer.setModel("zai", "glm-5", 200000, true);
        footer.setCwd("/Users/z/campusclaw");
        footer.setThinkingLevel("medium");
        footer.updateUsage(752, 71, 832, 0, 0.001);

        root.addChild(chatContainer);
        root.addChild(editorContainer);
        root.addChild(footer);

        List<String> lines = root.render(80);

        // Print for visual inspection
        System.out.println("=== RENDERED (" + lines.size() + " lines) ===");
        for (int i = 0; i < lines.size(); i++) {
            String clean = stripAnsi(lines.get(i));
            System.out.printf("%3d: |%s|\n", i, clean);
        }

        // Structural assertions
        assertTrue(lines.size() >= 10, "Should have at least 10 lines, got " + lines.size());

        String all = String.join("\n", lines.stream().map(this::stripAnsi).toList());

        // Welcome
        assertTrue(all.contains("CampusClaw"), "Missing welcome");

        // User message with background (check raw ANSI for RGB bg)
        String rawAll = String.join("\n", lines);
        assertTrue(rawAll.contains("\033[48;2;52;53;65m"), "Missing user message background color");

        // User message content
        assertTrue(all.contains("你好"), "Missing user message text");

        // Thinking content (italic dim)
        assertTrue(rawAll.contains("\033[3m"), "Missing italic for thinking");
        assertTrue(all.contains("asking which model"), "Missing thinking content");

        // Assistant markdown — **pi** should render as bold
        assertTrue(rawAll.contains("\033[1m"), "Missing bold in markdown");

        // "..." should NOT appear (message is complete)
        assertFalse(all.contains("..."), "Streaming indicator '...' should not appear when complete");

        // Editor — two separator lines
        long separatorCount = lines.stream()
                .map(this::stripAnsi)
                .filter(l -> l.matches("─{10,}"))
                .count();
        assertTrue(separatorCount >= 2, "Should have at least 2 separator lines (editor), got " + separatorCount);

        // Editor input area — check for inverse video cursor (empty editor)
        assertTrue(rawAll.contains("\033[7m"), "Missing editor cursor (inverse video)");

        // Footer — pwd with ~
        assertTrue(all.contains("~"), "Missing ~ in footer pwd");

        // Footer — token stats
        assertTrue(all.contains("752"), "Missing input tokens");
        assertTrue(all.contains("71"), "Missing output tokens");

        // Footer — cost
        assertTrue(all.contains("$0.001"), "Missing cost");

        // Footer — model info
        assertTrue(all.contains("(zai)"), "Missing provider");
        assertTrue(all.contains("glm-5"), "Missing model name");

        // Footer — thinking level
        assertTrue(all.contains("medium"), "Missing thinking level");
    }

    @Test
    void streamingIndicatorAppearsWhenIncomplete() {
        var assistant = new AssistantMessageComponent();
        // No content yet, not complete
        var lines = assistant.render(80);
        String all = String.join("\n", lines.stream().map(this::stripAnsi).toList());
        assertTrue(all.contains("..."), "Should show '...' when streaming with no content");
    }

    @Test
    void streamingIndicatorDisappearsOnComplete() {
        var assistant = new AssistantMessageComponent();
        assistant.appendText("Hello");
        assistant.setComplete(true);
        var lines = assistant.render(80);
        String all = String.join("\n", lines.stream().map(this::stripAnsi).toList());
        assertFalse(all.contains("..."), "Should NOT show '...' when complete");
        assertTrue(all.contains("Hello"), "Should show text content");
    }

    @Test
    void streamingIndicatorDisappearsWhenContentArrives() {
        var assistant = new AssistantMessageComponent();
        // First render: no content
        var lines1 = assistant.render(80);
        assertTrue(String.join("\n", lines1.stream().map(this::stripAnsi).toList()).contains("..."));

        // Second render: text arrives
        assistant.appendText("Hello");
        var lines2 = assistant.render(80);
        assertFalse(String.join("\n", lines2.stream().map(this::stripAnsi).toList()).contains("..."));
    }

    @Test
    void tuiRendersWithoutCrash() {
        var terminal = new TestTerminal(80, 24);
        var tui = new Tui(terminal);

        var root = new Container();
        root.addChild(new Text("Hello World"));
        root.addChild(new EditorContainer());
        var footer = new FooterComponent();
        footer.setModel("zai", "glm-5", 200000, true);
        footer.setCwd("/tmp");
        root.addChild(footer);

        tui.setRoot(root);
        tui.start();
        tui.render();
        tui.stop();

        String output = terminal.getFullOutput();
        assertTrue(output.contains("Hello World"), "TUI should render content");
        assertTrue(output.contains("glm-5"), "TUI should render footer");
    }

    @Test
    void userMessageHasBackgroundColor() {
        var msg = new UserMessageComponent("Test message");
        var lines = msg.render(80);
        String raw = String.join("", lines);
        assertTrue(raw.contains("\033[48;2;52;53;65m"), "Should have #343541 background");
        assertTrue(raw.contains("Test message"), "Should contain text");
    }

    private String stripAnsi(String s) {
        return s.replaceAll("\033\\[[;\\d]*[a-zA-Z]", "")
                .replaceAll("\033\\[\\d+;\\d+;\\d+;\\d+;\\d+m", "");
    }
}
