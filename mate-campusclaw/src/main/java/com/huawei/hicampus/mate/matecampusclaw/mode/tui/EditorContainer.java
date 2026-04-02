package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui;

import java.util.*;
import java.util.function.Consumer;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;
import com.huawei.hicampus.mate.matecampusclaw.tui.component.Editor;

/**
 * Editor area with colored separator lines above and below.
 * Uses multi-line Editor with Enter-to-submit mode, matching campusclaw TS.
 * <p>
 * Includes inline slash command autocomplete: when the editor text starts with
 * {@code /}, matching commands are shown below the editor. Tab cycles through
 * suggestions, Enter accepts the selected one.
 */
public class EditorContainer implements Component, Focusable {

    // Default border is "border" blue from theme
    public static final String BORDER_DEFAULT = "\033[38;2;95;135;255m";
    // Bash mode border: green #b5bd68
    public static final String BORDER_BASH = "\033[38;2;181;189;104m";
    // Thinking level border colors (from campusclaw dark theme)
    public static final String THINKING_OFF = "\033[38;2;80;80;80m";       // #505050
    public static final String THINKING_MINIMAL = "\033[38;2;110;110;110m"; // #6e6e6e
    public static final String THINKING_LOW = "\033[38;2;95;135;175m";     // #5f87af
    public static final String THINKING_MEDIUM = "\033[38;2;129;162;190m"; // #81a2be
    public static final String THINKING_HIGH = "\033[38;2;178;148;187m";   // #b294bb
    public static final String THINKING_XHIGH = "\033[38;2;209;131;232m";  // #d183e8
    // Legacy aliases
    public static final String CYAN = BORDER_DEFAULT;
    public static final String YELLOW = BORDER_BASH;
    private static final String ANSI_RESET = "\033[0m";
    private static final int MAX_SUGGESTIONS = 8;
    private static final String KEY_TAB = "\t";
    private static final String KEY_SHIFT_TAB = "\033[Z";
    private static final String KEY_UP = "\033[A";
    private static final String KEY_DOWN = "\033[B";

    private final Editor editor;
    private Consumer<String> onSubmit;
    private String borderColor = CYAN;

    // Slash command autocomplete
    private List<CommandSuggestion> allCommands = List.of();
    private List<CommandSuggestion> filteredSuggestions = List.of();
    private int suggestionIndex = -1;
    private boolean showingSuggestions;

    /**
     * A slash command suggestion with name and description.
     */
    public record CommandSuggestion(String name, String description) {}

    public EditorContainer() {
        this.editor = new Editor();
        this.editor.setSubmitOnEnter(true);
        this.editor.setPlaceholder("Type a message...");
        this.editor.setFocused(true);
    }

    public void setOnSubmit(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
        this.editor.setOnSubmit(value -> {
            // If showing suggestions, accept the suggestion and submit immediately
            if (showingSuggestions && suggestionIndex >= 0 && suggestionIndex < filteredSuggestions.size()) {
                String name = filteredSuggestions.get(suggestionIndex).name();
                String commandText = "/" + name;
                dismissSuggestions();
                editor.setText(commandText);
                if (this.onSubmit != null) {
                    this.onSubmit.accept(commandText);
                }
                return;
            }
            dismissSuggestions();
            if (this.onSubmit != null) {
                this.onSubmit.accept(value);
            }
        });
    }

    public void clear() {
        editor.setText("");
        dismissSuggestions();
    }

    /** Add a submitted prompt to command history for up/down navigation. */
    public void addToHistory(String text) {
        editor.addToHistory(text);
    }

    public Editor getEditor() {
        return editor;
    }

    /** Sets the border color ANSI code (e.g. CYAN for normal, YELLOW for bash mode). */
    public void setBorderColor(String color) {
        this.borderColor = color;
    }

    /** Sets the border color based on thinking level (matching campusclaw dynamic border). */
    public void setBorderForThinkingLevel(String level) {
        this.borderColor = switch (level != null ? level.toLowerCase() : "off") {
            case "off" -> THINKING_OFF;
            case "minimal" -> THINKING_MINIMAL;
            case "low" -> THINKING_LOW;
            case "medium" -> THINKING_MEDIUM;
            case "high" -> THINKING_HIGH;
            case "xhigh" -> THINKING_XHIGH;
            default -> THINKING_OFF;
        };
    }

    /**
     * Sets the available slash commands for autocomplete.
     */
    public void setCommands(List<CommandSuggestion> commands) {
        this.allCommands = commands != null ? List.copyOf(commands) : List.of();
    }

    /** Returns true if the slash command suggestion menu is currently visible. */
    public boolean isShowingSuggestions() {
        return showingSuggestions;
    }

