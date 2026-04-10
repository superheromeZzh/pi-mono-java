package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClientInfo(
    @JsonProperty("name") String name,
    @JsonProperty("version") String version,
    @JsonProperty("os") String os,
    @JsonProperty("arch") String arch,
    @JsonProperty("pid") Long pid
) {}
