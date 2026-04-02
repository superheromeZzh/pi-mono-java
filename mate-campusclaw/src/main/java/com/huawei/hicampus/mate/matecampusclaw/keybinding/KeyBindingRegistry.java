package com.huawei.hicampus.mate.matecampusclaw.codingagent.keybinding;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class KeyBindingRegistry {
    private static final Logger log = LoggerFactory.getLogger(KeyBindingRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path KEYBINDINGS_FILE = com.huawei.hicampus.mate.matecampusclaw.codingagent.config.AppPaths.KEYBINDINGS_FILE;

    private final Map<String, KeyBinding> bindings = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        registerDefaults();
        loadCustomBindings();
    }

    private void registerDefaults() {
        register(new KeyBinding("app.interrupt", "escape", "Interrupt"));
        register(new KeyBinding("app.clear", "ctrl+c", "Clear"));
        register(new KeyBinding("app.exit", "ctrl+d", "Exit"));
        register(new KeyBinding("app.suspend", "ctrl+z", "Suspend"));
        register(new KeyBinding("app.thinking.cycle", "shift+tab", "Cycle thinking level"));
        register(new KeyBinding("app.model.cycleForward", "ctrl+p", "Next model"));
        register(new KeyBinding("app.model.cycleBackward", "shift+ctrl+p", "Previous model"));
        register(new KeyBinding("app.model.select", "ctrl+l", "Select model"));
        register(new KeyBinding("app.tools.expand", "ctrl+o", "Expand tools"));
        register(new KeyBinding("app.thinking.toggle", "ctrl+t", "Toggle thinking visibility"));
        register(new KeyBinding("app.editor.external", "ctrl+g", "External editor"));
        register(new KeyBinding("app.message.followUp", "alt+enter", "Follow-up message"));
        register(new KeyBinding("app.clipboard.pasteImage", "ctrl+v", "Paste image"));
    }

    private void loadCustomBindings() {
        if (!Files.exists(KEYBINDINGS_FILE)) return;
        try {
            Map<String, String> custom = MAPPER.readValue(
                Files.readString(KEYBINDINGS_FILE), new TypeReference<>() {});
            custom.forEach((action, key) -> {
                var existing = bindings.get(action);
                if (existing != null) {
                    bindings.put(action, new KeyBinding(action, key, existing.description()));
                }
            });
            log.info("Loaded {} custom keybinding(s)", custom.size());
        } catch (Exception e) {
            log.warn("Failed to load custom keybindings", e);
        }
    }

    public void register(KeyBinding binding) {
        bindings.put(binding.action(), binding);
    }

    public Optional<KeyBinding> get(String action) {
        return Optional.ofNullable(bindings.get(action));
    }

    public Optional<String> getKey(String action) {
        return get(action).map(KeyBinding::key);
    }

    /** Find the action bound to a given key combo. */
    public Optional<String> findAction(String key) {
        return bindings.values().stream()
            .filter(b -> b.key().equalsIgnoreCase(key))
            .map(KeyBinding::action)
            .findFirst();
    }

    public Collection<KeyBinding> getAll() {
        return Collections.unmodifiableCollection(bindings.values());
    }
}
