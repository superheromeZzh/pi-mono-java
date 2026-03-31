package com.campusclaw.cron.engine;

import com.campusclaw.cron.model.CronEvent;

/**
 * Listener for cron engine events (job started, completed, failed).
 */
@FunctionalInterface
public interface CronEventListener {
    void onCronEvent(CronEvent event);
}
