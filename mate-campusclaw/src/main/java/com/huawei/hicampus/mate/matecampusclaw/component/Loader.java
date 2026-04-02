package com.huawei.hicampus.mate.matecampusclaw.tui.component;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;

/**
 * A terminal loading animation component.
 *
 * <p>Displays an animated spinner with an optional message.
 * The spinner cycles through a set of frames on each render.
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
     */
    public Loader(String message) {
        this(message, Style.SPINNER);
    }

    /**
     * Creates a loader with the given message and animation style.
     */
    public Loader(String message, Style style) {
        this.message = message != null ? message : "";
        this.style = style;
        this.frameIndex = 0;
    }

    /**
     * Updates the loader message.
     */
    public void setMessage(String message) {
        this.message = message != null ? message : "";
    }

    /**
     * Returns the current message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets whether the loader is visible.
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
        if (!visible) return List.of();

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
