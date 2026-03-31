package com.campusclaw.tui.image;

import java.util.Base64;

/**
 * Terminal image display protocols (Kitty Graphics Protocol, iTerm2 inline images).
 * Generates escape sequences to display images inline in supported terminals.
 */
public final class TerminalImageProtocol {
    private TerminalImageProtocol() {}

    public enum Protocol { KITTY, ITERM2, SIXEL, NONE }

    /** Detect the best available image protocol for the current terminal. */
    public static Protocol detect() {
        String term = System.getenv("TERM");
        String termProgram = System.getenv("TERM_PROGRAM");
        String kittyId = System.getenv("KITTY_WINDOW_ID");

        if (kittyId != null) return Protocol.KITTY;
        if ("iTerm.app".equals(termProgram) || "iTerm2".equals(termProgram)) return Protocol.ITERM2;
        if ("WezTerm".equals(termProgram)) return Protocol.ITERM2; // WezTerm supports iTerm2 protocol
        if (term != null && term.contains("sixel")) return Protocol.SIXEL;
        return Protocol.NONE;
    }

    /**
     * Generate Kitty Graphics Protocol escape sequence.
     * Format: ESC_G <key=value,...> ; <base64-data> ESC \
     *
     * @param imageBytes PNG image data
     * @param width      display width in cells (0 = auto)
     * @param height     display height in cells (0 = auto)
     */
    public static String kittyImage(byte[] imageBytes, int width, int height) {
        String b64 = Base64.getEncoder().encodeToString(imageBytes);
        StringBuilder sb = new StringBuilder();

        // Kitty splits large payloads across chunks
        int chunkSize = 4096;
        int offset = 0;
        boolean first = true;

        while (offset < b64.length()) {
            int end = Math.min(offset + chunkSize, b64.length());
            String chunk = b64.substring(offset, end);
            boolean more = end < b64.length();

            sb.append("\033_G");
            if (first) {
                sb.append("a=T,f=100"); // action=transmit, format=PNG
                if (width > 0) sb.append(",c=").append(width);
                if (height > 0) sb.append(",r=").append(height);
                first = false;
            }
            sb.append(more ? ",m=1" : ",m=0");
            sb.append(";").append(chunk);
            sb.append("\033\\");

            offset = end;
        }
        return sb.toString();
    }

    /**
     * Generate iTerm2 inline image escape sequence.
     * Format: ESC ] 1337 ; File=[args] : base64-data BEL
     *
     * @param imageBytes  image data (any format supported by iTerm2)
     * @param width       display width (e.g. "auto", "50px", "10")
     * @param height      display height (e.g. "auto", "50px", "5")
     * @param preserveAR  preserve aspect ratio
     */
    public static String iterm2Image(byte[] imageBytes, String width, String height, boolean preserveAR) {
        String b64 = Base64.getEncoder().encodeToString(imageBytes);
        StringBuilder sb = new StringBuilder();
        sb.append("\033]1337;File=inline=1");
        sb.append(";size=").append(imageBytes.length);
        if (width != null) sb.append(";width=").append(width);
        if (height != null) sb.append(";height=").append(height);
        sb.append(";preserveAspectRatio=").append(preserveAR ? "1" : "0");
        sb.append(":").append(b64);
        sb.append("\007"); // BEL terminator
        return sb.toString();
    }

    /**
     * Generate image display escape sequence using best available protocol.
     *
     * @param imageBytes PNG image data
     * @param widthCells  display width in terminal cells
     * @param heightCells display height in terminal cells
     * @return escape sequence string, or null if no protocol available
     */
    public static String renderImage(byte[] imageBytes, int widthCells, int heightCells) {
        Protocol protocol = detect();
        return switch (protocol) {
            case KITTY -> kittyImage(imageBytes, widthCells, heightCells);
            case ITERM2 -> iterm2Image(imageBytes,
                widthCells > 0 ? String.valueOf(widthCells) : "auto",
                heightCells > 0 ? String.valueOf(heightCells) : "auto",
                true);
            case SIXEL, NONE -> null;
        };
    }

    /** Check if the current terminal supports inline images. */
    public static boolean isSupported() {
        return detect() != Protocol.NONE;
    }
}
