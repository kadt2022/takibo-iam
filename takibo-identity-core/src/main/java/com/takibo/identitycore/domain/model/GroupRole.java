package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.GroupId;
import com.takibo.identitycore.domain.vo.RoleId;
import com.takibo.identitycore.domain.vo.SpaceId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public final class GroupRole {

    /** Identifiant technique (optionnel à la création – laissé à null pour forcer un INSERT) */
    @EqualsAndHashCode.Include
    private final UUID id;

    /** Contexte d’appartenance */
    private final SpaceId spaceId;

    /** Lien (groupe ↔ rôle) dans le space */
    private final GroupId groupId;
    private final RoleId roleId;

    /** Métadonnées d’assignation */
    private final Instant assignedAt;
    private final UUID assignedBy;

    /** Audit & locking */
    private final Instant createdAt;
    private final Instant updatedAt;
    @Builder.Default
    private final Long version = 0L;


    /** Factory simple (id null → INSERT) */
    public static GroupRole create(SpaceId spaceId, GroupId groupId, RoleId roleId) {
        Instant when = Instant.now();
        return GroupRole.builder()
                .id(null)
                .spaceId(spaceId)
                .groupId(groupId)
                .roleId(roleId)
                .assignedAt(when)
                .assignedBy(null) // provisoirement
                .createdAt(when)
                .version( 0L)
                .build();
    }
}
