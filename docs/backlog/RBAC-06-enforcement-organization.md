# RBAC-06 — Enforcement des routes ORGANIZATION

**Statut** : à faire
**Doctrine** : [ADR 0003 §6, §8](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : RBAC-05
**Risque** : élevé

## Contexte

Le plan SPACE est stabilisé et prouvé. Les opérations organisationnelles peuvent alors
être migrées sur le même modèle, avec une exigence supplémentaire : un rôle de plan
SPACE ne doit jamais gagner d'autorité organisationnelle.

## Loi du récit

Pour toute route `/api/v1/orgs/{orgId}/...`, la frontière `token.org_id == orgId` est
exigée — sauf intervention plateforme explicitement située (RBAC-08).

Un `R_SPACE_ADMIN` n'obtient jamais une permission `P_ORG_*`, même si son Space appartient
à l'organisation ciblée.

## Périmètre

Migrer les routes de plan ORGANIZATION, dans cet ordre :

```text
1. Lecture et modification de l'organisation
2. Liste et cycle de vie des Spaces
3. Utilisateurs de l'organisation
4. Clients de l'organisation
5. Catalogue et attributions RBAC
6. Politiques
7. Audit
```

## Hors périmètre

- Les opérations d'ownership, qui ne passent jamais par les endpoints génériques
  (RBAC-07).
- L'intervention plateforme (RBAC-08).
- Les migrations de données (RBAC-09).

## Critères d'acceptation

### AC-01 — Frontière organisationnelle

Un Org Admin n'accède qu'à son organisation. `token.org_id != orgId` est refusé.

### AC-02 — Spécialisation respectée

- `R_ORG_USER_ADMIN` ne gère pas les clients.
- `R_ORG_CLIENT_ADMIN` ne gère pas les utilisateurs.
- `R_ORG_USER_ADMIN` ne peut pas attribuer de rôles (`P_ORG_RBAC_ASSIGN` absent).

### AC-03 — Auditeur en lecture stricte

`R_ORG_AUDITOR` ne modifie aucune ressource : toute écriture est refusée.

### AC-04 — Space Admin cloisonné

Un `R_SPACE_ADMIN` ne peut ni créer ni supprimer un Space, ni lire ou modifier
l'organisation.

### AC-05 — Ownership absent de l'Org Admin

`R_ORG_ADMIN` ne détient aucune des trois permissions d'ownership ; les routes
correspondantes lui sont refusées.

### AC-06 — Aucune décision par nom de rôle

Aucun contrôleur de plan ORGANIZATION ne teste un nom de rôle.

## Vérifications

- `:takibo-security-management:test`
- `:takibo-identity-core:test`
- `:takibo-management-service:test`
- BVT : matrice autorisé/refusé pour les cinq rôles ORGANIZATION.

## Branche

`feat/rbac-organization-enforcement`

## Commit proposé

`feat(rbac): enforce organization routes with situated permissions`

## Clôture documentaire

Lorsque ce récit est terminé et validé :

1. passer son statut à `TERMINÉ` ;
2. déplacer ce fichier de `docs/backlog` vers `docs/terminer` avec `git mv` ;
3. mettre à jour les index et liens concernés ;
4. inclure ce déplacement dans la PR de clôture du récit.
