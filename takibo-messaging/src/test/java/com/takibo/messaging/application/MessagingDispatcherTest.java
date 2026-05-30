package com.takibo.messaging.application;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagingDispatcherTest {

    @Test
    void detectsMessageDeliveryDedupConstraintByName() throws Exception {
        assertTrue(isDedupConflict("duplicate key value violates constraint \"UQ_MESSAGE_DELIVERIES_DEDUP_KEY\""));
    }

    @Test
    void detectsMessageDeliveryDedupConstraintByTableAndColumn() throws Exception {
        assertTrue(isDedupConflict("duplicate key on message_deliveries for dedup_key"));
    }

    @Test
    void ignoresUnrelatedConstraint() throws Exception {
        assertFalse(isDedupConflict("duplicate key value violates constraint \"other_key\""));
    }

    private static boolean isDedupConflict(String message) throws Exception {
        MessagingDispatcher dispatcher = new MessagingDispatcher(
                repositoryProxy(),
                List.of(),
                Map.of(),
                messageType -> java.util.Optional.empty(),
                new TemplateEngine(),
                new com.fasterxml.jackson.databind.ObjectMapper(),
                Clock.systemUTC()
        );
        Method method = MessagingDispatcher.class.getDeclaredMethod("isDedupConflict", DataIntegrityViolationException.class);
        method.setAccessible(true);
        DataIntegrityViolationException exception = new DataIntegrityViolationException("dispatch failed", new RuntimeException(message));
        return (boolean) method.invoke(dispatcher, exception);
    }

    private static com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository repositoryProxy() {
        return (com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository) Proxy.newProxyInstance(
                MessagingDispatcherTest.class.getClassLoader(),
                new Class<?>[]{com.takibo.messaging.infrastructure.jpa.MessageDeliveryRepository.class},
                (proxy, method, args) -> {
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
