/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.mode.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.config.CustomModelLoader;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.model.ModelCatalogService;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.prompt.SystemPromptBuilder;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.session.SessionConfig;
import com.huawei.hicampus.mate.matecampusclaw.codingagent.settings.SettingsManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RequestPredicate;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

/**
 * Drives the wiring paths in {@link ServerMode} introduced by d986437c —
 * the new constructor that accepts {@link SettingsManager} +
 * {@link CustomModelLoader}, the {@code buildSettingsHandler} guard, and the
 * {@code buildRoutes} branch that adds {@code /api/settings/*} only when all
 * three settings deps are wired.
 *
 * <p>{@code run()} itself binds a real TCP port and is excluded — the test
 * exercises the same private helpers that {@code run()} delegates to.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/28]
 * @since [br_eCampusCore 25.1.0_Next]
 */
class ServerModeTest {

    private static ServerMode buildServer(
            SettingsManager settingsManager, CustomModelLoader customModelLoader, ModelCatalogService modelCatalog) {
        return new ServerMode(
                mock(CampusClawAiService.class),
                mock(ModelRegistry.class),
                mock(SystemPromptBuilder.class),
                List.of(),
                mock(SessionConfig.class),
                3000,
                "localhost",
                null,
                false,
                modelCatalog,
                true,
                settingsManager,
                customModelLoader);
    }

    private static Object invokeBuildSettingsHandler(ServerMode server) throws Exception {
        Method m = ServerMode.class.getDeclaredMethod("buildSettingsHandler");
        m.setAccessible(true);
        return m.invoke(server);
    }

    @SuppressWarnings("unchecked")
    private static RouterFunction<ServerResponse> invokeBuildRoutes(ServerMode server, SettingsHandler handler)
            throws Exception {
        Method m = ServerMode.class.getDeclaredMethod(
                "buildRoutes", ChatHandler.class, SkillHandler.class, SessionPool.class, SettingsHandler.class);
        m.setAccessible(true);
        return (RouterFunction<ServerResponse>)
                m.invoke(server, mock(ChatHandler.class), mock(SkillHandler.class), mock(SessionPool.class), handler);
    }

    @Nested
    class BuildSettingsHandler {

        @Test
        void allDepsWiredReturnsHandler() throws Exception {
            ServerMode server = buildServer(
                    mock(SettingsManager.class), mock(CustomModelLoader.class), mock(ModelCatalogService.class));

            Object handler = invokeBuildSettingsHandler(server);
            assertThat(handler).isInstanceOf(SettingsHandler.class);
        }

        @Test
        void nullSettingsManagerReturnsNull() throws Exception {
            ServerMode server = buildServer(null, mock(CustomModelLoader.class), mock(ModelCatalogService.class));
            assertThat(invokeBuildSettingsHandler(server)).isNull();
        }

        @Test
        void nullCustomModelLoaderReturnsNull() throws Exception {
            ServerMode server = buildServer(mock(SettingsManager.class), null, mock(ModelCatalogService.class));
            assertThat(invokeBuildSettingsHandler(server)).isNull();
        }

        @Test
        void nullModelCatalogReturnsNull() throws Exception {
            ServerMode server = buildServer(mock(SettingsManager.class), mock(CustomModelLoader.class), null);
            assertThat(invokeBuildSettingsHandler(server)).isNull();
        }
    }

    @Nested
    class BuildRoutes {

        // The settings handler branch should add exactly 3 routes:
        // GET /api/settings/models, PUT /api/settings/models/default, PUT /api/settings/customModels.
        @Test
        void addsThreeSettingsRoutesWhenHandlerProvided() throws Exception {
            ServerMode server = buildServer(
                    mock(SettingsManager.class), mock(CustomModelLoader.class), mock(ModelCatalogService.class));

            int withHandler = countRoutes(invokeBuildRoutes(server, mock(SettingsHandler.class)));
            int withoutHandler = countRoutes(invokeBuildRoutes(server, null));

            assertThat(withHandler - withoutHandler).isEqualTo(3);
        }

