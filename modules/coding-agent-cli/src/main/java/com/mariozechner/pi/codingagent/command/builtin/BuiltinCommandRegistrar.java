package com.mariozechner.pi.codingagent.command.builtin;

import com.mariozechner.pi.codingagent.command.SlashCommandRegistry;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class BuiltinCommandRegistrar {

    private final SlashCommandRegistry registry;

    public BuiltinCommandRegistrar(SlashCommandRegistry registry) {
        this.registry = registry;
    }

    @PostConstruct
    void registerBuiltins() {
        registry.register(new HelpCommand(registry));
        registry.register(new ModelCommand());
        registry.register(new CompactCommand());
        registry.register(new NewCommand());
        registry.register(new QuitCommand());
        registry.register(new SettingsCommand());
        registry.register(new ExportCommand());
        registry.register(new CopyCommand());
    }
}
