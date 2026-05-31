/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.server;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.CustomModelLoader;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.model.ModelCatalogService;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.Settings;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Handles HTTP endpoints that mutate {@code ~/file/.campusclaw/agent/settings.json}
 * from a UI. Three endpoints today, all writing to the global file only:
 *
 * <ul>
 *   <li>GET    /api/settings/models               — current {@code defaultModel} +
 *       {@code customModels} + the resolved list of available models the picker
 *       should display.</li>
 *   <li>PUT    /api/settings/models/default       — sets {@code defaultModel}.
 *       Request body: {@code {"model": "<id>"}}. Validates the id is present in
 *       the registry; 400 otherwise.</li>
 *   <li>PUT    /api/settings/customModels         — full idempotent replacement
 *       of the {@code customModels} array. Triggers
 *       {@link CustomModelLoader#refresh()} so the next WebSocket connection
 *       (or {@code list_models} command) sees the new catalogue.</li>
 * </ul>
 *
 * <p>Persisted writes don't notify existing WebSocket connections — clients
 * that care should rely on a fresh connection to observe the change. This
 * matches the broader pattern of {@link SettingsManager#load()} re-reading
 * the file on every call rather than caching.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/22]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class SettingsHandler {

    private static final Logger log = LoggerFactory.getLogger(SettingsHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Settings.CustomModelConfig>> CUSTOM_MODEL_LIST_TYPE =
            new TypeReference<>() {};

    private final SettingsManager settingsManager;
    private final ModelRegistry modelRegistry;
    private final ModelCatalogService modelCatalog;
    private final CustomModelLoader customModelLoader;

    public SettingsHandler(
            SettingsManager settingsManager,
            ModelRegistry modelRegistry,
            ModelCatalogService modelCatalog,
            CustomModelLoader customModelLoader) {
        this.settingsManager = settingsManager;
        this.modelRegistry = modelRegistry;
        this.modelCatalog = modelCatalog;
        this.customModelLoader = customModelLoader;
    }

    /**
     * GET /api/settings/models — snapshot for the configuration UI.
     *
     * @param request the request
     * @return the response Mono
     */
    public Mono<ServerResponse> getModels(ServerRequest request) {
        return Mono.fromCallable(this::buildModelsSnapshot)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(snapshot -> ServerResponse.ok().bodyValue(snapshot))
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed to read settings snapshot", e);
                    return ServerResponse.status(500).bodyValue(Map.of("error", "Internal error: " + e.getMessage()));
                });
    }

    /**
     * PUT /api/settings/models/default — body {@code {"model": "<id>"}}.
     *
     * @param request the request
     * @return the response Mono
     */
    public Mono<ServerResponse> setDefaultModel(ServerRequest request) {
        return request.bodyToMono(DefaultModelRequest.class)
                .defaultIfEmpty(new DefaultModelRequest(null))
                .flatMap(
                        req -> Mono.fromCallable(() -> applyDefaultModel(req)).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(this::toResponse)
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed to update defaultModel", e);
                    return ServerResponse.status(500).bodyValue(Map.of("error", "Internal error: " + e.getMessage()));
                });
    }

    /**
     * PUT /api/settings/customModels — body is a JSON array of
     * {@link Settings.CustomModelConfig}; replaces the existing list.
     *
     * @param request the request
     * @return the response Mono
     */
    public Mono<ServerResponse> setCustomModels(ServerRequest request) {
        return request.bodyToMono(JsonNode.class)
                .defaultIfEmpty(MAPPER.createArrayNode())
                .flatMap(body ->
                        Mono.fromCallable(() -> applyCustomModels(body)).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(this::toResponse)
                .onErrorResume(Exception.class, e -> {
                    log.error("Failed to update customModels", e);
                    return ServerResponse.status(500).bodyValue(Map.of("error", "Internal error: " + e.getMessage()));
                });
    }

    Map<String, Object> buildModelsSnapshot() {
        Settings settings = settingsManager.load();
        List<Model> available = modelCatalog.getAvailableModels();
        var entries = new ArrayList<Map<String, Object>>(available.size());
        for (Model m : available) {
            entries.add(modelToWireFormat(m, modelCatalog.hasCredentials(m)));
        }

        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("defaultModel", settings.resolvedDefaultModel());
        snapshot.put("customModels", settings.customModels() == null ? List.of() : settings.customModels());
        snapshot.put("availableModels", entries);
        snapshot.put("filtered", modelCatalog.isFiltered());
        return snapshot;
    }

    ApiResult applyDefaultModel(DefaultModelRequest req) {
        if (req == null || req.model() == null || req.model().isBlank()) {
            return ApiResult.badRequest("model is required");
        }
        String requested = req.model();
        if (!modelIdExists(requested)) {
            return ApiResult.badRequest("unknown model: " + requested);
        }
        settingsManager.setGlobal("defaultModel", requested);
        return ApiResult.ok(Map.of("defaultModel", requested));
    }

    ApiResult applyCustomModels(JsonNode body) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return ApiResult.badRequest("request body must be a JSON array of customModels");
        }
        if (!body.isArray()) {
            return ApiResult.badRequest("customModels payload must be a JSON array");
        }

        List<Settings.CustomModelConfig> parsed;
        try {
            parsed = MAPPER.convertValue(body, CUSTOM_MODEL_LIST_TYPE);
        } catch (IllegalArgumentException e) {
            return ApiResult.badRequest("malformed customModels payload: " + e.getMessage());
        }
        if (parsed == null) {
            parsed = List.of();
        }

        String validationError = validateCustomModels(parsed);
        if (validationError != null) {
            return ApiResult.badRequest(validationError);
        }

        settingsManager.setGlobal("customModels", parsed);
        customModelLoader.refresh();
        return ApiResult.ok(Map.of("customModels", parsed, "count", parsed.size()));
    }

    @Nullable
    private static String validateCustomModels(List<Settings.CustomModelConfig> list) {
        Set<String> seenIds = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            Settings.CustomModelConfig cfg = list.get(i);
            if (cfg == null) {
                return "customModels[" + i + "] is null";
            }
            if (cfg.id() == null || cfg.id().isBlank()) {
                return "customModels[" + i + "].id is required";
            }
            if (cfg.api() == null || cfg.api().isBlank()) {
                return "customModels[" + i + "].api is required (id=" + cfg.id() + ")";
            }
            if (!seenIds.add(cfg.id())) {
                return "duplicate customModels id: " + cfg.id();
            }
        }
        return null;
    }

    private boolean modelIdExists(String modelId) {
        for (Model m : modelRegistry.getAllModels()) {
            if (m.id().equals(modelId)) {
                return true;
            }
        }
        return false;
    }

    private Mono<ServerResponse> toResponse(ApiResult result) {
        if (result.errorMessage != null) {
            return ServerResponse.status(result.status).bodyValue(Map.of("error", result.errorMessage));
        }
        return ServerResponse.ok().bodyValue(result.body);
    }

    private static Map<String, Object> modelToWireFormat(Model m, boolean hasCredentials) {
        var entry = new LinkedHashMap<String, Object>();
        entry.put("id", m.id());
        entry.put("name", m.name());
        entry.put("provider", m.provider().value());
        entry.put("contextWindow", m.contextWindow());
        entry.put("maxTokens", m.maxTokens());
        entry.put("reasoning", m.reasoning());
        entry.put("hasCredentials", hasCredentials);
        if (m.cost() != null) {
            var cost = new LinkedHashMap<String, Object>();
            cost.put("input", m.cost().input());
            cost.put("output", m.cost().output());
            cost.put("cacheRead", m.cost().cacheRead());
            cost.put("cacheWrite", m.cost().cacheWrite());
            entry.put("cost", cost);
        }
        return entry;
    }

    /**
     * Request payload for {@code PUT /api/settings/models/default}.
     */
    public record DefaultModelRequest(@JsonProperty("model") @Nullable String model) {}

    static final class ApiResult {
        final int status;

        @Nullable
        final String errorMessage;

        @Nullable
        final Map<String, Object> body;

        private ApiResult(int status, @Nullable String errorMessage, @Nullable Map<String, Object> body) {
            this.status = status;
            this.errorMessage = errorMessage;
            this.body = body;
        }

        static ApiResult ok(Map<String, Object> body) {
            return new ApiResult(200, null, body);
        }

        static ApiResult badRequest(String message) {
            return new ApiResult(400, message, null);
        }
    }
}
