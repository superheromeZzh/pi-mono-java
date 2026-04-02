package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.tui;

import java.util.*;
import java.util.function.Consumer;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.tui.Component;
import com.huawei.hicampus.mate.matecampusclaw.tui.Focusable;
import com.huawei.hicampus.mate.matecampusclaw.tui.component.Input;
import com.huawei.hicampus.mate.matecampusclaw.tui.component.SelectList;

/**
 * Model selector overlay with fuzzy search input.
 * Shows a search box and a scrollable list of models.
 * Matches campusclaw's Ctrl+L model selector behavior.
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

        // Collect all models sorted by provider + id
        this.allModels = new ArrayList<>(modelRegistry.getAllModels());
        allModels.sort(Comparator.comparing((Model m) -> m.provider().value())
                .thenComparing(Model::id));

        // Search input
        this.searchInput = new Input();
        searchInput.setPlaceholder("Search models...");

        // Select list
        this.selectList = new SelectList<>(allModels, this::renderModelItem, 15);
        selectList.setOnSelect(model -> {
            if (onSelect != null) onSelect.accept(model);
        });
        selectList.setOnCancel(() -> {
            if (onCancel != null) onCancel.run();
        });

        // Pre-select current model
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
        String marker = ModelRegistry.modelsAreEqual(model, currentModel) ? "● " : "  ";
        String provider = model.provider().value();
        String thinking = model.reasoning() ? " ✦" : "";
        return marker + String.format("%-14s %s%s", provider, model.id(), thinking);
    }

    private void filterModels(String query) {
        if (query == null || query.isBlank()) {
            selectList.setItems(allModels);
            return;
        }
        String lower = query.toLowerCase();
        var filtered = allModels.stream()
                .filter(m -> m.id().toLowerCase().contains(lower)
                        || m.name().toLowerCase().contains(lower)
                        || m.provider().value().toLowerCase().contains(lower))
                .toList();
        selectList.setItems(filtered);
    }

    @Override
    public void handleInput(String data) {
        // Escape / Ctrl+C → cancel
        if ("\033".equals(data) || "\003".equals(data)) {
            if (onCancel != null) onCancel.run();
            return;
        }
        // Enter → select
        if ("\r".equals(data) || "\n".equals(data)) {
            var selected = selectList.getSelectedItem();
            if (selected != null && onSelect != null) {
                onSelect.accept(selected);
            }
            return;
        }
        // Arrow keys → route to select list
        if (data.startsWith("\033[A") || data.startsWith("\033[B")) {
            selectList.handleInput(data);
            return;
        }
        // Everything else → route to search input, then re-filter
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

        // Title
        lines.add("");
        lines.add(" " + ANSI_BOLD + ANSI_ACCENT + "Select Model" + ANSI_RESET
                + ANSI_DIM + "  (↑↓ navigate, Enter select, Esc cancel)" + ANSI_RESET);
        lines.add("");

        // Search input
        var inputLines = searchInput.render(Math.max(1, width - 2));
        for (String line : inputLines) {
            lines.add(" " + line);
        }
        lines.add("");

        // Model list
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
