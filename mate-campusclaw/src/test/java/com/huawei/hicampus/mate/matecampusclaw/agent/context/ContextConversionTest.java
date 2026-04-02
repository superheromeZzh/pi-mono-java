package com.huawei.hicampus.mate.matecampusclaw.agent.context;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.huawei.hicampus.mate.matecampusclaw.agent.tool.CancellationToken;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.UserMessage;

import org.junit.jupiter.api.Test;

class ContextConversionTest {

    @Test
    void defaultMessageConverterPassesMessagesThrough() {
        var messages = List.<Message>of(
            new UserMessage("hello", 1L),
            new UserMessage(List.of(new TextContent("world")), 2L)
        );
        var converter = new DefaultMessageConverter();

        var converted = converter.convert(messages);

        assertSame(messages, converted);
    }

    @Test
    void messageConverterSupportsLambdaImplementations() {
        MessageConverter converter = messages -> List.of(messages.getLast());
        var messages = List.<Message>of(
            new UserMessage("first", 1L),
            new UserMessage("second", 2L)
        );

        var converted = converter.convert(messages);

        assertEquals(1, converted.size());
        assertEquals(messages.getLast(), converted.getFirst());
    }

    @Test
    void contextTransformerCanTransformMessagesAsynchronously() throws ExecutionException, InterruptedException {
        var signal = new CancellationToken();
        ContextTransformer transformer = (messages, cancellationToken) -> CompletableFuture.supplyAsync(() -> {
            assertSame(signal, cancellationToken);
            return List.<Message>of(new UserMessage("transformed", 3L), messages.getFirst());
        });
        var input = List.<Message>of(new UserMessage("original", 1L));

        var transformed = transformer.transform(input, signal).get();

        assertEquals(2, transformed.size());
        assertEquals("transformed", ((TextContent) ((UserMessage) transformed.getFirst()).content().getFirst()).text());
        assertEquals(input.getFirst(), transformed.get(1));
    }

    @Test
    void contextTransformerCanReactToCancellation() throws ExecutionException, InterruptedException {
        var signal = new CancellationToken();
        signal.cancel();
        var sawCancelledSignal = new AtomicBoolean(false);
        ContextTransformer transformer = (messages, cancellationToken) -> {
            if (cancellationToken.isCancelled()) {
                sawCancelledSignal.set(true);
            }
            return CompletableFuture.completedFuture(messages);
        };
        var input = List.<Message>of(new UserMessage("original", 1L));

        var transformed = transformer.transform(input, signal).get();

        assertTrue(sawCancelledSignal.get());
        assertSame(input, transformed);
    }

    @Test
    void contextTransformerSupportsMethodReferenceStyleUsage() throws ExecutionException, InterruptedException {
        var helper = new TransformerHelper();
        ContextTransformer transformer = helper::transform;
        var signal = new CancellationToken();
        var input = List.<Message>of(new UserMessage("original", 1L));

        var transformed = transformer.transform(input, signal).get();

        assertEquals(List.of(new UserMessage("helper", 4L)), transformed);
        assertSame(signal, helper.signalRef.get());
    }

    private static final class TransformerHelper {
        private final AtomicReference<CancellationToken> signalRef = new AtomicReference<>();

        private CompletableFuture<List<Message>> transform(List<Message> messages, CancellationToken signal) {
            signalRef.set(signal);
            return CompletableFuture.completedFuture(List.of(new UserMessage("helper", 4L)));
        }
    }
}
