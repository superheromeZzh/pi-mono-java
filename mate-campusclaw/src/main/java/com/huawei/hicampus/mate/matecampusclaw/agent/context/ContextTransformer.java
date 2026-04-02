package com.huawei.hicampus.mate.matecampusclaw.agent.context;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;

/**
 * Asynchronously transforms agent messages before they are sent to the model.
 */
@FunctionalInterface
public interface ContextTransformer {

    CompletableFuture<List<Message>> transform(List<Message> messages, CancellationToken signal);
}
