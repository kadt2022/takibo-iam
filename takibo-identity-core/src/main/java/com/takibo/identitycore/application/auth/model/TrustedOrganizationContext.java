package com.takibo.identitycore.application.auth.model;

import com.takibo.identitycore.integration.space.port.ResolvedOrgKey;
import com.takibo.identitycore.integration.space.port.ResolvedSpaceKey;

import java.util.Objects;
import java.util.UUID;

/**
 * L'Organization dans laquelle un humain va être authentifié, résolue côté serveur AVANT toute
 * vérification de mot de passe. Elle ne se construit qu'à partir d'une résolution, jamais d'un
 * identifiant fourni tel quel par un navigateur ou un client.
 * <p>
 * Résoudre l'Organization ne prouve ni que l'humain lui appartient, ni qu'un client OAuth est
 * autorisé à y agir : ce sont deux vérifications distinctes, faites ailleurs.
 */
public final class TrustedOrganizationContext {

    private final UUID orgId;

    private TrustedOrganizationContext(UUID orgId) {
        this.orgId = Objects.requireNonNull(orgId, "orgId");
    }

    /** Organization résolue et vérifiée ACTIVE ({@code resolveActiveOrganization}). */
    public static TrustedOrganizationContext from(ResolvedOrgKey key) {
        return new TrustedOrganizationContext(key.orgId());
    }

    /** Chemin transitoire SPACE du login REST : Organization du space résolu. */
    public static TrustedOrganizationContext from(ResolvedSpaceKey key) {
        return new TrustedOrganizationContext(key.orgId());
    }

    public UUID orgId() {
        return orgId;
    }

    @Override
    public String toString() {
        return "TrustedOrganizationContext[orgId=" + orgId + "]";
    }
}
