package com.huawei.hicampus.mate.matecampusclaw.cron.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import org.springframework.lang.Nullable;

/**
 * Sealed payload type for cron jobs. Defines what to execute when a job fires.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = CronPayload.AgentPrompt.class, name = "agent_prompt")
})
public sealed interface CronPayload {

    /** Execute an AI agent with the given prompt. */
    record AgentPrompt(
        String prompt,
        @Nullable String systemPrompt,
        @Nullable String modelId,
        @Nullable List<String> allowedTools
    ) implements CronPayload {}
}
