package com.huawei.hicampus.mate.matecampusclaw.codingagent.command.builtin;

import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommandRegistry;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.compaction.Compactor;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.loop.LoopManager;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;

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
    private final CampusClawAiService piAiService;
    private final SettingsManager settingsManager;
    private final LoopManager loopManager;

    public BuiltinCommandRegistrar(SlashCommandRegistry registry, CampusClawAiService piAiService,
                                   SettingsManager settingsManager, LoopManager loopManager) {
        this.registry = registry;
        this.piAiService = piAiService;
        this.settingsManager = settingsManager;
        this.loopManager = loopManager;
    }

    @PostConstruct
    void registerBuiltins() {
        registry.register(new HelpCommand(registry));
        registry.register(new ModelCommand());
        registry.register(new CompactCommand(new Compactor(piAiService)));
        registry.register(new NewCommand());
        registry.register(new QuitCommand());
        registry.register(new SettingsCommand(settingsManager));
        registry.register(new ExportCommand());
        registry.register(new CopyCommand());
        registry.register(new HotkeysCommand());
        registry.register(new SessionCommand());
        registry.register(new NameCommand());
        registry.register(new ReloadCommand());
        registry.register(new DebugCommand());
        registry.register(new ChangelogCommand());
        registry.register(new ImportCommand());
        registry.register(new ResumeCommand());
        registry.register(new ForkCommand());
        registry.register(new ShareCommand());
        registry.register(new TreeCommand());
        registry.register(new ScopedModelsCommand());
        registry.register(new LoginCommand());
        registry.register(new LogoutCommand());
        registry.register(new LoopCommand(loopManager));
        registry.register(new CronCommand());
    }
}
