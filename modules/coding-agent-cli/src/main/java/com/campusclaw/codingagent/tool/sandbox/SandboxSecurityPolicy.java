/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.sandbox;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 沙箱安全策略
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Slf4j
@Component
public class SandboxSecurityPolicy {

    // 危险命令模式
    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("rm\\s+-rf\\s+/"),
            Pattern.compile("mkfs\\."),
            Pattern.compile("dd\\s+if=/dev/zero"),
            Pattern.compile(":\\(\\)\\{\\s*:|:&\\s*\\};:"), // fork bomb
            Pattern.compile("curl\\s+[^|]*\\|\\s*sh"),
            Pattern.compile("wget\\s+[^|]*\\|\\s*sh"),
            Pattern.compile("eval\\s+.*\\$\\(.*\\)"),
            Pattern.compile(">\\s*/etc/"),
            Pattern.compile("chmod\\s+777\\s+/"),
            Pattern.compile("chmod\\s+-R\\s+777"));

    // 受保护路径
    private static final List<Pattern> PROTECTED_PATHS = List.of(
            Pattern.compile("^/etc/.*"),
            Pattern.compile("^/usr/.*"),
            Pattern.compile("^/bin/.*"),
            Pattern.compile("^/sbin/.*"),
            Pattern.compile("^/lib.*"),
            Pattern.compile("^/sys/.*"),
            Pattern.compile("^/proc/.*"),
            Pattern.compile("^/dev/.*"),
            Pattern.compile("^/root/.*"),
            Pattern.compile("\\.\\./.*"),
            Pattern.compile("^/.*\\.\\./.*") // 路径遍历
            );

    /**
     * 检查命令是否危险
     *
     * @param command the command
     * @return the result
     */
    public boolean isDangerousCommand(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        String lower = command.toLowerCase(Locale.ROOT);
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(lower).find()) {
                log.warn("Dangerous command detected: {}", command);
                return true;
            }
        }
        return false;
    }

    /**
     * 检查路径是否受保护
     *
     * @param path the path
     * @return the result
     */
    public boolean isProtectedPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String normalized = normalizePath(path);
        for (Pattern pattern : PROTECTED_PATHS) {
            if (pattern.matcher(normalized).matches()) {
                log.warn("Protected path access attempted: {}", path);
                return true;
            }
        }
        return false;
    }

    /**
     * 验证命令是否允许执行
     *
     * @param command the command
     *
     * @throws SecurityException if the operation fails
     */
    public void validateCommand(String command) throws SecurityException {
        if (isDangerousCommand(command)) {
            throw new SecurityException("Command blocked by security policy: " + command);
        }
    }

    /**
     * 验证路径访问是否允许
     *
     * @param path the path
     *
     * @throws SecurityException if the operation fails
     */
    public void validatePath(String path) throws SecurityException {
        if (isProtectedPath(path)) {
            throw new SecurityException("Access to protected path blocked: " + path);
        }
    }

    /**
     * 标准化路径
     *
     * @param path the path
     * @return the result
     */
    private String normalizePath(String path) {
        return path.replaceAll("/+", "/").replaceAll("/\\./", "/").replaceAll("/+$", "");
    }

    /**
     * 安全命令列表
     *
     * @param command the command
     * @return the result
     */
    public boolean isSafeCommand(String command) {
        if (command == null || command.isEmpty()) {
            return false;
        }
        String base = command.trim().split("\\s+")[0].toLowerCase(Locale.ROOT);
        return Set.of(
                        "cat", "head", "tail", "grep", "awk", "sed", "wc", "ls", "pwd", "echo", "sort", "uniq", "find",
                        "which", "whoami", "id", "uname", "date", "git", "diff", "patch")
                .contains(base);
    }
}
