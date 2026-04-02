package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui;

import java.util.*;
import java.util.function.Consumer;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;
import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;
import com.huawei.hicampus.mate.matecampusclaw.tui.component.SelectList;

/**
 * Tree selector overlay for /tree command.
 * Shows session messages as a navigable list and lets user navigate branches.
 * Supports switching to a different point in the conversation.
 */
public class TreeSelectorOverlay implements Component, Focusable {

    private static final String ANSI_DIM = "\033[38;2;102;102;102m";
    private static final String ANSI_ACCENT = "\033[38;2;138;190;183m";
    private static final String ANSI_USER = "\033[38;2;95;135;255m";
    private static final String ANSI_ASSISTANT = "\033[38;2;178;148;187m";
    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BOLD = "\033[1m";

    public record TreeItem(int index, String role, String preview) {}

    private final SelectList<TreeItem> selectList;
    private Consumer<TreeItem> onSelect;
    private Runnable onCancel;
    private boolean focused;
    private final boolean empty;

    public TreeSelectorOverlay(List<com.huawei.hicampus.mate.matecampusclaw.ai.types.Message> messages) {
        var items = buildItems(messages);
        this.empty = items.isEmpty();

        this.selectList = new SelectList<>(items, this::renderItem, 20);
        // Default to last item
        if (!items.isEmpty()) {
            selectList.setSelectedIndex(items.size() - 1);
        }
        selectList.setOnSelect(item -> {
            if (onSelect != null) onSelect.accept(item);
        });
        selectList.setOnCancel(() -> {
            if (onCancel != null) onCancel.run();
        });
    }

    public void setOnSelect(Consumer<TreeItem> onSelect) {
        this.onSelect = onSelect;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    public boolean isEmpty() {
        return empty;
    }

    private List<TreeItem> buildItems(List<com.huawei.hicampus.mate.matecampusclaw.ai.types.Message> messages) {
        var items = new ArrayList<TreeItem>();
        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);
            String role;
            String preview;
            if (msg instanceof UserMessage um) {
                role = "user";
                preview = extractPreview(um.content());
            } else if (msg instanceof AssistantMessage am) {
                role = "assistant";
                preview = extractPreview(am.content());
            } else {
                role = "tool";
                preview = "(tool result)";
            }
            items.add(new TreeItem(i, role, preview));
        }
        return items;
    }

    private String extractPreview(List<ContentBlock> content) {
        for (var block : content) {
            if (block instanceof TextContent tc) {
                String text = tc.text().strip().replaceAll("\\s+", " ");
                return text.length() > 60 ? text.substring(0, 57) + "..." : text;
            }
        }
        return "";
    }

    private String renderItem(TreeItem item) {
        String roleColor = switch (item.role) {
            case "user" -> ANSI_USER;
            case "assistant" -> ANSI_ASSISTANT;
            default -> ANSI_DIM;
        };
        String roleLabel = switch (item.role) {
            case "user" -> "U";
            case "assistant" -> "A";
            default -> "T";
        };
        return String.format("%s[%s]%s %s#%d%s %s",
                roleColor, roleLabel, ANSI_RESET,
                ANSI_DIM, item.index + 1, ANSI_RESET,
                item.preview);
    }

    @Override
    public void handleInput(String data) {
        if ("\033".equals(data) || "\003".equals(data)) {
            if (onCancel != null) onCancel.run();
            return;
        }
        selectList.handleInput(data);
    }

    @Override
    public void invalidate() {
        selectList.invalidate();
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        lines.add("");
        lines.add(" " + ANSI_BOLD + ANSI_ACCENT + "Session Tree" + ANSI_RESET
                + ANSI_DIM + "  (↑↓ navigate, Enter select, Esc cancel)" + ANSI_RESET);
        lines.add("");

        if (empty) {
            lines.add(" " + ANSI_DIM + "No messages in session." + ANSI_RESET);
        } else {
            var listLines = selectList.render(Math.max(1, width - 2));
            for (String line : listLines) {
                lines.add(" " + line);
            }
        }
        lines.add("");
        return lines;
    }

    @Override
    public boolean isFocused() { return focused; }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }
}
