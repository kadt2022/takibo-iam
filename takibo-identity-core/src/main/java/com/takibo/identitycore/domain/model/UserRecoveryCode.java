package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.RecoveryCodeId;
import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;

@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SuppressWarnings({"FieldMayBeFinal", "unused"})
public class UserRecoveryCode {
    @EqualsAndHashCode.Include @ToString.Include
    private final RecoveryCodeId id;

    private final SpaceId spaceId;
    private final UserId userId;
    private final String codeHash;
    private final Instant createdAt;

    private boolean used;
    private Instant usedAt;
    private Instant updatedAt;
    private long version;

    public UserRecoveryCode(RecoveryCodeId id, SpaceId spaceId, UserId userId,
                            String codeHash, boolean used, Instant usedAt,
                            Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.codeHash = Objects.requireNonNull(codeHash, "codeHash");
        this.used = used;
        this.usedAt = usedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    /** Idempotent */
    public void markUsed(Instant when){
        Objects.requireNonNull(when, "when");
        if (!this.used) { this.used = true; this.usedAt = when; }
    }
}
