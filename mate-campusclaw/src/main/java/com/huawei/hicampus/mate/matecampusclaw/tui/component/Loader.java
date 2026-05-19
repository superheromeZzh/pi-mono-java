/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;

/**
 * A terminal loading animation component.
 *
 * <p>Displays an animated spinner with an optional message.
 * The spinner cycles through a set of frames on each render.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class Loader implements Component {

    private static final String[] SPINNER_FRAMES = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
    private static final String[] DOTS_FRAMES = {"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"};

    /**
     * Animation style for the loader.
     */
    public enum Style {
        SPINNER(SPINNER_FRAMES),
        DOTS(DOTS_FRAMES);

        final String[] frames;

        Style(String[] frames) {
            this.frames = frames;
        }
    }

    private String message;
    private final Style style;
    private int frameIndex;
    private boolean visible = true;

    /**
     * Creates a loader with the given message and default spinner style.
     *
     * @param message text shown next to the spinner
     */
    public Loader(String message) {
        this(message, Style.SPINNER);
    }

    /**
     * Creates a loader with the given message and animation style.
     *
     * @param message text shown next to the spinner
     * @param style animation variant (spinner/dots/etc.)
     */
    public Loader(String message, Style style) {
        this.message = message != null ? message : "";
        this.style = style;
        this.frameIndex = 0;
    }

    /**
     * Updates the loader message.
     *
     * @param message new spinner message
     */
    public void setMessage(String message) {
        this.message = message != null ? message : "";
    }

    /**
     * Returns the current message.
     *
     * @return the current spinner message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets whether the loader is visible.
     *
     * @param visible {@code true} to render, {@code false} to hide
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    /**
     * Advances the animation by one frame.
     */
    public void tick() {
        frameIndex = (frameIndex + 1) % style.frames.length;
    }

    @Override
    public List<String> render(int width) {
        if (!visible) {
            return List.of();
        }

        tick();
        String frame = style.frames[frameIndex];
        String line = frame + " " + message;

        // Truncate to width
        if (line.length() > width) {
            line = line.substring(0, width - 1) + "…";
        }

        return List.of(line);
    }

    @Override
    public void invalidate() {
        frameIndex = 0;
    }
}
