//package com.takibo.outbox.jpa.mapper;
//
//import com.takibo.outbox.core.model.OutboxEnvelope;
//import com.takibo.outbox.core.model.OutboxMessage;
//import com.takibo.outbox.core.model.OutboxStatus;
//import com.takibo.outbox.jpa.entity.OutboxMessageEntity;
//
//import java.time.Instant;
//
//public final class OutboxJpaMapper {
//
//    private OutboxJpaMapper() {
//    }
//
//    public static OutboxMessageEntity toEntity(OutboxEnvelope envelope, Instant now) {
//        OutboxMessageEntity e = new OutboxMessageEntity();
//        e.setId(envelope.id());
//        e.setEventType(envelope.eventType());
//        e.setAggregateType(envelope.aggregateType());
//        e.setAggregateId(envelope.aggregateId());
//        e.setOrgId(envelope.orgId());
//        e.setSpaceId(envelope.spaceId());
//        e.setPayloadJson(envelope.payloadJson());
//        e.setDedupKey(envelope.dedupKey());
//        e.setAttempts(0);
//        e.setStatus(OutboxStatus.PENDING);
//        e.setNextRunAt(now);
//        e.setCreatedAt(envelope.createdAt());
//        e.setUpdatedAt(now);
//        return e;
//    }
//
//    public static OutboxEnvelope toEnvelope(OutboxMessageEntity e) {
//        return new OutboxEnvelope(
//                e.getId(),
//                e.getEventType(),
//                e.getAggregateType(),
//                e.getAggregateId(),
//                e.getOrgId(),
//                e.getSpaceId(),
//                e.getPayloadJson(),
//                e.getDedupKey(),
//                e.getCreatedAt()
//        );
//    }
//
//    public static OutboxMessage toModel(OutboxMessageEntity e) {
//        return new OutboxMessage(
//                e.getId(),
//                e.getEventType(),
//                e.getAggregateType(),
//                e.getAggregateId(),
//                e.getOrgId(),
//                e.getSpaceId(),
//                e.getPayloadJson(),
//                e.getStatus(),
//                e.getAttempts(),
//                e.getNextRunAt(),
//                e.getLastError(),
//                e.getLockedAt(),
//                e.getLockedBy(),
//                e.getDedupKey(),
//                e.getCreatedAt(),
//                e.getUpdatedAt()
//        );
//    }
//}
