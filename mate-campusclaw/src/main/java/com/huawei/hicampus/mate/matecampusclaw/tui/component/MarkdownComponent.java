/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

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
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
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
            Matcher codeFence = CODE_FENCE_PATTERN.matcher(line);
            if (codeFence.matches()) {
                i = consumeCodeBlock(rawLines, i, codeFence.group(1).trim(), width, output);
                continue;
            }
            Matcher heading = HEADING_PATTERN.matcher(line);
            if (heading.matches()) {
                renderHeading(heading.group(1).length(), heading.group(2), width, output);
                i++;
                continue;
            }
            if (HR_PATTERN.matcher(line).matches()) {
                renderHorizontalRule(width, output);
                i++;
                continue;
            }
            if (BLOCKQUOTE_PATTERN.matcher(line).matches()) {
                i = renderBlockquote(rawLines, i, width, output);
                continue;
            }
            if (isTableStart(rawLines, i)) {
                i = renderTable(rawLines, i, width, output);
                continue;
            }
            if (UNORDERED_LIST_PATTERN.matcher(line).matches()
                    || ORDERED_LIST_PATTERN.matcher(line).matches()) {
                i = renderList(rawLines, i, width, output);
                continue;
            }
            if (line.isEmpty()) {
                output.add("");
                i++;
                continue;
            }
            i = consumeParagraph(rawLines, i, width, output);
        }
        return output;
    }

    private int consumeCodeBlock(String[] rawLines, int fenceIdx, String lang, int width, List<String> out) {
        int i = fenceIdx + 1;
        List<String> codeLines = new ArrayList<>();
        while (i < rawLines.length && !rawLines[i].startsWith("```")) {
            codeLines.add(rawLines[i]);
            i++;
        }
        if (i < rawLines.length) {
            i++;
        }
        renderCodeBlock(codeLines, lang, width, out);
        return i;
    }

    private boolean isTableStart(String[] rawLines, int i) {
        return rawLines[i].contains("|")
                && i + 1 < rawLines.length
                && TABLE_SEPARATOR_PATTERN.matcher(rawLines[i + 1]).matches();
    }

    private boolean isBlockBoundary(String[] rawLines, int i) {
        String line = rawLines[i];
        return line.isEmpty()
                || HEADING_PATTERN.matcher(line).matches()
                || HR_PATTERN.matcher(line).matches()
                || CODE_FENCE_PATTERN.matcher(line).matches()
                || UNORDERED_LIST_PATTERN.matcher(line).matches()
                || ORDERED_LIST_PATTERN.matcher(line).matches()
                || BLOCKQUOTE_PATTERN.matcher(line).matches()
                || isTableStart(rawLines, i);
    }

    private int consumeParagraph(String[] rawLines, int startIdx, int width, List<String> out) {
        StringBuilder para = new StringBuilder();
        int i = startIdx;
        while (i < rawLines.length && !isBlockBoundary(rawLines, i)) {
            if (para.length() > 0) {
                para.append(' ');
            }
            para.append(rawLines[i]);
            i++;
        }
        renderParagraph(para.toString(), width, out);
        return i;
    }

    // -------------------------------------------------------------------
    // Block renderers
    // -------------------------------------------------------------------

    private void renderHeading(int level, String rawText, int width, List<String> out) {
        String styled = renderInline(rawText);
        String formatted =
                switch (level) {
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
     *
     * @param rawLines source markdown lines
     * @param startIdx index of the first line in the run
     * @param width target render width
     * @param out collector for rendered output lines
     * @return the index after the last consumed line
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

    private void renderListItem(
            String prefixWithBullet, String rawText, int width, int continuationIndent, List<String> out) {
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
     *
     * @param rawLines source markdown lines
     * @param startIdx index of the first blockquote line
     * @param width target render width
     * @param out collector for rendered output lines
     * @return the index after the last consumed line
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
     *
     * @param rawLines source markdown lines
     * @param startIdx index of the table header line
     * @param width target render width
     * @param out collector for rendered output lines
     * @return the index after the last consumed line
     */
    private int renderTable(String[] rawLines, int startIdx, int width, List<String> out) {
        List<String> headers = parseTableRow(rawLines[startIdx]);
        int numCols = headers.size();
        if (numCols == 0) {
            return startIdx + 1;
        }
        int i = startIdx + 1;
        if (i < rawLines.length && TABLE_SEPARATOR_PATTERN.matcher(rawLines[i]).matches()) {
            i++;
        }
        var rows = new ArrayList<List<String>>();
        i = parseTableDataRows(rawLines, i, numCols, rows);

        // Border overhead: "│ " + (n-1) * " │ " + " │" = 3n + 1
        int borderOverhead = 3 * numCols + 1;
        int[] colWidths = computeColumnWidths(headers, rows, width - borderOverhead);
        if (colWidths == null) {
            for (int j = startIdx; j < i; j++) {
                out.addAll(AnsiUtils.wrapTextWithAnsi(rawLines[j], width));
            }
            return i;
        }

        out.add(renderHorizontalBorder(colWidths, "┌", "┬", "┐"));
        renderTableRow(headers, colWidths, numCols, true, out);
        String separator = renderHorizontalBorder(colWidths, "├", "┼", "┤");
        out.add(separator);
        for (int r = 0; r < rows.size(); r++) {
            renderTableRow(rows.get(r), colWidths, numCols, false, out);
            if (r < rows.size() - 1) {
                out.add(separator);
            }
        }
        out.add(renderHorizontalBorder(colWidths, "└", "┴", "┘"));
        return i;
    }

    private int parseTableDataRows(String[] rawLines, int startIdx, int numCols, List<List<String>> rows) {
        int i = startIdx;
        while (i < rawLines.length) {
            String line = rawLines[i];
            if (!line.contains("|") || line.isBlank()) {
                break;
            }
            List<String> row = parseTableRow(line);
            while (row.size() < numCols) {
                row.add("");
            }
            if (row.size() > numCols) {
                row = row.subList(0, numCols);
            }
            rows.add(row);
            i++;
        }
        return i;
    }

    private int[] computeColumnWidths(List<String> headers, List<List<String>> rows, int availableForCells) {
        int numCols = headers.size();
        if (availableForCells < numCols) {
            return null;
        }
        int[] natural = new int[numCols];
        for (int c = 0; c < numCols; c++) {
            natural[c] = AnsiUtils.visibleWidth(renderInline(headers.get(c)));
        }
        for (var row : rows) {
            for (int c = 0; c < numCols; c++) {
                natural[c] = Math.max(natural[c], AnsiUtils.visibleWidth(renderInline(row.get(c))));
            }
        }
        int totalNatural = 0;
        for (int w : natural) {
            totalNatural += w;
        }
        if (totalNatural <= availableForCells) {
            return natural;
        }

        // Proportional shrink + leftover redistribution
        int[] colWidths = new int[numCols];
        int allocated = 0;
        for (int c = 0; c < numCols; c++) {
            colWidths[c] = Math.max(1, (int) ((double) natural[c] / totalNatural * availableForCells));
            allocated += colWidths[c];
        }
        for (int c = 0, leftover = availableForCells - allocated; leftover > 0 && c < numCols; c++, leftover--) {
            colWidths[c]++;
        }
        return colWidths;
    }

    private static String renderHorizontalBorder(int[] colWidths, String left, String mid, String right) {
        var parts = new ArrayList<String>();
        for (int w : colWidths) {
            parts.add("─".repeat(w));
        }
        return left + "─" + String.join("─" + mid + "─", parts) + "─" + right;
    }

    private void renderTableRow(List<String> cells, int[] colWidths, int numCols, boolean bold, List<String> out) {
        // Wrap each cell and find max lines
        var wrappedCells = new ArrayList<List<String>>();
        int maxLines = 1;
        for (int c = 0; c < numCols; c++) {
            String styled = renderInline(cells.get(c));
            List<String> wrapped = AnsiUtils.wrapTextWithAnsi(styled, colWidths[c]);
            if (wrapped.isEmpty()) {
                wrapped = List.of("");
            }
            wrappedCells.add(wrapped);
            maxLines = Math.max(maxLines, wrapped.size());
        }

        for (int lineIdx = 0; lineIdx < maxLines; lineIdx++) {
            var sb = new StringBuilder("│ ");
            for (int c = 0; c < numCols; c++) {
                if (c > 0) {
                    sb.append(" │ ");
                }
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
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
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
     * Processes in a specific order to handle nesting correctly:
     * code / link / strikethrough are stashed into placeholders to prevent
     * further marker matching inside them; bold/italic render in place.
     *
     * @param text raw inline text containing markdown markers
     * @return text with ANSI styling substituted for markdown markers
     */
    String renderInline(String text) {
        List<String> placeholders = new ArrayList<>();
        text = replaceAll(text, INLINE_CODE, m -> stash(placeholders, theme.code(m.group(1))));
        text = replaceAll(
                text,
                LINK,
                m -> stash(placeholders, theme.link(m.group(1)) + " " + theme.linkUrl("(" + m.group(2) + ")")));
        text = replaceAll(text, STRIKETHROUGH, m -> stash(placeholders, theme.strikethrough(m.group(1))));

        // Bold first so ** isn't consumed as two * by ITALIC.
        text = replaceAll(text, BOLD, m -> theme.bold(m.group(1)));
        text = replaceAll(text, BOLD_UNDERSCORE, m -> theme.bold(m.group(1)));
        text = replaceAll(text, ITALIC, m -> theme.italic(m.group(1)));
        text = replaceAll(text, ITALIC_UNDERSCORE, m -> theme.italic(m.group(1)));
        for (int i = 0; i < placeholders.size(); i++) {
            text = text.replace("\0PH" + i + "\0", placeholders.get(i));
        }
        return text;
    }

    private static String stash(List<String> placeholders, String value) {
        String placeholder = "\0PH" + placeholders.size() + "\0";
        placeholders.add(value);
        return placeholder;
    }

    private static String replaceAll(
            String text, Pattern pattern, java.util.function.Function<Matcher, String> replacer) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(replacer.apply(m)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
