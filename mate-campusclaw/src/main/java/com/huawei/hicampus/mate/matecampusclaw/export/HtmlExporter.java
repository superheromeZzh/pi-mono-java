package com.huawei.hicampus.mate.matecampusclaw.codingagent.export;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ThinkingContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolCall;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ToolResultMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

/**
 * Exports a conversation (list of messages) to an HTML document.
 * Converts ANSI escape codes to HTML spans and provides a styled output.
 */
public final class HtmlExporter {

    private static final Pattern ANSI_PATTERN = Pattern.compile("\033\\[([0-9;]*)m");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private HtmlExporter() {}

    /**
     * Exports messages to a complete HTML document string.
     *
     * @param messages  the conversation messages
     * @param title     document title
     * @param modelName the model name to display in the header
     * @return complete HTML document as string
     */
    public static String export(List<Message> messages, String title, String modelName) {
        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(escapeHtml(title)).append("</title>\n");
        sb.append("<style>\n").append(CSS).append("\n</style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<div class=\"container\">\n");
        sb.append("<h1>").append(escapeHtml(title)).append("</h1>\n");
        sb.append("<p class=\"meta\">Model: ").append(escapeHtml(modelName))
            .append(" | Exported: ").append(TIME_FMT.format(Instant.now())).append("</p>\n");

        for (var message : messages) {
            renderMessage(sb, message);
        }

        sb.append("</div>\n</body>\n</html>");
        return sb.toString();
    }

    private static void renderMessage(StringBuilder sb, Message message) {
        switch (message) {
            case UserMessage um -> {
                sb.append("<div class=\"message user\">\n");
                sb.append("<div class=\"role\">User</div>\n");
                sb.append("<div class=\"content\">").append(ansiToHtml(escapeHtml(extractText(um.content())))).append("</div>\n");
                sb.append("</div>\n");
            }
            case AssistantMessage am -> {
                sb.append("<div class=\"message assistant\">\n");
                sb.append("<div class=\"role\">Assistant</div>\n");
                for (var block : am.content()) {
                    switch (block) {
                        case TextContent tc ->
                            sb.append("<div class=\"content\">").append(ansiToHtml(escapeHtml(tc.text()))).append("</div>\n");
                        case ThinkingContent tc ->
                            sb.append("<details class=\"thinking\"><summary>Thinking</summary><pre>")
                                .append(escapeHtml(tc.thinking())).append("</pre></details>\n");
                        case ToolCall tc ->
                            sb.append("<div class=\"tool-call\">Tool: <code>").append(escapeHtml(tc.name()))
                                .append("</code></div>\n");
                        default -> {}
                    }
                }
                if (am.usage() != null) {
                    sb.append("<div class=\"usage\">Tokens: ")
                        .append(am.usage().input()).append(" in / ")
                        .append(am.usage().output()).append(" out</div>\n");
                }
                sb.append("</div>\n");
            }
            case ToolResultMessage trm -> {
                sb.append("<div class=\"message tool-result\">\n");
                sb.append("<div class=\"role\">Tool Result</div>\n");
                String trmText = extractText(trm.content());
                sb.append("<pre class=\"content\">").append(escapeHtml(
                    trmText.length() > 2000 ? trmText.substring(0, 2000) + "..." : trmText
                )).append("</pre>\n");
                sb.append("</div>\n");
            }
            default -> {}
        }
    }

    /**
     * Converts ANSI escape codes to HTML span elements with CSS classes.
     */
    public static String ansiToHtml(String text) {
        if (text == null) return "";
        Matcher matcher = ANSI_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        boolean inSpan = false;

        while (matcher.find()) {
            if (inSpan) {
                matcher.appendReplacement(sb, "</span>");
                inSpan = false;
            }
            String codes = matcher.group(1);
            if (codes.isEmpty() || "0".equals(codes)) {
                matcher.appendReplacement(sb, inSpan ? "</span>" : "");
                inSpan = false;
            } else {
                String cssClass = ansiCodeToClass(codes);
                matcher.appendReplacement(sb, "<span class=\"" + cssClass + "\">");
                inSpan = true;
            }
        }
        matcher.appendTail(sb);
        if (inSpan) sb.append("</span>");
        return sb.toString();
    }

    private static String ansiCodeToClass(String codes) {
        return switch (codes) {
            case "1" -> "bold";
            case "3" -> "italic";
            case "4" -> "underline";
            case "30" -> "fg-black";
            case "31" -> "fg-red";
            case "32" -> "fg-green";
            case "33" -> "fg-yellow";
            case "34" -> "fg-blue";
            case "35" -> "fg-magenta";
            case "36" -> "fg-cyan";
            case "37" -> "fg-white";
            case "90" -> "fg-bright-black";
            case "91" -> "fg-bright-red";
            case "92" -> "fg-bright-green";
            case "93" -> "fg-bright-yellow";
            case "94" -> "fg-bright-blue";
            case "95" -> "fg-bright-magenta";
            case "96" -> "fg-bright-cyan";
            case "97" -> "fg-bright-white";
            default -> "ansi-" + codes.replace(';', '-');
        };
    }

    private static String extractText(List<ContentBlock> content) {
        var sb = new StringBuilder();
        for (var block : content) {
            if (block instanceof TextContent tc) sb.append(tc.text());
        }
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\"", "&quot;")
            .replace("\n", "<br>");
    }

    private static final String CSS = """
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
               background: #1a1a2e; color: #e0e0e0; line-height: 1.6; }
        .container { max-width: 900px; margin: 0 auto; padding: 20px; }
        h1 { color: #fff; margin-bottom: 5px; }
        .meta { color: #888; margin-bottom: 20px; font-size: 0.9em; }
        .message { margin: 15px 0; padding: 15px; border-radius: 8px; }
        .message.user { background: #16213e; border-left: 3px solid #0f3460; }
        .message.assistant { background: #1a1a2e; border-left: 3px solid #e94560; }
        .message.tool-result { background: #0f0f23; border-left: 3px solid #533483; font-size: 0.85em; }
        .role { font-weight: bold; color: #888; margin-bottom: 8px; text-transform: uppercase; font-size: 0.8em; }
        .content { white-space: pre-wrap; word-break: break-word; }
        .thinking { margin: 10px 0; }
        .thinking summary { cursor: pointer; color: #888; }
        .thinking pre { background: #0f0f23; padding: 10px; border-radius: 4px; overflow-x: auto; font-size: 0.85em; }
        .tool-call { color: #888; font-size: 0.9em; margin: 5px 0; }
        .tool-call code { background: #2a2a4a; padding: 2px 6px; border-radius: 3px; }
        .usage { color: #666; font-size: 0.8em; margin-top: 8px; }
        pre { overflow-x: auto; }
        .bold { font-weight: bold; } .italic { font-style: italic; } .underline { text-decoration: underline; }
        .fg-red { color: #ff6b6b; } .fg-green { color: #51cf66; } .fg-yellow { color: #ffd43b; }
        .fg-blue { color: #339af0; } .fg-magenta { color: #cc5de8; } .fg-cyan { color: #22b8cf; }
        .fg-white { color: #fff; } .fg-black { color: #495057; }
        .fg-bright-red { color: #ff8787; } .fg-bright-green { color: #69db7c; }
        .fg-bright-yellow { color: #ffe066; } .fg-bright-blue { color: #5c7cfa; }
        .fg-bright-magenta { color: #da77f2; } .fg-bright-cyan { color: #3bc9db; }
        .fg-bright-white { color: #f8f9fa; } .fg-bright-black { color: #868e96; }
        """;
}
