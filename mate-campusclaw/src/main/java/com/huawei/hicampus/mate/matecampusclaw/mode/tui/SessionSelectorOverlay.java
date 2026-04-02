package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths;
import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;
import com.huawei.hicampus.mate.matecampusclaw.tui.component.SelectList;

/**
 * Session selector overlay for /resume.
 * Shows recent session files with metadata.
 */
public class SessionSelectorOverlay implements Component, Focusable {

    private static final String ANSI_DIM = "\033[38;2;102;102;102m";
    private static final String ANSI_ACCENT = "\033[38;2;138;190;183m";
    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BOLD = "\033[1m";

    public record SessionItem(String id, Path path, Instant modified, long sizeKb) {}

    private final SelectList<SessionItem> selectList;
    private Consumer<SessionItem> onSelect;
    private Runnable onCancel;
    private boolean focused;
    private final boolean empty;

    public SessionSelectorOverlay(String cwd) {
        var sessions = loadSessions(cwd);
        this.empty = sessions.isEmpty();

        this.selectList = new SelectList<>(sessions, this::renderItem, 15);
        selectList.setOnSelect(item -> {
            if (onSelect != null) onSelect.accept(item);
        });
        selectList.setOnCancel(() -> {
            if (onCancel != null) onCancel.run();
        });
    }

    public void setOnSelect(Consumer<SessionItem> onSelect) {
        this.onSelect = onSelect;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    public boolean isEmpty() {
        return empty;
    }

    private String renderItem(SessionItem item) {
        String time = item.modified.toString().substring(0, 16).replace("T", " ");
        return String.format("%-10s  %s  %dKB", item.id, time, item.sizeKb);
    }

    private List<SessionItem> loadSessions(String cwd) {
        String safePath = "--" + cwd.replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--";
        Path sessionDir = AppPaths.SESSIONS_DIR.resolve(safePath);

        if (!Files.isDirectory(sessionDir)) return List.of();

        try (var stream = Files.list(sessionDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".jsonl"))
                    .sorted(Comparator.comparingLong((Path p) -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (IOException e) { return 0; }
                    }).reversed())
                    .limit(20)
                    .map(p -> {
                        try {
                            String id = p.getFileName().toString().replace(".jsonl", "");
                            Instant modified = Files.getLastModifiedTime(p).toInstant();
                            long size = Files.size(p) / 1024;
                            return new SessionItem(id, p, modified, size);
                        } catch (IOException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public void handleInput(String data) {
        // Escape / Ctrl+C → cancel
        if ("\033".equals(data) || "\003".equals(data)) {
            if (onCancel != null) onCancel.run();
            return;
        }
        selectList.handleInput(data);
    }

    @Override
    public void invalidate() {
        selectList.invalidate();
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();
        lines.add("");
        lines.add(" " + ANSI_BOLD + ANSI_ACCENT + "Resume Session" + ANSI_RESET
                + ANSI_DIM + "  (↑↓ navigate, Enter select, Esc cancel)" + ANSI_RESET);
        lines.add("");

        if (empty) {
            lines.add(" " + ANSI_DIM + "No sessions found for current directory." + ANSI_RESET);
        } else {
            var listLines = selectList.render(Math.max(1, width - 2));
            for (String line : listLines) {
                lines.add(" " + line);
            }
        }
        lines.add("");
        return lines;
    }

    @Override
    public boolean isFocused() { return focused; }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }
}
