package com.takibo.identitycore.domain.model;

import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.domain.vo.TakiboIdentityId;
import com.takibo.identitycore.infrastructure.entity.IdentityStatus;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder(toBuilder = true)
public class TakiboIdentity {

    @EqualsAndHashCode.Include
    private final TakiboIdentityId id;

    private final OrganizationId orgId;
    private final AccountId accountId;

    private final IdentityType identityType;
    private final IdentityStatus identityStatus;

    @Builder.Default
    private final Integer trustLevel = 0;

    @Builder.Default
    private final Integer riskScore = 0;

    private final Instant lastActiveAt;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    @Builder.Default
    private final Long version = 0L;

    // ===== FACTORY METHODS =====

    public static TakiboIdentity createHuman(TakiboIdentityId id, 
                                             OrganizationId orgId, 
                                             AccountId accountId) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orgId, "orgId is required");
        Objects.requireNonNull(accountId, "accountId is required");

        Instant now = Instant.now();

        return TakiboIdentity.builder()
                .id(id)
                .orgId(orgId)
                .accountId(accountId)
                .identityType(IdentityType.HUMAN)
                .identityStatus(IdentityStatus.ACTIVE)
                .trustLevel(0)
                .riskScore(0)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("SYSTEM")
                .version(0L)
                .build();
    }

    public static TakiboIdentity createService(TakiboIdentityId id,
                                               OrganizationId orgId,
                                               AccountId accountId) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orgId, "orgId is required");
        Objects.requireNonNull(accountId, "accountId is required");

        Instant now = Instant.now();

        return TakiboIdentity.builder()
                .id(id)
                .orgId(orgId)
                .accountId(accountId)
                .identityType(IdentityType.SERVICE)
                .identityStatus(IdentityStatus.ACTIVE)
                .trustLevel(0)
                .riskScore(0)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("SYSTEM")
                .version(0L)
                .build();
    }

    public static TakiboIdentity createMachine(TakiboIdentityId id,
                                               OrganizationId orgId,
                                               AccountId accountId) {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orgId, "orgId is required");
        Objects.requireNonNull(accountId, "accountId is required");

        Instant now = Instant.now();

        return TakiboIdentity.builder()
                .id(id)
                .orgId(orgId)
                .accountId(accountId)
                .identityType(IdentityType.MACHINE)
                .identityStatus(IdentityStatus.ACTIVE)
                .trustLevel(0)
                .riskScore(0)
                .createdAt(now)
                .updatedAt(now)
                .createdBy("SYSTEM")
                .version(0L)
                .build();
    }

    // ===== BUSINESS METHODS =====

    public TakiboIdentity suspend() {
        return this.toBuilder()
                .identityStatus(IdentityStatus.SUSPENDED)
                .updatedAt(Instant.now())
                .build();
    }

    public TakiboIdentity activate() {
        return this.toBuilder()
                .identityStatus(IdentityStatus.ACTIVE)
                .updatedAt(Instant.now())
                .build();
    }

    public TakiboIdentity disable() {
        return this.toBuilder()
                .identityStatus(IdentityStatus.DISABLED)
                .updatedAt(Instant.now())
                .build();
    }

    public TakiboIdentity updateRiskScore(Integer newScore) {
        return this.toBuilder()
                .riskScore(newScore)
                .updatedAt(Instant.now())
                .build();
    }

    public TakiboIdentity updateTrustLevel(Integer newLevel) {
        return this.toBuilder()
                .trustLevel(newLevel)
                .updatedAt(Instant.now())
                .build();
    }

    public TakiboIdentity recordActivity() {
        return this.toBuilder()
                .lastActiveAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}