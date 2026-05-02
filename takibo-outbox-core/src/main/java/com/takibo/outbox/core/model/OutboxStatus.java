package com.takibo.outbox.core.model;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    FAILED,
    PROCESSED,
    DEAD
}
