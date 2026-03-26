package com.mariozechner.pi.agent.context;

import com.mariozechner.pi.agent.tool.CancellationToken;
import com.mariozechner.pi.ai.types.Message;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronously transforms agent messages before they are sent to the model.
 */
@FunctionalInterface
public interface ContextTransformer {

    CompletableFuture<List<Message>> transform(List<Message> messages, CancellationToken signal);
}
