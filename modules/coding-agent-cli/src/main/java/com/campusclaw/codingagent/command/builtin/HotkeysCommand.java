package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.codingagent.command.SlashCommand;
import com.campusclaw.codingagent.command.SlashCommandContext;

/**
 * Displays keyboard shortcuts matching campusclaw TS /hotkeys command.
 */
public class HotkeysCommand implements SlashCommand {

    @Override
    public String name() {
        return "hotkeys";
    }

    @Override
    public String description() {
        return "Show all keyboard shortcuts";
    }

    @Override
    public void execute(SlashCommandContext context, String arguments) {
        context.output().println("Keyboard Shortcuts:");
        context.output().println("");
        context.output().println("  Enter           Submit message");
        context.output().println("  Shift+Enter     New line (or type \\ before Enter)");
        context.output().println("  Alt+Enter       Queue follow-up message");
        context.output().println("  ↑/↓             Navigate command history");
        context.output().println("  Ctrl+C          Interrupt execution / clear input");
        context.output().println("  Ctrl+D          Exit (empty input)");
        context.output().println("  Ctrl+A          Move to start of line");
        context.output().println("  Ctrl+E          Move to end of line");
        context.output().println("  Ctrl+K          Delete to end of line");
        context.output().println("  Ctrl+U          Delete to start of line");
        context.output().println("  Ctrl+W          Delete word backward");
        context.output().println("  Alt+D           Delete word forward");
        context.output().println("  Ctrl+Y          Yank (paste from kill ring)");
        context.output().println("  Ctrl+Z          Undo");
        context.output().println("");
        context.output().println("  !command        Run bash command");
        context.output().println("  !!command       Run bash command (excluded from context)");
        context.output().println("  /command        Run slash command");
    }
}
