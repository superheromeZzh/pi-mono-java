/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.subagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentBackend;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentEvent;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentRegistry;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentSession;
import com.huawei.hicampus.mate.matecampusclaw.agent.subagent.SubAgentSessionKey;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolResult;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentToolUpdateCallback;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import reactor.core.publisher.Flux;

class SpawnAgentToolTest {

    @Test
    void schemaIncludesBackendAndTask() {
        var tool = new SpawnAgentTool(emptyRegistry());

        var schema = tool.parameters();
        assertThat(schema.get("type").asText()).isEqualTo("object");
        var props = schema.get("properties");
        assertThat(props.has("backend")).isTrue();
        assertThat(props.has("task")).isTrue();
        assertThat(schema.get("required").toString()).contains("backend").contains("task");
    }

    @Test
    void executeForwardsTextDeltasAndAccumulatesResult() {
        var backend = new FakeBackend(
                "stub",
                Flux.<SubAgentEvent>just(
                                new SubAgentEvent.TextDelta(SubAgentEvent.Stream.OUTPUT, "Hello, "),
                                new SubAgentEvent.TextDelta(SubAgentEvent.Stream.OUTPUT, "world!"),
                                new SubAgentEvent.Done(SubAgentEvent.StopReason.END_TURN))
                        .delayElements(java.time.Duration.ofMillis(5L)));
        var registry = emptyRegistry();
        registry.register(backend);
        var tool = new SpawnAgentTool(registry);

        var updates = new ArrayList<String>();
        AgentToolUpdateCallback onUpdate =
                partial -> updates.add(((TextContent) partial.content().get(0)).text());

        AgentToolResult result = tool.execute(
                "call-1", Map.of("backend", "stub", "task", "do thing"), new CancellationToken(), onUpdate);

        String body = ((TextContent) result.content().get(0)).text();
        assertThat(body).contains("Hello, world!").contains("END_TURN");
        assertThat(updates).last().asString().contains("Hello, world!");
        assertThat(backend.openCount.get()).isEqualTo(1);
        assertThat(backend.closeCount.get()).isEqualTo(1);
    }

    @Test
    void unknownBackendReturnsErrorResult() {
        var tool = new SpawnAgentTool(emptyRegistry());

        AgentToolResult result = tool.execute(
                "call-1", Map.of("backend", "missing", "task", "x"), new CancellationToken(), partial -> {});

        String body = ((TextContent) result.content().get(0)).text();
        assertThat(body).contains("missing").contains("Known backends");
    }

    @Test
    void cancellationTokenCancelsBackend() throws Exception {
        var cancelled = new AtomicReference<String>();
        Flux<SubAgentEvent> never = Flux.<SubAgentEvent>never();
        var backend = new FakeBackend("stub", never) {

            @Override
            public void cancel(SubAgentSession session, String reason) {
                cancelled.set(reason);
                super.cancel(session, reason);
            }
        };
        var registry = emptyRegistry();
        registry.register(backend);
        var tool = new SpawnAgentTool(registry);

        var signal = new CancellationToken();
        var runner = Thread.ofVirtual()
                .start(() -> tool.execute(
                        "call-1", Map.of("backend", "stub", "task", "x", "timeout_seconds", 1), signal, partial -> {}));

        Thread.sleep(200L);
        signal.cancel();
        runner.join(5_000L);

        assertThat(backend.cancelCount.get()).isGreaterThanOrEqualTo(1);
    }

    private static SubAgentRegistry emptyRegistry() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SubAgentBackend> provider =
                (ObjectProvider<SubAgentBackend>) java.lang.reflect.Proxy.newProxyInstance(
                        ObjectProvider.class.getClassLoader(),
                        new Class<?>[] {ObjectProvider.class},
                        (proxy, method, args) -> {
                            String name = method.getName();
                            if ("orderedStream".equals(name) || "stream".equals(name)) {
                                return java.util.stream.Stream.empty();
                            }
                            if ("iterator".equals(name)) {
                                return java.util.Collections.emptyIterator();
                            }
                            return null;
                        });
        return new SubAgentRegistry(provider);
    }

    private static class FakeBackend implements SubAgentBackend {

        private final String id;
        private final Flux<SubAgentEvent> events;
        final AtomicInteger openCount = new AtomicInteger();
        final AtomicInteger closeCount = new AtomicInteger();
        final AtomicInteger cancelCount = new AtomicInteger();

        FakeBackend(String id, Flux<SubAgentEvent> events) {
            this.id = id;
            this.events = events;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public SubAgentSession open(OpenRequest request) {
            openCount.incrementAndGet();
            return new SubAgentSession(SubAgentSessionKey.newKey(request.parentAgentId(), id), "sid", this);
        }

        @Override
        public Flux<SubAgentEvent> prompt(SubAgentSession session, String text, CancellationToken signal) {
            return events;
        }

        @Override
        public void cancel(SubAgentSession session, String reason) {
            cancelCount.incrementAndGet();
        }

        @Override
        public void close(SubAgentSession session, String reason) {
            closeCount.incrementAndGet();
        }

        @SuppressWarnings("unused")
        List<SubAgentEvent> sampleEvents() {
            return List.of();
        }
    }
}
