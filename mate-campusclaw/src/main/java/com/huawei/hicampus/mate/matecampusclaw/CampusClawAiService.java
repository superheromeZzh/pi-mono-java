package com.huawei.hicampus.mate.matecampusclaw.ai;

import java.util.List;
import java.util.Objects;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProvider;
import com.huawei.hicampus.mate.matecampusclaw.ai.provider.ApiProviderRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEvent;
import com.huawei.hicampus.mate.matecampusclaw.ai.stream.AssistantMessageEventStream;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.*;

import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import reactor.core.publisher.Mono;

/**
 * Top-level API for interacting with LLM providers.
 *
 * <p>Provides streaming ({@link #stream}, {@link #streamSimple}) and blocking
 * ({@link #complete}, {@link #completeSimple}) methods that look up the
 * appropriate {@link ApiProvider} from the {@link ApiProviderRegistry} based
 * on the model's {@link Api} type, then delegate the actual call.
 *
 * <p>Corresponds to the top-level {@code stream()} / {@code complete()} functions
 * in the TypeScript campusclaw-ai module (section 1.10 of the architecture doc).
 */
@Service
public class CampusClawAiService {

    private final ApiProviderRegistry providerRegistry;
    private final ModelRegistry modelRegistry;

    public CampusClawAiService(ApiProviderRegistry providerRegistry, ModelRegistry modelRegistry) {
        this.providerRegistry = Objects.requireNonNull(providerRegistry, "providerRegistry must not be null");
        this.modelRegistry = Objects.requireNonNull(modelRegistry, "modelRegistry must not be null");
    }

    /**
     * Starts a streaming LLM call with full provider-specific options.
     *
     * @param model   the model to invoke
     * @param context the conversation context (system prompt, messages, tools)
     * @param options streaming options, or {@code null} for defaults
     * @return an event stream of assistant message events
     * @throws IllegalArgumentException if no provider is registered for the model's API
     */
    public AssistantMessageEventStream stream(Model model, Context context, @Nullable StreamOptions options) {
        var provider = resolveProvider(model);
        return provider.stream(model, context, options);
    }

    /**
     * Starts a streaming LLM call with simplified options that include
     * reasoning/thinking configuration.
     *
     * @param model   the model to invoke
     * @param context the conversation context (system prompt, messages, tools)
     * @param options simple streaming options with reasoning config, or {@code null} for defaults
     * @return an event stream of assistant message events
     * @throws IllegalArgumentException if no provider is registered for the model's API
     */
    public AssistantMessageEventStream streamSimple(Model model, Context context, @Nullable SimpleStreamOptions options) {
        var provider = resolveProvider(model);
        return provider.streamSimple(model, context, options);
    }

    /**
     * Makes a blocking LLM call with full options by consuming the stream until
     * a {@link AssistantMessageEvent.DoneEvent} or {@link AssistantMessageEvent.ErrorEvent}
     * is received, then returns the final {@link AssistantMessage}.
     *
     * @param model   the model to invoke
     * @param context the conversation context
     * @param options streaming options, or {@code null} for defaults
     * @return a Mono that resolves to the complete assistant message
     */
    public Mono<AssistantMessage> complete(Model model, Context context, @Nullable StreamOptions options) {
        return stream(model, context, options).result();
    }

    /**
     * Makes a blocking LLM call with simplified options by consuming the stream until
     * completion, then returns the final {@link AssistantMessage}.
     *
     * @param model   the model to invoke
     * @param context the conversation context
     * @param options simple streaming options, or {@code null} for defaults
     * @return a Mono that resolves to the complete assistant message
     */
    public Mono<AssistantMessage> completeSimple(Model model, Context context, @Nullable SimpleStreamOptions options) {
        return streamSimple(model, context, options).result();
    }

    /**
     * Convenience method: sends a single user text message and returns the
     * complete assistant response.
     *
     * @param model       the model to invoke
     * @param userMessage the user's text message
     * @return a Mono that resolves to the complete assistant message
     */
    public Mono<AssistantMessage> complete(Model model, String userMessage) {
        Objects.requireNonNull(userMessage, "userMessage must not be null");
        var context = new Context(
            null,
            List.of(new UserMessage(userMessage, System.currentTimeMillis())),
            null
        );
        return complete(model, context, null);
    }

    /**
     * Returns the provider registry used by this service.
     */
    public ApiProviderRegistry getProviderRegistry() {
        return providerRegistry;
    }

    /**
     * Returns the model registry used by this service.
     */
    public ModelRegistry getModelRegistry() {
        return modelRegistry;
    }

    private ApiProvider resolveProvider(Model model) {
        Objects.requireNonNull(model, "model must not be null");
        return providerRegistry.getProvider(model.api())
            .orElseThrow(() -> new IllegalArgumentException(
                "No ApiProvider registered for API: " + model.api().value()));
    }
}
