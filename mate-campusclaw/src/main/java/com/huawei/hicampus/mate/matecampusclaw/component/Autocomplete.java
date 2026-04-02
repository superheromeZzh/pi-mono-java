package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

/**
 * File path autocomplete component — provides tab-completion for file paths using fuzzy matching.
 * <p>
 * Wraps an {@link Input} component and adds tab-completion behavior. When the user presses Tab,
 * the current input value is treated as a partial file path and matching entries from the
 * filesystem are displayed as suggestions. Subsequent Tab presses cycle through suggestions.
 * <p>
 * Keyboard handling:
 * <ul>
 *   <li>Tab — trigger completion / cycle to next suggestion</li>
 *   <li>Shift+Tab — cycle to previous suggestion</li>
 *   <li>Enter — accept the current suggestion (or submit if no suggestions)</li>
 *   <li>Escape — dismiss suggestions or cancel</li>
 *   <li>All other keys — delegated to the embedded {@link Input}</li>
 * </ul>
 */
public class Autocomplete implements Component, Focusable {

    private static final String KEY_TAB = "\t";
    private static final String KEY_SHIFT_TAB = "\033[Z";
    private static final String KEY_ENTER = "\r";
    private static final String KEY_NEWLINE = "\n";
    private static final String KEY_ESCAPE = "\033";
    private static final String KEY_CTRL_C = "\003";

    private static final int MAX_SUGGESTIONS = 8;

    private final Input input;
    private List<String> suggestions = Collections.emptyList();
    private int suggestionIndex = -1;
    private String originalInput = "";
    private boolean showingSuggestions;

    // Callbacks
    private Consumer<String> onSubmit;
    private Runnable onEscape;

    // Cache
    private int cachedWidth = -1;
    private List<String> cachedLines;
    private String cachedValue;
    private int cachedSuggestionIndex = -1;

    public Autocomplete() {
        this(null);
    }

    public Autocomplete(String placeholder) {
        this.input = new Input(placeholder);
    }

    // -------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------

    public String getValue() {
        return input.getValue();
    }

    public void setValue(String value) {
        input.setValue(value);
        dismissSuggestions();
    }

    public Input getInput() {
        return input;
    }

    public void setOnSubmit(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
    }

    public void setOnEscape(Runnable onEscape) {
        this.onEscape = onEscape;
    }

    public List<String> getSuggestions() {
        return Collections.unmodifiableList(suggestions);
    }

    public boolean isShowingSuggestions() {
        return showingSuggestions;
    }

    // -------------------------------------------------------------------
    // Component interface
    // -------------------------------------------------------------------

    @Override
    public void invalidate() {
        cachedWidth = -1;
        cachedLines = null;
        cachedValue = null;
        cachedSuggestionIndex = -1;
        input.invalidate();
    }

    @Override
    public List<String> render(int width) {
        String currentValue = input.getValue();

        // Cache hit
        if (cachedLines != null
                && cachedWidth == width
                && currentValue.equals(cachedValue)
                && cachedSuggestionIndex == suggestionIndex) {
            return cachedLines;
        }

        List<String> result = new ArrayList<>();

        // Render the input line
        result.addAll(input.render(width));

        // Render suggestions below the input
        if (showingSuggestions && !suggestions.isEmpty()) {
            int visibleCount = Math.min(MAX_SUGGESTIONS, suggestions.size());
            for (int i = 0; i < visibleCount; i++) {
                String suggestion = suggestions.get(i);
                boolean isSelected = (i == suggestionIndex);

                String prefix = isSelected ? "→ " : "  ";
                int availableWidth = Math.max(1, width - 2);
                String display = suggestion;

                int visWidth = AnsiUtils.visibleWidth(display);
                if (visWidth > availableWidth) {
                    display = AnsiUtils.sliceByColumn(display, 0, availableWidth - 1) + "\u2026";
                }

                String line;
                if (isSelected) {
                    line = "\033[34m" + prefix + "\033[0m\033[1m" + display + "\033[0m";
                } else {
                    line = "\033[2m" + prefix + display + "\033[0m";
                }
                result.add(line);
            }

            if (suggestions.size() > visibleCount) {
                result.add("\033[2m  (" + suggestions.size() + " total matches)\033[0m");
            }
        }

        cachedWidth = width;
        cachedValue = currentValue;
        cachedSuggestionIndex = suggestionIndex;
        cachedLines = result;
        return result;
    }

