package com.campusclaw.codingagent.command.builtin;

import com.campusclaw.ai.CampusClawAiService;
import com.campusclaw.codingagent.command.SlashCommandRegistry;
import com.campusclaw.codingagent.compaction.Compactor;
import com.campusclaw.codingagent.settings.SettingsManager;
import com.campusclaw.assistant.task.RecurringTaskHandler;
import com.campusclaw.assistant.task.TaskManager;
import com.campusclaw.assistant.task.TaskRepository;

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
    private final TaskManager taskManager;
    private final TaskRepository taskRepository;
    private final RecurringTaskHandler recurringTaskHandler;

    public BuiltinCommandRegistrar(SlashCommandRegistry registry, CampusClawAiService piAiService,
                                   SettingsManager settingsManager,
                                   TaskManager taskManager, TaskRepository taskRepository,
                                   RecurringTaskHandler recurringTaskHandler) {
        this.registry = registry;
        this.piAiService = piAiService;
        this.settingsManager = settingsManager;
        this.taskManager = taskManager;
        this.taskRepository = taskRepository;
        this.recurringTaskHandler = recurringTaskHandler;
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
        registry.register(new TaskCommand(taskManager, taskRepository, recurringTaskHandler));
    }
}
