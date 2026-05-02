package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserPermissionGrant {
    @EqualsAndHashCode.Include @ToString.Include
    private final UUID id;

    private final SpaceId spaceId;
    private final UserId userId;
    private final PermissionId permissionId;
    private PermissionEffect effect;     // ALLOW/DENY
    private Instant createdAtUserPerm;   // ≈ infra.created_at_user_perm
    private long version;

    public UserPermissionGrant(UUID id, SpaceId spaceId, UserId userId, PermissionId permissionId,
                               PermissionEffect effect, Instant createdAtUserPerm, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.permissionId = Objects.requireNonNull(permissionId, "permissionId");
        this.effect = Objects.requireNonNull(effect, "effect");
        this.createdAtUserPerm = createdAtUserPerm;
        this.version = version;
    }

    public void setEffect(PermissionEffect effect) { this.effect = Objects.requireNonNull(effect); }
}
