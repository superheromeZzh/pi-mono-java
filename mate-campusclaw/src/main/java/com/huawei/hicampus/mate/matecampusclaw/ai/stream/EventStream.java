package com.huawei.hicampus.mate.matecampusclaw.ai.stream;

import java.util.function.Function;
import java.util.function.Predicate;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * A push-based event stream that bridges imperative event emission
 * into Reactor's {@link Flux}/{@link Mono} model.
 *
 * <p>Producers call {@link #push(Object)} to emit events. The stream completes
 * when either the {@code isComplete} predicate matches an event (auto-extracting
 * the result via {@code extractResult}) or {@link #end(Object)} is called explicitly.
 * Consumers subscribe via {@link #asFlux()} and retrieve the final result via
 * {@link #result()}.
 *
 * <p>Thread-safe: multiple threads may call {@code push()} concurrently.
 * Internally uses a {@link Sinks.Many} unicast sink with unbounded buffering,
 * so events pushed before subscription are queued and delivered when a subscriber
 * connects.
 *
 * @param <T> the event type
 * @param <R> the final result type
 */
public class EventStream<T, R> {

    private final Predicate<T> isComplete;
    private final Function<T, R> extractResult;

    private final Sinks.Many<T> eventSink;
    private final Sinks.One<R> resultSink;

    private final Object lock = new Object();
    private boolean done = false;

    /**
     * Creates an EventStream.
     *
     * @param isComplete    predicate tested on each pushed event; when it returns
     *                      {@code true}, the stream auto-completes and the result
     *                      is extracted from that event
     * @param extractResult function to extract the final result from the completion event
     */
    public EventStream(Predicate<T> isComplete, Function<T, R> extractResult) {
        this.isComplete = isComplete;
        this.extractResult = extractResult;
        this.eventSink = Sinks.many().unicast().onBackpressureBuffer();
        this.resultSink = Sinks.one();
    }

    /**
     * Pushes an event into the stream.
     *
     * <p>If the {@code isComplete} predicate returns {@code true} for this event,
     * the result is extracted and the stream completes. The completion event itself
     * is still delivered to subscribers before the stream terminates.
     *
     * <p>Events pushed after the stream has ended are silently ignored.
     *
     * @param event the event to push
     */
    public void push(T event) {
        synchronized (lock) {
            if (done) return;

            if (isComplete.test(event)) {
                done = true;
                resultSink.tryEmitValue(extractResult.apply(event));
            }

            eventSink.tryEmitNext(event).orThrow();

            if (done) {
                eventSink.tryEmitComplete();
            }
        }
    }

    /**
     * Ends the stream with an explicit result.
     *
     * <p>No additional event is emitted; the Flux completes and the result Mono
     * resolves with the given value. Calls after the stream has already ended
     * are silently ignored.
     *
     * @param result the final result value
     */
    public void end(R result) {
        synchronized (lock) {
            if (done) return;
            done = true;
            resultSink.tryEmitValue(result);
            eventSink.tryEmitComplete();
        }
    }

    /**
     * Ends the stream without providing a result.
     *
     * <p>The Flux completes. If the result was already set by a prior
     * {@link #push(Object)} that triggered {@code isComplete}, the result Mono
     * retains that value. Otherwise the result Mono completes empty.
     */
    public void end() {
        synchronized (lock) {
            if (done) return;
            done = true;
            resultSink.tryEmitEmpty();
            eventSink.tryEmitComplete();
        }
    }

    /**
     * Terminates the stream with an error.
     *
     * <p>The error is propagated to both the event Flux and the result Mono.
     * Calls after the stream has already ended are silently ignored.
     *
     * @param e the error to propagate
     */
    public void error(Throwable e) {
        synchronized (lock) {
            if (done) return;
            done = true;
            resultSink.tryEmitError(e);
            eventSink.tryEmitError(e);
        }
    }

    /**
     * Returns the event stream as a {@link Flux}.
     *
     * <p>This is a unicast Flux — only one subscriber is supported.
     * Events pushed before subscription are buffered and delivered
     * when a subscriber connects.
     */
    public Flux<T> asFlux() {
        return eventSink.asFlux();
    }

    /**
     * Returns a {@link Mono} that resolves to the final result.
     *
     * <p>The result is set either by a {@link #push(Object)} event that triggers
     * the {@code isComplete} predicate, or by an explicit call to {@link #end(Object)}.
     * If the stream ends without a result ({@link #end()}), the Mono completes empty.
     * If the stream ends with an error ({@link #error(Throwable)}), the Mono signals
     * that error.
     */
    public Mono<R> result() {
        return resultSink.asMono();
    }
}
