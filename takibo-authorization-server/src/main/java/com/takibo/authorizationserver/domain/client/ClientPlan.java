package com.takibo.authorizationserver.domain.client;

/**
 * Plan d'un client OAuth2 : l'etendue de la frontiere qu'il porte.
 * <p>
 * Le plan n'est pas une commodite de lecture, c'est un invariant. Il determine exactement
 * quels identifiants de tenant un client peut porter, et le {@link ResolvedOAuthClient} le
 * fait respecter a la construction :
 * <ul>
 *   <li>{@link #PLATFORM} — autorite d'instance TAKIBO. Aucune organisation, aucun space.
 *       Un token issu d'un tel client est ferme aux routes situees, par absence de tenant
 *       et non par filtrage.</li>
 *   <li>{@link #ORGANIZATION} — une organisation, sans space. Le space situe l'action ;
 *       l'organisation situe l'autorite.</li>
 *   <li>{@link #SPACE} — une organisation et un space, tous deux exiges.</li>
 * </ul>
 * Les valeurs de claim sont figees ici et verifiees par test contre le canon partage, de
 * sorte que le domaine ne depende pas de la couche d'infrastructure qui les emet.
 */
public enum ClientPlan {

    PLATFORM("PLATFORM", "platform_client"),
    ORGANIZATION("ORGANIZATION", "oauth2_client"),
    SPACE("SPACE", "oauth2_client");

    private final String claimValue;
    private final String tenantSource;

    ClientPlan(String claimValue, String tenantSource) {
        this.claimValue = claimValue;
        this.tenantSource = tenantSource;
    }

    /** Valeur portee par le claim {@code takibo_scope_level}. */
    public String claimValue() {
        return claimValue;
    }

    /** Provenance de la frontiere, portee par le claim {@code takibo_tenant_source}. */
    public String tenantSource() {
        return tenantSource;
    }

    public boolean requiresOrganization() {
        return this != PLATFORM;
    }

    public boolean requiresSpace() {
        return this == SPACE;
    }
}
