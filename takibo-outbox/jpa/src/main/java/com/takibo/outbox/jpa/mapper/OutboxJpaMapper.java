package com.takibo.outbox.jpa.mapper;

import com.takibo.outbox.core.model.OutboxEnvelope;
import com.takibo.outbox.core.model.OutboxMessage;
import com.takibo.outbox.core.model.OutboxStatus;
import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OutboxJpaMapper {

    @Mapping(target = "id", source = "envelope.id")
    @Mapping(target = "eventType", source = "envelope.eventType")
    @Mapping(target = "aggregateType", source = "envelope.aggregateType")
    @Mapping(target = "aggregateId", source = "envelope.aggregateId")
    @Mapping(target = "orgId", source = "envelope.orgId")
    @Mapping(target = "spaceId", source = "envelope.spaceId")
    @Mapping(target = "payloadJson", source = "envelope.payloadJson")
    @Mapping(target = "dedupKey", source = "envelope.dedupKey")
    @Mapping(target = "attempts", constant = "0")
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "nextRunAt", source = "now")
    @Mapping(target = "createdAt", source = "envelope.createdAt")
    @Mapping(target = "updatedAt", source = "now")
    @Mapping(target = "lastError", ignore = true)
    @Mapping(target = "lockedAt", ignore = true)
    @Mapping(target = "lockedBy", ignore = true)
    OutboxMessageEntity toEntity(OutboxEnvelope envelope, Instant now);

    @Mapping(target = "id", source = "id")
    @Mapping(target = "eventType", source = "eventType")
    @Mapping(target = "aggregateType", source = "aggregateType")
    @Mapping(target = "aggregateId", source = "aggregateId")
    @Mapping(target = "orgId", source = "orgId")
    @Mapping(target = "spaceId", source = "spaceId")
    @Mapping(target = "payloadJson", source = "payloadJson")
    @Mapping(target = "dedupKey", source = "dedupKey")
    @Mapping(target = "createdAt", source = "createdAt")
    OutboxEnvelope toEnvelope(OutboxMessageEntity entity);

    @Mapping(target = "payloadJson", source = "payloadJson")
    OutboxMessage toModel(OutboxMessageEntity entity);
}