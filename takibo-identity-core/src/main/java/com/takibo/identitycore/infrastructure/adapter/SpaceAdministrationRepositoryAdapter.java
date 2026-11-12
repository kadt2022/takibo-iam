package com.takibo.identitycore.infrastructure.adapter;

import com.takibo.identitycore.domain.rbac.repository.SpaceAdministrationRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implémentation PostgreSQL de {@link SpaceAdministrationRepository}.
 * <p>
 * Le verrou est un verrou consultatif de transaction ({@code pg_advisory_xact_lock}) dont la clé
 * dérive du Space : il ne verrouille aucune ligne métier, ne couple pas identity-core à la table
 * {@code spaces}, et se libère seul à la validation ou à l'annulation. Une collision de clés
 * entre deux Spaces ne ferait que sérialiser inutilement leurs retraits.
 */
@Component
@RequiredArgsConstructor
public class SpaceAdministrationRepositoryAdapter implements SpaceAdministrationRepository {

    private static final String LOCK = "SELECT pg_advisory_xact_lock(hashtextextended(:key, 0))";

    /** Administrateurs effectifs ; le marqueur %s choisit l'Account visé ou exclu. */
    private static final String EFFECTIVE = """
            SELECT COUNT(DISTINCT u.account_id) FROM users u
             WHERE u.org_id = :orgId AND u.space_id = :spaceId AND u.status = 'ACTIVE'
               AND u.account_id %s :accountId
               AND (EXISTS (SELECT 1 FROM role_assignments r
                             WHERE r.org_id = u.org_id AND r.space_id = u.space_id
                               AND r.identity_type = 'ACCOUNT' AND r.identity_id = u.account_id
                               AND r.role_code = 'R_SPACE_ADMIN')
                 OR EXISTS (SELECT 1 FROM group_assignments g
                             WHERE g.org_id = u.org_id AND g.space_id = u.space_id
                               AND g.identity_type = 'ACCOUNT' AND g.identity_id = u.account_id
                               AND g.group_code = 'G_SPACE_ADMINS'))
            """;

    private final EntityManager em;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAdministrationOf(UUID spaceId) {
        em.createNativeQuery(LOCK).setParameter("key", "takibo:space-administration:" + spaceId).getSingleResult();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isEffectiveAdministrator(UUID orgId, UUID spaceId, UUID accountId) {
        return count(EFFECTIVE.formatted("="), orgId, spaceId, accountId) > 0;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long countEffectiveAdministratorsOtherThan(UUID orgId, UUID spaceId, UUID accountId) {
        return count(EFFECTIVE.formatted("<>"), orgId, spaceId, accountId);
    }

    private long count(String sql, UUID orgId, UUID spaceId, UUID accountId) {
        return ((Number) em.createNativeQuery(sql)
                .setParameter("orgId", orgId)
                .setParameter("spaceId", spaceId)
                .setParameter("accountId", accountId)
                .getSingleResult()).longValue();
    }
}
