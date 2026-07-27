# RBAC-03 — Calcul des permissions effectives

**Statut** : TERMINÉ
**Doctrine** : [ADR 0003 §7](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : RBAC-02
**Risque** : moyen

## Contexte

La matrice dit ce qu'un rôle accorde *en principe*. Elle ne dit pas ce qu'un sujet peut
faire *ici et maintenant* : un Org Admin qui travaille dans un Space n'exerce pas ses
permissions de plan ORGANIZATION, mais leurs équivalents de plan SPACE, et uniquement
dans les Spaces de son organisation.

## Loi du récit

Un moteur central transforme les rôles réels d'un sujet en permissions effectives dans un
contexte donné. **Le rôle réel n'est jamais réécrit** : un Org Admin reste
`R_ORG_ADMIN`, il ne devient jamais `R_SPACE_ADMIN`.

## Périmètre

- Introduire `EffectivePermissionResolver`.

  Entrée : `roles`, `groups`, `authorityPlan` cible, `orgId`, `spaceId`, `subjectNature`,
  `actorSource`. Sortie : `Set<PermissionCode>`.

- Implémenter la **table de projection** ORGANIZATION → SPACE de l'ADR 0003 §7.
- Prendre en compte l'héritage par les groupes.
- Implémenter les protections de refus.

## Table de projection

| Permission ORGANIZATION | Permission SPACE dérivée |
| --- | --- |
| `P_ORG_SPACES_READ` | `P_SPACE_READ` |
| `P_ORG_SPACES_MANAGE` | `P_SPACE_UPDATE` |
| `P_ORG_USERS_READ` | `P_SPACE_USERS_READ` |
| `P_ORG_USERS_MANAGE` | `P_SPACE_USERS_MANAGE` |
| `P_ORG_USERS_LIFECYCLE` | `P_SPACE_USERS_LIFECYCLE` |
| `P_ORG_CLIENTS_READ` | `P_SPACE_CLIENTS_READ` |
| `P_ORG_CLIENTS_MANAGE` | `P_SPACE_CLIENTS_MANAGE` |
| `P_ORG_CLIENTS_ROTATE_SECRET` | `P_SPACE_CLIENTS_ROTATE_SECRET` |
| `P_ORG_CLIENTS_LIFECYCLE` | `P_SPACE_CLIENTS_LIFECYCLE` |
| `P_ORG_RBAC_READ` | `P_SPACE_RBAC_READ` |
| `P_ORG_RBAC_ASSIGN` | `P_SPACE_RBAC_ASSIGN` |
| `P_ORG_POLICY_READ` | `P_SPACE_POLICY_READ` |
| `P_ORG_POLICY_UPDATE` | `P_SPACE_POLICY_UPDATE` |
| `P_ORG_AUDIT_READ` | `P_SPACE_AUDIT_READ` |
| `P_ORG_AUDIT_EXPORT` | `P_SPACE_AUDIT_EXPORT` |

**Non projetables** : `P_ORG_SPACES_CREATE`, `P_ORG_SPACES_DELETE`, `P_ORG_READ`,
`P_ORG_UPDATE`, `P_ORG_OWNERSHIP_TRANSFER`, `P_ORG_DEACTIVATE`, `P_ORG_DELETION_REQUEST`.

La projection est **unidirectionnelle** : aucune permission `P_SPACE_*` ne produit jamais
une permission `P_ORG_*`.

## Hors périmètre

- La projection dans les tokens (RBAC-04).
- L'usage du moteur par le `PolicyEvaluator` (RBAC-05, RBAC-06).
- L'intervention plateforme (RBAC-08).

## Critères d'acceptation

### AC-01 — Org Admin dans un Space

Entrée : `roles = [R_ORG_ADMIN]`, `org_id = A`, `space_id = A1`, plan cible `SPACE`.

Sortie : les 15 permissions `P_SPACE_*` projetées, et `roles` inchangé à `[R_ORG_ADMIN]`.
Le moteur ne produit jamais `[R_ORG_ADMIN, R_SPACE_ADMIN]`.

### AC-02 — Space Admin ne remonte jamais

Entrée : `roles = [R_SPACE_ADMIN]`, plan cible `ORGANIZATION`. Sortie : aucune permission
`P_ORG_*`.

### AC-03 — Héritage par les groupes

Un sujet membre d'un groupe transmettant un rôle obtient les permissions effectives de ce
rôle, dans les mêmes conditions de frontière.

### AC-04 — Protections

Le moteur refuse :

```text
permission SPACE demandée sans space_id
permission ORGANIZATION demandée sans org_id
space_id appartenant à une autre organisation
rôle SPACE utilisé dans un autre Space que celui de son attribution
projection automatique PLATFORM vers un tenant
```

### AC-05 — Non-projetables respectées

`P_ORG_SPACES_CREATE` et `P_ORG_SPACES_DELETE` ne produisent aucune permission de plan
SPACE, quel que soit le contexte.

### AC-06 — Déterminisme

Deux appels avec la même entrée produisent exactement le même jeu de permissions.

## Vérifications

- `:takibo-identity-core:test`
- Tests de projection couvrant chaque ligne de la table et chaque non-projetable.
- Tests de refus couvrant les cinq protections.

## Validation de clôture

- Résolveur et protections implémentés dans la PR
  [#47](https://github.com/kadt2022/takibo-iam/pull/47).
- 14 tests ciblés et 343 tests `takibo-identity-core` réussis.
- BVT IAM, analyse SonarCloud, Quality Gate et CI globale au vert.
- Critères d'acceptation AC-01 à AC-06 validés.

## Branche

`feat/rbac-effective-permission-resolver`

## Commit proposé

`feat(rbac): resolve effective permissions from roles and boundary`
