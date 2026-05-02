package com.takibo.authorizationserver.infrastructure.jpa.mapper;

import com.takibo.authorizationserver.domain.audit.model.TasAuditEvent;
import com.takibo.authorizationserver.infrastructure.jpa.entity.TasAuditEventEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface TasAuditEventMapper {

    TasAuditEvent toDomain(TasAuditEventEntity entity);

    @Mapping(target = "occurredAt", ignore = true)
    TasAuditEventEntity toEntity(TasAuditEvent domain);
}
