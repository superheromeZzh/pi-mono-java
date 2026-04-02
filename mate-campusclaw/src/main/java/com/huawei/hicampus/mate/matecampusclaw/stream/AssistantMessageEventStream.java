package com.huawei.hicampus.mate.matecampusclaw.ai.stream;

import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A specialized {@link EventStream} for {@link AssistantMessageEvent}s that
 * resolves to a final {@link AssistantMessage}.
 *
 * <p>The stream auto-completes when a {@link AssistantMessageEvent.DoneEvent}
 * or {@link AssistantMessageEvent.ErrorEvent} is pushed, extracting the
 * {@link AssistantMessage} from the terminal event.
 *
 * <p>Provides convenience methods for pushing common event types without
 * manually constructing the event records.
 */
public class AssistantMessageEventStream {

    private final EventStream<AssistantMessageEvent, AssistantMessage> delegate;

    /**
     * Creates a new AssistantMessageEventStream.
     */
    public AssistantMessageEventStream() {
        this.delegate = new EventStream<>(
            AssistantMessageEventStream::isTerminal,
            AssistantMessageEventStream::extractMessage
        );
    }

    private static boolean isTerminal(AssistantMessageEvent event) {
        return event instanceof AssistantMessageEvent.DoneEvent
            || event instanceof AssistantMessageEvent.ErrorEvent;
    }

    private static AssistantMessage extractMessage(AssistantMessageEvent event) {
        return switch (event) {
            case AssistantMessageEvent.DoneEvent e -> e.message();
            case AssistantMessageEvent.ErrorEvent e -> e.error();
            default -> throw new IllegalStateException(
                "extractMessage called on non-terminal event: " + event.getClass().getSimpleName()
            );
        };
    }

    /**
     * Pushes a raw event into the stream.
     *
     * @param event the event to push
     */
    public void push(AssistantMessageEvent event) {
        delegate.push(event);
    }

    /**
     * Pushes a {@link AssistantMessageEvent.TextDeltaEvent}.
     *
     * @param contentIndex the index of the text content block
     * @param delta        the incremental text fragment
     * @param partial      the current partial assistant message
     */
    public void pushTextDelta(int contentIndex, String delta, AssistantMessage partial) {
        delegate.push(new AssistantMessageEvent.TextDeltaEvent(contentIndex, delta, partial));
    }

    /**
     * Pushes a {@link AssistantMessageEvent.DoneEvent}, completing the stream.
     *
     * @param reason  the stop reason
     * @param message the final complete assistant message
     */
    public void pushDone(StopReason reason, AssistantMessage message) {
        delegate.push(new AssistantMessageEvent.DoneEvent(reason, message));
    }

    /**
     * Pushes an {@link AssistantMessageEvent.ErrorEvent}, completing the stream.
     *
     * @param reason the error reason (e.g. "error" or "aborted")
     * @param error  the assistant message containing error details
     */
    public void pushError(String reason, AssistantMessage error) {
        delegate.push(new AssistantMessageEvent.ErrorEvent(reason, error));
    }

    /**
     * Ends the stream with an explicit result, without emitting an additional event.
     *
     * @param result the final assistant message
     */
    public void end(AssistantMessage result) {
        delegate.end(result);
    }

    /**
     * Terminates the stream with an error.
     *
     * @param e the error to propagate
     */
    public void error(Throwable e) {
        delegate.error(e);
    }

    /**
     * Returns the event stream as a {@link Flux}.
     */
    public Flux<AssistantMessageEvent> asFlux() {
        return delegate.asFlux();
    }

    /**
     * Returns a {@link Mono} that resolves to the final {@link AssistantMessage}.
     */
    public Mono<AssistantMessage> result() {
        return delegate.result();
    }
}
