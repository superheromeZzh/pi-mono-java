package com.huawei.hicampus.mate.matecampusclaw.codingagent.extension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.huawei.hicampus.mate.matecampusclaw.agent.event.AgentEventListener;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AfterToolCallHandler;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.BeforeToolCallHandler;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.command.SlashCommand;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registry for managing extensions that contribute tools, commands, and hooks.
 *
 * <p>Extensions are registered by id and can be queried for their contributions
 * to various extension points.
 */
public class ExtensionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ExtensionRegistry.class);

    private final Map<String, Extension> extensions = new LinkedHashMap<>();

    /**
     * Registers an extension. Replaces any existing extension with the same id.
     */
    public void register(Extension extension) {
        var prev = extensions.put(extension.id(), extension);
        if (prev != null) {
            prev.onUnload();
            log.debug("Replaced extension: {}", extension.id());
        }
        extension.onLoad();
        log.info("Registered extension: {} ({})", extension.name(), extension.id());
    }

    /**
     * Unregisters an extension by id.
     */
    public void unregister(String extensionId) {
        var ext = extensions.remove(extensionId);
        if (ext != null) {
            ext.onUnload();
            log.info("Unregistered extension: {}", extensionId);
        }
    }

    /**
     * Returns an extension by id.
     */
    public Optional<Extension> get(String extensionId) {
        return Optional.ofNullable(extensions.get(extensionId));
    }

    /**
     * Returns all registered extensions.
     */
    public List<Extension> getAll() {
        return List.copyOf(extensions.values());
    }

    /**
     * Returns all tools contributed by all extensions.
     */
    public List<AgentTool> getAllTools() {
        var tools = new ArrayList<AgentTool>();
        for (var ext : extensions.values()) {
            tools.addAll(ext.tools());
        }
        return tools;
    }

    /**
     * Returns all slash commands contributed by all extensions.
     */
    public List<SlashCommand> getAllCommands() {
        var commands = new ArrayList<SlashCommand>();
        for (var ext : extensions.values()) {
            commands.addAll(ext.commands());
        }
        return commands;
    }

    /**
     * Returns all before-tool-call handlers from all extensions.
     */
    public List<BeforeToolCallHandler> getAllBeforeToolCallHandlers() {
        var handlers = new ArrayList<BeforeToolCallHandler>();
        for (var ext : extensions.values()) {
            handlers.addAll(ext.beforeToolCallHandlers());
        }
        return handlers;
    }

    /**
     * Returns all after-tool-call handlers from all extensions.
     */
    public List<AfterToolCallHandler> getAllAfterToolCallHandlers() {
        var handlers = new ArrayList<AfterToolCallHandler>();
        for (var ext : extensions.values()) {
            handlers.addAll(ext.afterToolCallHandlers());
        }
        return handlers;
    }

    /**
     * Returns all event listeners from all extensions.
     */
    public List<AgentEventListener> getAllEventListeners() {
        var listeners = new ArrayList<AgentEventListener>();
        for (var ext : extensions.values()) {
            listeners.addAll(ext.eventListeners());
        }
        return listeners;
    }

    /**
     * Removes all extensions and calls onUnload for each.
     */
    public void clear() {
        for (var ext : extensions.values()) {
            ext.onUnload();
        }
        extensions.clear();
    }
}
