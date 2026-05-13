/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.mode.server;

import java.util.List;
import java.util.Map;

import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.ai.CampusClawAiService;
import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.codingagent.config.AppPaths;
import com.campusclaw.codingagent.model.ModelCatalogService;
import com.campusclaw.codingagent.prompt.SystemPromptBuilder;
import com.campusclaw.codingagent.session.SessionConfig;
import com.campusclaw.codingagent.skill.SandboxSkillParser;
import com.campusclaw.codingagent.skill.SkillLoader;
import com.campusclaw.codingagent.skill.SkillManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.reactive.ReactorHttpHandlerAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerResponse;

/**
 * HTTP server mode: starts a Reactor Netty server exposing REST + SSE endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET    /api/health                — health check</li>
 *   <li>POST   /api/chat                  — streaming chat (SSE), multi-conversation</li>
 *   <li>DELETE /api/conversations/{id}     — remove a conversation</li>
 *   <li>POST   /api/skills                — upload skill archive</li>
 *   <li>GET    /api/skills                — list installed skills</li>
 *   <li>DELETE /api/skills/{name}         — remove a skill</li>
 *   <li>POST   /api/skills/{name}/enable  — enable a skill</li>
 *   <li>POST   /api/skills/{name}/disable — disable a skill</li>
 * </ul>
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public class ServerMode {

    private static final Logger log = LoggerFactory.getLogger(ServerMode.class);

    private final CampusClawAiService aiService;
    private final ModelRegistry modelRegistry;
    private final SystemPromptBuilder promptBuilder;
    private final List<AgentTool> tools;
    private final SessionConfig baseConfig;
    private final int port;
    private final String host;
    private final SandboxSkillParser sandboxParser;
    private final boolean useSandbox;
    private final ModelCatalogService modelCatalog;
    private final boolean sessionPersistenceEnabled;

    public ServerMode(
            CampusClawAiService aiService,
            ModelRegistry modelRegistry,
            SystemPromptBuilder promptBuilder,
            List<AgentTool> tools,
            SessionConfig baseConfig,
            int port) {
        this(aiService, modelRegistry, promptBuilder, tools, baseConfig, port, "localhost", null, false, null, true);
    }

    public ServerMode(
            CampusClawAiService aiService,
            ModelRegistry modelRegistry,
            SystemPromptBuilder promptBuilder,
            List<AgentTool> tools,
            SessionConfig baseConfig,
            int port,
            String host,
            SandboxSkillParser sandboxParser,
            boolean useSandbox,
            ModelCatalogService modelCatalog) {
        this(
                aiService,
                modelRegistry,
                promptBuilder,
                tools,
                baseConfig,
                port,
                host,
                sandboxParser,
                useSandbox,
                modelCatalog,
                true);
    }

    public ServerMode(
            CampusClawAiService aiService,
            ModelRegistry modelRegistry,
            SystemPromptBuilder promptBuilder,
            List<AgentTool> tools,
            SessionConfig baseConfig,
            int port,
            String host,
            SandboxSkillParser sandboxParser,
            boolean useSandbox,
            ModelCatalogService modelCatalog,
            boolean sessionPersistenceEnabled) {
        this.aiService = aiService;
        this.modelRegistry = modelRegistry;
        this.promptBuilder = promptBuilder;
        this.tools = tools;
        this.baseConfig = baseConfig;
        this.port = port;
        this.host = host;
        this.sandboxParser = sandboxParser;
        this.useSandbox = useSandbox;
        this.modelCatalog = modelCatalog;
        this.sessionPersistenceEnabled = sessionPersistenceEnabled;
    }

    public void run() {
        var sessionPool = new SessionPool(
                aiService,
                modelRegistry,
                promptBuilder,
                tools,
                baseConfig,
                sandboxParser,
                useSandbox,
                sessionPersistenceEnabled);
        var chatHandler = new ChatHandler(sessionPool);
        var wsHandler = new ChatWebSocketHandler(sessionPool, modelCatalog);
        var conversationLister = new com.campusclaw.codingagent.session.ConversationLister();

        var skillHandler = new SkillHandler(
                new SkillManager(AppPaths.USER_SKILLS_DIR, sandboxParser, useSandbox),
                new SkillLoader(sandboxParser, useSandbox));

        RouterFunction<ServerResponse> routes = RouterFunctions.route()
                .GET("/api/health", req -> ServerResponse.ok().bodyValue(Map.of("status", "ok")))
                .POST("/api/chat", chatHandler::chat)
                .GET("/api/conversations", req -> ServerResponse.ok()
                        .bodyValue(Map.of(
                                "conversations",
                                com.campusclaw.codingagent.session.ConversationLister.toWireFormat(
                                        conversationLister.listForServer()))))
                .DELETE("/api/conversations/{id}", req -> {
                    String id = req.pathVariable("id");
                    boolean removed = sessionPool.remove(id);
                    if (removed) {
                        return ServerResponse.ok().bodyValue(Map.of("message", "Removed conversation: " + id));
                    }
                    return ServerResponse.status(404).bodyValue(Map.of("error", "Conversation not found: " + id));
                })
                .POST("/api/skills", skillHandler::upload)
                .GET("/api/skills", skillHandler::list)
                .DELETE("/api/skills/{name}", skillHandler::delete)
                .POST("/api/skills/{name}/enable", skillHandler::enable)
                .POST("/api/skills/{name}/disable", skillHandler::disable)
                .build();

        var httpHandler = RouterFunctions.toHttpHandler(routes);
        var adapter = new ReactorHttpHandlerAdapter(httpHandler);

        var server = HttpServer.create()
                .host(host)
                .port(port)
                .route(r -> r
                        // CORS preflight — `fetch()` from the Vite dev server (a different
                        // origin than the API) sends OPTIONS for any non-simple request.
                        // Reply 204 with the allow headers; browsers cache for 1h.
                        .options("/api/**", (req, res) -> {
                            applyCorsHeaders(res);
                            return res.status(204).send();
                        })
                        .get("/api/ws/chat", (req, res) -> {
                            String convId = extractQueryParam(req.uri(), "conversation_id");
                            return res.sendWebsocket((in, out) -> wsHandler.handle(in, out, convId));
                        })
                        // All other routes (the WebFlux RouterFunctions adapter) go
                        // through here. We pre-stamp CORS headers on the response so
                        // even simple GETs from the browser pass the same-origin check.
                        .route(req -> true, (req, res) -> {
                            applyCorsHeaders(res);
                            return adapter.apply(req, res);
                        }))
                .bindNow();

        log.info("CampusClaw API server started on {}:{}", host, port);
        System.out.println("CampusClaw API server started on http://" + host + ":" + port);
        System.out.println("Endpoints:");
        System.out.println("  GET    /api/health");
        System.out.println("  POST   /api/chat");
        System.out.println("  DELETE /api/conversations/{id}");
        System.out.println("  POST   /api/skills");
        System.out.println("  GET    /api/skills");
        System.out.println("  DELETE /api/skills/{name}");
        System.out.println("  POST   /api/skills/{name}/enable");
        System.out.println("  POST   /api/skills/{name}/disable");
        System.out.println("  WS     /api/ws/chat");

        server.onDispose().block();
        sessionPool.shutdown();
    }

    /**
     * Stamps the response with permissive CORS headers so the Vite dev server
     * (a different origin than the API) can call the REST endpoints from JS.
     *
     * <p>Origin is wide-open ({@code *}) because the server is meant to be run
     * on the developer's own machine; tightening this should be done by an
     * operator deploying the server publicly.
     *
     * @param res the res
     */
    static void applyCorsHeaders(HttpServerResponse res) {
        res.header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                .header("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept")
                .header("Access-Control-Max-Age", "3600");
    }

    /**
     * Extracts a query parameter value from a request URI.
     * Returns null if the parameter is absent or empty.
     *
     * @param uri the uri
     * @param name the name
     * @return the result
     */
    static String extractQueryParam(String uri, String name) {
        if (uri == null) {
            return null;
        }
        int qIdx = uri.indexOf('?');
        if (qIdx < 0 || qIdx == uri.length() - 1) {
            return null;
        }
        String query = uri.substring(qIdx + 1);
        String prefix = name + "=";
        for (String pair : query.split("&")) {
            if (pair.startsWith(prefix)) {
                String value = pair.substring(prefix.length());
                if (value.isEmpty()) {
                    return null;
                }
                return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
