package com.takibo.identitycore.application.rbac.catalog.model;

/**
 * Provenance d'un élément du catalogue RBAC.
 * <p>
 * {@code TECHNICAL} : défini par la plateforme dans le code (enums du catalogue technique),
 * identique pour tous les tenants, jamais éditable par un tenant.
 * {@code DATABASE} : défini et persisté en base pour un space donné.
 */
public enum CatalogOrigin {
    TECHNICAL,
    DATABASE
}
