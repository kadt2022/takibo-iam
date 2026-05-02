package com.takibo.outbox.starter;

import com.takibo.outbox.spring.config.OutboxSpringConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(prefix = "takibo.outbox", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import(OutboxSpringConfiguration.class)
public class OutboxAutoConfiguration {
}
