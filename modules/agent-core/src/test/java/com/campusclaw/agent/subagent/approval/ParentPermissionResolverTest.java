/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.approval;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ParentPermissionResolverTest {

    @Test
    void defaultResolverDeniesEverything() {
        var resolver = new TimeoutDeniedResolver();
        var request = new ParentPermissionRequest(
                "agent:main:claude-code:abc",
                "claude-code",
                "bash",
                ApprovalClassifier.Risk.EXEC,
                Map.of("command", "rm -rf /"),
                List.of(new ParentPermissionRequest.Option("allow_once", "allow_once", "Allow once")));

        ParentPermissionDecision decision = resolver.resolve(request, Duration.ofSeconds(1L));

        assertThat(decision.outcome()).isEqualTo(ParentPermissionDecision.Outcome.CANCELLED);
        assertThat(decision.optionId()).isNull();
    }

    @Test
    void selectedDecisionCarriesOptionId() {
        var decision = ParentPermissionDecision.selected("allow_once");
        assertThat(decision.outcome()).isEqualTo(ParentPermissionDecision.Outcome.SELECTED);
        assertThat(decision.optionId()).isEqualTo("allow_once");
    }

    @Test
    void requestNormalisesNullCollections() {
        var request =
                new ParentPermissionRequest("key", "backend", "tool", ApprovalClassifier.Risk.READ_ONLY, null, null);
        assertThat(request.params()).isEmpty();
        assertThat(request.options()).isEmpty();
    }
}
