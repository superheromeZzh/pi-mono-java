/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.agent.subagent.acp.backend;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Test-only stub ACP server. Reads ndJSON envelopes from stdin and writes canned JSON-RPC
 * responses to stdout. Used by {@link ProcessAcpBackendTest} so that {@code ProcessAcpBackend.open}
 * can complete its handshake against a real child process, which then exercises the post-open
 * code paths (prompt/cancel/close, drainStderr at scale, destroyTree waitFor + onExit, etc.).
 *
 * <p>Each request is recognised by its {@code "method":"..."} substring; we don't fully parse
 * JSON because the protocol surface we care about for tests is small and the field shapes are
 * stable. The corresponding response copies the request {@code id} back via a regex.
 *
 * <p>Optional behaviour switches via system properties:
 * <ul>
 *   <li>{@code fakeacp.stderr.lines=N} — print N lines to stderr at startup (used to exercise
 *       the {@code StderrTail} ring-buffer overflow branch).</li>
 *   <li>{@code fakeacp.exit.after.prompt=true} — exit immediately after writing the prompt
 *       reply (so the parent's close path sees an already-dead child).</li>
 * </ul>
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/20]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@SuppressWarnings("checkstyle:no_system_out_err") // FakeAcpServer's stdout IS the ACP wire.
public final class FakeAcpServer {

    private FakeAcpServer() {}

    private record ServerOptions(
            boolean exitAfterPrompt,
            boolean sendPermission,
            String toolNameField,
            String toolName,
            boolean includeToolCall,
            boolean includeParams,
            String optionsShape) {}

    private static ServerOptions readOptions() {
        return new ServerOptions(
                Boolean.parseBoolean(System.getProperty("fakeacp.exit.after.prompt", "false")),
                Boolean.parseBoolean(System.getProperty("fakeacp.send.permission", "false")),
                System.getProperty("fakeacp.tool.name.field", "name"),
                System.getProperty("fakeacp.tool.name", "bash"),
                Boolean.parseBoolean(System.getProperty("fakeacp.include.toolcall", "true")),
                Boolean.parseBoolean(System.getProperty("fakeacp.include.params", "true")),
                System.getProperty("fakeacp.options.shape", "allow_reject"));
    }

    public static void main(String[] args) throws Exception {
        // Optional: blast some stderr at startup so the parent can drain a non-trivial tail.
        int stderrLines = Integer.parseInt(System.getProperty("fakeacp.stderr.lines", "0"));
        for (int i = 0; i < stderrLines; i++) {
            System.err.println("stderr-noise-line-" + i);
        }
        System.err.flush();

        ServerOptions opts = readOptions();
        PrintStream out = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        try (var reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            runDispatchLoop(reader, out, opts);
        }
    }

    private static void runDispatchLoop(BufferedReader reader, PrintStream out, ServerOptions opts) throws Exception {
        long serverInitiatedId = 1000L;
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("\"method\":\"session/cancel\"")) {
                // Notification — no id, nothing to send back.
                continue;
            }
            String id = extractId(trimmed);
            if (id == null) {
                continue;
            }
            if (!dispatchOne(trimmed, id, out, opts, reader, serverInitiatedId)) {
                return;
            }
            if (opts.sendPermission() && trimmed.contains("\"method\":\"session/prompt\"")) {
                serverInitiatedId++;
            }
        }
    }

    // Returns false if the server should terminate (EOF or exit-after-prompt).
    private static boolean dispatchOne(
            String trimmed,
            String id,
            PrintStream out,
            ServerOptions opts,
            BufferedReader reader,
            long serverInitiatedId)
            throws Exception {
        if (trimmed.contains("\"method\":\"initialize\"")) {
            out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"protocolVersion\":1}}");
            out.flush();
            return true;
        }
        if (trimmed.contains("\"method\":\"session/new\"")) {
            out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"sessionId\":\"sess-fake\"}}");
            out.flush();
            return true;
        }
        if (trimmed.contains("\"method\":\"session/prompt\"")) {
            if (opts.sendPermission()) {
                out.println(buildPermissionRequest(
                        serverInitiatedId,
                        opts.toolNameField(),
                        opts.toolName(),
                        opts.includeToolCall(),
                        opts.includeParams(),
                        opts.optionsShape()));
                out.flush();
                if (reader.readLine() == null) {
                    return false;
                }
            }
            out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"stopReason\":\"end_turn\"}}");
            out.flush();
            return !opts.exitAfterPrompt();
        }
        out.println("{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"error\":{\"code\":-32601,\"message\":\"method not supported by fake server\"}}");
        out.flush();
        return true;
    }

    private static String buildPermissionRequest(
            long id,
            String toolNameField,
            String toolName,
            boolean includeToolCall,
            boolean includeParams,
            String optionsShape) {
        String toolCallJson = "null";
        if (includeToolCall) {
            String paramsJson = includeParams ? ",\"params\":{\"command\":\"ls\",\"cwd\":\"/tmp\"}" : "";
            toolCallJson = "{\"" + toolNameField + "\":\"" + toolName + "\"" + paramsJson + "}";
        }

        String optionsJson =
                switch (optionsShape) {
                    case "none" -> "null";
                    case "empty" -> "[]";
                    case "no_kind" -> "[{\"optionId\":\"opt1\",\"name\":\"Only id\"}]";
                    case "reject_only" ->
                        "[{\"optionId\":\"reject_once\",\"kind\":\"reject_once\",\"name\":\"Reject\"}]";
                    case "allow_reject" ->
                        "[{\"optionId\":\"allow_once\",\"kind\":\"allow_once\",\"name\":\"Allow\"},"
                                + "{\"optionId\":\"reject_once\",\"kind\":\"reject_once\",\"name\":\"Reject\"}]";
                    case "options_object" -> "{\"opt\":\"not-an-array\"}";
                    default -> "[]";
                };

        return "{\"jsonrpc\":\"2.0\",\"id\":" + id
                + ",\"method\":\"session/request_permission\","
                + "\"params\":{\"sessionId\":\"sess-fake\",\"toolCall\":" + toolCallJson
                + ",\"options\":" + optionsJson + "}}";
    }

    /**
     * Extracts the integer {@code "id"} field from a request envelope. Returns {@code null} for
     * notifications (no id) or malformed input.
     *
     * @param json one line of ndJSON request payload
     * @return the id token as a string, or {@code null} if not found
     */
    private static String extractId(String json) {
        int idx = json.indexOf("\"id\":");
        if (idx < 0) {
            return null;
        }
        int start = idx + 5;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
            end++;
        }
        if (end == start) {
            return null;
        }
        return json.substring(start, end);
    }
}
