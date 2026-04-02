package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class StreamOptionsTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // --- Transport ---

    @Nested
    class TransportTests {

        @Test
        void jsonValues() {
            assertEquals("sse", Transport.SSE.value());
            assertEquals("websocket", Transport.WEBSOCKET.value());
            assertEquals("auto", Transport.AUTO.value());
        }

        @Test
        void fromValue() {
            assertEquals(Transport.SSE, Transport.fromValue("sse"));
            assertEquals(Transport.WEBSOCKET, Transport.fromValue("websocket"));
            assertEquals(Transport.AUTO, Transport.fromValue("auto"));
        }

        @Test
        void fromValueUnknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> Transport.fromValue("grpc"));
        }

        @Test
        void serialization() throws JsonProcessingException {
            assertEquals("\"sse\"", mapper.writeValueAsString(Transport.SSE));
            assertEquals("\"websocket\"", mapper.writeValueAsString(Transport.WEBSOCKET));
        }

        @Test
        void deserialization() throws JsonProcessingException {
            assertEquals(Transport.AUTO, mapper.readValue("\"auto\"", Transport.class));
        }
    }

    // --- CacheRetention ---

    @Nested
    class CacheRetentionTests {

        @Test
        void jsonValues() {
            assertEquals("none", CacheRetention.NONE.value());
            assertEquals("short", CacheRetention.SHORT.value());
            assertEquals("long", CacheRetention.LONG.value());
        }

        @Test
        void fromValue() {
            assertEquals(CacheRetention.NONE, CacheRetention.fromValue("none"));
            assertEquals(CacheRetention.SHORT, CacheRetention.fromValue("short"));
            assertEquals(CacheRetention.LONG, CacheRetention.fromValue("long"));
        }

        @Test
        void fromValueUnknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> CacheRetention.fromValue("forever"));
        }

        @Test
        void serialization() throws JsonProcessingException {
            assertEquals("\"short\"", mapper.writeValueAsString(CacheRetention.SHORT));
        }

        @Test
        void deserialization() throws JsonProcessingException {
            assertEquals(CacheRetention.LONG, mapper.readValue("\"long\"", CacheRetention.class));
        }
    }

    // --- ThinkingLevel ---

    @Nested
    class ThinkingLevelTests {

        @Test
        void jsonValues() {
            assertEquals("off", ThinkingLevel.OFF.value());
            assertEquals("minimal", ThinkingLevel.MINIMAL.value());
            assertEquals("low", ThinkingLevel.LOW.value());
            assertEquals("medium", ThinkingLevel.MEDIUM.value());
            assertEquals("high", ThinkingLevel.HIGH.value());
            assertEquals("xhigh", ThinkingLevel.XHIGH.value());
        }

        @Test
        void fromValue() {
            assertEquals(ThinkingLevel.OFF, ThinkingLevel.fromValue("off"));
            assertEquals(ThinkingLevel.XHIGH, ThinkingLevel.fromValue("xhigh"));
        }

        @Test
        void fromValueUnknownThrows() {
            assertThrows(IllegalArgumentException.class, () -> ThinkingLevel.fromValue("ultra"));
        }

        @Test
        void serialization() throws JsonProcessingException {
            assertEquals("\"medium\"", mapper.writeValueAsString(ThinkingLevel.MEDIUM));
        }

        @Test
        void deserialization() throws JsonProcessingException {
            assertEquals(ThinkingLevel.HIGH, mapper.readValue("\"high\"", ThinkingLevel.class));
        }
    }

    // --- ThinkingBudgets ---

    @Nested
    class ThinkingBudgetsTests {

        @Test
        void creation() {
            var budgets = new ThinkingBudgets(1024, 2048, 4096, 8192);
            assertEquals(1024, budgets.minimal());
            assertEquals(2048, budgets.low());
            assertEquals(4096, budgets.medium());
            assertEquals(8192, budgets.high());
        }

        @Test
        void nullableFields() {
            var budgets = new ThinkingBudgets(null, null, 4096, null);
            assertNull(budgets.minimal());
            assertNull(budgets.low());
            assertEquals(4096, budgets.medium());
            assertNull(budgets.high());
        }

        @Test
        void roundTrip() throws JsonProcessingException {
            var original = new ThinkingBudgets(1024, 2048, 4096, 8192);
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, ThinkingBudgets.class);
            assertEquals(original, restored);
        }

        @Test
        void deserializationPartial() throws JsonProcessingException {
            var json = """
                {"medium": 4096}""";
            var budgets = mapper.readValue(json, ThinkingBudgets.class);
            assertNull(budgets.minimal());
            assertNull(budgets.low());
            assertEquals(4096, budgets.medium());
            assertNull(budgets.high());
        }
    }

    // --- StreamOptions ---

    @Nested
    class StreamOptionsTests {

        @Test
        void empty() {
            var opts = StreamOptions.empty();
            assertNull(opts.temperature());
            assertNull(opts.maxTokens());
            assertNull(opts.apiKey());
            assertNull(opts.transport());
            assertNull(opts.cacheRetention());
            assertNull(opts.sessionId());
            assertNull(opts.headers());
            assertNull(opts.maxRetryDelayMs());
            assertNull(opts.metadata());
        }

        @Test
        void builder() {
            var opts = StreamOptions.builder()
                .temperature(0.7)
                .maxTokens(4096)
                .apiKey("sk-test")
                .transport(Transport.SSE)
                .cacheRetention(CacheRetention.SHORT)
                .sessionId("session-1")
                .headers(Map.of("X-Custom", "value"))
                .maxRetryDelayMs(5000L)
                .metadata(Map.of("key", "val"))
                .build();

            assertEquals(0.7, opts.temperature());
            assertEquals(4096, opts.maxTokens());
            assertEquals("sk-test", opts.apiKey());
            assertEquals(Transport.SSE, opts.transport());
            assertEquals(CacheRetention.SHORT, opts.cacheRetention());
            assertEquals("session-1", opts.sessionId());
            assertEquals("value", opts.headers().get("X-Custom"));
            assertEquals(5000L, opts.maxRetryDelayMs());
            assertEquals("val", opts.metadata().get("key"));
        }

        @Test
        void toBuilder() {
            var original = StreamOptions.builder()
                .temperature(0.5)
                .maxTokens(1024)
                .build();

            var modified = original.toBuilder()
                .temperature(0.9)
                .apiKey("sk-new")
                .build();

            assertEquals(0.9, modified.temperature());
            assertEquals(1024, modified.maxTokens());
            assertEquals("sk-new", modified.apiKey());
        }

        @Test
        void serialization() throws JsonProcessingException {
            var opts = StreamOptions.builder()
                .temperature(0.7)
                .transport(Transport.WEBSOCKET)
                .cacheRetention(CacheRetention.LONG)
                .build();

            var json = mapper.readTree(mapper.writeValueAsString(opts));
            assertEquals(0.7, json.get("temperature").asDouble());
            assertEquals("websocket", json.get("transport").asText());
            assertEquals("long", json.get("cacheRetention").asText());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {
                  "temperature": 0.5,
                  "maxTokens": 2048,
                  "apiKey": "sk-abc",
                  "transport": "sse",
                  "cacheRetention": "none",
                  "sessionId": "sess-42",
                  "headers": {"Auth": "Bearer token"},
                  "maxRetryDelayMs": 10000,
                  "metadata": {"env": "test"}
                }""";
            var opts = mapper.readValue(json, StreamOptions.class);
            assertEquals(0.5, opts.temperature());
            assertEquals(2048, opts.maxTokens());
            assertEquals("sk-abc", opts.apiKey());
            assertEquals(Transport.SSE, opts.transport());
            assertEquals(CacheRetention.NONE, opts.cacheRetention());
            assertEquals("sess-42", opts.sessionId());
            assertEquals("Bearer token", opts.headers().get("Auth"));
            assertEquals(10000L, opts.maxRetryDelayMs());
            assertEquals("test", opts.metadata().get("env"));
        }

        @Test
        void deserializationPartial() throws JsonProcessingException {
            var json = """
                {"temperature": 1.0}""";
            var opts = mapper.readValue(json, StreamOptions.class);
            assertEquals(1.0, opts.temperature());
            assertNull(opts.maxTokens());
            assertNull(opts.transport());
        }

        @Test
        void roundTrip() throws JsonProcessingException {
            var original = StreamOptions.builder()
                .temperature(0.8)
                .maxTokens(4096)
                .transport(Transport.AUTO)
                .cacheRetention(CacheRetention.SHORT)
                .sessionId("s1")
                .maxRetryDelayMs(3000L)
                .build();

            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, StreamOptions.class);
            assertEquals(original, restored);
        }
    }

    // --- SimpleStreamOptions ---

    @Nested
    class SimpleStreamOptionsTests {

        @Test
        void empty() {
            var opts = SimpleStreamOptions.empty();
            assertNull(opts.temperature());
            assertNull(opts.reasoning());
            assertNull(opts.thinkingBudgets());
        }

        @Test
        void builder() {
            var budgets = new ThinkingBudgets(1024, 2048, 4096, 8192);
            var opts = SimpleStreamOptions.builder()
                .temperature(0.7)
                .maxTokens(4096)
                .transport(Transport.SSE)
                .reasoning(ThinkingLevel.HIGH)
                .thinkingBudgets(budgets)
                .build();

            assertEquals(0.7, opts.temperature());
            assertEquals(4096, opts.maxTokens());
            assertEquals(Transport.SSE, opts.transport());
            assertEquals(ThinkingLevel.HIGH, opts.reasoning());
            assertEquals(budgets, opts.thinkingBudgets());
        }

        @Test
        void fromStreamOptions() {
            var base = StreamOptions.builder()
                .temperature(0.5)
                .maxTokens(2048)
                .apiKey("sk-test")
                .transport(Transport.WEBSOCKET)
                .build();

            var simple = SimpleStreamOptions.from(base);
            assertEquals(0.5, simple.temperature());
            assertEquals(2048, simple.maxTokens());
            assertEquals("sk-test", simple.apiKey());
            assertEquals(Transport.WEBSOCKET, simple.transport());
            assertNull(simple.reasoning());
            assertNull(simple.thinkingBudgets());
        }

        @Test
        void toStreamOptions() {
            var simple = SimpleStreamOptions.builder()
                .temperature(0.7)
                .maxTokens(4096)
                .reasoning(ThinkingLevel.MEDIUM)
                .build();

            var base = simple.toStreamOptions();
            assertEquals(0.7, base.temperature());
            assertEquals(4096, base.maxTokens());
            // reasoning fields are not present in StreamOptions
        }

        @Test
        void toBuilder() {
            var original = SimpleStreamOptions.builder()
                .temperature(0.5)
                .reasoning(ThinkingLevel.LOW)
                .build();

            var modified = original.toBuilder()
                .reasoning(ThinkingLevel.HIGH)
                .thinkingBudgets(new ThinkingBudgets(null, null, null, 8192))
                .build();

            assertEquals(0.5, modified.temperature());
            assertEquals(ThinkingLevel.HIGH, modified.reasoning());
            assertEquals(8192, modified.thinkingBudgets().high());
        }

        @Test
        void serialization() throws JsonProcessingException {
            var opts = SimpleStreamOptions.builder()
                .temperature(0.7)
                .reasoning(ThinkingLevel.XHIGH)
                .thinkingBudgets(new ThinkingBudgets(1024, 2048, 4096, 8192))
                .build();

            var json = mapper.readTree(mapper.writeValueAsString(opts));
            assertEquals(0.7, json.get("temperature").asDouble());
            assertEquals("xhigh", json.get("reasoning").asText());
            assertTrue(json.has("thinkingBudgets"));
            assertEquals(4096, json.get("thinkingBudgets").get("medium").asInt());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {
                  "temperature": 0.5,
                  "maxTokens": 2048,
                  "transport": "auto",
                  "reasoning": "high",
                  "thinkingBudgets": {
                    "minimal": 512,
                    "low": 1024,
                    "medium": 2048,
                    "high": 4096
                  }
                }""";
            var opts = mapper.readValue(json, SimpleStreamOptions.class);
            assertEquals(0.5, opts.temperature());
            assertEquals(2048, opts.maxTokens());
            assertEquals(Transport.AUTO, opts.transport());
            assertEquals(ThinkingLevel.HIGH, opts.reasoning());
            assertEquals(512, opts.thinkingBudgets().minimal());
            assertEquals(4096, opts.thinkingBudgets().high());
        }

        @Test
        void roundTrip() throws JsonProcessingException {
            var original = SimpleStreamOptions.builder()
                .temperature(0.8)
                .maxTokens(4096)
                .transport(Transport.SSE)
                .cacheRetention(CacheRetention.LONG)
                .reasoning(ThinkingLevel.MEDIUM)
                .thinkingBudgets(new ThinkingBudgets(1024, 2048, 4096, 8192))
                .build();

            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, SimpleStreamOptions.class);
            assertEquals(original, restored);
        }
    }
}
