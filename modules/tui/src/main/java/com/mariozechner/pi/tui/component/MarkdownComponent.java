package com.mariozechner.pi.tui.component;

import com.mariozechner.pi.tui.Component;
import com.mariozechner.pi.tui.ansi.AnsiUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders Markdown text as ANSI-styled terminal output.
 * <p>
 * Supports: headings (# ## ###), code blocks (```), inline code (`),
 * bold (**), italic (*), unordered lists (- *), ordered lists (1.),
 * links ([text](url)), and horizontal rules (---).
 * <p>
 * Uses a hand-written line-by-line parser — no external Markdown library needed.
 */
public class MarkdownComponent implements Component {

    private String content;
    private MarkdownTheme theme;

    // Cache
    private String cachedContent;
    private int cachedWidth = -1;
    private List<String> cachedLines;

    public MarkdownComponent() {
        this("", MarkdownTheme.defaultTheme());
    }

    public MarkdownComponent(String content) {
        this(content, MarkdownTheme.defaultTheme());
    }

    public MarkdownComponent(String content, MarkdownTheme theme) {
        this.content = content != null ? content : "";
        this.theme = theme;
    }

    public void setContent(String content) {
        this.content = content != null ? content : "";
        invalidate();
    }

    public String getContent() {
        return content;
    }

    public void setTheme(MarkdownTheme theme) {
        this.theme = theme;
        invalidate();
    }

    @Override
    public void invalidate() {
        cachedContent = null;
        cachedWidth = -1;
        cachedLines = null;
    }

    @Override
    public List<String> render(int width) {
        if (cachedLines != null && content.equals(cachedContent) && width == cachedWidth) {
            return cachedLines;
        }

        if (content.isEmpty()) {
            cachedContent = content;
            cachedWidth = width;
            cachedLines = Collections.emptyList();
            return cachedLines;
        }

        List<String> result = renderMarkdown(content, width);
        cachedContent = content;
        cachedWidth = width;
        cachedLines = result;
        return result;
    }

    // -------------------------------------------------------------------
    // Markdown rendering engine
    // -------------------------------------------------------------------

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,3})\\s+(.+)$");
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^(\\s*)[-*]\\s+(.+)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^(\\s*)(\\d+)\\.\\s+(.+)$");
    private static final Pattern HR_PATTERN = Pattern.compile("^\\s*([-*_])\\1{2,}\\s*$");
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("^```(.*)$");

    private List<String> renderMarkdown(String markdown, int width) {
        String[] rawLines = markdown.split("\n", -1);
        List<String> output = new ArrayList<>();
        int i = 0;

        while (i < rawLines.length) {
            String line = rawLines[i];

            // --- Code block ---
            Matcher codeFence = CODE_FENCE_PATTERN.matcher(line);
            if (codeFence.matches()) {
                String lang = codeFence.group(1).trim();
                i++;
                List<String> codeLines = new ArrayList<>();
                while (i < rawLines.length && !rawLines[i].startsWith("```")) {
                    codeLines.add(rawLines[i]);
                    i++;
                }
                if (i < rawLines.length) i++; // skip closing ```

                renderCodeBlock(codeLines, lang, width, output);
                continue;
            }

            // --- Heading ---
            Matcher heading = HEADING_PATTERN.matcher(line);
            if (heading.matches()) {
                int level = heading.group(1).length();
                String text = heading.group(2);
                renderHeading(level, text, width, output);
                i++;
                continue;
            }

            // --- Horizontal rule ---
            if (HR_PATTERN.matcher(line).matches()) {
                renderHorizontalRule(width, output);
                i++;
                continue;
            }

            // --- Unordered list ---
            Matcher ul = UNORDERED_LIST_PATTERN.matcher(line);
            if (ul.matches()) {
                i = renderList(rawLines, i, width, output);
                continue;
            }

            // --- Ordered list ---
            Matcher ol = ORDERED_LIST_PATTERN.matcher(line);
            if (ol.matches()) {
                i = renderList(rawLines, i, width, output);
                continue;
            }

            // --- Empty line ---
            if (line.isEmpty()) {
                output.add("");
                i++;
                continue;
            }

            // --- Paragraph (collect consecutive non-special lines) ---
            StringBuilder para = new StringBuilder();
            while (i < rawLines.length) {
                String pLine = rawLines[i];
                if (pLine.isEmpty()
                        || HEADING_PATTERN.matcher(pLine).matches()
                        || HR_PATTERN.matcher(pLine).matches()
                        || CODE_FENCE_PATTERN.matcher(pLine).matches()
                        || UNORDERED_LIST_PATTERN.matcher(pLine).matches()
                        || ORDERED_LIST_PATTERN.matcher(pLine).matches()) {
                    break;
                }
                if (para.length() > 0) para.append(' ');
                para.append(pLine);
                i++;
            }
            renderParagraph(para.toString(), width, output);
        }

        return output;
    }

    // -------------------------------------------------------------------
    // Block renderers
    // -------------------------------------------------------------------

    private void renderHeading(int level, String rawText, int width, List<String> out) {
        String styled = renderInline(rawText);
        String formatted = switch (level) {
            case 1 -> theme.heading1(styled);
            case 2 -> theme.heading2(styled);
            default -> theme.heading3(styled);
        };
        List<String> wrapped = AnsiUtils.wrapTextWithAnsi(formatted, width);
        out.addAll(wrapped);
    }

    private void renderCodeBlock(List<String> codeLines, String lang, int width, List<String> out) {
        // Opening fence
        String fenceLabel = lang.isEmpty() ? "```" : "``` " + lang;
        out.add(theme.codeBlockBorder(fenceLabel));

        // Code lines with background
        int codeWidth = Math.max(1, width - 2); // 2-char indent
        for (String codeLine : codeLines) {
            String indented = "  " + codeLine;
            int visLen = AnsiUtils.visibleWidth(indented);
            int pad = Math.max(0, width - visLen);
            out.add(theme.codeBlock(indented + " ".repeat(pad)));
        }

        // Closing fence
        out.add(theme.codeBlockBorder("```"));
    }

    private void renderHorizontalRule(int width, List<String> out) {
        out.add(theme.hr("─".repeat(width)));
    }

    /**
     * Renders a contiguous run of list items (unordered or ordered, possibly mixed).
     * Returns the index after the last consumed line.
     */
    private int renderList(String[] rawLines, int startIdx, int width, List<String> out) {
        int i = startIdx;
        while (i < rawLines.length) {
            String line = rawLines[i];

            Matcher ul = UNORDERED_LIST_PATTERN.matcher(line);
            if (ul.matches()) {
                String indent = ul.group(1);
                String text = ul.group(2);
                int depth = indent.length() / 2;
                String prefix = "  ".repeat(depth);
                String bullet = theme.listBullet("•") + " ";
                renderListItem(prefix + bullet, text, width, prefix.length() + 2, out);
                i++;
                continue;
            }

            Matcher ol = ORDERED_LIST_PATTERN.matcher(line);
            if (ol.matches()) {
                String indent = ol.group(1);
                String number = ol.group(2);
                String text = ol.group(3);
                int depth = indent.length() / 2;
                String prefix = "  ".repeat(depth);
                String marker = theme.listBullet(number + ".") + " ";
                renderListItem(prefix + marker, text, width, prefix.length() + number.length() + 2, out);
                i++;
                continue;
            }

            break; // Not a list item → stop
        }
        return i;
    }

    private void renderListItem(String prefixWithBullet, String rawText, int width,
                                int continuationIndent, List<String> out) {
        String styled = renderInline(rawText);
        int contentWidth = Math.max(1, width - continuationIndent);
        List<String> wrapped = AnsiUtils.wrapTextWithAnsi(styled, contentWidth);
        for (int j = 0; j < wrapped.size(); j++) {
            if (j == 0) {
                out.add(prefixWithBullet + wrapped.get(j));
            } else {
                out.add(" ".repeat(continuationIndent) + wrapped.get(j));
            }
        }
    }

    private void renderParagraph(String rawText, int width, List<String> out) {
        String styled = renderInline(rawText);
        List<String> wrapped = AnsiUtils.wrapTextWithAnsi(styled, width);
        out.addAll(wrapped);
    }

    // -------------------------------------------------------------------
    // Inline rendering
    // -------------------------------------------------------------------

    // Patterns for inline elements — ordered by precedence
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");

    /**
     * Applies inline Markdown formatting (bold, italic, code, links) to text.
     * Processes in a specific order to handle nesting correctly.
     */
    String renderInline(String text) {
        // Process in order: code (which should not have further formatting),
        // then links, bold, italic

        // We use a placeholder approach: replace processed segments with placeholders,
        // then process remaining patterns, then restore.
        List<String> placeholders = new ArrayList<>();

        // 1. Inline code — no further formatting inside
        text = replaceAll(text, INLINE_CODE, m -> {
            String placeholder = "\0PH" + placeholders.size() + "\0";
            placeholders.add(theme.code(m.group(1)));
            return placeholder;
        }, placeholders);

        // 2. Links
        text = replaceAll(text, LINK, m -> {
            String placeholder = "\0PH" + placeholders.size() + "\0";
            placeholders.add(theme.link(m.group(1)) + " " + theme.linkUrl("(" + m.group(2) + ")"));
            return placeholder;
        }, placeholders);

        // 3. Bold (before italic to avoid ** matching as two *)
        text = replaceAll(text, BOLD, m -> {
            String inner = m.group(1);
            return theme.bold(inner);
        }, placeholders);

        // 4. Italic
        text = replaceAll(text, ITALIC, m -> {
            String inner = m.group(1);
            return theme.italic(inner);
        }, placeholders);

        // Restore placeholders
        for (int i = 0; i < placeholders.size(); i++) {
            text = text.replace("\0PH" + i + "\0", placeholders.get(i));
        }

        return text;
    }

    /**
     * Replaces all occurrences of a pattern, using a function to produce replacements.
     */
    private static String replaceAll(String text, Pattern pattern,
                                     java.util.function.Function<Matcher, String> replacer,
                                     List<String> placeholders) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
