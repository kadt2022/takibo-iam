package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.MfaFactorId;
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
public class UserMfaFactor {
    @EqualsAndHashCode.Include @ToString.Include
    private final MfaFactorId id;

    private final SpaceId spaceId;
    private final UserId userId;
    private final MfaFactorType factorType;  // TOTP/WEBAUTHN/SMS/EMAIL/PUSH
    private final String label;

    // Champs spécifiques par type (laisser null si non applicable)
    private final String secretEnc;           // TOTP (chiffré)
    private final String webauthnPublicKey;   // WEBAUTHN
    private final String webauthnCredentialId;// WEBAUTHN
    private final String phoneNumber;         // SMS
    private final String emailAddress;        // EMAIL

    private boolean verified;
    private boolean primary;
    private Instant lastUsedAt;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    public UserMfaFactor(MfaFactorId id, SpaceId spaceId, UserId userId, MfaFactorType factorType,
                         String label, String secretEnc, String webauthnPublicKey, String webauthnCredentialId,
                         String phoneNumber, String emailAddress, boolean verified, boolean primary,
                         Instant lastUsedAt, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.spaceId = Objects.requireNonNull(spaceId, "spaceId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.factorType = Objects.requireNonNull(factorType, "factorType");
        this.label = label;
        this.secretEnc = secretEnc;
        this.webauthnPublicKey = webauthnPublicKey;
        this.webauthnCredentialId = webauthnCredentialId;
        this.phoneNumber = phoneNumber;
        this.emailAddress = emailAddress;
        this.verified = verified;
        this.primary = primary;
        this.lastUsedAt = lastUsedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public void enablePrimary(){ this.primary = true; }
    public void disablePrimary(){ this.primary = false; }
    public void markVerified(){ this.verified = true; }
    public void markUsed(Instant when){ this.lastUsedAt = Objects.requireNonNull(when); }
}
