/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.cron.engine;

import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronEvent;

/**
 * Listener for cron engine events (job started, completed, failed).
 */
@FunctionalInterface
public interface CronEventListener {
    void onCronEvent(CronEvent event);
}
