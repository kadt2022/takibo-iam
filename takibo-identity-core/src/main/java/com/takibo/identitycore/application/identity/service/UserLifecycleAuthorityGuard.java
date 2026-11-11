package com.takibo.identitycore.application.identity.service;

import com.takibo.identitycore.application.rbac.effective.port.in.EffectiveRbacQueryCase;
import com.takibo.identitycore.domain.exception.SelfDemotionException;
import com.takibo.identitycore.domain.exception.UserLifecycleAuthorityException;
import com.takibo.identitycore.domain.model.User;
import com.takibo.identitycore.integration.security.port.CurrentAccountContextCase;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * SEC-HUNT-01 — autorité requise pour restreindre un utilisateur (suspendre, verrouiller,
 * désactiver).
 * <p>
 * La permission {@code P_SPACE_USERS_LIFECYCLE} ouvre la surface ; elle ne dit rien de la cible.
 * Trois règles s'y ajoutent, calquées sur la surface de gouvernance RBAC :
 * <ul>
 *   <li>le propriétaire n'est jamais restreint ici, comme {@code R_ORG_OWNER} n'y est jamais
 *       retiré ;</li>
 *   <li>personne ne se restreint soi-même, comme un administrateur ne s'y rétrograde pas ;</li>
 *   <li>l'autorité effective de la cible ne dépasse jamais celle de l'auteur.</li>
 * </ul>
 * Les autorités sont lues en base, rôles directs et hérités par les groupes, avec le même calcul
 * que celui qui signe les tokens — jamais dans les claims du token de l'auteur.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class UserLifecycleAuthorityGuard {

    private final CurrentAccountContextCase currentAccountContext;
    private final EffectiveRbacQueryCase effectiveRbac;

    void assertMayRestrict(ResolvedSpaceKey key, User target) {
        UUID actorAccountId = currentAccountContext.requireCurrentAccountId();
        UUID targetAccountId = target.getAccountId().getValue();
        if (actorAccountId.equals(targetAccountId)) {
            throw new SelfDemotionException("A user cannot restrict their own access on this surface");
        }
        Authority targetAuthority = authorityOf(key, targetAccountId);
        if (targetAuthority == Authority.OWNER) {
            log.warn("Lifecycle restriction refused: target is the organization owner userId={} actorAccountId={}",
                    target.getId().value(), actorAccountId);
            throw new UserLifecycleAuthorityException("The organization owner cannot be restricted on this surface");
        }
        Authority actorAuthority = authorityOf(key, actorAccountId);
        if (targetAuthority.outranks(actorAuthority)) {
            log.warn("Lifecycle restriction refused: target authority {} exceeds actor authority {} userId={}"
                    + " actorAccountId={}", targetAuthority, actorAuthority, target.getId().value(), actorAccountId);
            throw new UserLifecycleAuthorityException(
                    "Restricting this user requires an authority at least equal to theirs");
        }
    }

    private Authority authorityOf(ResolvedSpaceKey key, UUID accountId) {
        return Authority.highestOf(effectiveRbac.effectiveFor(key.orgId(), key.spaceId(), accountId).roles());
    }

    /** Ordre d'autorité d'un utilisateur dans le Space ; seuls les rôles d'administration comptent. */
    enum Authority {
        MEMBER(null),
        SPACE_ADMIN("R_SPACE_ADMIN"),
        ORGANIZATION_ADMIN("R_ORG_ADMIN"),
        OWNER("R_ORG_OWNER");

        private final String roleCode;

        Authority(String roleCode) {
            this.roleCode = roleCode;
        }

        static Authority highestOf(Collection<String> roles) {
            Authority highest = MEMBER;
            for (Authority authority : values()) {
                if (authority.roleCode != null && roles.contains(authority.roleCode)) {
                    highest = authority;
                }
            }
            return highest;
        }

        boolean outranks(Authority other) {
            return compareTo(other) > 0;
        }
    }
}
