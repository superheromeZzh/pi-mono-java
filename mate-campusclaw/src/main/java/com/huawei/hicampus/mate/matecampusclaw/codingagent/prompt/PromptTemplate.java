package com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles prompt template expansion with positional parameters.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@code $1}, {@code $2}, ... — individual positional arguments</li>
 *   <li>{@code $@} — all arguments joined by spaces</li>
 *   <li>{@code $#} — number of arguments</li>
 * </ul>
 *
 * <p>Example: {@code "Explain $1 in the context of $2"} with args ["React", "frontend"]
 * produces {@code "Explain React in the context of frontend"}.
 */
public final class PromptTemplate {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\$([0-9]+|@|#)");

    private PromptTemplate() {}

    /**
     * Expands a template string with the given arguments.
     *
     * @param template the template string containing $1, $2, $@, $# placeholders
     * @param args     the positional arguments
     * @return the expanded string
     */
    public static String expand(String template, List<String> args) {
        if (template == null || template.isEmpty()) return template;
        if (args == null || args.isEmpty()) {
            // Remove unfilled placeholders
            return PARAM_PATTERN.matcher(template).replaceAll("");
        }

        Matcher matcher = PARAM_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String ref = matcher.group(1);
            String replacement;
            if ("@".equals(ref)) {
                replacement = String.join(" ", args);
            } else if ("#".equals(ref)) {
                replacement = String.valueOf(args.size());
            } else {
                int index = Integer.parseInt(ref);
                if (index >= 1 && index <= args.size()) {
                    replacement = args.get(index - 1);
                } else {
                    replacement = ""; // Out of range
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Checks if a template string contains any parameter placeholders.
     */
    public static boolean hasParameters(String template) {
        return template != null && PARAM_PATTERN.matcher(template).find();
    }
}
