//package com.takibo.identitycore.domain.model;
//
//import com.takibo.identitycore.domain.rbac.model.GroupSource;
//import lombok.AllArgsConstructor;
//import lombok.Builder;
//import lombok.Getter;
//
//import java.time.Instant;
//import java.util.UUID;
//
//@Getter
//@Builder(toBuilder = true)
//@AllArgsConstructor
//public class GroupAssignment {
//    private final UUID id;
//    private final UUID orgId;
//    private final UUID spaceId;  // Nullable - org-scoped OU space-scoped
//    private final IdentityType identityType;
//    private final UUID identityId;
//    private final GroupRefKind groupRefKind;
//    private final String groupCode;
//    private final UUID businessGroupId;
//    private final GroupSource source;
//    private final Instant createdAt;
//    private final UUID createdBy;
//    private final Instant updatedAt;
//    private final UUID updatedBy;
//}
