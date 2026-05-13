/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import reactor.core.publisher.Flux;

class SubAgentRegistryTest {

    @Test
    void requireBackendThrowsWhenMissing() {
        var registry = new SubAgentRegistry(emptyProvider());
        assertThatThrownBy(() -> registry.requireBackend("nope"))
                .isInstanceOf(SubAgentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void registerExposesBackendById() {
        var registry = new SubAgentRegistry(emptyProvider());
        var backend = new RecordingBackend("foo");

        registry.register(backend);

        assertThat(registry.backend("foo")).contains(backend);
        assertThat(registry.backend("FOO")).contains(backend);
        assertThat(registry.backendIds()).contains("foo");
    }

    @Test
    void cancelAllNotifiesEverySession() {
        var registry = new SubAgentRegistry(emptyProvider());
        var backend = new RecordingBackend("foo");
        registry.register(backend);

        var session = new SubAgentSession(SubAgentSessionKey.newKey("main", "foo"), "remote-1", backend);
        registry.trackSession(session);

        registry.cancelAll("parent-abort");

        assertThat(backend.cancelCount.get()).isEqualTo(1);
    }

    private static ObjectProvider<SubAgentBackend> emptyProvider() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SubAgentBackend> provider =
                (ObjectProvider<SubAgentBackend>) java.lang.reflect.Proxy.newProxyInstance(
                        ObjectProvider.class.getClassLoader(),
                        new Class<?>[] {ObjectProvider.class},
                        (proxy, method, args) -> {
                            if ("orderedStream".equals(method.getName())) {
                                return java.util.stream.Stream.empty();
                            }
                            if ("stream".equals(method.getName())) {
                                return java.util.stream.Stream.empty();
                            }
                            if ("iterator".equals(method.getName())) {
                                return java.util.Collections.emptyIterator();
                            }
                            return null;
                        });
        return provider;
    }

    private static final class RecordingBackend implements SubAgentBackend {

        private final String id;
        final AtomicInteger cancelCount = new AtomicInteger();

        RecordingBackend(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public SubAgentSession open(OpenRequest request) {
            return new SubAgentSession(SubAgentSessionKey.newKey(request.parentAgentId(), id), "sid", this);
        }

        @Override
        public Flux<SubAgentEvent> prompt(SubAgentSession session, String text, CancellationToken signal) {
            return Flux.just(new SubAgentEvent.Done(SubAgentEvent.StopReason.END_TURN));
        }

        @Override
        public void cancel(SubAgentSession session, String reason) {
            cancelCount.incrementAndGet();
        }

        @Override
        public void close(SubAgentSession session, String reason) {}

        @SuppressWarnings("unused")
        OpenRequest sample() {
            return new OpenRequest("main", null, null, null, Map.of(), Duration.ofSeconds(1L));
        }
    }
}
