package com.takibo.authorizationserver.infrastructure.jpa.mapper;

import com.takibo.authorizationserver.domain.authz.model.OAuth2AuthorizationConsent;
import com.takibo.authorizationserver.infrastructure.jpa.entity.OAuth2AuthorizationConsentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OAuth2AuthorizationConsentMapper {

    OAuth2AuthorizationConsent toDomain(OAuth2AuthorizationConsentEntity entity);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    OAuth2AuthorizationConsentEntity toEntity(OAuth2AuthorizationConsent domain);
}
