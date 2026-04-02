package com.huawei.hicampus.mate.matecampusclaw.codingagent.tool.ops;

import com.huawei.hicampus.mate.matecampusclaw.codingagent.util.FileMutationQueue;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that provides local filesystem implementations
 * of the tool operations interfaces and shared utilities.
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
