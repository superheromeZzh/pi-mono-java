package com.mariozechner.pi.codingagent.mode.tui;

import com.mariozechner.pi.tui.Component;
import com.mariozechner.pi.tui.Focusable;
import com.mariozechner.pi.tui.ansi.AnsiUtils;
import com.mariozechner.pi.tui.component.Editor;

import java.util.*;
import java.util.function.Consumer;

/**
 * Editor area with colored separator lines above and below.
 * Uses multi-line Editor with Enter-to-submit mode, matching pi-mono TS.
 * <p>
 * Includes inline slash command autocomplete: when the editor text starts with
 * {@code /}, matching commands are shown below the editor. Tab cycles through
 * suggestions, Enter accepts the selected one.
 */
public class EditorContainer implements Component, Focusable {

    public static final String CYAN = "\033[38;2;95;135;255m";
    public static final String YELLOW = "\033[38;2;181;189;104m";
    private static final String ANSI_RESET = "\033[0m";
    private static final int MAX_SUGGESTIONS = 8;
    private static final String KEY_TAB = "\t";
    private static final String KEY_SHIFT_TAB = "\033[Z";

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
            // If showing suggestions and user hasn't typed the full command name,
            // accept the suggestion. If it matches exactly, submit instead.
            if (showingSuggestions && suggestionIndex >= 0 && suggestionIndex < filteredSuggestions.size()) {
                String selectedName = "/" + filteredSuggestions.get(suggestionIndex).name();
                String currentText = editor.getText();
                if (currentText != null && !currentText.equals(selectedName)) {
                    acceptSuggestion();
                    return;
                }
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

    /**
     * Sets the available slash commands for autocomplete.
     */
    public void setCommands(List<CommandSuggestion> commands) {
        this.allCommands = commands != null ? List.copyOf(commands) : List.of();
    }

    @Override
    public void handleInput(String data) {
        // Tab — cycle autocomplete suggestions
        if (KEY_TAB.equals(data) && showingSuggestions) {
            cycleNext();
            return;
        }
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

        // Slash command suggestions
        if (showingSuggestions && !filteredSuggestions.isEmpty()) {
            int visibleCount = Math.min(MAX_SUGGESTIONS, filteredSuggestions.size());
            for (int i = 0; i < visibleCount; i++) {
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
            if (filteredSuggestions.size() > visibleCount) {
                lines.add("\033[2m  (" + filteredSuggestions.size() + " total matches)\033[0m");
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
