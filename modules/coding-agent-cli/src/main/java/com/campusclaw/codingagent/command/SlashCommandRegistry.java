/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.command;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * In-process registry of {@link SlashCommand} instances keyed by command name. Provides
 * registration, lookup, enumeration, and a parser that dispatches a {@code /name args...}
 * input line to the matching command handler.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
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

    /**
     * Parse and execute a slash command string like {@code /model gpt-4o}.
     *
     * @param input the raw slash-command string entered by the user
     * @param context execution context passed to the command handler
     * @return {@code true} if a matching command was found and executed;
     *         {@code false} when no matching registered command exists
     */
    public boolean execute(String input, SlashCommandContext context) {
        if (!input.startsWith("/")) {
            return false;
        }
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
