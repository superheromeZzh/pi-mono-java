/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.cron;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import com.huawei.hicampus.mate.matecampusclaw.ai.utils.CampusClawHome;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installs/uninstalls CampusClaw cron into the OS scheduler.
 * macOS: launchd plist; Linux: crontab entry; Windows: Task Scheduler (schtasks).
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SystemSchedulerInstaller {

    private static final Logger log = LoggerFactory.getLogger(SystemSchedulerInstaller.class);

    private static final String LABEL = "com.huawei.hicampus.mate.matecampusclaw.cron";
    private static final String TASK_NAME = "CampusClaw-Cron";
    private static final Path PLIST_PATH = Path.of(System.getProperty("user.home"))
            .resolve("Library/LaunchAgents")
            .resolve(LABEL + ".plist");
    private static final String CRONTAB_MARKER = "# campusclaw-cron";
    private static final long PROC_TIMEOUT_SECONDS = 10L;
    private static final long ID_TIMEOUT_SECONDS = 5L;

    private final Path launcherScript;

    public SystemSchedulerInstaller(Path launcherScript) {
        this.launcherScript = launcherScript.toAbsolutePath().normalize();
    }

    /**
     * Install the cron tick scheduler for the current OS.
     * @param intervalSeconds tick interval (default 60)
     * @return human-readable status message
     *
     * @throws IOException if the operation fails
     */
    public String install(int intervalSeconds) throws IOException {
        if (isWindows()) {
            return installWindows(intervalSeconds);
        } else if (isMacOS()) {
            return installLaunchd(intervalSeconds);
        } else {
            return installCrontab(intervalSeconds);
        }
    }

    /**
     * Uninstall the cron tick scheduler.
     *
     * @return the result
     *
     * @throws IOException if the operation fails
     */
    public String uninstall() throws IOException {
        if (isWindows()) {
            return uninstallWindows();
        } else if (isMacOS()) {
            return uninstallLaunchd();
        } else {
            return uninstallCrontab();
        }
    }

    /**
     * Check if the scheduler is currently installed.
     *
     * @return the result
     */
    public String status() {
        if (isWindows()) {
            return statusWindows();
        } else if (isMacOS()) {
            return statusLaunchd();
        } else {
            return statusCrontab();
        }
    }

    // --- macOS launchd ---

    /**
     * launchd plist template — single {@code %s,%s,%d,%s,%s} replacement (label/launcher/interval/logDir/logDir).
     */
    private static final String PLIST_TEMPLATE =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" \
            "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>%s</string>
                <key>ProgramArguments</key>
                <array>
                    <string>%s</string>
                    <string>--cron-tick</string>
                </array>
                <key>StartInterval</key>
                <integer>%d</integer>
                <key>StandardOutPath</key>
                <string>%s/cron-tick.log</string>
                <key>StandardErrorPath</key>
                <string>%s/cron-tick.err</string>
                <key>RunAtLoad</key>
                <true/>
            </dict>
            </plist>
            """;

    private String installLaunchd(int intervalSeconds) throws IOException {
        Path logDir = CampusClawHome.agentDir().resolve("cron");
        Files.createDirectories(logDir);
        Files.createDirectories(PLIST_PATH.getParent());
        String plist = PLIST_TEMPLATE.formatted(LABEL, launcherScript, intervalSeconds, logDir, logDir);
        Files.writeString(PLIST_PATH, plist);
        reloadLaunchd();
        return "Installed launchd agent: " + PLIST_PATH
                + "\nInterval: every " + intervalSeconds + "s"
                + "\nLogs: " + logDir + "/cron-tick.log";
    }

    // Stop an existing plist (if any) and reload the new one. Falls back to
    // the legacy `launchctl load` path if `bootstrap` is unavailable.
    private void reloadLaunchd() {
        runDiscardingOutput("launchctl bootout", "launchctl", "bootout", "gui/" + getUid(), PLIST_PATH.toString());
        Integer exit = runDiscardingOutput(
                "launchctl bootstrap", "launchctl", "bootstrap", "gui/" + getUid(), PLIST_PATH.toString());
        if (exit == null || exit != 0) {
            runDiscardingOutput("launchctl load", "launchctl", "load", PLIST_PATH.toString());
        }
    }

    /**
     * Run a command discarding both stdout and stderr at the OS level so we never
     * have to drain pipes.
     *
     * @param description label used in debug log lines
     * @param command argv to execute
     * @return the process exit code, or {@code null} if the process could not start,
     *         was interrupted, or timed out
     */
    private Integer runDiscardingOutput(String description, String... command) {
        Process proc = null;
        try {
            proc = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();

            drainToNull(proc.getInputStream());
            drainToNull(proc.getErrorStream());

            if (!proc.waitFor(PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                log.debug("{} timed out after {}s", description, PROC_TIMEOUT_SECONDS);
                return null;
            }
            return proc.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (proc != null) {
                proc.destroyForcibly();
            }
            log.debug("{} interrupted", description, e);
            return null;
        } catch (IOException e) {
            log.debug("{} failed", description, e);
            return null;
        }
    }

    private static void drainToNull(InputStream in) throws IOException {
        try (in;
                OutputStream sink = OutputStream.nullOutputStream()) {
            in.transferTo(sink);
        }
    }

    private String uninstallLaunchd() throws IOException {
        if (!Files.exists(PLIST_PATH)) {
            return "Not installed (no plist found)";
        }
        Integer exit = runDiscardingOutput(
                "launchctl bootout", "launchctl", "bootout", "gui/" + getUid(), PLIST_PATH.toString());
        if (exit == null) {
            // Fallback to legacy unload
            runDiscardingOutput("launchctl unload", "launchctl", "unload", PLIST_PATH.toString());
        }
        Files.deleteIfExists(PLIST_PATH);
        return "Uninstalled launchd agent: " + LABEL;
    }

    private String statusLaunchd() {
        if (!Files.exists(PLIST_PATH)) {
            return "Not installed";
        }
        Process proc = null;
        try {
            proc = new ProcessBuilder("launchctl", "print", "gui/" + getUid() + "/" + LABEL)
                    .redirectErrorStream(true)
                    .start();

            // Drain stdout before waitFor to avoid pipe-fill deadlock; output is read
            // for completeness but not used in the status string.
            proc.getInputStream().readAllBytes();
            if (!proc.waitFor(PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return "Plist exists: " + PLIST_PATH + " (status check timed out)";
            }
            if (proc.exitValue() == 0) {
                return "Installed and active\nPlist: " + PLIST_PATH;
            } else {
                return "Plist exists but service not loaded\nPlist: " + PLIST_PATH + "\nRun: /cron install to reload";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (proc != null) {
                proc.destroyForcibly();
            }
            return "Plist exists: " + PLIST_PATH + " (interrupted)";
        } catch (IOException e) {
            log.debug("launchctl print failed", e);
            return "Plist exists: " + PLIST_PATH + " (status check failed)";
        }
    }

    // --- Linux crontab ---

    private String installCrontab(int intervalSeconds) throws IOException {
        int minutes = Math.max(1, intervalSeconds / 60);
        String cronExpr = minutes >= 60 ? "0 */" + (minutes / 60) + " * * *" : "*/" + minutes + " * * * *";
        String cronLine = cronExpr + " " + launcherScript + " --cron-tick >> "
                + CampusClawHome.agentDir().resolve("cron/cron-tick.log")
                + " 2>&1 " + CRONTAB_MARKER;

        String existing = getCurrentCrontab();

        // Remove any existing campusclaw entry
        String cleaned = existing.lines()
                .filter(l -> !l.contains(CRONTAB_MARKER))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        String newCrontab = cleaned.isEmpty() ? cronLine + "\n" : cleaned + "\n" + cronLine + "\n";

        writeCrontab(newCrontab);
        return "Installed crontab entry: " + cronLine;
    }

    private String uninstallCrontab() throws IOException {
        String existing = getCurrentCrontab();
        String cleaned = existing.lines()
                .filter(l -> !l.contains(CRONTAB_MARKER))
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
        if (cleaned.equals(existing)) {
            return "Not installed (no crontab entry found)";
        }
        writeCrontab(cleaned.isEmpty() ? "" : cleaned + "\n");
        return "Removed crontab entry";
    }

    private String statusCrontab() {
        try {
            String existing = getCurrentCrontab();
            var entry = existing.lines().filter(l -> l.contains(CRONTAB_MARKER)).findFirst();
            return entry.map(s -> "Installed: " + s).orElse("Not installed");
        } catch (Exception e) {
            return "Unable to check crontab: " + e.getMessage();
        }
    }

    private String getCurrentCrontab() throws IOException {
        Process proc = null;
        try {
            proc = new ProcessBuilder("crontab", "-l").redirectErrorStream(true).start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proc.waitFor(PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                log.debug("crontab -l timed out after {}s", PROC_TIMEOUT_SECONDS);
                return "";
            }
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (proc != null) {
                proc.destroyForcibly();
            }
            return "";
        }
    }

    private void writeCrontab(String content) throws IOException {
        var tmpFile = Files.createTempFile("campusclaw-crontab-", ".tmp");
        Files.writeString(tmpFile, content);
        runDiscardingOutput("crontab apply", "crontab", tmpFile.toString());
        Files.deleteIfExists(tmpFile);
    }

    // --- Windows Task Scheduler ---

    private String installWindows(int intervalSeconds) throws IOException {
        Path logDir = CampusClawHome.agentDir().resolve("cron");
        Files.createDirectories(logDir);

        // schtasks /MINUTE supports 1-1439 minute intervals
        int minutes = Math.max(1, intervalSeconds / 60);

        // Build the command: campusclaw.bat --cron-tick >> log 2>&1
        String command =
                "cmd /c \"" + launcherScript + " --cron-tick >> " + logDir.resolve("cron-tick.log") + " 2>&1\"";

        // Delete existing task first (ignore errors if not found)
        runDiscardingOutput("schtasks /Delete (pre-install)", "schtasks", "/Delete", "/TN", TASK_NAME, "/F");

        Process proc = null;
        try {
            proc = new ProcessBuilder(
                            "schtasks",
                            "/Create",
                            "/SC",
                            "MINUTE",
                            "/MO",
                            String.valueOf(minutes),
                            "/TN",
                            TASK_NAME,
                            "/TR",
                            command,
                            "/F" // force overwrite
                            )
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proc.waitFor(PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return "Failed to create task: timed out after " + PROC_TIMEOUT_SECONDS + "s";
            }
            if (proc.exitValue() != 0) {
                return "Failed to create task: " + output;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (proc != null) {
                proc.destroyForcibly();
            }
        }

        return "Installed Windows scheduled task: " + TASK_NAME
                + "\nInterval: every " + minutes + " minute(s)"
                + "\nCommand: " + launcherScript + " --cron-tick"
                + "\nLogs: " + logDir + "\\cron-tick.log";
    }

    private String uninstallWindows() throws IOException {
        Process proc = null;
        try {
            proc = new ProcessBuilder("schtasks", "/Delete", "/TN", TASK_NAME, "/F")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proc.waitFor(PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return "schtasks /Delete timed out after " + PROC_TIMEOUT_SECONDS + "s";
            }
            if (proc.exitValue() != 0) {
                return "Not installed or failed to remove: " + output.trim();
            }
            return "Uninstalled Windows scheduled task: " + TASK_NAME;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (proc != null) {
                proc.destroyForcibly();
            }
            return "Interrupted while removing task";
        }
    }

    private String statusWindows() {
        Process proc = null;
        try {
            proc = new ProcessBuilder("schtasks", "/Query", "/TN", TASK_NAME, "/FO", "LIST")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!proc.waitFor(PROC_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                return "Unable to check task: schtasks /Query timed out";
            }
            if (proc.exitValue() == 0) {
                // Extract key info from LIST format
                String status = output.lines()
                        .filter(l -> l.contains("Status:")
                                || l.contains("Next Run Time:")
                                || l.contains("状态:")
                                || l.contains("下次运行时间:"))
                        .reduce("", (a, b) -> a.isEmpty() ? b.trim() : a + "\n" + b.trim());
                return "Installed: " + TASK_NAME + "\n" + status;
            } else {
                return "Not installed";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (proc != null) {
                proc.destroyForcibly();
            }
            return "Unable to check task: interrupted";
        } catch (IOException e) {
            log.debug("schtasks /Query failed", e);
            return "Unable to check task: " + e.getMessage();
        }
    }

    // --- Helpers ---

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static boolean isMacOS() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static String getUid() {
        Process proc = null;
        try {
            proc = new ProcessBuilder("id", "-u")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String uid = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!proc.waitFor(ID_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                log.debug("id -u timed out after {}s; using fallback uid", ID_TIMEOUT_SECONDS);
                return "501";
            }
            return uid;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (proc != null) {
                proc.destroyForcibly();
            }
            return "501";
        } catch (IOException e) {
            log.debug("id -u failed; using fallback uid", e);
            return "501";
        }
    }

    /**
     * Auto-detect the launcher script path from the running JAR location.
     * Looks for campusclaw.sh (macOS/Linux) or campusclaw.bat (Windows).
     *
     * @return the result
     */
    public static Path detectLauncherScript() {
        String scriptName = isWindows() ? "campusclaw.bat" : "campusclaw.sh";
        try {
            Path jarPath = Path.of(SystemSchedulerInstaller.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());

            // JAR is at modules/coding-agent-cli/build/libs/*.jar
            // launcher script is at the repo root
            Path root = jarPath.getParent().getParent().getParent().getParent().getParent();
            Path script = root.resolve(scriptName);
            if (Files.exists(script)) {
                return script;
            }
        } catch (Exception e) {
            // jar location lookup is best-effort — fall through to cwd-relative resolution
            log.debug("jar-relative launcher lookup failed; falling back to cwd-relative", e);
        }

        // Fallback: look relative to cwd
        Path cwd = Path.of(System.getProperty("user.dir")).resolve(scriptName);
        if (Files.exists(cwd)) {
            return cwd;
        }
        return null;
    }
}
