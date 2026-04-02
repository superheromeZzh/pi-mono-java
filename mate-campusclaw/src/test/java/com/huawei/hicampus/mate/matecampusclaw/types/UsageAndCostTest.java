package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UsageAndCostTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // --- Cost ---

    @Nested
    class CostTests {

        @Test
        void creation() {
            var cost = new Cost(0.01, 0.03, 0.005, 0.002, 0.047);
            assertEquals(0.01, cost.input());
            assertEquals(0.03, cost.output());
            assertEquals(0.005, cost.cacheRead());
            assertEquals(0.002, cost.cacheWrite());
            assertEquals(0.047, cost.total());
        }

        @Test
        void emptyFactory() {
            var cost = Cost.empty();
            assertEquals(0.0, cost.input());
            assertEquals(0.0, cost.output());
            assertEquals(0.0, cost.cacheRead());
            assertEquals(0.0, cost.cacheWrite());
            assertEquals(0.0, cost.total());
        }

        @Test
        void serialization() throws JsonProcessingException {
            var cost = new Cost(0.01, 0.03, 0.005, 0.002, 0.047);
            var json = mapper.readTree(mapper.writeValueAsString(cost));
            assertEquals(0.01, json.get("input").asDouble());
            assertEquals(0.03, json.get("output").asDouble());
            assertEquals(0.005, json.get("cacheRead").asDouble());
            assertEquals(0.002, json.get("cacheWrite").asDouble());
            assertEquals(0.047, json.get("total").asDouble());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {"input":0.01,"output":0.03,"cacheRead":0.005,"cacheWrite":0.002,"total":0.047}""";
            var cost = mapper.readValue(json, Cost.class);
            assertEquals(0.01, cost.input());
            assertEquals(0.03, cost.output());
            assertEquals(0.005, cost.cacheRead());
            assertEquals(0.002, cost.cacheWrite());
            assertEquals(0.047, cost.total());
        }

        @Test
        void roundTrip() throws JsonProcessingException {
            var original = new Cost(1.5, 2.5, 0.1, 0.2, 4.3);
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, Cost.class);
            assertEquals(original, restored);
        }

        @Test
        void emptyRoundTrip() throws JsonProcessingException {
            var original = Cost.empty();
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, Cost.class);
            assertEquals(original, restored);
        }
    }

    // --- Usage ---

    @Nested
    class UsageTests {

        @Test
        void creation() {
            var cost = new Cost(0.01, 0.03, 0.005, 0.002, 0.047);
            var usage = new Usage(1000, 500, 200, 100, 1800, cost);
            assertEquals(1000, usage.input());
            assertEquals(500, usage.output());
            assertEquals(200, usage.cacheRead());
            assertEquals(100, usage.cacheWrite());
            assertEquals(1800, usage.totalTokens());
            assertEquals(cost, usage.cost());
        }

        @Test
        void emptyFactory() {
            var usage = Usage.empty();
            assertEquals(0, usage.input());
            assertEquals(0, usage.output());
            assertEquals(0, usage.cacheRead());
            assertEquals(0, usage.cacheWrite());
            assertEquals(0, usage.totalTokens());
            assertEquals(Cost.empty(), usage.cost());
        }

        @Test
        void serialization() throws JsonProcessingException {
            var cost = new Cost(0.01, 0.03, 0.0, 0.0, 0.04);
            var usage = new Usage(1000, 500, 0, 0, 1500, cost);
            var json = mapper.readTree(mapper.writeValueAsString(usage));
            assertEquals(1000, json.get("input").asInt());
            assertEquals(500, json.get("output").asInt());
            assertEquals(0, json.get("cacheRead").asInt());
            assertEquals(0, json.get("cacheWrite").asInt());
            assertEquals(1500, json.get("totalTokens").asInt());
            assertTrue(json.has("cost"));
            assertEquals(0.04, json.get("cost").get("total").asDouble());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {
                  "input": 1000,
                  "output": 500,
                  "cacheRead": 200,
                  "cacheWrite": 100,
                  "totalTokens": 1800,
                  "cost": {
                    "input": 0.01,
                    "output": 0.03,
                    "cacheRead": 0.005,
                    "cacheWrite": 0.002,
                    "total": 0.047
                  }
                }""";
            var usage = mapper.readValue(json, Usage.class);
            assertEquals(1000, usage.input());
            assertEquals(500, usage.output());
            assertEquals(200, usage.cacheRead());
            assertEquals(100, usage.cacheWrite());
            assertEquals(1800, usage.totalTokens());
            assertEquals(0.01, usage.cost().input());
            assertEquals(0.047, usage.cost().total());
        }

        @Test
        void roundTrip() throws JsonProcessingException {
            var original = new Usage(2000, 1000, 500, 250, 3750,
                new Cost(0.02, 0.06, 0.005, 0.003, 0.088));
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, Usage.class);
            assertEquals(original, restored);
        }

        @Test
        void emptyRoundTrip() throws JsonProcessingException {
            var original = Usage.empty();
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, Usage.class);
            assertEquals(original, restored);
        }
    }
}
