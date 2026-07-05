package com.takibo.identitycore.application.rbac.catalog.model;

/**
 * Nature d'un élément du catalogue RBAC. TECHNICAL n'est PAS de la gouvernance
 * venue du code : c'est une nature à part entière.
 * <p>
 * {@code TECHNICAL} : rôle/groupe/permission système défini par la plateforme, stable,
 * non modifiable par tenant.
 * {@code GOVERNANCE} : élément tenant persisté en base, servant à l'administration locale.
 * {@code BUSINESS} : élément tenant persisté en base, servant à la logique métier.
 */
public enum CatalogNature {
    TECHNICAL,
    GOVERNANCE,
    BUSINESS
}
