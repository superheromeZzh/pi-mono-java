package com.huawei.hicampus.mate.matecampusclaw.ai.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToolAndContextTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // --- Tool ---

    @Nested
    class ToolTests {

        private JsonNode sampleSchema() {
            return mapper.createObjectNode()
                .put("type", "object")
                .<com.fasterxml.jackson.databind.node.ObjectNode>set("properties",
                    mapper.createObjectNode()
                        .set("query", mapper.createObjectNode()
                            .put("type", "string")
                            .put("description", "The search query")))
                .set("required", mapper.createArrayNode().add("query"));
        }

        @Test
        void creation() {
            var schema = sampleSchema();
            var tool = new Tool("search", "Search the web", schema);
            assertEquals("search", tool.name());
            assertEquals("Search the web", tool.description());
            assertEquals("object", tool.parameters().get("type").asText());
            assertEquals("string", tool.parameters().get("properties").get("query").get("type").asText());
        }

        @Test
        void serialization() throws JsonProcessingException {
            var tool = new Tool("search", "Search the web", sampleSchema());
            var json = mapper.readTree(mapper.writeValueAsString(tool));
            assertEquals("search", json.get("name").asText());
            assertEquals("Search the web", json.get("description").asText());
            assertTrue(json.has("parameters"));
            assertEquals("object", json.get("parameters").get("type").asText());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {
                  "name": "read_file",
                  "description": "Read a file from disk",
                  "parameters": {
                    "type": "object",
                    "properties": {
                      "path": {"type": "string"}
                    },
                    "required": ["path"]
                  }
                }""";
            var tool = mapper.readValue(json, Tool.class);
            assertEquals("read_file", tool.name());
            assertEquals("Read a file from disk", tool.description());
            assertEquals("object", tool.parameters().get("type").asText());
            assertEquals("string", tool.parameters().get("properties").get("path").get("type").asText());
        }

        @Test
        void roundTrip() throws JsonProcessingException {
            var original = new Tool("bash", "Run a bash command", sampleSchema());
            var json = mapper.writeValueAsString(original);
            var restored = mapper.readValue(json, Tool.class);
            assertEquals(original, restored);
        }
    }

    // --- Context ---

    @Nested
    class ContextTests {

        @Test
        void creationWithAllFields() {
            var tool = new Tool("search", "Search", mapper.createObjectNode().put("type", "object"));
            var msg = new UserMessage("hello", 1000L);
            var ctx = new Context("You are helpful.", List.of(msg), List.of(tool));
            assertEquals("You are helpful.", ctx.systemPrompt());
            assertEquals(1, ctx.messages().size());
            assertEquals(1, ctx.tools().size());
        }

        @Test
        void creationWithNullOptionals() {
            var msg = new UserMessage("hi", 2000L);
            var ctx = new Context(null, List.of(msg), null);
            assertNull(ctx.systemPrompt());
            assertNull(ctx.tools());
            assertEquals(1, ctx.messages().size());
        }

        @Test
        void serialization() throws JsonProcessingException {
            var tool = new Tool("search", "Search", mapper.createObjectNode().put("type", "object"));
            var ctx = new Context("Be concise.", List.of(new UserMessage("hi", 1000L)), List.of(tool));
            var json = mapper.readTree(mapper.writeValueAsString(ctx));
            assertEquals("Be concise.", json.get("systemPrompt").asText());
            assertTrue(json.get("messages").isArray());
            assertEquals(1, json.get("messages").size());
            assertEquals("user", json.get("messages").get(0).get("role").asText());
            assertTrue(json.get("tools").isArray());
            assertEquals(1, json.get("tools").size());
            assertEquals("search", json.get("tools").get(0).get("name").asText());
        }

        @Test
        void serializationWithNullOptionals() throws JsonProcessingException {
            var ctx = new Context(null, List.of(new UserMessage("hi", 1000L)), null);
            var json = mapper.readTree(mapper.writeValueAsString(ctx));
            assertTrue(json.has("systemPrompt"));
            assertTrue(json.get("systemPrompt").isNull());
            assertTrue(json.has("tools"));
            assertTrue(json.get("tools").isNull());
            assertTrue(json.get("messages").isArray());
        }

        @Test
        void deserialization() throws JsonProcessingException {
            var json = """
                {
                  "systemPrompt": "You are a coding assistant.",
                  "messages": [
                    {"role": "user", "content": [{"type": "text", "text": "help"}], "timestamp": 5000}
                  ],
                  "tools": [
                    {
                      "name": "bash",
                      "description": "Run commands",
                      "parameters": {"type": "object", "properties": {"cmd": {"type": "string"}}}
                    }
                  ]
                }""";
            var ctx = mapper.readValue(json, Context.class);
            assertEquals("You are a coding assistant.", ctx.systemPrompt());
            assertEquals(1, ctx.messages().size());
            assertInstanceOf(UserMessage.class, ctx.messages().get(0));
            assertEquals(1, ctx.tools().size());
            assertEquals("bash", ctx.tools().get(0).name());
        }

        @Test
        void deserializationWithoutOptionals() throws JsonProcessingException {
            var json = """
                {
                  "messages": [
                    {"role": "user", "content": [{"type": "text", "text": "hi"}], "timestamp": 1000}
                  ]
                }""";
            var ctx = mapper.readValue(json, Context.class);
            assertNull(ctx.systemPrompt());
            assertNull(ctx.tools());
            assertEquals(1, ctx.messages().size());
        }

        @Test
        void roundTrip() throws JsonProcessingException {
            var tool = new Tool("search", "Search the web",
                mapper.createObjectNode().put("type", "object"));
            var ctx = new Context("system", List.of(new UserMessage("q", 1L)), List.of(tool));
            var json = mapper.writeValueAsString(ctx);
            var restored = mapper.readValue(json, Context.class);
            assertEquals(ctx.systemPrompt(), restored.systemPrompt());
            assertEquals(ctx.messages().size(), restored.messages().size());
            assertEquals(ctx.tools().size(), restored.tools().size());
            assertEquals(ctx.tools().get(0).name(), restored.tools().get(0).name());
        }
    }
}
