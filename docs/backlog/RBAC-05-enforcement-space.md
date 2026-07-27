# RBAC-05 — Enforcement des routes SPACE

**Statut** : à faire
**Doctrine** : [ADR 0003 §1, §6](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : RBAC-04
**Risque** : élevé

## Contexte

Le token porte désormais des permissions situées. Les routes, elles, décident encore à
partir d'un nom de rôle. Le plan `SPACE` est la frontière la plus précise et la plus
facile à prouver : c'est par lui qu'il faut commencer.

## Loi du récit

Une décision d'autorisation lit toujours le couple **permission + frontière**. Aucune
route n'accorde un accès sur la seule présence d'un nom de rôle, ni sur la seule présence
d'un code de permission.

```text
permission correcte + mauvais space_id = 403
```

## Périmètre

Migrer les routes de plan SPACE, dans cet ordre :

```text
1. Users
2. Clients OAuth2/OIDC
3. Rôles et groupes
4. Configuration du Space
5. Politiques
6. Audit
```

Faire évoluer le `PolicyEvaluator` vers une demande structurée :

```java
AuthorizationRequest.builder()
    .requiredPermission(P_SPACE_USERS_MANAGE)
    .targetOrganizationId(orgId)
    .targetSpaceId(spaceId)
    .build();
```

Conditions à réunir pour autoriser :

```text
permission requise présente dans le token
token.org_id   == route.orgId
token.space_id == route.spaceId
organisation active
space actif
acteur authentifié et situé
```

## Hors périmètre

- Les routes de plan ORGANIZATION (RBAC-06).
- L'ownership (RBAC-07).
- L'intervention plateforme (RBAC-08).
- L'interface (RBAC-09).

## Compatibilité transitoire

Pendant la migration, la conversion « ancien rôle connu → permissions v2 » est autorisée,
mais **centralisée dans le moteur**. Le motif suivant est interdit dans les contrôleurs :

```java
hasRole("R_SPACE_ADMIN") || hasPermission("P_SPACE_USERS_MANAGE")
```

Le double système doit être temporaire et vivre en un seul endroit.

## Critères d'acceptation

### AC-01 — Permission correcte, bonne frontière

Autorisé.

### AC-02 — Permission correcte, mauvais Space

Refusé (403), y compris si le Space appartient à la même organisation.

### AC-03 — Bonne frontière, permission absente

Refusé.

### AC-04 — Org Admin par projection

Un `R_ORG_ADMIN` disposant d'un token situé sur un Space de son organisation est
autorisé, sans détenir `R_SPACE_ADMIN`.

### AC-05 — Space Admin d'un autre Space

Refusé.

### AC-06 — Token sans `space_id`

Refusé sur toute route de plan SPACE.

### AC-07 — Space suspendu

Refusé, quelles que soient les permissions portées.

### AC-08 — Aucune décision par nom de rôle

Aucun contrôleur de plan SPACE ne teste un nom de rôle.

## Vérifications

- `:takibo-security-management:test`
- `:takibo-identity-core:test`
- `:takibo-management-service:test`
- BVT : scénarios autorisés et refusés pour chaque ressource migrée.

## Branche

`feat/rbac-space-enforcement`

## Commit proposé

`feat(rbac): enforce space routes with situated permissions`

## Clôture documentaire

Lorsque ce récit est terminé et validé :

1. passer son statut à `TERMINÉ` ;
2. déplacer ce fichier de `docs/backlog` vers `docs/terminer` avec `git mv` ;
3. mettre à jour les index et liens concernés ;
4. inclure ce déplacement dans la PR de clôture du récit.
