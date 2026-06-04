package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.SpaceId;
import lombok.*;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@Builder
public class Group {
    @EqualsAndHashCode.Include @ToString.Include
    private final GroupId id;
    private final SpaceId spaceId;
    private final GroupNature nature;

    @ToString.Include
    private String name;
    private String code;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    public static Group createNew(SpaceId spaceId, String code, String name, String description, GroupNature nature) {
        Instant now = Instant.now();

        Objects.requireNonNull(spaceId, "spaceId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(nature, "nature");

        return Group.builder()
                .id(GroupId.generate())
                .spaceId(spaceId)
                .nature(nature)
                .code(code)
                .name(name)
                .description(description)
                .createdAt(now)
                .updatedAt(now)
                .version(0L)
                .build();
    }
}

