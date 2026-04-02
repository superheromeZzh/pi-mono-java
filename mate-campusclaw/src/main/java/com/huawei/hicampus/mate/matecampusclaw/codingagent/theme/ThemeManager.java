package com.huawei.hicampus.mate.matecampusclaw.codingagent.theme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages themes for the coding agent. Supports built-in themes and custom themes
 * loaded from ~/.campusclaw/agent/themes/.
 */
@Service
public class ThemeManager {
    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Theme> themes = new ConcurrentHashMap<>();
    private volatile Theme activeTheme;

    public ThemeManager() {
        registerBuiltins();
        activeTheme = themes.get("default");
    }

    /** Get the active theme. */
    public Theme getActiveTheme() {
        return activeTheme;
    }

    /** Set active theme by name. */
    public boolean setActiveTheme(String name) {
        Theme theme = themes.get(name);
        if (theme == null) return false;
        activeTheme = theme;
        return true;
    }

    /** Get all available theme names. */
    public List<String> getThemeNames() {
        return List.copyOf(themes.keySet());
    }

    /** Get a theme by name. */
    public Optional<Theme> getTheme(String name) {
        return Optional.ofNullable(themes.get(name));
    }

    /** Register a custom theme. */
    public void register(Theme theme) {
        themes.put(theme.name(), theme);
    }

    /** Load custom themes from a directory. */
    public void loadFromDirectory(Path dir) {
        if (!Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                .forEach(p -> {
                    try {
                        Theme theme = MAPPER.readValue(p.toFile(), Theme.class);
                        register(theme);
                        log.debug("Loaded theme: {}", theme.name());
                    } catch (IOException e) {
                        log.warn("Failed to load theme from {}: {}", p, e.getMessage());
                    }
                });
        } catch (IOException e) {
            log.warn("Failed to list theme directory: {}", dir, e);
        }
    }

