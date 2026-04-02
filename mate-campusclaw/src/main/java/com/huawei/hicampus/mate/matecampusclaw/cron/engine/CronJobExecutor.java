package com.huawei.hicampus.mate.matecampusclaw.cron.engine;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.huawei.hicampus.mate.matecampusclaw.agent.Agent;
import com.huawei.hicampus.mate.matecampusclaw.agent.tool.AgentTool;
import com.huawei.hicampus.mate.matecampusclaw.ai.CampusClawAiService;
import com.huawei.hicampus.mate.matecampusclaw.ai.model.ModelRegistry;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.AssistantMessage;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.ContentBlock;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Message;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Model;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.Provider;
import com.huawei.hicampus.mate.matecampusclaw.ai.types.TextContent;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronJob;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronPayload;
import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronRunRecord;
import com.huawei.hicampus.mate.matecampusclaw.cron.store.CronRunLog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Executes a single cron job by creating an isolated Agent instance.
 */
@Service
public class CronJobExecutor {

    private static final Logger log = LoggerFactory.getLogger(CronJobExecutor.class);
    private static final int DEFAULT_TIMEOUT_SECONDS = 300;

    private final CampusClawAiService aiService;
    private final ModelRegistry modelRegistry;
    private final CronRunLog runLog;
    private final List<AgentTool> availableTools;
    private final com.huawei.hicampus.mate.matecampusclaw.cron.CronService cronService;

    public CronJobExecutor(CampusClawAiService aiService, ModelRegistry modelRegistry,
                           CronRunLog runLog, @Lazy List<AgentTool> availableTools,
                           @Lazy com.huawei.hicampus.mate.matecampusclaw.cron.CronService cronService) {
        this.aiService = aiService;
        this.modelRegistry = modelRegistry;
        this.runLog = runLog;
        this.availableTools = availableTools;
        this.cronService = cronService;
    }

    public CronRunRecord execute(CronJob job) {
        String runId = UUID.randomUUID().toString().substring(0, 8);
        long startedAt = System.currentTimeMillis();

        var startRecord = new CronRunRecord(
            runId, job.id(), startedAt, 0,
            CronRunRecord.RunStatus.RUNNING, null, null, 0
        );
        runLog.appendRun(startRecord);

        try {
            var payload = (CronPayload.AgentPrompt) job.payload();

            // Create isolated agent
            Agent agent = new Agent(aiService);

            // Resolve model — required for Agent to execute
            Model model = resolveModel(payload.modelId());
            if (model == null) {
                throw new IllegalStateException("No model available for cron job execution");
            }
            agent.setModel(model);

            // Set system prompt
            if (payload.systemPrompt() != null) {
                agent.setSystemPrompt(payload.systemPrompt());
            } else {
                agent.setSystemPrompt("You are a cron task executor. Complete the task efficiently and concisely.");
            }

            // Filter tools
            List<AgentTool> tools = filterTools(payload.allowedTools());
            agent.setTools(tools);

            // Execute with timeout — await the prompt future directly
            // (waitForIdle() won't surface errors if prompt() itself fails)
            int timeout = DEFAULT_TIMEOUT_SECONDS;
            try {
                agent.prompt(payload.prompt()).get(timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                agent.abort();
                log.warn("Cron job {} timed out after {}s", job.name(), timeout);
                var record = new CronRunRecord(
                    runId, job.id(), startedAt, System.currentTimeMillis(),
                    CronRunRecord.RunStatus.FAILED, "Timeout after " + timeout + "s", null, 0
                );
                runLog.appendRun(record);
                return record;
            }

            // Extract assistant response text
            String output = extractOutput(agent);

            var record = new CronRunRecord(
                runId, job.id(), startedAt, System.currentTimeMillis(),
                CronRunRecord.RunStatus.SUCCESS, null, output, 0
            );
            runLog.appendRun(record);
            return record;

        } catch (Exception e) {
            log.error("Cron job {} failed: {}", job.name(), e.getMessage(), e);
            var record = new CronRunRecord(
                runId, job.id(), startedAt, System.currentTimeMillis(),
                CronRunRecord.RunStatus.FAILED, e.getMessage(), null, 0
            );
            runLog.appendRun(record);
            return record;
        }
    }

    private String extractOutput(Agent agent) {
        var messages = agent.getState().getMessages();
        var sb = new StringBuilder();
        // Walk messages in reverse to find the last assistant response
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage am) {
                for (ContentBlock block : am.content()) {
                    if (block instanceof TextContent tc && tc.text() != null && !tc.text().isBlank()) {
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
        String effectiveId = (modelId != null && !modelId.isBlank()) ? modelId
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
