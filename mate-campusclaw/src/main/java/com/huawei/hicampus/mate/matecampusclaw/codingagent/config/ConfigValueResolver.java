package com.huawei.hicampus.mate.matecampusclaw.codingagent.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves configuration values by expanding environment variable references
 * and other dynamic placeholders.
 *
 * <p>Supports:
 * <ul>
 *   <li>{@code ${ENV_VAR}} — expands to the value of the environment variable</li>
 *   <li>{@code ${ENV_VAR:-default}} — expands with a default value if unset</li>
 *   <li>{@code ~} — expands to the user's home directory</li>
 * </ul>
 */
public final class ConfigValueResolver {

    private static final Pattern ENV_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern ENV_DEFAULT_PATTERN = Pattern.compile("^([^:]+):-(.*)$");

    private ConfigValueResolver() {}

    /**
     * Resolves a configuration value by expanding all placeholders.
     *
     * @param value the raw configuration value
     * @return the resolved value with placeholders expanded
     */
    public static String resolve(String value) {
        if (value == null) return null;

        // Expand ~ to home directory
        if (value.startsWith("~")) {
            value = System.getProperty("user.home") + value.substring(1);
        }

        // Expand ${ENV_VAR} and ${ENV_VAR:-default}
        Matcher matcher = ENV_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String expr = matcher.group(1);
            String replacement = resolveEnvExpr(expr);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Resolves a single environment variable expression.
     */
    private static String resolveEnvExpr(String expr) {
        Matcher defaultMatcher = ENV_DEFAULT_PATTERN.matcher(expr);
        if (defaultMatcher.matches()) {
            String varName = defaultMatcher.group(1).trim();
            String defaultValue = defaultMatcher.group(2);
            String envValue = System.getenv(varName);
            return (envValue != null && !envValue.isBlank()) ? envValue : defaultValue;
        }

        String envValue = System.getenv(expr.trim());
        return envValue != null ? envValue : "";
    }

    /**
     * Checks if a value contains any unresolved placeholders.
     */
    public static boolean hasPlaceholders(String value) {
        return value != null && (ENV_PATTERN.matcher(value).find() || value.startsWith("~"));
    }
}
