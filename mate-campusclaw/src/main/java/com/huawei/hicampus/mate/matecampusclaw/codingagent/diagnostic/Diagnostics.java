package com.huawei.hicampus.mate.matecampusclaw.codingagent.diagnostic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

/**
 * Diagnostic checks for the coding agent environment.
 * Validates configuration, resources, and detects conflicts.
 */
public class Diagnostics {
    private static final Logger log = LoggerFactory.getLogger(Diagnostics.class);

    public enum Severity { INFO, WARNING, ERROR }

    public record DiagnosticResult(
        String checkName,
        Severity severity,
        String message,
        @Nullable String suggestion
    ) {}

    private final List<DiagnosticCheck> checks = new ArrayList<>();

    @FunctionalInterface
    public interface DiagnosticCheck {
        List<DiagnosticResult> run();
    }

    /** Register a diagnostic check. */
    public void registerCheck(DiagnosticCheck check) {
        checks.add(check);
    }

    /** Run all diagnostic checks. */
    public List<DiagnosticResult> runAll() {
        List<DiagnosticResult> results = new ArrayList<>();
        for (DiagnosticCheck check : checks) {
            try {
                results.addAll(check.run());
            } catch (Exception e) {
                results.add(new DiagnosticResult("diagnostic-error", Severity.ERROR,
                    "Diagnostic check failed: " + e.getMessage(), null));
            }
        }
        return results;
    }

    /** Register built-in checks. */
    public void registerBuiltins(Path projectDir) {
        registerCheck(() -> checkJavaVersion());
        registerCheck(() -> checkConfigFiles(projectDir));
        registerCheck(() -> checkApiKeys());
        registerCheck(() -> checkDiskSpace());
    }

    private List<DiagnosticResult> checkJavaVersion() {
        var results = new ArrayList<DiagnosticResult>();
        String version = System.getProperty("java.version", "unknown");
        int major = parseMajorVersion(version);
        if (major < 21) {
            results.add(new DiagnosticResult("java-version", Severity.ERROR,
                "Java " + version + " detected, minimum required is Java 21",
                "Install Java 21+ and set JAVA_HOME"));
        } else {
            results.add(new DiagnosticResult("java-version", Severity.INFO,
                "Java " + version + " ✓", null));
        }
        return results;
    }

    private List<DiagnosticResult> checkConfigFiles(Path projectDir) {
        var results = new ArrayList<DiagnosticResult>();
        // Global config
        Path globalDir = com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.USER_AGENT_DIR;
        if (Files.isDirectory(globalDir)) {
            results.add(new DiagnosticResult("global-config", Severity.INFO,
                "Global config directory exists: " + globalDir, null));
        } else {
            results.add(new DiagnosticResult("global-config", Severity.WARNING,
                "Global config directory not found: " + globalDir,
                "Run the agent once to auto-create it"));
        }
        // Project config
        Path projectConfig = projectDir.resolve(com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.CONFIG_DIR_NAME);
        if (Files.isDirectory(projectConfig)) {
            results.add(new DiagnosticResult("project-config", Severity.INFO,
                "Project config directory exists: " + projectConfig, null));
        }
        return results;
    }

    private List<DiagnosticResult> checkApiKeys() {
        var results = new ArrayList<DiagnosticResult>();
        Map<String, String> keyChecks = Map.of(
            "ANTHROPIC_API_KEY", "Anthropic",
            "OPENAI_API_KEY", "OpenAI",
            "ZAI_API_KEY", "ZAI (智谱)",
            "KIMI_API_KEY", "Kimi Coding",
            "MINIMAX_API_KEY", "MiniMax",
            "GOOGLE_API_KEY", "Google"
        );
        int found = 0;
        for (var entry : keyChecks.entrySet()) {
            String val = System.getenv(entry.getKey());
            if (val != null && !val.isBlank()) {
                results.add(new DiagnosticResult("api-key-" + entry.getKey(), Severity.INFO,
                    entry.getValue() + " API key configured ✓", null));
                found++;
            }
        }
        if (found == 0) {
            results.add(new DiagnosticResult("api-keys", Severity.WARNING,
                "No API keys found in environment",
                "Set at least one API key (e.g. ZAI_API_KEY, ANTHROPIC_API_KEY)"));
        }
        return results;
    }

    private List<DiagnosticResult> checkDiskSpace() {
        var results = new ArrayList<DiagnosticResult>();
        Path home = Path.of(System.getProperty("user.home", "/"));
        long usableSpace = home.toFile().getUsableSpace();
        long mb = usableSpace / (1024 * 1024);
        if (mb < 100) {
            results.add(new DiagnosticResult("disk-space", Severity.WARNING,
                "Low disk space: " + mb + " MB remaining",
                "Free up disk space for session storage"));
        } else {
            results.add(new DiagnosticResult("disk-space", Severity.INFO,
                "Disk space: " + (mb > 1024 ? (mb / 1024) + " GB" : mb + " MB") + " available ✓", null));
        }
        return results;
    }

    private int parseMajorVersion(String version) {
        try {
            String[] parts = version.split("[._-]");
            int major = Integer.parseInt(parts[0]);
            // Java 1.x format
            if (major == 1 && parts.length > 1) {
                return Integer.parseInt(parts[1]);
            }
            return major;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Format all results as a readable report. */
    public static String formatReport(List<DiagnosticResult> results) {
        var sb = new StringBuilder();
        sb.append("=== Diagnostic Report ===\n\n");
        for (DiagnosticResult r : results) {
            String icon = switch (r.severity) {
                case INFO -> "ℹ️ ";
                case WARNING -> "⚠️ ";
                case ERROR -> "❌ ";
            };
            sb.append(icon).append("[").append(r.checkName()).append("] ").append(r.message()).append('\n');
            if (r.suggestion() != null) {
                sb.append("   → ").append(r.suggestion()).append('\n');
            }
        }
        return sb.toString();
    }
}
