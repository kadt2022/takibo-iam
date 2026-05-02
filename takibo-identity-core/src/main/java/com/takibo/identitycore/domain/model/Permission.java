package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.SpaceId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Permission {
    @EqualsAndHashCode.Include @ToString.Include
    private final PermissionId id;
    private final SpaceId spaceId;

    @ToString.Include
    private String code;
    private String name;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    public Permission(PermissionId id, SpaceId spaceId, String code, String description,
                      Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.code = Objects.requireNonNull(code, "code");
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }
}
