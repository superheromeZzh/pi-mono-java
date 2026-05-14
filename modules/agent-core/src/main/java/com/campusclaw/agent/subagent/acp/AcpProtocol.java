/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.acp;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Wire-level types for the Agent Client Protocol (ACP), framed as JSON-RPC 2.0 over ndJSON.
 *
 * <p>Only the subset required by {@code AcpClient} is modelled. Optional fields (most of the
 * params bag) are kept as {@link JsonNode} / {@link Map} so a wire-spec bump does not require a
 * code change here.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
public final class AcpProtocol {

    public static final String JSON_RPC_VERSION = "2.0";
    public static final int PROTOCOL_VERSION = 1;

    public static final String METHOD_INITIALIZE = "initialize";
    public static final String METHOD_NEW_SESSION = "session/new";
    public static final String METHOD_PROMPT = "session/prompt";
    public static final String METHOD_CANCEL = "session/cancel";
    public static final String METHOD_UPDATE = "session/update";
    public static final String METHOD_REQUEST_PERMISSION = "session/request_permission";

    public static final String UPDATE_AGENT_MESSAGE = "agent_message_chunk";
    public static final String UPDATE_AGENT_THOUGHT = "agent_thought_chunk";
    public static final String UPDATE_TOOL_CALL = "tool_call";
    public static final String UPDATE_TOOL_CALL_UPDATE = "tool_call_update";
    public static final String UPDATE_PLAN = "plan";
    public static final String UPDATE_AVAILABLE_COMMANDS = "available_commands_update";
    public static final String UPDATE_CURRENT_MODE = "current_mode_update";

    private AcpProtocol() {}

    /**
     * Generic JSON-RPC envelope. Exactly one of {@code method+params} (request/notification) or
     * {@code result}/{@code error} (response) is populated. {@code id} is {@code null} for
     * notifications.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Envelope(String jsonrpc, Object id, String method, JsonNode params, JsonNode result, Error error) {

        public static Envelope request(long id, String method, JsonNode params) {
            return new Envelope(JSON_RPC_VERSION, id, method, params, null, null);
        }

        public static Envelope notification(String method, JsonNode params) {
            return new Envelope(JSON_RPC_VERSION, null, method, params, null, null);
        }

        public static Envelope ok(Object id, JsonNode result) {
            return new Envelope(JSON_RPC_VERSION, id, null, null, result, null);
        }

        public static Envelope fail(Object id, Error error) {
            return new Envelope(JSON_RPC_VERSION, id, null, null, null, error);
        }

        @JsonIgnore
        public boolean isRequest() {
            return method != null && id != null;
        }

        @JsonIgnore
        public boolean isNotification() {
            return method != null && id == null;
        }

        @JsonIgnore
        public boolean isResponse() {
            return method == null && id != null;
        }
    }

    /**
     * JSON-RPC 2.0 error object.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Error(int code, String message, JsonNode data) {

        public static final int PARSE_ERROR = -32700;
        public static final int INVALID_REQUEST = -32600;
        public static final int METHOD_NOT_FOUND = -32601;
        public static final int INVALID_PARAMS = -32602;
        public static final int INTERNAL_ERROR = -32603;
    }

    /**
     * Params for the {@code initialize} request.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InitializeRequest(
            int protocolVersion, ClientCapabilities clientCapabilities, ClientInfo clientInfo) {}

    /**
     * Capabilities the client advertises during {@code initialize}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClientCapabilities(FsCapability fs, Boolean terminal) {

        public static ClientCapabilities none() {
            return new ClientCapabilities(new FsCapability(false, false), false);
        }
    }

    /**
     * Filesystem sub-capability flags.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FsCapability(boolean readTextFile, boolean writeTextFile) {}

    /**
     * Client identity reported during {@code initialize}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClientInfo(String name, String version) {}

    /**
     * Reply to {@code initialize}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InitializeResponse(
            int protocolVersion, JsonNode agentCapabilities, JsonNode agentInfo, JsonNode authMethods) {}

    /**
     * Params for {@code session/new}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NewSessionRequest(String cwd, List<Map<String, Object>> mcpServers) {}

    /**
     * Reply to {@code session/new}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NewSessionResponse(String sessionId) {}

    /**
     * Params for {@code session/prompt}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PromptRequest(String sessionId, List<ContentBlock> prompt) {}

    /**
     * Reply to {@code session/prompt}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PromptResponse(String stopReason) {}

    /**
     * Params for the {@code session/cancel} notification.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CancelRequest(String sessionId) {}

    /**
     * Inbound {@code session/update} notification. The shape of {@code update} depends on
     * {@code update.sessionUpdate} — kept as a raw map to stay tolerant of wire evolution.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record UpdateNotification(String sessionId, JsonNode update) {}

    /**
     * Inbound {@code session/request_permission} request.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RequestPermissionRequest(String sessionId, JsonNode toolCall, JsonNode options) {}

    /**
     * Reply to {@code session/request_permission}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RequestPermissionResponse(Outcome outcome) {

        /**
         * Outcome of a permission decision.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public record Outcome(String outcome, String optionId) {

            public static Outcome selected(String optionId) {
                return new Outcome("selected", optionId);
            }

            public static Outcome cancelled() {
                return new Outcome("cancelled", null);
            }
        }
    }

    /**
     * ACP {@code ContentBlock} for prompts. Only text is exercised by the parent today.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ContentBlock(String type, String text) {

        public static ContentBlock text(String value) {
            return new ContentBlock("text", value);
        }
    }
}
