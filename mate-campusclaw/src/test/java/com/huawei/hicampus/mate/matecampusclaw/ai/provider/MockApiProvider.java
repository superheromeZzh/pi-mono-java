/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.ai.provider;

import java.util.List;

import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Api;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Context;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.SimpleStreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StopReason;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.StreamOptions;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Usage;

import jakarta.annotation.Nullable;

/**
 * A mock {@link ApiProvider} for testing that emits a configurable
 * sequence of events into an {@link AssistantMessageEventStream}.
 *
 * <p>By default, produces a simple text response: start -> text_start ->
 * text_delta -> text_end -> done. Custom event sequences can be supplied
 * via the constructor.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class MockApiProvider implements ApiProvider {

    private final Api api;
    private final List<AssistantMessageEvent> events;

    /**
     * Creates a MockApiProvider that returns a default text-response stream.
     *
     * @param api the API protocol to report
     */
    public MockApiProvider(Api api) {
        this(api, null);
    }

    /**
     * Creates a MockApiProvider with a custom event sequence.
     *
     * @param api    the API protocol to report
     * @param events custom events to emit, or {@code null} for the default sequence
     */
    public MockApiProvider(Api api, @Nullable List<AssistantMessageEvent> events) {
        this.api = api;
        this.events = events;
    }

    @Override
    public Api getApi() {
        return api;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, @Nullable StreamOptions options) {
        return buildStream();
    }

    @Override
    public AssistantMessageEventStream streamSimple(
            Model model, Context context, @Nullable SimpleStreamOptions options) {
        return buildStream();
    }

    private AssistantMessageEventStream buildStream() {
        var stream = new AssistantMessageEventStream();
        if (events != null) {
            for (var event : events) {
                stream.push(event);
            }
        } else {
            var partial = defaultPartialMessage();
            var finalMsg = defaultFinalMessage();
            stream.push(new AssistantMessageEvent.StartEvent(partial));
            stream.push(new AssistantMessageEvent.TextStartEvent(0, partial));
            stream.push(new AssistantMessageEvent.TextDeltaEvent(0, "Hello from mock", partial));
            stream.push(new AssistantMessageEvent.TextEndEvent(0, "Hello from mock", partial));
            stream.push(new AssistantMessageEvent.DoneEvent(StopReason.STOP, finalMsg));
        }
        return stream;
    }

    private AssistantMessage defaultPartialMessage() {
        return new AssistantMessage(
                List.of(new TextContent("Hello from mock")),
                api.value(),
                "mock",
                "mock-model",
                null,
                Usage.empty(),
                StopReason.STOP,
                null,
                System.currentTimeMillis());
    }

    private AssistantMessage defaultFinalMessage() {
        return new AssistantMessage(
                List.of(new TextContent("Hello from mock")),
                api.value(),
                "mock",
                "mock-model",
                null,
                Usage.empty(),
                StopReason.STOP,
                null,
                System.currentTimeMillis());
    }
}
