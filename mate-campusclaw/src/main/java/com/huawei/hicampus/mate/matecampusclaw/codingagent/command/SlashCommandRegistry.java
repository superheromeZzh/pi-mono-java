package com.huawei.hicampus.mate.matecampusclaw.codingagent.command;

import java.util.*;

import org.springframework.stereotype.Service;

@Service
public class SlashCommandRegistry {
    private final Map<String, SlashCommand> commands = new LinkedHashMap<>();

    public void register(SlashCommand command) {
        commands.put(command.name(), command);
    }

    public Optional<SlashCommand> get(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    public Collection<SlashCommand> getAll() {
        return Collections.unmodifiableCollection(commands.values());
    }

    /** Parse and execute a slash command string like "/model gpt-4o".
     *  Returns false if no matching registered command is found. */
    public boolean execute(String input, SlashCommandContext context) {
        if (!input.startsWith("/")) return false;
        String stripped = input.substring(1).trim();
        int spaceIdx = stripped.indexOf(' ');
        String name = spaceIdx >= 0 ? stripped.substring(0, spaceIdx) : stripped;
        String args = spaceIdx >= 0 ? stripped.substring(spaceIdx + 1).trim() : "";
        var cmd = commands.get(name);
        if (cmd == null) {
            return false;
        }
        cmd.execute(context, args);
        return true;
    }
}
