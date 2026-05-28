/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.mode.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import com.campusclaw.ai.CampusClawAiService;
import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.codingagent.config.CustomModelLoader;
import com.campusclaw.codingagent.model.ModelCatalogService;
import com.campusclaw.codingagent.prompt.SystemPromptBuilder;
import com.campusclaw.codingagent.session.SessionConfig;
import com.campusclaw.codingagent.settings.SettingsManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

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

        @Test
        void withSettingsHandlerProducesRouter() throws Exception {
            ServerMode server = buildServer(
                    mock(SettingsManager.class), mock(CustomModelLoader.class), mock(ModelCatalogService.class));

            RouterFunction<ServerResponse> routes = invokeBuildRoutes(server, mock(SettingsHandler.class));
            assertThat(routes).isNotNull();
        }

        @Test
        void withoutSettingsHandlerProducesRouter() throws Exception {
            ServerMode server = buildServer(null, null, null);

            RouterFunction<ServerResponse> routes = invokeBuildRoutes(server, null);
            assertThat(routes).isNotNull();
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

            assertFieldNull(server, "settingsManager");
            assertFieldNull(server, "customModelLoader");
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

            assertFieldNull(server, "settingsManager");
            assertFieldNull(server, "customModelLoader");
        }

        private static void assertFieldNull(ServerMode server, String name) throws Exception {
            Field f = ServerMode.class.getDeclaredField(name);
            f.setAccessible(true);
            assertThat(f.get(server)).isNull();
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
