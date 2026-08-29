package com.takibo.authorizationserver.domain.client;

/**
 * Porteur thread-local du {@link ResolvedOAuthClient} résolu pour la requête en cours
 * (TAS-GRANTS-01).
 * <p>
 * {@code TenantResolutionFilter} le peuple une seule fois par requête ;
 * {@code PkceEnforcementFilter}, qui s'exécute après lui dans la chaîne, le relit — c'est ce
 * qui élimine le second lookup parallèle par {@code (org_id, space_id, client_id)} que
 * l'ancien filtre effectuait de son côté. Doit être purgé après chaque requête, y compris en
 * échec, pour ne pas fuiter vers une requête suivante sur le même thread.
 */
public final class ResolvedOAuthClientContextHolder {

    private static final ThreadLocal<ResolvedOAuthClient> CONTEXT = new ThreadLocal<>();

    private ResolvedOAuthClientContextHolder() {
    }

    public static void set(ResolvedOAuthClient client) {
        CONTEXT.set(client);
    }

    public static ResolvedOAuthClient get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