    @Override
    public void handleInput(String data) {
        // Arrow Up/Down — navigate autocomplete suggestions
        if (KEY_UP.equals(data) && showingSuggestions) {
            cyclePrev();
            return;
        }
        if (KEY_DOWN.equals(data) && showingSuggestions) {
            cycleNext();
            return;
        }

        // Tab — accept selected suggestion (matching pi-mono behavior)
        if (KEY_TAB.equals(data) && showingSuggestions) {
            acceptSuggestion();
            return;
        }
        // Shift+Tab — cycle to previous suggestion
        if (KEY_SHIFT_TAB.equals(data) && showingSuggestions) {
            cyclePrev();
            return;
        }

        // Escape — dismiss suggestions
        if ("\033".equals(data) && showingSuggestions) {
            dismissSuggestions();
            return;
        }

        editor.handleInput(data);
        updateSuggestions();
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        // Top separator
        lines.add(borderColor + "─".repeat(width) + ANSI_RESET);
        // Editor line(s)
        lines.addAll(editor.render(width));

        // Slash command suggestions with scrolling window
        if (showingSuggestions && !filteredSuggestions.isEmpty()) {
            int total = filteredSuggestions.size();
            // Calculate visible window centered on selected item (matching pi-mono SelectList)
            int startIndex = Math.max(0,
                    Math.min(suggestionIndex - MAX_SUGGESTIONS / 2, total - MAX_SUGGESTIONS));
            int endIndex = Math.min(startIndex + MAX_SUGGESTIONS, total);

            for (int i = startIndex; i < endIndex; i++) {
                var suggestion = filteredSuggestions.get(i);
                boolean isSelected = (i == suggestionIndex);
                String prefix = isSelected ? "→ " : "  ";
                int availableWidth = Math.max(1, width - 2);

                String display = "/" + suggestion.name();
                if (suggestion.description() != null && !suggestion.description().isEmpty()) {
                    String desc = " — " + suggestion.description();
                    int nameWidth = AnsiUtils.visibleWidth(display);
                    if (nameWidth + desc.length() > availableWidth) {
                        int remaining = availableWidth - nameWidth - 4;
                        if (remaining > 10) {
                            desc = " — " + suggestion.description().substring(0, Math.min(remaining, suggestion.description().length()));
                            if (remaining < suggestion.description().length()) desc += "…";
                        } else {
                            desc = "";
                        }
                    }
                    display += desc;
                }

                if (AnsiUtils.visibleWidth(display) > availableWidth) {
                    display = AnsiUtils.sliceByColumn(display, 0, availableWidth - 1) + "…";
                }

                String line;
                if (isSelected) {
                    line = "\033[34m" + prefix + "\033[0m\033[1m" + display + "\033[0m";
                } else {
                    line = "\033[2m" + prefix + display + "\033[0m";
                }
                lines.add(line);
            }
            // Show scroll position indicator when not all items are visible
            if (startIndex > 0 || endIndex < total) {
                lines.add("\033[2m  (" + (suggestionIndex + 1) + "/" + total + ")\033[0m");
            }
        }

        // Bottom separator
        lines.add(borderColor + "─".repeat(width) + ANSI_RESET);
        return lines;
    }

    @Override
    public void invalidate() {
        editor.invalidate();
    }

    @Override
    public boolean isFocused() {
        return editor.isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        editor.setFocused(focused);
    }

    // --- Autocomplete logic ---

    private void updateSuggestions() {
        String text = editor.getText();
        if (text == null || !text.startsWith("/") || text.contains("\n") || text.contains(" ")) {
            dismissSuggestions();
            return;
        }

        String prefix = text.substring(1).toLowerCase();
        List<CommandSuggestion> matches = new ArrayList<>();
        for (var cmd : allCommands) {
            if (prefix.isEmpty() || cmd.name().toLowerCase().startsWith(prefix)) {
                matches.add(cmd);
            }
        }

        if (matches.isEmpty()) {
            dismissSuggestions();
        } else {
            filteredSuggestions = matches;
            showingSuggestions = true;
            suggestionIndex = 0;
        }
    }

    private void cycleNext() {
        if (filteredSuggestions.isEmpty()) return;
        suggestionIndex = (suggestionIndex + 1) % filteredSuggestions.size();
    }

    private void cyclePrev() {
        if (filteredSuggestions.isEmpty()) return;
        suggestionIndex = suggestionIndex <= 0 ? filteredSuggestions.size() - 1 : suggestionIndex - 1;
    }

    private void acceptSuggestion() {
        if (suggestionIndex >= 0 && suggestionIndex < filteredSuggestions.size()) {
            String name = filteredSuggestions.get(suggestionIndex).name();
            editor.setText("/" + name + " ");
        }
        dismissSuggestions();
    }

    private void dismissSuggestions() {
        showingSuggestions = false;
        filteredSuggestions = List.of();
        suggestionIndex = -1;
    }
}