        @Test
        void includesAllBaselineRoutesWhenSettingsHandlerNull() throws Exception {
            ServerMode server = buildServer(null, null, null);

            // Baseline routes: health, chat, list-conversations, delete-conversation,
            // skills upload/list/delete/enable/disable = 9 routes
            int routes = countRoutes(invokeBuildRoutes(server, null));
            assertThat(routes).isEqualTo(9);
        }

        private static int countRoutes(RouterFunction<ServerResponse> routes) {
            CountingVisitor v = new CountingVisitor();
            routes.accept(v);
            return v.count;
        }

        private static final class CountingVisitor implements RouterFunctions.Visitor {
            int count;

            @Override
            public void startNested(RequestPredicate predicate) {}

            @Override
            public void endNested(RequestPredicate predicate) {}

            @Override
            public void route(RequestPredicate predicate, HandlerFunction<?> handlerFunction) {
                count++;
            }

            @Override
            public void resources(Function<ServerRequest, Mono<Resource>> lookupFunction) {}

            @Override
            public void attributes(Map<String, Object> attributes) {}

            @Override
            public void unknown(RouterFunction<?> routerFunction) {}
        }
    }

    @Nested
    class LegacyOverloads {

        @Test
        void sixArgConstructorLeavesSettingsNull() throws Exception {
            ServerMode server = new ServerMode(
                    mock(CampusClawAiService.class),
                    mock(ModelRegistry.class),
                    mock(SystemPromptBuilder.class),
                    List.of(),
                    mock(SessionConfig.class),
                    3000);

            assertThat(readField(server, "settingsManager")).isNull();
            assertThat(readField(server, "customModelLoader")).isNull();
        }

        @Test
        void elevenArgConstructorLeavesSettingsNull() throws Exception {
            ServerMode server = new ServerMode(
                    mock(CampusClawAiService.class),
                    mock(ModelRegistry.class),
                    mock(SystemPromptBuilder.class),
                    List.of(),
                    mock(SessionConfig.class),
                    3000,
                    "localhost",
                    null,
                    false,
                    mock(ModelCatalogService.class),
                    true);

            assertThat(readField(server, "settingsManager")).isNull();
            assertThat(readField(server, "customModelLoader")).isNull();
        }

        private static Object readField(ServerMode server, String name) throws Exception {
            Field f = ServerMode.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(server);
        }
    }

    @Nested
    class ExtractQueryParam {

        @Test
        void returnsValueForPresentParam() {
            String value = ServerMode.extractQueryParam(
                    "http://localhost/api/ws/chat?conversation_id=abc123", "conversation_id");
            assertThat(value).isEqualTo("abc123");
        }

        @Test
        void returnsNullWhenAbsent() {
            String value = ServerMode.extractQueryParam("http://localhost/api/ws/chat?foo=bar", "conversation_id");
            assertThat(value).isNull();
        }

        @Test
        void returnsNullWhenNoQueryString() {
            assertThat(ServerMode.extractQueryParam("http://localhost/api/ws/chat", "conversation_id"))
                    .isNull();
        }

        @Test
        void returnsNullForNullUri() {
            assertThat(ServerMode.extractQueryParam(null, "conversation_id")).isNull();
        }

        @Test
        void returnsNullForEmptyValue() {
            assertThat(ServerMode.extractQueryParam("http://x/y?conversation_id=", "conversation_id"))
                    .isNull();
        }

        @Test
        void urlDecodesValue() {
            String value = ServerMode.extractQueryParam("http://x/y?conversation_id=hello%20world", "conversation_id");
            assertThat(value).isEqualTo("hello world");
        }

        @Test
        void returnsNullWhenQueryEndsWithQuestionMark() {
            assertThat(ServerMode.extractQueryParam("http://x/y?", "conversation_id"))
                    .isNull();
        }
    }
}
