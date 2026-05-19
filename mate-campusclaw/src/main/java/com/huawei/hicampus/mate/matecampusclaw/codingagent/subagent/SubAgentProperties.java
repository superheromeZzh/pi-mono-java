/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.codingagent.subagent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * YAML-bound configuration for sub-agent backends.
 *
 * <p>One entry per backend keyed by id. The {@link BackendSpec#getType()} discriminator switches
 * between the ACP process backend and the HTTP backend; field validation happens later, at
 * registration time, because the binding layer cannot express a sealed/union shape directly.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/12]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@ConfigurationProperties(prefix = "subagent")
public class SubAgentProperties {

    private boolean enabled = true;

    private Map<String, BackendSpec> backends = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, BackendSpec> getBackends() {
        return backends;
    }

    public void setBackends(Map<String, BackendSpec> backends) {
        this.backends = backends == null ? new LinkedHashMap<>() : backends;
    }

    /**
     * Per-backend configuration shape.
     */
    public static class BackendSpec {

        /**
         * Backend transport: {@code acp} (process + ndJSON), {@code http} (CampusClaw HTTP NDJSON),
         * or {@code a2a} (Huawei mate-service flavoured A2A JSON-RPC).
         */
        private String type;

        private boolean disabled;

        // --- ACP fields ---
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new LinkedHashMap<>();
        private String clientName;
        private String clientVersion;
        private Duration promptTimeout;

        // --- HTTP fields ---
        private String url;
        private String authType;
        private String authToken;
        private String authHeaderName;
        private Duration connectTimeout;
        private Duration requestTimeout;

        // --- A2A fields (Huawei mate-service) ---
        private String agentName;
        private String hwId;
        private String hwAppKey;
        private String model;
        private boolean insecureSkipVerify;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isDisabled() {
            return disabled;
        }

        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args == null ? new ArrayList<>() : args;
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env == null ? new LinkedHashMap<>() : env;
        }

        public String getClientName() {
            return clientName;
        }

        public void setClientName(String clientName) {
            this.clientName = clientName;
        }

        public String getClientVersion() {
            return clientVersion;
        }

        public void setClientVersion(String clientVersion) {
            this.clientVersion = clientVersion;
        }

        public Duration getPromptTimeout() {
            return promptTimeout;
        }

        public void setPromptTimeout(Duration promptTimeout) {
            this.promptTimeout = promptTimeout;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getAuthType() {
            return authType;
        }

        public void setAuthType(String authType) {
            this.authType = authType;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }

        public String getAuthHeaderName() {
            return authHeaderName;
        }

        public void setAuthHeaderName(String authHeaderName) {
            this.authHeaderName = authHeaderName;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getRequestTimeout() {
            return requestTimeout;
        }

        public void setRequestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
        }

        public String getAgentName() {
            return agentName;
        }

        public void setAgentName(String agentName) {
            this.agentName = agentName;
        }

        public String getHwId() {
            return hwId;
        }

        public void setHwId(String hwId) {
            this.hwId = hwId;
        }

        public String getHwAppKey() {
            return hwAppKey;
        }

        public void setHwAppKey(String hwAppKey) {
            this.hwAppKey = hwAppKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public boolean isInsecureSkipVerify() {
            return insecureSkipVerify;
        }

        public void setInsecureSkipVerify(boolean insecureSkipVerify) {
            this.insecureSkipVerify = insecureSkipVerify;
        }
    }
}
