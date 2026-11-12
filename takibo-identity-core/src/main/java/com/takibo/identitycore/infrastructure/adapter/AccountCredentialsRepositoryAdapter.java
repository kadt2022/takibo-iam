package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.model.AccountCredentials;
import com.takibo.identitycore.domain.model.FailedAttemptReservation;
import com.takibo.identitycore.domain.repository.AccountCredentialsRepository;
import com.takibo.identitycore.domain.vo.AccountId;
import com.takibo.identitycore.domain.vo.OrganizationId;
import com.takibo.identitycore.infrastructure.entity.AccountCredentialsEntity;
import com.takibo.identitycore.infrastructure.entity.AccountEntity;
import com.takibo.identitycore.infrastructure.jpa.mapper.AccountCredentialsJpaMapper;
import com.takibo.identitycore.infrastructure.jpa.repository.JpaAccountCredentialsRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AccountCredentialsRepositoryAdapter implements AccountCredentialsRepository {

    private final JpaAccountCredentialsRepository jpa;
    private final AccountCredentialsJpaMapper mapper;
    private final EntityManager em;

    @Override
    @Transactional
    public AccountCredentials save(AccountCredentials domain, UUID orgId) {
        UUID accountId = domain.getAccountId().getValue();
        AccountCredentialsEntity.AccountCredentialsId id =
                new AccountCredentialsEntity.AccountCredentialsId(orgId, accountId);

        // Ligne existante (échec/reset de login, rotation de mot de passe) : on mute
        // l'entité CHARGÉE. mapper.toEntity() marque isNew=true (Persistable) et
        // provoquerait un INSERT en doublon de la PK (org_id, account_id).
        Optional<AccountCredentialsEntity> existing = jpa.findById(id);
        if (existing.isPresent()) {
            AccountCredentialsEntity entity = applyMutableState(existing.get(), domain);
            return mapper.toDomain(jpa.saveAndFlush(entity));
        }

        AccountCredentialsEntity entity = mapper.toEntity(domain, orgId);
        AccountEntity accountRef = em.getReference(AccountEntity.class, accountId);

        entity.setAccount(accountRef);
        entity.setOrgId(orgId);
        entity.setAccountId(accountId);

        return mapper.toDomain(jpa.saveAndFlush(entity));
    }

    private AccountCredentialsEntity applyMutableState(AccountCredentialsEntity entity, AccountCredentials domain) {
        entity.setPasswordHash(domain.getPasswordHash().getHash());
        entity.setPasswordAlgo(domain.getPasswordHash().getAlgo());
        entity.setPasswordVersion(domain.getPasswordHash().getVersion());
        entity.setPasswordUpdatedAt(domain.getPasswordUpdatedAt());
        entity.setMustChangeNextLogin(domain.isMustChangeNextLogin());
        entity.setFailedAttempts(domain.getFailedAttempts());
        entity.setLockedUntil(domain.getLockedUntil());
        return entity;
    }

    @Override
    public Optional<AccountCredentials> find(OrganizationId orgId, AccountId accountId) {
        AccountCredentialsEntity.AccountCredentialsId id =
                new AccountCredentialsEntity.AccountCredentialsId(orgId.getValue(), accountId.getValue());
        return jpa.findById(id).map(mapper::toDomain);
    }

    // SEC-HUNT-02 : chaque écriture du compteur est une seule instruction SQL. PostgreSQL la
    // sérialise par le verrou de ligne : aucune mise à jour perdue, aucun conflit de version
    // (StaleStateException) sous des connexions concurrentes. Les expressions de SET lisent
    // l'état antérieur de la ligne ; RETURNING rend l'état postérieur.

    private static final String ORG_ID = "orgId";
    private static final String ACCOUNT_ID = "accountId";

    private static final String RESERVE = """
            UPDATE account_credentials
               SET failed_attempts = failed_attempts + 1,
                   locked_until = CASE WHEN :lockEnabled AND failed_attempts + 1 >= :maxFailedAttempts
                                       THEN :lockUntil ELSE locked_until END,
                   updated_at = :now,
                   version = version + 1
             WHERE org_id = :orgId AND account_id = :accountId
               AND (locked_until IS NULL OR locked_until <= :now)
            RETURNING failed_attempts
            """;

    private static final String CANCEL = """
            UPDATE account_credentials
               SET failed_attempts = GREATEST(failed_attempts - 1, 0),
                   locked_until = CASE WHEN :clearLock OR failed_attempts - 1 < :maxFailedAttempts
                                       THEN NULL ELSE locked_until END,
                   updated_at = :now,
                   version = version + 1
             WHERE org_id = :orgId AND account_id = :accountId
            """;

    private static final String CLEAR = """
            UPDATE account_credentials
               SET failed_attempts = 0,
                   locked_until = NULL,
                   updated_at = :now,
                   version = version + 1
             WHERE org_id = :orgId AND account_id = :accountId
            """;

    @Override
    @Transactional
    public Optional<FailedAttemptReservation> reserveFailedAttempt(OrganizationId orgId, AccountId accountId,
                                                                   int maxFailedAttempts, Instant lockUntil,
                                                                   Instant now) {
        boolean lockEnabled = lockUntil != null;
        List<?> counters = em.createNativeQuery(RESERVE)
                .setParameter("lockEnabled", lockEnabled)
                .setParameter("maxFailedAttempts", maxFailedAttempts)
                .setParameter("lockUntil", lockEnabled ? lockUntil : now)
                .setParameter("now", now)
                .setParameter(ORG_ID, orgId.getValue())
                .setParameter(ACCOUNT_ID, accountId.getValue())
                .getResultList();
        if (counters.isEmpty()) {
            return Optional.empty();
        }
        int failedAttempts = ((Number) counters.getFirst()).intValue();
        return Optional.of(new FailedAttemptReservation(failedAttempts,
                lockEnabled && failedAttempts >= maxFailedAttempts));
    }

    @Override
    @Transactional
    public void cancelFailedAttempt(OrganizationId orgId, AccountId accountId, FailedAttemptReservation reservation,
                                    int maxFailedAttempts, Instant now) {
        // Le verrou est levé si cette réservation l'a posé, ou si le compteur repasse sous le
        // seuil : un échec concurrent a pu le poser en comptant notre réservation.
        em.createNativeQuery(CANCEL)
                .setParameter("clearLock", reservation.locksAccount())
                .setParameter("maxFailedAttempts", maxFailedAttempts)
                .setParameter("now", now)
                .setParameter(ORG_ID, orgId.getValue())
                .setParameter(ACCOUNT_ID, accountId.getValue())
                .executeUpdate();
    }

    @Override
    @Transactional
    public void clearFailedAttempts(OrganizationId orgId, AccountId accountId, Instant now) {
        em.createNativeQuery(CLEAR)
                .setParameter("now", now)
                .setParameter(ORG_ID, orgId.getValue())
                .setParameter(ACCOUNT_ID, accountId.getValue())
                .executeUpdate();
    }
}
