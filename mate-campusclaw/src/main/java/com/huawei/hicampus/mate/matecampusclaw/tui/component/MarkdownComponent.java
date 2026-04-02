package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.ansi.AnsiUtils;

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
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^(\\s*)[-*•]\\s+(.+)$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^(\\s*)(\\d+)\\.\\s+(.+)$");
    private static final Pattern HR_PATTERN = Pattern.compile("^\\s*([-*_])\\1{2,}\\s*$");
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("^```(.*)$");
    private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("^>\\s?(.*)$");
    private static final Pattern TABLE_SEPARATOR_PATTERN = Pattern.compile("^\\|?[\\s-:|]+\\|?$");

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

            // --- Blockquote ---
            Matcher bq = BLOCKQUOTE_PATTERN.matcher(line);
            if (bq.matches()) {
                i = renderBlockquote(rawLines, i, width, output);
                continue;
            }

            // --- Table (detect: line with |, followed by separator line) ---
            if (line.contains("|") && i + 1 < rawLines.length
                    && TABLE_SEPARATOR_PATTERN.matcher(rawLines[i + 1]).matches()) {
                i = renderTable(rawLines, i, width, output);
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
                        || ORDERED_LIST_PATTERN.matcher(pLine).matches()
                        || BLOCKQUOTE_PATTERN.matcher(pLine).matches()
                        || (pLine.contains("|") && i + 1 < rawLines.length
                            && TABLE_SEPARATOR_PATTERN.matcher(rawLines[i + 1]).matches())) {
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
        out.add(theme.hr("─".repeat(Math.min(width, 80))));
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
                String bullet = theme.listBullet("-") + " ";
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

    /**
     * Renders a contiguous run of blockquote lines (> ...).
     * Returns the index after the last consumed line.
     */
    private int renderBlockquote(String[] rawLines, int startIdx, int width, List<String> out) {
        // Collect blockquote content
        var quoteContent = new ArrayList<String>();
        int i = startIdx;
        while (i < rawLines.length) {
            Matcher bq = BLOCKQUOTE_PATTERN.matcher(rawLines[i]);
            if (bq.matches()) {
                quoteContent.add(bq.group(1));
                i++;
            } else {
                break;
            }
        }

        // Render content within the blockquote (recursive markdown)
        String innerMarkdown = String.join("\n", quoteContent);
        int quoteContentWidth = Math.max(1, width - 2); // "│ " = 2 chars
        List<String> innerLines = renderMarkdown(innerMarkdown, quoteContentWidth);

        // Remove trailing empty lines
        while (!innerLines.isEmpty() && innerLines.getLast().isEmpty()) {
            innerLines.removeLast();
        }

        // Prefix each line with "│ " border and apply italic styling
        for (String line : innerLines) {
            String styledLine = theme.italic(line);
            out.add(theme.quoteBorder("│ ") + styledLine);
        }

        return i;
    }

    /**
     * Renders a markdown table with box-drawing borders.
     * Returns the index after the last consumed line.
     */
    private int renderTable(String[] rawLines, int startIdx, int width, List<String> out) {
        int i = startIdx;

        // Parse header
        String headerLine = rawLines[i++];
        List<String> headers = parseTableRow(headerLine);
        int numCols = headers.size();
        if (numCols == 0) return i;

        // Skip separator line
        if (i < rawLines.length && TABLE_SEPARATOR_PATTERN.matcher(rawLines[i]).matches()) {
            i++;
        }

        // Parse data rows
        var rows = new ArrayList<List<String>>();
        while (i < rawLines.length) {
            String line = rawLines[i];
            if (!line.contains("|") || line.isBlank()) break;
            List<String> row = parseTableRow(line);
            // Pad or truncate to match header column count
            while (row.size() < numCols) row.add("");
            if (row.size() > numCols) row = row.subList(0, numCols);
            rows.add(row);
            i++;
        }

        // Calculate border overhead: "│ " + (n-1) * " │ " + " │" = 3n + 1
        int borderOverhead = 3 * numCols + 1;
        int availableForCells = width - borderOverhead;
        if (availableForCells < numCols) {
            // Too narrow — fall back to raw text
            for (int j = startIdx; j < i; j++) {
                out.addAll(AnsiUtils.wrapTextWithAnsi(rawLines[j], width));
            }
            return i;
        }

        // Calculate column widths
        int[] naturalWidths = new int[numCols];
        for (int c = 0; c < numCols; c++) {
            naturalWidths[c] = AnsiUtils.visibleWidth(renderInline(headers.get(c)));
        }
        for (var row : rows) {
            for (int c = 0; c < numCols; c++) {
                naturalWidths[c] = Math.max(naturalWidths[c],
                        AnsiUtils.visibleWidth(renderInline(row.get(c))));
            }
        }

        int totalNatural = 0;
        for (int w : naturalWidths) totalNatural += w;

        int[] colWidths;
        if (totalNatural + borderOverhead <= width) {
            colWidths = naturalWidths;
        } else {
            // Distribute available space proportionally
            colWidths = new int[numCols];
            for (int c = 0; c < numCols; c++) {
                colWidths[c] = Math.max(1, (int) ((double) naturalWidths[c] / totalNatural * availableForCells));
            }
            // Distribute leftover
            int allocated = 0;
            for (int w : colWidths) allocated += w;
            int leftover = availableForCells - allocated;
            for (int c = 0; leftover > 0 && c < numCols; c++) {
                colWidths[c]++;
                leftover--;
            }
        }

        // Render top border
        var topParts = new ArrayList<String>();
        for (int c = 0; c < numCols; c++) topParts.add("─".repeat(colWidths[c]));
        out.add("┌─" + String.join("─┬─", topParts) + "─┐");

        // Render header row (bold)
        renderTableRow(headers, colWidths, numCols, true, out);

        // Render separator
        var sepParts = new ArrayList<String>();
        for (int c = 0; c < numCols; c++) sepParts.add("─".repeat(colWidths[c]));
        String separator = "├─" + String.join("─┼─", sepParts) + "─┤";
        out.add(separator);

        // Render data rows
        for (int r = 0; r < rows.size(); r++) {
            renderTableRow(rows.get(r), colWidths, numCols, false, out);
            if (r < rows.size() - 1) {
                out.add(separator);
            }
        }

        // Render bottom border
        var bottomParts = new ArrayList<String>();
        for (int c = 0; c < numCols; c++) bottomParts.add("─".repeat(colWidths[c]));
        out.add("└─" + String.join("─┴─", bottomParts) + "─┘");

        return i;
    }

    private void renderTableRow(List<String> cells, int[] colWidths, int numCols,
                                boolean bold, List<String> out) {
        // Wrap each cell and find max lines
        var wrappedCells = new ArrayList<List<String>>();
        int maxLines = 1;
        for (int c = 0; c < numCols; c++) {
            String styled = renderInline(cells.get(c));
            List<String> wrapped = AnsiUtils.wrapTextWithAnsi(styled, colWidths[c]);
            if (wrapped.isEmpty()) wrapped = List.of("");
            wrappedCells.add(wrapped);
            maxLines = Math.max(maxLines, wrapped.size());
        }

        for (int lineIdx = 0; lineIdx < maxLines; lineIdx++) {
            var sb = new StringBuilder("│ ");
            for (int c = 0; c < numCols; c++) {
                if (c > 0) sb.append(" │ ");
                List<String> cellLines = wrappedCells.get(c);
                String text = lineIdx < cellLines.size() ? cellLines.get(lineIdx) : "";
                int pad = Math.max(0, colWidths[c] - AnsiUtils.visibleWidth(text));
                if (bold) {
                    sb.append(theme.bold(text + " ".repeat(pad)));
                } else {
                    sb.append(text).append(" ".repeat(pad));
                }
            }
            sb.append(" │");
            out.add(sb.toString());
        }
    }

    private static List<String> parseTableRow(String line) {
        // Remove leading/trailing |
        String trimmed = line.strip();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        String[] parts = trimmed.split("\\|");
        var result = new ArrayList<String>();
        for (String part : parts) {
            result.add(part.strip());
        }
        return result;
    }

    // -------------------------------------------------------------------
    // Inline rendering
    // -------------------------------------------------------------------

    // Patterns for inline elements — ordered by precedence
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern STRIKETHROUGH = Pattern.compile("~~(.+?)~~");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern BOLD_UNDERSCORE = Pattern.compile("(?<!\\w)__(.+?)__(?!\\w)");
    private static final Pattern ITALIC = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)");
    private static final Pattern ITALIC_UNDERSCORE = Pattern.compile("(?<!\\w)_(?!_)(.+?)(?<!_)_(?!\\w)");

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

        // 3. Strikethrough
        text = replaceAll(text, STRIKETHROUGH, m -> {
            String placeholder = "\0PH" + placeholders.size() + "\0";
            placeholders.add(theme.strikethrough(m.group(1)));
            return placeholder;
        }, placeholders);

        // 4. Bold (before italic to avoid ** matching as two *)
        text = replaceAll(text, BOLD, m -> {
            String inner = m.group(1);
            return theme.bold(inner);
        }, placeholders);

        // 4b. Bold with underscores (__text__)
        text = replaceAll(text, BOLD_UNDERSCORE, m -> {
            String inner = m.group(1);
            return theme.bold(inner);
        }, placeholders);

        // 5. Italic
        text = replaceAll(text, ITALIC, m -> {
            String inner = m.group(1);
            return theme.italic(inner);
        }, placeholders);

        // 5b. Italic with underscores (_text_)
        text = replaceAll(text, ITALIC_UNDERSCORE, m -> {
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
