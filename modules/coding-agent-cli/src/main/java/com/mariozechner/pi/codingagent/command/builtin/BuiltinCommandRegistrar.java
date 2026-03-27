package com.mariozechner.pi.codingagent.command.builtin;

import com.mariozechner.pi.ai.PiAiService;
import com.mariozechner.pi.codingagent.command.SlashCommandRegistry;
import com.mariozechner.pi.codingagent.compaction.Compactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Lazy(false)
public class BuiltinCommandRegistrar {

    private static final Logger log = LoggerFactory.getLogger(BuiltinCommandRegistrar.class);

    private final SlashCommandRegistry registry;
    private final PiAiService piAiService;

    public BuiltinCommandRegistrar(SlashCommandRegistry registry, PiAiService piAiService) {
        this.registry = registry;
        this.piAiService = piAiService;
    }

    @PostConstruct
    void registerBuiltins() {
        log.info("Registering built-in slash commands");
        registry.register(new HelpCommand(registry));
        registry.register(new ModelCommand());
        registry.register(new CompactCommand(new Compactor(piAiService)));
        registry.register(new NewCommand());
        registry.register(new QuitCommand());
        registry.register(new SettingsCommand());
        registry.register(new ExportCommand());
        registry.register(new CopyCommand());
        registry.register(new HotkeysCommand());
        registry.register(new SessionCommand());
        registry.register(new NameCommand());
        registry.register(new ReloadCommand());
        registry.register(new DebugCommand());
    }
}
