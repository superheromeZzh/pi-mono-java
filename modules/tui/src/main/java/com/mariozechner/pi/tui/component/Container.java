package com.mariozechner.pi.tui.component;

import com.mariozechner.pi.tui.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container component — arranges children vertically.
 * Each child is rendered at the full available width and the results are concatenated.
 */
public class Container implements Component {

    private final List<Component> children = new ArrayList<>();

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
