package com.takibo.outbox.jpa.publisher;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JpaOutboxPublisherTest {

    @Test
    void detectsOutboxDedupConstraintByName() throws Exception {
        assertTrue(isDedupConflict("duplicate key value violates constraint \"UQ_OUTBOX_DEDUP_KEY\""));
    }

    @Test
    void detectsOutboxDedupConstraintByTableAndColumn() throws Exception {
        assertTrue(isDedupConflict("duplicate key on outbox_messages for dedup_key"));
    }

    @Test
    void ignoresUnrelatedConstraint() throws Exception {
        assertFalse(isDedupConflict("duplicate key value violates constraint \"other_key\""));
    }

    private static boolean isDedupConflict(String message) throws Exception {
        JpaOutboxPublisher publisher = new JpaOutboxPublisher(null, null, null);
        Method method = JpaOutboxPublisher.class.getDeclaredMethod("isDedupConflict", DataIntegrityViolationException.class);
        method.setAccessible(true);
        DataIntegrityViolationException exception = new DataIntegrityViolationException("persist failed", new RuntimeException(message));
        return (boolean) method.invoke(publisher, exception);
    }
}
