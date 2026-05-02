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
public class UserMfaSettings {
    @EqualsAndHashCode.Include @ToString.Include
    private final UUID id;

    private final SpaceId spaceId;
    private final UserId userId;
    private boolean enabled;
    private final MfaFactorType preferredFactor; // nullable
    private int recoveryCodesCount;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    public UserMfaSettings(UUID id, SpaceId spaceId, UserId userId,
                           boolean enabled, MfaFactorType preferredFactor, int recoveryCodesCount,
                           Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.enabled = enabled;
        this.preferredFactor = preferredFactor;
        this.recoveryCodesCount = recoveryCodesCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public void enable(){ this.enabled = true; }
    public void disable(){ this.enabled = false; }
}

