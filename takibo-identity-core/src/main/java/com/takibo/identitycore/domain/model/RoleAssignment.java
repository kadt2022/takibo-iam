//package com.takibo.identitycore.domain.model;
//
//import com.takibo.identitycore.domain.rbac.model.RoleSource;
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Getter;
//
//import java.time.Instant;
//import java.util.UUID;
//
//@Getter
//public class RoleAssignment {
//
//    private final UUID id;
//    private final UUID orgId;
//    private final UUID spaceId;
//    private final IdentityType identityType;
//    private final UUID identityId;
//    private final RoleRefKind roleRefKind;
//    private final String roleCode;
//    private final UUID businessRoleId;
//    private final RoleSource source;
//    private final Instant createdAt;
//    private final UUID createdBy;
//    private final Instant updatedAt;
//    private final UUID updatedBy;
//
//    @Builder(toBuilder = true)
//    public RoleAssignment(
//            UUID id,
//            UUID orgId,
//            UUID spaceId,
//            IdentityType identityType,
//            UUID identityId,
//            RoleRefKind roleRefKind,
//            String roleCode,
//            UUID businessRoleId,
//            RoleSource source,
//            Instant createdAt,
//            UUID createdBy,
//            Instant updatedAt,
//            UUID updatedBy
//    ) {
//        if (orgId == null) throw new IllegalArgumentException("orgId is required");
//        if (identityType == null) throw new IllegalArgumentException("identityType is required");
//        if (identityId == null) throw new IllegalArgumentException("identityId is required");
//        if (roleCode == null || roleCode.isBlank()) throw new IllegalArgumentException("roleCode is required");
//
//        this.roleRefKind = (roleRefKind != null) ? roleRefKind : RoleRefKind.TECHNICAL_CODE;
//        this.source = (source != null) ? source : RoleSource.TECHNICAL;
//
//        this.id = id;
//        this.orgId = orgId;
//        this.spaceId = spaceId;
//        this.identityType = identityType;
//        this.identityId = identityId;
//        this.roleCode = roleCode;
//        this.businessRoleId = businessRoleId;
//        this.createdAt = createdAt != null ? createdAt : Instant.now();
//        this.createdBy = createdBy;
//        this.updatedAt = updatedAt;
//        this.updatedBy = updatedBy;
//    }
//}
