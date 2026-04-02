package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.function.UnaryOperator;

/**
 * Theme for {@link SelectList} — configurable styling functions for each visual element.
 * <p>
 * Use {@link #builder()} for fluent configuration, or {@link #defaultTheme()} / {@link #plainTheme()}
 * for preset themes.
 */
public class SelectListTheme {

    private final UnaryOperator<String> selectedPrefix;
    private final UnaryOperator<String> selectedText;
    private final UnaryOperator<String> description;
    private final UnaryOperator<String> scrollInfo;
    private final UnaryOperator<String> noMatch;

    private SelectListTheme(UnaryOperator<String> selectedPrefix,
                            UnaryOperator<String> selectedText,
                            UnaryOperator<String> description,
                            UnaryOperator<String> scrollInfo,
                            UnaryOperator<String> noMatch) {
        this.selectedPrefix = selectedPrefix;
        this.selectedText = selectedText;
        this.description = description;
        this.scrollInfo = scrollInfo;
        this.noMatch = noMatch;
    }

    // --- Styling accessors ---

    /** Styles the arrow prefix ("→") for the selected item. */
    public String selectedPrefix(String text) {
        return selectedPrefix.apply(text);
    }

    /** Styles the entire line of the selected item. */
    public String selectedText(String text) {
        return selectedText.apply(text);
    }

    /** Styles description text. */
    public String description(String text) {
        return description.apply(text);
    }

    /** Styles the scroll indicator (e.g. "(3/10)"). */
    public String scrollInfo(String text) {
        return scrollInfo.apply(text);
    }

    /** Styles the "no matching items" message. */
    public String noMatch(String text) {
        return noMatch.apply(text);
    }

    // --- Presets ---

    /** Plain theme — identity functions, no ANSI styling. Useful for testing. */
    public static SelectListTheme plainTheme() {
        return builder().build();
    }

    /** Default theme with ANSI colors: blue prefix, bold selection, dim descriptions. */
    public static SelectListTheme defaultTheme() {
        return builder()
                .selectedPrefix(text -> "\033[34m" + text + "\033[0m")   // blue
                .selectedText(text -> "\033[1m" + text + "\033[0m")      // bold
                .description(text -> "\033[2m" + text + "\033[0m")       // dim
                .scrollInfo(text -> "\033[2m" + text + "\033[0m")        // dim
                .noMatch(text -> "\033[2m" + text + "\033[0m")           // dim
                .build();
    }

    // --- Builder ---

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private UnaryOperator<String> selectedPrefix = UnaryOperator.identity();
        private UnaryOperator<String> selectedText = UnaryOperator.identity();
        private UnaryOperator<String> description = UnaryOperator.identity();
        private UnaryOperator<String> scrollInfo = UnaryOperator.identity();
        private UnaryOperator<String> noMatch = UnaryOperator.identity();

        public Builder selectedPrefix(UnaryOperator<String> fn) {
            this.selectedPrefix = fn;
            return this;
        }

        public Builder selectedText(UnaryOperator<String> fn) {
            this.selectedText = fn;
            return this;
        }

        public Builder description(UnaryOperator<String> fn) {
            this.description = fn;
            return this;
        }

        public Builder scrollInfo(UnaryOperator<String> fn) {
            this.scrollInfo = fn;
            return this;
        }

        public Builder noMatch(UnaryOperator<String> fn) {
            this.noMatch = fn;
            return this;
        }

        public SelectListTheme build() {
            return new SelectListTheme(selectedPrefix, selectedText, description, scrollInfo, noMatch);
        }
    }
}
