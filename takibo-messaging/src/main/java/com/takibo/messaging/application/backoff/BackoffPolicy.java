package com.takibo.messaging.application.backoff;

import java.time.Duration;

public interface BackoffPolicy {

    Duration nextDelay(int attempt);
}
