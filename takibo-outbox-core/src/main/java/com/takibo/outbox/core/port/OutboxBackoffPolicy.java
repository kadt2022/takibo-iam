package com.takibo.outbox.core.port;

import java.time.Duration;

public interface OutboxBackoffPolicy {
    Duration nextDelay(int attempts);
}
