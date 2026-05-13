/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.agent.subagent.approval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApprovalClassifierTest {

    private final ApprovalClassifier classifier = new ApprovalClassifier();

    @Test
    void readOnlyNamesAreReadOnly() {
        assertThat(classifier.classify("read")).isEqualTo(ApprovalClassifier.Risk.READ_ONLY);
        assertThat(classifier.classify("Grep")).isEqualTo(ApprovalClassifier.Risk.READ_ONLY);
        assertThat(classifier.classify("get_status")).isEqualTo(ApprovalClassifier.Risk.READ_ONLY);
    }

    @Test
    void writeNamesAreFileWrite() {
        assertThat(classifier.classify("write")).isEqualTo(ApprovalClassifier.Risk.FILE_WRITE);
        assertThat(classifier.classify("edit")).isEqualTo(ApprovalClassifier.Risk.FILE_WRITE);
        assertThat(classifier.classify("delete_file")).isEqualTo(ApprovalClassifier.Risk.FILE_WRITE);
    }

    @Test
    void shellNamesAreExec() {
        assertThat(classifier.classify("bash")).isEqualTo(ApprovalClassifier.Risk.EXEC);
        assertThat(classifier.classify("exec_script")).isEqualTo(ApprovalClassifier.Risk.EXEC);
    }

    @Test
    void networkNamesAreNetwork() {
        assertThat(classifier.classify("WebFetch")).isEqualTo(ApprovalClassifier.Risk.NETWORK);
        assertThat(classifier.classify("websearch")).isEqualTo(ApprovalClassifier.Risk.NETWORK);
        assertThat(classifier.classify("http_post")).isEqualTo(ApprovalClassifier.Risk.NETWORK);
    }

    @Test
    void unknownIsUnknown() {
        assertThat(classifier.classify("frobnicate")).isEqualTo(ApprovalClassifier.Risk.UNKNOWN);
        assertThat(classifier.classify("")).isEqualTo(ApprovalClassifier.Risk.UNKNOWN);
        assertThat(classifier.classify(null)).isEqualTo(ApprovalClassifier.Risk.UNKNOWN);
    }

    @Test
    void defaultPolicyAllowsOnlyReadOnly() {
        var policy = new DefaultApprovalPolicy();

        assertThat(policy.decide(ApprovalClassifier.Risk.READ_ONLY, "read")).isEqualTo(ApprovalDecision.AUTO_ALLOW);
        assertThat(policy.decide(ApprovalClassifier.Risk.FILE_WRITE, "write")).isEqualTo(ApprovalDecision.ASK_PARENT);
        assertThat(policy.decide(ApprovalClassifier.Risk.EXEC, "bash")).isEqualTo(ApprovalDecision.ASK_PARENT);
        assertThat(policy.decide(ApprovalClassifier.Risk.NETWORK, "webfetch")).isEqualTo(ApprovalDecision.ASK_PARENT);
        assertThat(policy.decide(ApprovalClassifier.Risk.UNKNOWN, "x")).isEqualTo(ApprovalDecision.ASK_PARENT);
    }
}
