package com.huawei.hicampus.mate.matecampusclaw.tui.image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.tui.Component;

/**
 * TUI component that displays an image inline in the terminal.
 * Falls back to a text placeholder if the terminal doesn't support images.
 */
public class ImageComponent implements Component {
    private byte[] imageData;
    private String altText;
    private int maxWidthCells = 80;
    private int maxHeightCells = 24;
    private List<String> cachedRender;

    public ImageComponent() {}

    public ImageComponent(byte[] imageData, String altText) {
        this.imageData = imageData;
        this.altText = altText;
    }

    /** Load image from file path. */
    public boolean loadFromFile(Path path) {
        try {
            this.imageData = Files.readAllBytes(path);
            this.altText = path.getFileName().toString();
            invalidate();
            return true;
        } catch (IOException e) {
            this.altText = "[Failed to load: " + path + "]";
            invalidate();
            return false;
        }
    }

    /** Set image from raw bytes. */
    public void setImageData(byte[] data, String alt) {
        this.imageData = data;
        this.altText = alt;
        invalidate();
    }

    /** Set maximum display dimensions in terminal cells. */
    public void setMaxDimensions(int widthCells, int heightCells) {
        this.maxWidthCells = widthCells;
        this.maxHeightCells = heightCells;
        invalidate();
    }

    @Override
    public List<String> render(int width) {
        if (cachedRender != null) return cachedRender;

        int displayWidth = Math.min(width, maxWidthCells);

        if (imageData != null && TerminalImageProtocol.isSupported()) {
            String escape = TerminalImageProtocol.renderImage(imageData, displayWidth, maxHeightCells);
            if (escape != null) {
                cachedRender = List.of(escape);
                return cachedRender;
            }
        }

        // Fallback: text placeholder
        String alt = altText != null ? altText : "image";
        int boxWidth = Math.max(Math.min(displayWidth - 2, alt.length() + 4), 2);
        String border = "┌" + "─".repeat(boxWidth) + "┐";
        String middle = "│ \033[2m📷 " + truncate(alt, boxWidth - 4) + "\033[0m │";
        String bottom = "└" + "─".repeat(boxWidth) + "┘";
        cachedRender = List.of(border, middle, bottom);
        return cachedRender;
    }

    @Override
    public void invalidate() {
        cachedRender = null;
    }

    private static String truncate(String s, int max) {
        if (max <= 0) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
}
