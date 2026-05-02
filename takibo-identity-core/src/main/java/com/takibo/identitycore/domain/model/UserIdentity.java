package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.SpaceId;
import com.takibo.identitycore.domain.vo.UserId;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Lien utilisateur ↔ fournisseur d'identité (ex: google/azuread/okta + subject) */
@Getter
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserIdentity {
    @EqualsAndHashCode.Include @ToString.Include
    private final UUID id;

    private final SpaceId spaceId;
    private final UserId userId;

    @ToString.Include
    private String provider; // ex: "google"
    private String subject;  // identifiant externe stable
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    public UserIdentity(UUID id, SpaceId spaceId, UserId userId,
                        String provider, String subject, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.subject = Objects.requireNonNull(subject, "subject");
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }
}

