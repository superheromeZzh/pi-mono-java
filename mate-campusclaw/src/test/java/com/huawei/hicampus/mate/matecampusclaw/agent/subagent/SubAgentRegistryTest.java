/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
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

    @Test
    void registerNormalizesIdAndAcceptsUppercase() {
        var registry = new SubAgentRegistry(emptyProvider());
        var backend = new RecordingBackend("Foo");
        registry.register(backend);
        assertThat(registry.backend("foo")).contains(backend);
        assertThat(registry.backend("FOO")).contains(backend);
        assertThat(registry.backend("  foo  ")).contains(backend);
    }

    @Test
    void registerBlankIdRejected() {
        var registry = new SubAgentRegistry(emptyProvider());
        assertThatThrownBy(() -> registry.register(new RecordingBackend("")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> registry.register(new RecordingBackend("   ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void registerReplacementKeepsLatest() {
        var registry = new SubAgentRegistry(emptyProvider());
        var first = new RecordingBackend("foo");
        var second = new RecordingBackend("foo");
        registry.register(first);
        registry.register(second);
        assertThat(registry.backend("foo")).contains(second);
    }

    @Test
    void requireBackendReturnsRegistered() {
        var registry = new SubAgentRegistry(emptyProvider());
        var backend = new RecordingBackend("foo");
        registry.register(backend);
        assertThat(registry.requireBackend("foo")).isSameAs(backend);
    }

    @Test
    void backendForUnknownIdEmpty() {
        var registry = new SubAgentRegistry(emptyProvider());
        assertThat(registry.backend("missing")).isEmpty();
        assertThat(registry.backend(null)).isEmpty();
    }

    @Test
    void trackAndForgetSessions() {
        var registry = new SubAgentRegistry(emptyProvider());
        var backend = new RecordingBackend("foo");
        registry.register(backend);

        var key = SubAgentSessionKey.newKey("main", "foo");
        var session = new SubAgentSession(key, "remote-1", backend);
        registry.trackSession(session, "my-label");

        assertThat(registry.session(session.keyString())).contains(session);
        assertThat(registry.sessions()).contains(session);

        registry.forgetSession(session);
        assertThat(registry.session(session.keyString())).isEmpty();
        assertThat(registry.sessions()).doesNotContain(session);
    }

    @Test
    void forgetUntrackedSessionIsNoOp() {
        var registry = new SubAgentRegistry(emptyProvider());
        var backend = new RecordingBackend("foo");
        registry.register(backend);
        var session = new SubAgentSession(SubAgentSessionKey.newKey("main", "foo"), "remote", backend);

        // forgetSession must be safe to call on a session that was never registered — the
        // registry should silently no-op rather than throw on the missing key lookup.
        assertThatNoException().isThrownBy(() -> registry.forgetSession(session));
        assertThat(registry.session(session.keyString())).isEmpty();
    }

    @Test
    void cancelAllSwallowsBackendException() {
        var registry = new SubAgentRegistry(emptyProvider());
        AtomicInteger cancelCount = new AtomicInteger();
        SubAgentBackend backend = new SubAgentBackend() {
            @Override
            public String id() {
                return "throwy";
            }

            @Override
            public SubAgentSession open(OpenRequest request) {
                return new SubAgentSession(SubAgentSessionKey.newKey(request.parentAgentId(), id()), "sid", this);
            }

            @Override
            public Flux<SubAgentEvent> prompt(SubAgentSession session, String text, CancellationToken signal) {
                return Flux.empty();
            }

            @Override
            public void cancel(SubAgentSession session, String reason) {
                cancelCount.incrementAndGet();
                throw new IllegalStateException("backend boom");
            }

            @Override
            public void close(SubAgentSession session, String reason) {}
        };
        registry.register(backend);
        var session = new SubAgentSession(SubAgentSessionKey.newKey("main", "throwy"), "remote", backend);
        registry.trackSession(session);

        // Should not propagate the exception
        registry.cancelAll("abort");
        assertThat(cancelCount.get()).isEqualTo(1);
    }

    @Test
    void backendIdsReturnsAllRegistered() {
        var registry = new SubAgentRegistry(emptyProvider());
        registry.register(new RecordingBackend("a"));
        registry.register(new RecordingBackend("b"));
        registry.register(new RecordingBackend("c"));
        assertThat(registry.backendIds()).containsExactlyInAnyOrder("a", "b", "c");
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
