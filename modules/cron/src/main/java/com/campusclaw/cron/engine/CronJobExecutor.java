/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.campusclaw.cron.engine;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.campusclaw.agent.Agent;
import com.campusclaw.agent.tool.AgentTool;
import com.campusclaw.ai.CampusClawAiService;
import com.campusclaw.ai.model.ModelRegistry;
import com.campusclaw.ai.types.AssistantMessage;
import com.campusclaw.ai.types.ContentBlock;
import com.campusclaw.ai.types.Message;
import com.campusclaw.ai.types.Model;
import com.campusclaw.ai.types.Provider;
import com.campusclaw.ai.types.TextContent;
import com.campusclaw.cron.model.CronJob;
import com.campusclaw.cron.model.CronPayload;
import com.campusclaw.cron.model.CronRunRecord;
import com.campusclaw.cron.store.CronRunLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Executes a single cron job by creating an isolated Agent instance.
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@Service
public class CronJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(CronJobExecutor.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final CampusClawAiService aiService;
    private final ModelRegistry modelRegistry;
    private final CronRunLog runLog;
    private final List<AgentTool> availableTools;
    private final com.campusclaw.cron.CronService cronService;

    public CronJobExecutor(
            CampusClawAiService aiService,
            ModelRegistry modelRegistry,
            CronRunLog runLog,
            @Lazy List<AgentTool> availableTools,
            @Lazy com.campusclaw.cron.CronService cronService) {
        this.aiService = aiService;
        this.modelRegistry = modelRegistry;
        this.runLog = runLog;
        this.availableTools = availableTools;
        this.cronService = cronService;
    }

    public CronRunRecord execute(CronJob job) {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        long startedAt = System.currentTimeMillis();
        runLog.appendRun(
                new CronRunRecord(runId, job.id(), startedAt, 0, CronRunRecord.RunStatus.RUNNING, null, null, 0));
        try {
            var payload = (CronPayload.AgentPrompt) job.payload();
            Agent agent = buildAgentForPayload(payload);
            try {
                agent.prompt(payload.prompt()).get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                agent.abort();
                log.warn("Cron job {} timed out after {}s", job.name(), DEFAULT_TIMEOUT_SECONDS);
                return appendAndReturn(new CronRunRecord(
                        runId,
                        job.id(),
                        startedAt,
                        System.currentTimeMillis(),
                        CronRunRecord.RunStatus.FAILED,
                        "Timeout after " + DEFAULT_TIMEOUT_SECONDS + "s",
                        null,
                        0));
            }
            return appendAndReturn(new CronRunRecord(
                    runId,
                    job.id(),
                    startedAt,
                    System.currentTimeMillis(),
                    CronRunRecord.RunStatus.SUCCESS,
                    null,
                    extractOutput(agent),
                    0));
        } catch (Exception e) {
            log.error("Cron job {} failed: {}", job.name(), e.getMessage(), e);
            return appendAndReturn(new CronRunRecord(
                    runId,
                    job.id(),
                    startedAt,
                    System.currentTimeMillis(),
                    CronRunRecord.RunStatus.FAILED,
                    e.getMessage(),
                    null,
                    0));
        }
    }

    private Agent buildAgentForPayload(CronPayload.AgentPrompt payload) {
        Agent agent = new Agent(aiService);
        Model model = resolveModel(payload.modelId());
        if (model == null) {
            throw new IllegalStateException("No model available for cron job execution");
        }
        agent.setModel(model);
        agent.setSystemPrompt(
                payload.systemPrompt() != null
                        ? payload.systemPrompt()
                        : "You are a cron task executor. Complete the task efficiently and concisely.");
        agent.setTools(filterTools(payload.allowedTools()));
        return agent;
    }

    private CronRunRecord appendAndReturn(CronRunRecord record) {
        runLog.appendRun(record);
        return record;
    }

    private String extractOutput(Agent agent) {
        var messages = agent.getState().getMessages();
        var sb = new StringBuilder();

        // Walk messages in reverse to find the last assistant response
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage am) {
                for (ContentBlock block : am.content()) {
                    if (block instanceof TextContent tc
                            && tc.text() != null
                            && !tc.text().isBlank()) {
                        sb.append(tc.text());
                    }
                }
                break; // Only the last assistant message
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private Model resolveModel(String modelId) {
        // Use explicit modelId, or fall back to the session's default model
        String effectiveId = (modelId != null && !modelId.isBlank())
                ? modelId
                : (cronService != null ? cronService.getDefaultModelId() : null);

        if (effectiveId != null && !effectiveId.isBlank()) {
            for (Provider provider : modelRegistry.getProviders()) {
                var model = modelRegistry.getModel(provider, effectiveId);
                if (model.isPresent()) {
                    return model.get();
                }
            }

            // Try substring match (e.g., "glm-5" matching "glm-5-plus")
            for (Model m : modelRegistry.getAllModels()) {
                if (m.id().contains(effectiveId) || effectiveId.contains(m.id())) {
                    return m;
                }
            }
            log.warn("Model {} not found in registry", effectiveId);
        }

        // Last resort: first available model
        var all = modelRegistry.getAllModels();
        return all.isEmpty() ? null : all.get(0);
    }

    private List<AgentTool> filterTools(List<String> allowedTools) {
        if (allowedTools == null || allowedTools.isEmpty()) {
            return availableTools;
        }
        return availableTools.stream()
                .filter(t -> allowedTools.contains(t.name()))
                .collect(Collectors.toList());
    }
}
