package com.huawei.hicampus.mate.matecampusclaw.ai.provider;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StreamOptions;

import jakarta.annotation.Nullable;

/**
 * Interface for LLM API providers.
 *
 * <p>Each provider implementation handles communication with a specific
 * LLM API (e.g. Anthropic Messages, OpenAI Completions) and translates
 * the provider-specific SSE/streaming protocol into the unified
 * {@link AssistantMessageEventStream}.
 *
 * <p>Implementations should be annotated with {@code @Component} so that
 * Spring automatically discovers and registers them in the
 * {@code ApiProviderRegistry}.
 */
public interface ApiProvider {

    /**
     * Returns the API protocol this provider handles.
     *
     * @return the API identifier (e.g. {@link Api#ANTHROPIC_MESSAGES})
     */
    Api getApi();

    /**
     * Starts a streaming LLM call with full provider-specific options.
     *
     * @param model   the model to invoke
     * @param context the conversation context (system prompt, messages, tools)
     * @param options streaming options, or {@code null} for defaults
     * @return an event stream of assistant message events
     */
    AssistantMessageEventStream stream(Model model, Context context, @Nullable StreamOptions options);

    /**
     * Starts a streaming LLM call with simplified options that include
     * reasoning/thinking configuration.
     *
     * @param model   the model to invoke
     * @param context the conversation context (system prompt, messages, tools)
     * @param options simple streaming options with reasoning config, or {@code null} for defaults
     * @return an event stream of assistant message events
     */
    AssistantMessageEventStream streamSimple(Model model, Context context, @Nullable SimpleStreamOptions options);
}
