package com.takibo.identitycore.domain.model;

import lombok.Value;
import java.util.UUID;

@Value
public class PermissionId {
    UUID orgId;
    UUID spaceId;
    UUID id;
    
    public static PermissionId of(UUID orgId, UUID spaceId, UUID id) {
        return new PermissionId(orgId, spaceId, id);
    }
    
    public static PermissionId newId(UUID orgId, UUID spaceId) {
        return new PermissionId(orgId, spaceId, UUID.randomUUID());
    }
}