    @Override
    public void handleInput(String data) {
        // Tab — trigger or cycle completions
        if (KEY_TAB.equals(data)) {
            if (!showingSuggestions) {
                triggerCompletion();
            } else {
                cycleNext();
            }
            invalidate();
            return;
        }

        // Shift+Tab — cycle backward
        if (KEY_SHIFT_TAB.equals(data)) {
            if (showingSuggestions) {
                cyclePrev();
                invalidate();
            }
            return;
        }

        // Enter — accept suggestion or submit
        if (KEY_ENTER.equals(data) || KEY_NEWLINE.equals(data)) {
            if (showingSuggestions && suggestionIndex >= 0 && suggestionIndex < suggestions.size()) {
                acceptSuggestion();
            } else if (onSubmit != null) {
                onSubmit.accept(input.getValue());
            }
            invalidate();
            return;
        }

        // Escape — dismiss suggestions or cancel
        if (KEY_ESCAPE.equals(data) || KEY_CTRL_C.equals(data)) {
            if (showingSuggestions) {
                dismissSuggestions();
                invalidate();
            } else if (onEscape != null) {
                onEscape.run();
            }
            return;
        }

        // All other keys — delegate to input and dismiss suggestions
        input.handleInput(data);
        if (showingSuggestions) {
            dismissSuggestions();
        }
        invalidate();
    }

    // -------------------------------------------------------------------
    // Focusable interface
    // -------------------------------------------------------------------

    @Override
    public boolean isFocused() {
        return input.isFocused();
    }

    @Override
    public void setFocused(boolean focused) {
        input.setFocused(focused);
    }

    // -------------------------------------------------------------------
    // Completion logic
    // -------------------------------------------------------------------

    private void triggerCompletion() {
        originalInput = input.getValue();
        suggestions = computeFileSuggestions(originalInput);

        if (suggestions.isEmpty()) {
            showingSuggestions = false;
            suggestionIndex = -1;
        } else if (suggestions.size() == 1) {
            // Single match — auto-accept
            input.setValue(suggestions.get(0));
            dismissSuggestions();
        } else {
            showingSuggestions = true;
            suggestionIndex = 0;
            input.setValue(suggestions.get(0));
        }
    }

    private void cycleNext() {
        if (suggestions.isEmpty()) return;
        suggestionIndex = (suggestionIndex + 1) % suggestions.size();
        input.setValue(suggestions.get(suggestionIndex));
    }

    private void cyclePrev() {
        if (suggestions.isEmpty()) return;
        suggestionIndex = suggestionIndex <= 0 ? suggestions.size() - 1 : suggestionIndex - 1;
        input.setValue(suggestions.get(suggestionIndex));
    }

    private void acceptSuggestion() {
        if (suggestionIndex >= 0 && suggestionIndex < suggestions.size()) {
            String accepted = suggestions.get(suggestionIndex);
            input.setValue(accepted);

            // If accepted is a directory, append separator and keep completing
            Path path = Paths.get(accepted);
            if (Files.isDirectory(path) && !accepted.endsWith("/")) {
                input.setValue(accepted + "/");
            }
        }
        dismissSuggestions();
    }

    private void dismissSuggestions() {
        showingSuggestions = false;
        suggestions = Collections.emptyList();
        suggestionIndex = -1;
    }

    /**
     * Computes file path suggestions for the given partial path.
     */
    static List<String> computeFileSuggestions(String partial) {
        if (partial == null || partial.isEmpty()) {
            partial = ".";
        }

        Path inputPath = Paths.get(partial);
        Path dir;
        String prefix;

        if (partial.endsWith("/") || partial.endsWith("\\")) {
            dir = inputPath;
            prefix = "";
        } else {
            dir = inputPath.getParent();
            if (dir == null) dir = Paths.get(".");
            prefix = inputPath.getFileName() != null ? inputPath.getFileName().toString() : "";
        }

        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }

        List<String> matches = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (prefix.isEmpty() || FuzzyMatcher.matches(prefix, name)) {
                    String fullPath;
                    if (dir.equals(Paths.get("."))) {
                        fullPath = name;
                    } else {
                        fullPath = dir.resolve(name).toString();
                    }
                    if (Files.isDirectory(entry)) {
                        fullPath += "/";
                    }
                    matches.add(fullPath);
                }
            }
        } catch (IOException e) {
            // Silently return empty on I/O errors
            return Collections.emptyList();
        }

        // Sort by fuzzy match score (best first), then alphabetically
        if (!prefix.isEmpty()) {
            String finalPrefix = prefix;
            matches.sort((a, b) -> {
                String nameA = Paths.get(a).getFileName().toString();
                String nameB = Paths.get(b).getFileName().toString();
                int scoreA = FuzzyMatcher.score(finalPrefix, nameA);
                int scoreB = FuzzyMatcher.score(finalPrefix, nameB);
                if (scoreA != scoreB) return Integer.compare(scoreB, scoreA);
                return nameA.compareToIgnoreCase(nameB);
            });
        } else {
            matches.sort(String.CASE_INSENSITIVE_ORDER);
        }

        return matches;
    }
}
