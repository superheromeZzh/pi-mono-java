package com.campusclaw.tui.component;

/**
 * Border character sets for {@link Box}.
 */
public enum BorderStyle {

    SINGLE('┌', '┐', '└', '┘', '─', '│'),
    DOUBLE('╔', '╗', '╚', '╝', '═', '║'),
    ROUNDED('╭', '╮', '╰', '╯', '─', '│');

    final char topLeft;
    final char topRight;
    final char bottomLeft;
    final char bottomRight;
    final char horizontal;
    final char vertical;

    BorderStyle(char topLeft, char topRight, char bottomLeft, char bottomRight,
                char horizontal, char vertical) {
        this.topLeft = topLeft;
        this.topRight = topRight;
        this.bottomLeft = bottomLeft;
        this.bottomRight = bottomRight;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }
}
