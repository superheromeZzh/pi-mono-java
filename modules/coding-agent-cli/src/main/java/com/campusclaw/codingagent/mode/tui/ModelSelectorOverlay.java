package com.campusclaw.codingagent.mode.tui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.Model;
import com.campusclaw.tui.Component;
import com.campusclaw.tui.Focusable;
import com.campusclaw.tui.component.FuzzyMatcher;
import com.campusclaw.tui.component.Input;
import com.campusclaw.tui.component.SelectList;

/**
 * Model selector overlay with fuzzy search, opencode-style row formatting,
 * and provider grouping.
 */
public class ModelSelectorOverlay implements Component, Focusable {

    private static final String ANSI_DIM = "\033[38;2;102;102;102m";
    private static final String ANSI_ACCENT = "\033[38;2;138;190;183m";
    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BOLD = "\033[1m";

    private final Input searchInput;
    private final SelectList<Model> selectList;
    private final List<Model> allModels;
    private final Model currentModel;
    private Consumer<Model> onSelect;
    private Runnable onCancel;
    private boolean focused;

    public ModelSelectorOverlay(ModelRegistry modelRegistry, Model currentModel) {
        this.currentModel = currentModel;

        this.allModels = new ArrayList<>(modelRegistry.getAllModels());
        allModels.sort(Comparator.comparing((Model m) -> m.provider().value())
                .thenComparing(Model::id));

        this.searchInput = new Input();
        searchInput.setPlaceholder("Search models...");

        this.selectList = new SelectList<>(allModels, this::renderModelItem, 15);
        selectList.setOnSelect(model -> {
            if (onSelect != null) { onSelect.accept(model); }
        });
        selectList.setOnCancel(() -> {
            if (onCancel != null) { onCancel.run(); }
        });

        if (currentModel != null) {
            for (int i = 0; i < allModels.size(); i++) {
                if (ModelRegistry.modelsAreEqual(allModels.get(i), currentModel)) {
                    selectList.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    public void setOnSelect(Consumer<Model> onSelect) {
        this.onSelect = onSelect;
    }

    public void setOnCancel(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    private String renderModelItem(Model model) {
        String marker = ModelRegistry.modelsAreEqual(model, currentModel)
                ? ANSI_ACCENT + "● " + ANSI_RESET
                : "  ";
        String provider = padRight(model.provider().value(), 14);
        String name = padRight(model.id(), 30);
        String ctx = padLeft(formatContext(model.contextWindow()), 6);
        String cost = formatCost(model);
        String thinking = model.reasoning() ? ANSI_ACCENT + " ✦" + ANSI_RESET : "  ";
        return marker + name + " " + ANSI_DIM + provider + ANSI_RESET
                + " " + ANSI_DIM + ctx + ANSI_RESET
                + " " + ANSI_DIM + padLeft(cost, 14) + ANSI_RESET
                + thinking;
    }

    private static String formatContext(int tokens) {
        if (tokens <= 0) { return "—"; }
        if (tokens >= 1_000_000) { return (tokens / 1_000_000) + "M"; }
        if (tokens >= 1_000) { return (tokens / 1_000) + "K"; }
        return String.valueOf(tokens);
    }

    private static String formatCost(Model model) {
        var c = model.cost();
        if (c == null) { return ""; }
        if (c.input() == 0 && c.output() == 0) { return "free"; }
        return "$" + trimZero(c.input()) + "/$" + trimZero(c.output());
    }

    private static String trimZero(double v) {
        if (v == Math.floor(v)) { return String.valueOf((long) v); }
        return String.format("%.2f", v);
    }

    private static String padRight(String s, int width) {
        if (s == null) { s = ""; }
        if (s.length() >= width) { return s.substring(0, width); }
        return s + " ".repeat(width - s.length());
    }

    private static String padLeft(String s, int width) {
        if (s == null) { s = ""; }
        if (s.length() >= width) { return s.substring(s.length() - width); }
        return " ".repeat(width - s.length()) + s;
    }

    private void filterModels(String query) {
        if (query == null || query.isBlank()) {
            selectList.setItems(allModels);
            return;
        }
        var scored = new ArrayList<ScoredModel>();
        for (Model m : allModels) {
            int s = scoreModel(query, m);
            if (s >= 0) { scored.add(new ScoredModel(m, s)); }
        }
        scored.sort(Comparator.comparingInt(ScoredModel::score).reversed());
        selectList.setItems(scored.stream().map(ScoredModel::model).toList());
    }

    /** Best fuzzy score across id / name / provider. */
    private static int scoreModel(String query, Model m) {
        int s1 = FuzzyMatcher.score(query, m.id());
        int s2 = FuzzyMatcher.score(query, m.name());
        int s3 = FuzzyMatcher.score(query, m.provider().value());
        return Math.max(s1, Math.max(s2, s3));
    }

    private record ScoredModel(Model model, int score) {}

    @Override
    public void handleInput(String data) {
        if ("\033".equals(data) || "\003".equals(data)) {
            if (onCancel != null) { onCancel.run(); }
            return;
        }
        if ("\r".equals(data) || "\n".equals(data)) {
            var selected = selectList.getSelectedItem();
            if (selected != null && onSelect != null) {
                onSelect.accept(selected);
            }
            return;
        }
        if (data.startsWith("\033[A") || data.startsWith("\033[B")) {
            selectList.handleInput(data);
            return;
        }
        searchInput.handleInput(data);
        filterModels(searchInput.getValue());
    }

    @Override
    public void invalidate() {
        searchInput.invalidate();
        selectList.invalidate();
    }

    @Override
    public List<String> render(int width) {
        var lines = new ArrayList<String>();

        lines.add("");
        lines.add(" " + ANSI_BOLD + ANSI_ACCENT + "Select Model" + ANSI_RESET
                + ANSI_DIM + "  (↑↓ navigate, Enter select, Esc cancel)" + ANSI_RESET);
        lines.add("");

        var inputLines = searchInput.render(Math.max(1, width - 2));
        for (String line : inputLines) {
            lines.add(" " + line);
        }
        lines.add("");

        var listLines = selectList.render(Math.max(1, width - 2));
        for (String line : listLines) {
            lines.add(" " + line);
        }
        lines.add("");

        return lines;
    }

    @Override
    public boolean isFocused() { return focused; }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        searchInput.setFocused(focused);
    }
}
