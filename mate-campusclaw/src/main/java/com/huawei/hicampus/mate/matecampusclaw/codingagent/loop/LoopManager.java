package com.huawei.hicampus.mate.matecampusclaw.codingagent.loop;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Manages in-session recurring prompts (loops). Unlike cron (isolated agent, persisted),
 * loops run within the current conversation session and output streams directly into chat.
 * Loops are session-scoped and do not survive application restart.
 */
@Service
public class LoopManager {

    private static final Logger log = LoggerFactory.getLogger(LoopManager.class);

    private volatile BlockingQueue<String> submitQueue;
    private volatile AtomicBoolean executingPrompt;
    private final Map<String, LoopEntry> activeLoops = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private volatile ScheduledExecutorService scheduler;

    public record LoopEntry(String id, String prompt, long intervalMs, ScheduledFuture<?> future) {}

    /**
     * Initialize with the interactive mode's submit queue and execution flag.
     * Must be called before start/stop operations.
     */
    public void init(BlockingQueue<String> submitQueue, AtomicBoolean executingPrompt) {
        this.submitQueue = submitQueue;
        this.executingPrompt = executingPrompt;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "loop-manager");
            t.setDaemon(true);
            return t;
        });
        log.debug("LoopManager initialized");
    }

    /**
     * Start a new loop that submits the given prompt at fixed intervals.
     * Skips iterations when the agent is busy (skip-if-running).
     *
     * @return the loop ID
     */
    public String start(String prompt, long intervalMs) {
        if (scheduler == null) {
            throw new IllegalStateException("LoopManager not initialized — only available in interactive mode");
        }
        String id = String.valueOf(nextId.getAndIncrement());
        var future = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (executingPrompt != null && !executingPrompt.get()) {
                    submitQueue.add(prompt);
                } else {
                    log.debug("Loop #{} skipped — agent is busy", id);
                }
            } catch (Exception e) {
                log.warn("Loop #{} error: {}", id, e.getMessage());
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        activeLoops.put(id, new LoopEntry(id, prompt, intervalMs, future));
        log.info("Started loop #{} (every {}ms): {}", id, intervalMs, prompt);
        return id;
    }

    public boolean stop(String id) {
        var entry = activeLoops.remove(id);
        if (entry != null) {
            entry.future().cancel(false);
            log.info("Stopped loop #{}", id);
            return true;
        }
        return false;
    }

    public int stopAll() {
        int count = activeLoops.size();
        activeLoops.values().forEach(e -> e.future().cancel(false));
        activeLoops.clear();
        if (count > 0) {
            log.info("Stopped all {} loop(s)", count);
        }
        return count;
    }

    public Collection<LoopEntry> list() {
        return List.copyOf(activeLoops.values());
    }

    public boolean isInitialized() {
        return scheduler != null;
    }

    public void shutdown() {
        stopAll();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        log.debug("LoopManager shutdown");
    }
}
