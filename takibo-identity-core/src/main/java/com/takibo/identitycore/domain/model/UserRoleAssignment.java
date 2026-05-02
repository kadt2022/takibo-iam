package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;
import java.util.UUID;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserRoleAssignment {
    @EqualsAndHashCode.Include @ToString.Include
    private final UUID id;

    private final SpaceId spaceId;
    private final UserId userId;
    private final RoleId roleId;
    private final AssignmentMeta meta; // assignedAt + assignedBy
    private long version;

    public UserRoleAssignment(UUID id, SpaceId spaceId, UserId userId, RoleId roleId,
                              AssignmentMeta meta, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.roleId = Objects.requireNonNull(roleId, "roleId");
        this.meta = Objects.requireNonNull(meta, "meta");
        this.version = version;
    }
}

