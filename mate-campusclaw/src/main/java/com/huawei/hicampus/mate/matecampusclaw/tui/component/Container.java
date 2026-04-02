package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;

/**
 * Container component — arranges children vertically.
 * Each child is rendered at the full available width and the results are concatenated.
 * Uses CopyOnWriteArrayList for thread-safe iteration during concurrent rendering.
 */
public class Container implements Component {

    private final List<Component> children = new CopyOnWriteArrayList<>();

    public void addChild(Component component) {
        children.add(component);
    }

    public void removeChild(Component component) {
        children.remove(component);
    }

    public void clear() {
        children.clear();
    }

    public List<Component> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public void invalidate() {
        for (Component child : children) {
            child.invalidate();
        }
    }

    @Override
    public List<String> render(int width) {
        List<String> lines = new ArrayList<>();
        for (Component child : children) {
            lines.addAll(child.render(width));
        }
        return lines;
    }
}
