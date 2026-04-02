package com.huawei.hicampus.mate.matecampusclaw.ai.provider;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ApiProviderRegistryTest {

    // --- Spring Auto-Collection ---

    @Nested
    class SpringAutoCollection {

        @Test
        void registersSpringDiscoveredProviders() {
            var anthropic = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            var openai = new MockApiProvider(Api.OPENAI_RESPONSES);

            var registry = new ApiProviderRegistry(List.of(anthropic, openai));

            assertTrue(registry.getProvider(Api.ANTHROPIC_MESSAGES).isPresent());
            assertTrue(registry.getProvider(Api.OPENAI_RESPONSES).isPresent());
            assertSame(anthropic, registry.getProvider(Api.ANTHROPIC_MESSAGES).get());
            assertSame(openai, registry.getProvider(Api.OPENAI_RESPONSES).get());
        }

        @Test
        void handlesNullProviderList() {
            var registry = new ApiProviderRegistry(null);

            assertTrue(registry.getProviders().isEmpty());
        }

        @Test
        void handlesEmptyProviderList() {
            var registry = new ApiProviderRegistry(List.of());

            assertTrue(registry.getProviders().isEmpty());
        }

        @Test
        void getProvidersReturnsAllSpringProviders() {
            var anthropic = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            var openai = new MockApiProvider(Api.OPENAI_RESPONSES);

            var registry = new ApiProviderRegistry(List.of(anthropic, openai));

            var providers = registry.getProviders();
            assertEquals(2, providers.size());
            assertTrue(providers.contains(anthropic));
            assertTrue(providers.contains(openai));
        }
    }

    // --- Lookup ---

    @Nested
    class Lookup {

        private ApiProviderRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new ApiProviderRegistry(null);
        }

        @Test
        void getProviderReturnsEmptyForUnregisteredApi() {
            assertTrue(registry.getProvider(Api.ANTHROPIC_MESSAGES).isEmpty());
        }

        @Test
        void getProviderReturnsRegisteredProvider() {
            var provider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            registry.register(provider, "test");

            var found = registry.getProvider(Api.ANTHROPIC_MESSAGES);
            assertTrue(found.isPresent());
            assertSame(provider, found.get());
        }
    }

    // --- Runtime Registration ---

    @Nested
    class RuntimeRegistration {

        private ApiProviderRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new ApiProviderRegistry(null);
        }

        @Test
        void registerAddsProvider() {
            var provider = new MockApiProvider(Api.BEDROCK_CONVERSE_STREAM);
            registry.register(provider, "plugin-a");

            assertSame(provider, registry.getProvider(Api.BEDROCK_CONVERSE_STREAM).orElse(null));
            assertEquals(1, registry.getProviders().size());
        }

        @Test
        void registerReplacesExistingProviderForSameApi() {
            var first = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            var second = new MockApiProvider(Api.ANTHROPIC_MESSAGES);

            registry.register(first, "source-1");
            registry.register(second, "source-2");

            assertSame(second, registry.getProvider(Api.ANTHROPIC_MESSAGES).orElse(null));
        }

        @Test
        void registerMultipleProvidersFromSameSource() {
            var p1 = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            var p2 = new MockApiProvider(Api.OPENAI_RESPONSES);

            registry.register(p1, "plugin-x");
            registry.register(p2, "plugin-x");

            assertEquals(2, registry.getProviders().size());
        }

        @Test
        void registerRejectsNullProvider() {
            assertThrows(NullPointerException.class, () -> registry.register(null, "test"));
        }

        @Test
        void registerRejectsNullSourceId() {
            var provider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            assertThrows(NullPointerException.class, () -> registry.register(provider, null));
        }
    }

    // --- Unregistration ---

    @Nested
    class Unregistration {

        private ApiProviderRegistry registry;

        @BeforeEach
        void setUp() {
            registry = new ApiProviderRegistry(null);
        }

        @Test
        void unregisterRemovesAllProvidersForSource() {
            var p1 = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            var p2 = new MockApiProvider(Api.OPENAI_RESPONSES);

            registry.register(p1, "plugin-a");
            registry.register(p2, "plugin-a");

            registry.unregister("plugin-a");

            assertTrue(registry.getProvider(Api.ANTHROPIC_MESSAGES).isEmpty());
            assertTrue(registry.getProvider(Api.OPENAI_RESPONSES).isEmpty());
            assertTrue(registry.getProviders().isEmpty());
        }

        @Test
        void unregisterDoesNotAffectOtherSources() {
            var p1 = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            var p2 = new MockApiProvider(Api.OPENAI_RESPONSES);

            registry.register(p1, "plugin-a");
            registry.register(p2, "plugin-b");

            registry.unregister("plugin-a");

            assertTrue(registry.getProvider(Api.ANTHROPIC_MESSAGES).isEmpty());
            assertTrue(registry.getProvider(Api.OPENAI_RESPONSES).isPresent());
            assertEquals(1, registry.getProviders().size());
        }

        @Test
        void unregisterOnlyRemovesIfCurrentMappingMatches() {
            var first = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            var second = new MockApiProvider(Api.ANTHROPIC_MESSAGES);

            registry.register(first, "source-1");
            registry.register(second, "source-2"); // overwrites first in Api index

            registry.unregister("source-1"); // should NOT remove second

            assertTrue(registry.getProvider(Api.ANTHROPIC_MESSAGES).isPresent());
            assertSame(second, registry.getProvider(Api.ANTHROPIC_MESSAGES).get());
        }

        @Test
        void unregisterUnknownSourceIsNoOp() {
            registry.register(new MockApiProvider(Api.ANTHROPIC_MESSAGES), "test");

            registry.unregister("nonexistent");

            assertEquals(1, registry.getProviders().size());
        }

        @Test
        void unregisterRejectsNullSourceId() {
            assertThrows(NullPointerException.class, () -> registry.unregister(null));
        }
    }

    // --- Clear ---

    @Nested
    class Clear {

        @Test
        void clearRemovesAllProviders() {
            var registry = new ApiProviderRegistry(List.of(
                new MockApiProvider(Api.ANTHROPIC_MESSAGES),
                new MockApiProvider(Api.OPENAI_RESPONSES)
            ));
            registry.register(new MockApiProvider(Api.BEDROCK_CONVERSE_STREAM), "plugin");

            registry.clear();

            assertTrue(registry.getProviders().isEmpty());
            assertTrue(registry.getProvider(Api.ANTHROPIC_MESSAGES).isEmpty());
            assertTrue(registry.getProvider(Api.OPENAI_RESPONSES).isEmpty());
            assertTrue(registry.getProvider(Api.BEDROCK_CONVERSE_STREAM).isEmpty());
        }

        @Test
        void clearAllowsReregistration() {
            var registry = new ApiProviderRegistry(List.of(
                new MockApiProvider(Api.ANTHROPIC_MESSAGES)
            ));

            registry.clear();
            assertTrue(registry.getProviders().isEmpty());

            var newProvider = new MockApiProvider(Api.ANTHROPIC_MESSAGES);
            registry.register(newProvider, "fresh");

            assertSame(newProvider, registry.getProvider(Api.ANTHROPIC_MESSAGES).orElse(null));
        }
    }

    // --- Immutability of returned lists ---

    @Nested
    class ReturnedListImmutability {

        @Test
        void getProvidersReturnsUnmodifiableList() {
            var registry = new ApiProviderRegistry(List.of(
                new MockApiProvider(Api.ANTHROPIC_MESSAGES)
            ));

            var providers = registry.getProviders();
            assertThrows(UnsupportedOperationException.class,
                () -> providers.add(new MockApiProvider(Api.OPENAI_RESPONSES)));
        }
    }
}
