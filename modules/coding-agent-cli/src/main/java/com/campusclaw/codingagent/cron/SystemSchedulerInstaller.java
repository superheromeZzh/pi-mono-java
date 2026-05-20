/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.cron;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

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

    private static final String LABEL = "com.campusclaw.cron";
    private static final String TASK_NAME = "CampusClaw-Cron";
    private static final Path PLIST_PATH = Path.of(System.getProperty("user.home"))
            .resolve("Library/LaunchAgents")
            .resolve(LABEL + ".plist");
    private static final String CRONTAB_MARKER = "# campusclaw-cron";

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
        Path logDir = Path.of(System.getProperty("user.home")).resolve(".campusclaw/agent/cron");
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
        try {
            new ProcessBuilder("launchctl", "bootout", "gui/" + getUid(), PLIST_PATH.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("launchctl bootout interrupted", e);
        } catch (IOException e) {
            // bootout fails when not loaded — safe to ignore for the reload path.
            log.debug("launchctl bootout failed (expected when plist not yet loaded)", e);
        }
        try {
            int exit = new ProcessBuilder("launchctl", "bootstrap", "gui/" + getUid(), PLIST_PATH.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            if (exit != 0) {
                new ProcessBuilder("launchctl", "load", PLIST_PATH.toString())
                        .redirectErrorStream(true)
                        .start()
                        .waitFor();
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private String uninstallLaunchd() throws IOException {
        if (!Files.exists(PLIST_PATH)) {
            return "Not installed (no plist found)";
        }
        try {
            new ProcessBuilder("launchctl", "bootout", "gui/" + getUid(), PLIST_PATH.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (Exception e) {
            // Fallback to legacy unload
            try {
                new ProcessBuilder("launchctl", "unload", PLIST_PATH.toString())
                        .redirectErrorStream(true)
                        .start()
                        .waitFor();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.debug("launchctl unload (legacy) interrupted", ie);
            } catch (IOException ie) {
                // legacy unload also failed — plist file removal below is still safe.
                log.debug("launchctl unload (legacy fallback) failed", ie);
            }
        }
        Files.deleteIfExists(PLIST_PATH);
        return "Uninstalled launchd agent: " + LABEL;
    }

    private String statusLaunchd() {
        if (!Files.exists(PLIST_PATH)) {
            return "Not installed";
        }
        try {
            var proc = new ProcessBuilder("launchctl", "print", "gui/" + getUid() + "/" + LABEL)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = proc.waitFor();
            if (exit == 0) {
                return "Installed and active\nPlist: " + PLIST_PATH;
            } else {
                return "Plist exists but service not loaded\nPlist: " + PLIST_PATH + "\nRun: /cron install to reload";
            }
        } catch (Exception e) {
            return "Plist exists: " + PLIST_PATH + " (status check failed)";
        }
    }

    // --- Linux crontab ---

    private String installCrontab(int intervalSeconds) throws IOException {
        int minutes = Math.max(1, intervalSeconds / 60);
        String cronExpr = minutes >= 60 ? "0 */" + (minutes / 60) + " * * *" : "*/" + minutes + " * * * *";
        String cronLine = cronExpr + " " + launcherScript + " --cron-tick >> "
                + Path.of(System.getProperty("user.home")).resolve(".campusclaw/agent/cron/cron-tick.log")
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
        try {
            var proc = new ProcessBuilder("crontab", "-l")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            proc.waitFor();
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    private void writeCrontab(String content) throws IOException {
        try {
            var tmpFile = Files.createTempFile("campusclaw-crontab-", ".tmp");
            Files.writeString(tmpFile, content);
            new ProcessBuilder("crontab", tmpFile.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            Files.deleteIfExists(tmpFile);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- Windows Task Scheduler ---

    private String installWindows(int intervalSeconds) throws IOException {
        Path logDir = Path.of(System.getProperty("user.home")).resolve(".campusclaw/agent/cron");
        Files.createDirectories(logDir);

        // schtasks /MINUTE supports 1-1439 minute intervals
        int minutes = Math.max(1, intervalSeconds / 60);

        // Build the command: campusclaw.bat --cron-tick >> log 2>&1
        String command =
                "cmd /c \"" + launcherScript + " --cron-tick >> " + logDir.resolve("cron-tick.log") + " 2>&1\"";

        try {
            // Delete existing task first (ignore errors if not found)
            new ProcessBuilder("schtasks", "/Delete", "/TN", TASK_NAME, "/F")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("schtasks /Delete interrupted", e);
        } catch (IOException e) {
            // pre-existing task may not exist — recreate below regardless
            log.debug("schtasks /Delete failed (expected when task not yet registered)", e);
        }

        try {
            var proc = new ProcessBuilder(
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
            int exit = proc.waitFor();
            if (exit != 0) {
                return "Failed to create task: " + output;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return "Installed Windows scheduled task: " + TASK_NAME
                + "\nInterval: every " + minutes + " minute(s)"
                + "\nCommand: " + launcherScript + " --cron-tick"
                + "\nLogs: " + logDir + "\\cron-tick.log";
    }

    private String uninstallWindows() throws IOException {
        try {
            var proc = new ProcessBuilder("schtasks", "/Delete", "/TN", TASK_NAME, "/F")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = proc.waitFor();
            if (exit != 0) {
                return "Not installed or failed to remove: " + output.trim();
            }
            return "Uninstalled Windows scheduled task: " + TASK_NAME;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Interrupted while removing task";
        }
    }

    private String statusWindows() {
        try {
            var proc = new ProcessBuilder("schtasks", "/Query", "/TN", TASK_NAME, "/FO", "LIST")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exit = proc.waitFor();
            if (exit == 0) {
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
        } catch (Exception e) {
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
        try {
            var proc = new ProcessBuilder("id", "-u").start();
            String uid = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            proc.waitFor();
            return uid;
        } catch (Exception e) {
            return "501"; // fallback
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
