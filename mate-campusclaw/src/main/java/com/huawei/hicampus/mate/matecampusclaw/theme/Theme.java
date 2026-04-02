package com.huawei.hicampus.mate.matecampusclaw.codingagent.theme;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.annotation.Nullable;

/**
 * Theme definition with configurable colors for all UI elements.
 * Supports 40+ configurable color properties for terminal display.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Theme(
    @JsonProperty("name") String name,
    @JsonProperty("colors") Map<String, String> colors,
    @JsonProperty("description") @Nullable String description
) {
    // Standard color keys
    public static final String PRIMARY = "primary";
    public static final String SECONDARY = "secondary";
    public static final String ACCENT = "accent";
    public static final String ERROR = "error";
    public static final String WARNING = "warning";
    public static final String SUCCESS = "success";
    public static final String INFO = "info";
    public static final String DIM = "dim";
    public static final String BORDER = "border";
    public static final String BACKGROUND = "background";
    public static final String TEXT = "text";
    public static final String TEXT_DIM = "text.dim";
    public static final String TEXT_BOLD = "text.bold";
    public static final String PROMPT_USER = "prompt.user";
    public static final String PROMPT_ASSISTANT = "prompt.assistant";
    public static final String PROMPT_SYSTEM = "prompt.system";
    public static final String DIFF_ADDED = "diff.added";
    public static final String DIFF_REMOVED = "diff.removed";
    public static final String DIFF_MODIFIED = "diff.modified";
    public static final String DIFF_CONTEXT = "diff.context";
    public static final String CODE_KEYWORD = "code.keyword";
    public static final String CODE_STRING = "code.string";
    public static final String CODE_COMMENT = "code.comment";
    public static final String CODE_NUMBER = "code.number";
    public static final String CODE_FUNCTION = "code.function";
    public static final String CODE_TYPE = "code.type";
    public static final String CODE_VARIABLE = "code.variable";
    public static final String CODE_OPERATOR = "code.operator";
    public static final String TOOL_NAME = "tool.name";
    public static final String TOOL_INPUT = "tool.input";
    public static final String TOOL_OUTPUT = "tool.output";
    public static final String THINKING_BORDER = "thinking.border";
    public static final String THINKING_TEXT = "thinking.text";
    public static final String STATUS_BAR = "status.bar";
    public static final String STATUS_TEXT = "status.text";
    public static final String FOOTER_TEXT = "footer.text";
    public static final String FOOTER_KEY = "footer.key";
    public static final String SPINNER = "spinner";
    public static final String PROGRESS_BAR = "progress.bar";
    public static final String PROGRESS_TRACK = "progress.track";
    public static final String SELECTION = "selection";
    public static final String CURSOR = "cursor";

    /** Get an ANSI color escape for a theme color key. */
    public String ansi(String key) {
        String color = colors.getOrDefault(key, null);
        if (color == null) return "";
        return toAnsi(color);
    }

    /** Convert hex color (#RRGGBB) or named color to ANSI escape. */
    public static String toAnsi(String color) {
        if (color.startsWith("#") && color.length() == 7) {
            int r = Integer.parseInt(color.substring(1, 3), 16);
            int g = Integer.parseInt(color.substring(3, 5), 16);
            int b = Integer.parseInt(color.substring(5, 7), 16);
            return "\033[38;2;" + r + ";" + g + ";" + b + "m";
        }
        return switch (color.toLowerCase()) {
            case "black" -> "\033[30m";
            case "red" -> "\033[31m";
            case "green" -> "\033[32m";
            case "yellow" -> "\033[33m";
            case "blue" -> "\033[34m";
            case "magenta" -> "\033[35m";
            case "cyan" -> "\033[36m";
            case "white" -> "\033[37m";
            case "bright_black", "gray", "grey" -> "\033[90m";
            case "bright_red" -> "\033[91m";
            case "bright_green" -> "\033[92m";
            case "bright_yellow" -> "\033[93m";
            case "bright_blue" -> "\033[94m";
            case "bright_magenta" -> "\033[95m";
            case "bright_cyan" -> "\033[96m";
            case "bright_white" -> "\033[97m";
            default -> "";
        };
    }
}
