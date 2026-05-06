/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.codingagent.tool.ops;

import com.campusclaw.codingagent.util.FileMutationQueue;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that provides local filesystem implementations
 * of the tool operations interfaces and shared utilities.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Configuration
public class ToolOpsConfig {

    @Bean
    public ReadOperations readOperations() {
        return new LocalReadOperations();
    }

    @Bean
    public WriteOperations writeOperations() {
        return new LocalWriteOperations();
    }

    @Bean
    public EditOperations editOperations() {
        return new LocalEditOperations();
    }

    @Bean
    public LsOperations lsOperations() {
        return new LocalLsOperations();
    }

    @Bean
    public FileMutationQueue fileMutationQueue() {
        return new FileMutationQueue();
    }
}
