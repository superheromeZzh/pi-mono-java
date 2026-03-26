package com.mariozechner.pi.tui.component;

import java.util.function.UnaryOperator;

/**
 * Theme for Markdown rendering — provides ANSI styling functions for each element type.
 */
public class MarkdownTheme {

    private final UnaryOperator<String> heading1;
    private final UnaryOperator<String> heading2;
    private final UnaryOperator<String> heading3;
    private final UnaryOperator<String> bold;
    private final UnaryOperator<String> italic;
    private final UnaryOperator<String> code;
    private final UnaryOperator<String> codeBlock;
    private final UnaryOperator<String> codeBlockBorder;
    private final UnaryOperator<String> link;
    private final UnaryOperator<String> linkUrl;
    private final UnaryOperator<String> listBullet;
    private final UnaryOperator<String> hr;

    private MarkdownTheme(Builder b) {
        this.heading1 = b.heading1;
        this.heading2 = b.heading2;
        this.heading3 = b.heading3;
        this.bold = b.bold;
        this.italic = b.italic;
        this.code = b.code;
        this.codeBlock = b.codeBlock;
        this.codeBlockBorder = b.codeBlockBorder;
        this.link = b.link;
        this.linkUrl = b.linkUrl;
        this.listBullet = b.listBullet;
        this.hr = b.hr;
    }

    public String heading1(String text) { return heading1.apply(text); }
    public String heading2(String text) { return heading2.apply(text); }
    public String heading3(String text) { return heading3.apply(text); }
    public String bold(String text) { return bold.apply(text); }
    public String italic(String text) { return italic.apply(text); }
    public String code(String text) { return code.apply(text); }
    public String codeBlock(String text) { return codeBlock.apply(text); }
    public String codeBlockBorder(String text) { return codeBlockBorder.apply(text); }
    public String link(String text) { return link.apply(text); }
    public String linkUrl(String text) { return linkUrl.apply(text); }
    public String listBullet(String text) { return listBullet.apply(text); }
    public String hr(String text) { return hr.apply(text); }

    /**
     * Returns the default theme with standard terminal colors.
     */
    public static MarkdownTheme defaultTheme() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private static final String RESET = "\033[0m";
        private static final String BOLD = "\033[1m";
        private static final String ITALIC = "\033[3m";
        private static final String UNDERLINE = "\033[4m";
        private static final String DIM = "\033[2m";
        private static final String CYAN = "\033[36m";
        private static final String YELLOW = "\033[33m";
        private static final String GREEN = "\033[32m";
        private static final String MAGENTA = "\033[35m";
        private static final String BG_GRAY = "\033[48;5;236m";

        // Default styling functions
        private UnaryOperator<String> heading1 = s -> BOLD + CYAN + UNDERLINE + s + RESET;
        private UnaryOperator<String> heading2 = s -> BOLD + CYAN + s + RESET;
        private UnaryOperator<String> heading3 = s -> BOLD + GREEN + s + RESET;
        private UnaryOperator<String> bold = s -> BOLD + s + RESET;
        private UnaryOperator<String> italic = s -> ITALIC + s + RESET;
        private UnaryOperator<String> code = s -> YELLOW + s + RESET;
        private UnaryOperator<String> codeBlock = s -> BG_GRAY + s + RESET;
        private UnaryOperator<String> codeBlockBorder = s -> DIM + s + RESET;
        private UnaryOperator<String> link = s -> CYAN + UNDERLINE + s + RESET;
        private UnaryOperator<String> linkUrl = s -> DIM + s + RESET;
        private UnaryOperator<String> listBullet = s -> CYAN + s + RESET;
        private UnaryOperator<String> hr = s -> DIM + s + RESET;

        public Builder heading1(UnaryOperator<String> fn) { this.heading1 = fn; return this; }
        public Builder heading2(UnaryOperator<String> fn) { this.heading2 = fn; return this; }
        public Builder heading3(UnaryOperator<String> fn) { this.heading3 = fn; return this; }
        public Builder bold(UnaryOperator<String> fn) { this.bold = fn; return this; }
        public Builder italic(UnaryOperator<String> fn) { this.italic = fn; return this; }
        public Builder code(UnaryOperator<String> fn) { this.code = fn; return this; }
        public Builder codeBlock(UnaryOperator<String> fn) { this.codeBlock = fn; return this; }
        public Builder codeBlockBorder(UnaryOperator<String> fn) { this.codeBlockBorder = fn; return this; }
        public Builder link(UnaryOperator<String> fn) { this.link = fn; return this; }
        public Builder linkUrl(UnaryOperator<String> fn) { this.linkUrl = fn; return this; }
        public Builder listBullet(UnaryOperator<String> fn) { this.listBullet = fn; return this; }
        public Builder hr(UnaryOperator<String> fn) { this.hr = fn; return this; }

        public MarkdownTheme build() {
            return new MarkdownTheme(this);
        }
    }
}
