/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.huawei.hicampus.mate.matecampusclaw.cron.engine;

import com.huawei.hicampus.mate.matecampusclaw.cron.model.CronEvent;

/**
 * Listener for cron engine events (job started, completed, failed).
 *
 * @version [br_eCampusCore 25.1.0_Next, 2026/05/06]
 * @since [br_eCampusCore 25.1.0_Next]
 */
@FunctionalInterface
public interface CronEventListener {
    void onCronEvent(CronEvent event);
}
