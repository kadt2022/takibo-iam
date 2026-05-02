package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.GroupId;
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
public class GroupMember {
    @EqualsAndHashCode.Include @ToString.Include
    private final UUID id;

    private final SpaceId spaceId;
    private final GroupId groupId;
    private final UserId userId;
    private Instant joinedAt;    // = infra.group_members.joined_at
    private long version;

    public GroupMember(UUID id, SpaceId spaceId, GroupId groupId, UserId userId,
                       Instant joinedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.groupId = Objects.requireNonNull(groupId, "groupId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.joinedAt = joinedAt;
        this.version = version;
    }
}

