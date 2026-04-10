package com.huawei.hicampus.mate.matecampusclaw.agent.loop;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;

/**
 * Functional interface for streaming LLM calls, decoupling the agent loop
 * from the concrete {@link com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService}.
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
