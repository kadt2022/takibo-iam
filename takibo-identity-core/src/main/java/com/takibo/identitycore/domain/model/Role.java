package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@Builder
public class Role {

    @EqualsAndHashCode.Include
    @ToString.Include
    private final RoleId id;

    private final SpaceId spaceId;

    @ToString.Include
    private String code;

    private String name;
    private String description;

    private final RoleNature nature;

    private final Instant createdAt;
    private Instant updatedAt;

    private long version;

    public static Role createNew(SpaceId spaceId, String code, String name, String description) {
        Instant now = Instant.now();

        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");

        return Role.builder()
                .id(RoleId.generate())
                .spaceId(spaceId)
                .code(code)
                .name(name)
                .description(description)
                .nature(RoleNature.BUSINESS)
                .createdAt(now)
                .updatedAt(now)
                .version(0L)
                .build();
    }

    public void updateInfo(String newName, String newDescription) {
        this.name = newName;
        this.description = newDescription;
        this.updatedAt = Instant.now();
    }
}
