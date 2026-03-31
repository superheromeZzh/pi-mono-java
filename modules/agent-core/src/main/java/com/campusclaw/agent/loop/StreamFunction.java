package com.campusclaw.agent.loop;

import com.campusclaw.ai.stream.AssistantMessageEventStream;
import com.campusclaw.ai.types.Context;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.SimpleStreamOptions;

/**
 * Functional interface for streaming LLM calls, decoupling the agent loop
 * from the concrete {@link com.campusclaw.ai.CampusClawAiService}.
 *
 * <p>Implementations can wrap CampusClawAiService, add caching, logging, or any
 * other cross-cutting concern.
 */
@FunctionalInterface
public interface StreamFunction {

    /**
     * Starts a streaming LLM call.
     *
     * @param model   the model to invoke
     * @param context the conversation context
     * @param options streaming options (may be null)
     * @return an event stream of assistant message events
     */
    AssistantMessageEventStream stream(Model model, Context context, SimpleStreamOptions options);
}
