package com.takibo.audit.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.takibo.audit.aspect.AuditAspect;
import com.takibo.audit.aspect.LoggingAspect;
import com.takibo.audit.core.AuditEventBuilder;
import com.takibo.audit.domain.EntityIdResolver;
import com.takibo.audit.infrastructure.resolver.ActionResolver;
import com.takibo.audit.infrastructure.service.AuditService;
import com.takibo.audit.infrastructure.service.LogDispatcher;
import com.takibo.audit.infrastructure.service.MaskingService;
import com.takibo.audit.spi.AuditActorProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class LoggingConfig {

    @Bean
    public LoggingAspect loggingAspect(
            LogDispatcher logDispatcher,
            ActionResolver actionResolver,
            MaskingService maskingService,
            AuditActorProvider auditActorProvider
    ) {
        return new LoggingAspect(logDispatcher, actionResolver, maskingService, auditActorProvider);
    }

    @Bean
    public AuditEventBuilder auditEventBuilder(
            EntityIdResolver entityResolver,
            MaskingService maskingService,
            ActionResolver actionResolver,
            AuditActorProvider auditActorProvider
    ) {
        return new AuditEventBuilder(entityResolver, maskingService, actionResolver, auditActorProvider);
    }

    @Bean
    public AuditAspect auditAspect(
            AuditService auditService,
            AuditEventBuilder auditEventBuilder
    ) {
        return new AuditAspect(auditService, auditEventBuilder);
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    @ConditionalOnMissingBean
    public AuditActorProvider auditActorProvider() {
        return java.util.Optional::empty;
    }
}
