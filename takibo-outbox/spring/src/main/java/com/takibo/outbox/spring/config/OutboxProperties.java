package com.takibo.outbox.spring.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "takibo.outbox")
public class OutboxProperties {

    private boolean enabled = true;

    @Valid
    private final Processor processor = new Processor();

    @Valid
    private final Backoff backoff = new Backoff();

    private boolean deadLetterLogPayload = false;

    @Getter
    @Setter
    public static class Processor {

        @Min(1)
        @Max(1000)
        private int batchSize = 50;

        private Duration fixedDelay = Duration.ofMillis(1000);

        @Min(1)
        @Max(1000)
        private int maxAttempts = 10;

        @NotBlank
        private String lockedBy = "takibo-outbox";

        private boolean schedulingEnabled = true;

        private Duration lockTimeout = Duration.ofMinutes(5);
    }

    @Getter
    @Setter
    public static class Backoff {

        private String type = "exponential";

        private Duration baseDelay = Duration.ofSeconds(1);

        private Duration maxDelay = Duration.ofSeconds(60);

        @Min(1)
        @Max(10)
        private int multiplier = 2;
    }
}
