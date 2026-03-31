package com.campusclaw.codingagent.config;

import com.campusclaw.codingagent.tool.bash.BashTool;
import com.campusclaw.codingagent.tool.edit.EditTool;
import com.campusclaw.codingagent.tool.glob.GlobTool;
import com.campusclaw.codingagent.tool.grep.GrepTool;
import com.campusclaw.codingagent.tool.hybrid.*;
import com.campusclaw.codingagent.tool.read.ReadTool;
import com.campusclaw.codingagent.tool.write.WriteTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * 混合执行模式配置
 */
@Slf4j
@Configuration
public class HybridExecutionConfiguration {

    /**
     * 混合模式：智能路由（默认）
     * 当 tool.execution.hybrid-enabled=true 时启用
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "tool.execution.hybrid-enabled", havingValue = "true", matchIfMissing = false)
    public HybridReadTool hybridReadToolPrimary(HybridReadTool tool) {
        log.info("Hybrid mode enabled: Read tool using smart routing");
        return tool;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "tool.execution.hybrid-enabled", havingValue = "true", matchIfMissing = false)
    public HybridWriteTool hybridWriteToolPrimary(HybridWriteTool tool) {
        log.info("Hybrid mode enabled: Write tool using smart routing");
        return tool;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "tool.execution.hybrid-enabled", havingValue = "true", matchIfMissing = false)
    public HybridEditTool hybridEditToolPrimary(HybridEditTool tool) {
        log.info("Hybrid mode enabled: Edit tool using smart routing");
        return tool;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "tool.execution.hybrid-enabled", havingValue = "true", matchIfMissing = false)
    public HybridBashTool hybridBashToolPrimary(HybridBashTool tool) {
        log.info("Hybrid mode enabled: Bash tool using smart routing");
        return tool;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "tool.execution.hybrid-enabled", havingValue = "true", matchIfMissing = false)
    public HybridGlobTool hybridGlobToolPrimary(HybridGlobTool tool) {
        log.info("Hybrid mode enabled: Glob tool using smart routing");
        return tool;
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "tool.execution.hybrid-enabled", havingValue = "true", matchIfMissing = false)
    public HybridGrepTool hybridGrepToolPrimary(HybridGrepTool tool) {
        log.info("Hybrid mode enabled: Grep tool using smart routing");
        return tool;
    }
}
