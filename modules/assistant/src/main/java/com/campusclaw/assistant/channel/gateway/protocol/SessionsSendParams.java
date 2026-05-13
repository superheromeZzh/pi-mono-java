/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.assistant.channel.gateway.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Parameters of the {@code sessions.send} request, addressing a target session by key and
 * carrying the opaque message body to deliver.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/13]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionsSendParams(@JsonProperty("key") String key, @JsonProperty("message") Object message) {}
