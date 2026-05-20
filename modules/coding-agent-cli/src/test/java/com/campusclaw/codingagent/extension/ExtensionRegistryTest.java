/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.extension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.campusclaw.agent.event.AgentEventListener;
import com.campusclaw.agent.tool.AfterToolCallHandler;
import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.agent.tool.BeforeToolCallHandler;
import com.campusclaw.codingagent.command.SlashCommand;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExtensionRegistryTest {

    static class StubExtension implements Extension {
        private final String id;
        private final List<AgentTool> tools;
        private final List<SlashCommand> commands;
        final AtomicInteger loadCount = new AtomicInteger();
        final AtomicInteger unloadCount = new AtomicInteger();

        StubExtension(String id) {
            this(id, List.of(), List.of());
        }

        StubExtension(String id, List<AgentTool> tools, List<SlashCommand> commands) {
            this.id = id;
            this.tools = tools;
            this.commands = commands;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return "Stub-" + id;
        }

        @Override
        public List<AgentTool> tools() {
            return tools;
        }

        @Override
        public List<SlashCommand> commands() {
            return commands;
        }

        @Override
        public void onLoad() {
            loadCount.incrementAndGet();
        }

        @Override
        public void onUnload() {
            unloadCount.incrementAndGet();
        }
    }

    @Nested
    class RegisterUnregister {

        @Test
        void registerInvokesOnLoad() {
            ExtensionRegistry r = new ExtensionRegistry();
            StubExtension ext = new StubExtension("e1");
            r.register(ext);
            assertThat(ext.loadCount.get()).isEqualTo(1);
            assertThat(r.get("e1")).isPresent();
        }

        @Test
        void duplicateIdUnloadsPrevious() {
            ExtensionRegistry r = new ExtensionRegistry();
            StubExtension first = new StubExtension("e1");
            StubExtension second = new StubExtension("e1");
            r.register(first);
            r.register(second);
            assertThat(first.unloadCount.get()).isEqualTo(1);
            assertThat(second.loadCount.get()).isEqualTo(1);
            assertThat(r.get("e1")).contains(second);
        }

        @Test
        void unregisterCallsOnUnload() {
            ExtensionRegistry r = new ExtensionRegistry();
            StubExtension ext = new StubExtension("e1");
            r.register(ext);
            r.unregister("e1");
            assertThat(ext.unloadCount.get()).isEqualTo(1);
            assertThat(r.get("e1")).isEmpty();
        }

        @Test
        void unregisterMissingIsSilent() {
            ExtensionRegistry r = new ExtensionRegistry();
            r.unregister("nope");
            assertThat(r.getAll()).isEmpty();
        }
    }

    @Nested
    class Aggregation {

        @Test
        void getAllReturnsAllRegistered() {
            ExtensionRegistry r = new ExtensionRegistry();
            r.register(new StubExtension("a"));
            r.register(new StubExtension("b"));
            assertThat(r.getAll()).hasSize(2);
        }

        @Test
        void getAllExtensionPointMethodsAggregate() {
            ExtensionRegistry r = new ExtensionRegistry();
            r.register(new StubExtension("a"));
            r.register(new StubExtension("b"));
            assertThat(r.getAllTools()).isEmpty();
            assertThat(r.getAllCommands()).isEmpty();
            assertThat(r.getAllBeforeToolCallHandlers()).isEmpty();
            assertThat(r.getAllAfterToolCallHandlers()).isEmpty();
            assertThat(r.getAllEventListeners()).isEmpty();
        }

        @Test
        void extensionContributesAreCollected() {
            ExtensionRegistry r = new ExtensionRegistry();
            BeforeToolCallHandler beforeHandler = ctx -> null;
            AfterToolCallHandler afterHandler = ctx -> null;
            AgentEventListener listener = ev -> {};
            Extension ext = new Extension() {
                @Override
                public String id() {
                    return "rich";
                }

                @Override
                public String name() {
                    return "rich";
                }

                @Override
                public List<BeforeToolCallHandler> beforeToolCallHandlers() {
                    return List.of(beforeHandler);
                }

                @Override
                public List<AfterToolCallHandler> afterToolCallHandlers() {
                    return List.of(afterHandler);
                }

                @Override
                public List<AgentEventListener> eventListeners() {
                    return List.of(listener);
                }
            };
            r.register(ext);
            assertThat(r.getAllBeforeToolCallHandlers()).containsExactly(beforeHandler);
            assertThat(r.getAllAfterToolCallHandlers()).containsExactly(afterHandler);
            assertThat(r.getAllEventListeners()).containsExactly(listener);
        }
    }

    @Nested
    class Clear {

        @Test
        void clearUnloadsAll() {
            ExtensionRegistry r = new ExtensionRegistry();
            StubExtension a = new StubExtension("a");
            StubExtension b = new StubExtension("b");
            r.register(a);
            r.register(b);
            r.clear();
            assertThat(a.unloadCount.get()).isEqualTo(1);
            assertThat(b.unloadCount.get()).isEqualTo(1);
            assertThat(r.getAll()).isEmpty();
        }
    }
}
