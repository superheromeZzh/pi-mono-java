package com.huawei.hicampus.mate.matecampusclaw.tui.component;

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
    private final UnaryOperator<String> strikethrough;
    private final UnaryOperator<String> quoteBorder;

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
        this.strikethrough = b.strikethrough;
        this.quoteBorder = b.quoteBorder;
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
    public String strikethrough(String text) { return strikethrough.apply(text); }
    public String quoteBorder(String text) { return quoteBorder.apply(text); }

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
        // RGB colors matching campusclaw dark theme
        private static final String HEADING_COLOR = "\033[38;2;240;198;116m"; // mdHeading #f0c674
        private static final String ACCENT = "\033[38;2;138;190;183m";       // accent #8abeb7
        private static final String LINK_COLOR = "\033[38;2;129;162;190m";   // mdLink #81a2be
        private static final String LINK_URL_COLOR = "\033[38;2;102;102;102m"; // mdLinkUrl=dimGray #666666
        private static final String CODE_COLOR = "\033[38;2;138;190;183m";   // mdCode=accent #8abeb7
        private static final String CODE_BLOCK_COLOR = "\033[38;2;181;189;104m"; // mdCodeBlock #b5bd68
        private static final String CODE_BORDER_COLOR = "\033[38;2;128;128;128m"; // mdCodeBlockBorder=gray #808080
        private static final String GRAY = "\033[38;2;128;128;128m";         // gray #808080
        private static final String BG_GRAY = "\033[48;5;236m";

        // Default styling functions — colors from campusclaw dark theme
        private UnaryOperator<String> heading1 = s -> BOLD + HEADING_COLOR + UNDERLINE + s + RESET;
        private UnaryOperator<String> heading2 = s -> BOLD + HEADING_COLOR + s + RESET;
        private UnaryOperator<String> heading3 = s -> BOLD + HEADING_COLOR + s + RESET;
        private UnaryOperator<String> bold = s -> BOLD + s + RESET;
        private UnaryOperator<String> italic = s -> ITALIC + s + RESET;
        private UnaryOperator<String> code = s -> CODE_COLOR + s + RESET;
        private UnaryOperator<String> codeBlock = s -> BG_GRAY + s + RESET;
        private UnaryOperator<String> codeBlockBorder = s -> CODE_BORDER_COLOR + s + RESET;
        private UnaryOperator<String> link = s -> LINK_COLOR + UNDERLINE + s + RESET;
        private UnaryOperator<String> linkUrl = s -> LINK_URL_COLOR + s + RESET;
        private UnaryOperator<String> listBullet = s -> ACCENT + s + RESET;
        private static final String STRIKETHROUGH_STYLE = "\033[9m";
        private UnaryOperator<String> hr = s -> GRAY + s + RESET;
        private UnaryOperator<String> strikethrough = s -> STRIKETHROUGH_STYLE + s + RESET;
        private UnaryOperator<String> quoteBorder = s -> GRAY + s + RESET;

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
        public Builder strikethrough(UnaryOperator<String> fn) { this.strikethrough = fn; return this; }
        public Builder quoteBorder(UnaryOperator<String> fn) { this.quoteBorder = fn; return this; }

        public MarkdownTheme build() {
            return new MarkdownTheme(this);
        }
    }
}
