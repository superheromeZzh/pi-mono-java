package com.campusclaw.ai.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Definition of a tool that an LLM can invoke.
 *
 * @param name        unique tool identifier
 * @param description human-readable description of the tool's purpose
 * @param parameters  JSON Schema defining the tool's input parameters
 */
public record Tool(
    @JsonProperty("name") String name,
    @JsonProperty("description") String description,
    @JsonProperty("parameters") JsonNode parameters
) {
}