    private void registerBuiltins() {
        register(new Theme("default", Map.ofEntries(
            Map.entry(Theme.PRIMARY, "cyan"),
            Map.entry(Theme.SECONDARY, "blue"),
            Map.entry(Theme.ACCENT, "magenta"),
            Map.entry(Theme.ERROR, "red"),
            Map.entry(Theme.WARNING, "yellow"),
            Map.entry(Theme.SUCCESS, "green"),
            Map.entry(Theme.INFO, "cyan"),
            Map.entry(Theme.DIM, "bright_black"),
            Map.entry(Theme.BORDER, "bright_black"),
            Map.entry(Theme.TEXT, "white"),
            Map.entry(Theme.TEXT_DIM, "bright_black"),
            Map.entry(Theme.PROMPT_USER, "green"),
            Map.entry(Theme.PROMPT_ASSISTANT, "cyan"),
            Map.entry(Theme.PROMPT_SYSTEM, "yellow"),
            Map.entry(Theme.DIFF_ADDED, "green"),
            Map.entry(Theme.DIFF_REMOVED, "red"),
            Map.entry(Theme.DIFF_MODIFIED, "yellow"),
            Map.entry(Theme.DIFF_CONTEXT, "bright_black"),
            Map.entry(Theme.TOOL_NAME, "magenta"),
            Map.entry(Theme.TOOL_INPUT, "cyan"),
            Map.entry(Theme.TOOL_OUTPUT, "white"),
            Map.entry(Theme.THINKING_BORDER, "bright_black"),
            Map.entry(Theme.THINKING_TEXT, "bright_black"),
            Map.entry(Theme.STATUS_BAR, "blue"),
            Map.entry(Theme.STATUS_TEXT, "white"),
            Map.entry(Theme.FOOTER_TEXT, "bright_black"),
            Map.entry(Theme.FOOTER_KEY, "cyan"),
            Map.entry(Theme.SPINNER, "cyan"),
            Map.entry(Theme.SELECTION, "blue"),
            Map.entry(Theme.CURSOR, "white")
        ), "Default theme"));

        register(new Theme("dark", Map.ofEntries(
            Map.entry(Theme.PRIMARY, "#61AFEF"),
            Map.entry(Theme.SECONDARY, "#56B6C2"),
            Map.entry(Theme.ACCENT, "#C678DD"),
            Map.entry(Theme.ERROR, "#E06C75"),
            Map.entry(Theme.WARNING, "#E5C07B"),
            Map.entry(Theme.SUCCESS, "#98C379"),
            Map.entry(Theme.INFO, "#61AFEF"),
            Map.entry(Theme.DIM, "#5C6370"),
            Map.entry(Theme.BORDER, "#3E4452"),
            Map.entry(Theme.TEXT, "#ABB2BF"),
            Map.entry(Theme.TEXT_DIM, "#5C6370"),
            Map.entry(Theme.PROMPT_USER, "#98C379"),
            Map.entry(Theme.PROMPT_ASSISTANT, "#61AFEF"),
            Map.entry(Theme.PROMPT_SYSTEM, "#E5C07B"),
            Map.entry(Theme.DIFF_ADDED, "#98C379"),
            Map.entry(Theme.DIFF_REMOVED, "#E06C75"),
            Map.entry(Theme.DIFF_MODIFIED, "#E5C07B"),
            Map.entry(Theme.DIFF_CONTEXT, "#5C6370"),
            Map.entry(Theme.TOOL_NAME, "#C678DD"),
            Map.entry(Theme.TOOL_INPUT, "#56B6C2"),
            Map.entry(Theme.TOOL_OUTPUT, "#ABB2BF"),
            Map.entry(Theme.THINKING_BORDER, "#3E4452"),
            Map.entry(Theme.THINKING_TEXT, "#5C6370"),
            Map.entry(Theme.STATUS_BAR, "#282C34"),
            Map.entry(Theme.STATUS_TEXT, "#ABB2BF"),
            Map.entry(Theme.FOOTER_TEXT, "#5C6370"),
            Map.entry(Theme.FOOTER_KEY, "#61AFEF"),
            Map.entry(Theme.SPINNER, "#61AFEF"),
            Map.entry(Theme.SELECTION, "#3E4452"),
            Map.entry(Theme.CURSOR, "#528BFF")
        ), "One Dark inspired theme"));

        register(new Theme("light", Map.ofEntries(
            Map.entry(Theme.PRIMARY, "#4078F2"),
            Map.entry(Theme.SECONDARY, "#0184BC"),
            Map.entry(Theme.ACCENT, "#A626A4"),
            Map.entry(Theme.ERROR, "#E45649"),
            Map.entry(Theme.WARNING, "#986801"),
            Map.entry(Theme.SUCCESS, "#50A14F"),
            Map.entry(Theme.INFO, "#4078F2"),
            Map.entry(Theme.DIM, "#A0A1A7"),
            Map.entry(Theme.BORDER, "#D3D3D3"),
            Map.entry(Theme.TEXT, "#383A42"),
            Map.entry(Theme.TEXT_DIM, "#A0A1A7"),
            Map.entry(Theme.PROMPT_USER, "#50A14F"),
            Map.entry(Theme.PROMPT_ASSISTANT, "#4078F2"),
            Map.entry(Theme.PROMPT_SYSTEM, "#986801"),
            Map.entry(Theme.DIFF_ADDED, "#50A14F"),
            Map.entry(Theme.DIFF_REMOVED, "#E45649"),
            Map.entry(Theme.DIFF_MODIFIED, "#986801"),
            Map.entry(Theme.DIFF_CONTEXT, "#A0A1A7"),
            Map.entry(Theme.TOOL_NAME, "#A626A4"),
            Map.entry(Theme.TOOL_INPUT, "#0184BC"),
            Map.entry(Theme.TOOL_OUTPUT, "#383A42"),
            Map.entry(Theme.THINKING_BORDER, "#D3D3D3"),
            Map.entry(Theme.THINKING_TEXT, "#A0A1A7"),
            Map.entry(Theme.STATUS_BAR, "#FAFAFA"),
            Map.entry(Theme.STATUS_TEXT, "#383A42"),
            Map.entry(Theme.FOOTER_TEXT, "#A0A1A7"),
            Map.entry(Theme.FOOTER_KEY, "#4078F2"),
            Map.entry(Theme.SPINNER, "#4078F2"),
            Map.entry(Theme.SELECTION, "#BFCEFF"),
            Map.entry(Theme.CURSOR, "#526FFF")
        ), "Light theme"));
    }
}
