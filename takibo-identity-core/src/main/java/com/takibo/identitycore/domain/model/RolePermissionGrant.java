package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RolePermissionGrant {
    @EqualsAndHashCode.Include @ToString.Include
    private final UUID id;

    private final SpaceId spaceId;
    private final RoleId roleId;
    private final PermissionId permissionId;
    private PermissionEffect effect;
    private Instant createdAtRolePerm;
    private long version;

    public RolePermissionGrant(UUID id, SpaceId spaceId, RoleId roleId, PermissionId permissionId,
                               PermissionEffect effect, Instant createdAtRolePerm, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.roleId = Objects.requireNonNull(roleId, "roleId");
        this.permissionId = Objects.requireNonNull(permissionId, "permissionId");
        this.effect = Objects.requireNonNull(effect, "effect");
        this.createdAtRolePerm = createdAtRolePerm;
        this.version = version;
    }

    public void setEffect(PermissionEffect effect) { this.effect = Objects.requireNonNull(effect); }
}
