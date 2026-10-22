package com.takibo.outbox.starter;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxAutoConfigurationSmokeTest {

    @Test
    void autoConfigurationCanLoad() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
                .withPropertyValues("takibo.outbox.enabled=false")
                .run(ctx -> assertTrue(ctx.isRunning()));
    }
}
