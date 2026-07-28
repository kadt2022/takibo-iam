# RBAC-04 — Projection des permissions dans les tokens

**Statut** : EN COURS
**Doctrine** : [ADR 0003 §1, §7](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : RBAC-03
**Risque** : élevé

## Contexte

Le moteur de RBAC-03 calcule la vérité. Tant qu'elle n'est pas portée par le token,
aucune décision d'autorisation ne peut s'appuyer dessus.

Doctrine de séparation en vigueur : **TIS-CORE calcule, TAS signe.** Le serveur
d'autorisation ne doit jamais inventer de permissions.

## Loi du récit

Le token porte l'autorité **située** : des permissions cohérentes avec son plan, plus les
claims de frontière qui les bornent. Un token ne contient jamais une permission
incompatible avec son propre plan.

## Périmètre

- Faire porter au token les permissions calculées par `EffectivePermissionResolver`.
- Fixer les claims minimaux et leur cohérence par plan.
- Recalculer les permissions à chaque émission et à chaque changement de contexte.

## Claims

```json
{
  "roles": ["R_ORG_ADMIN"],
  "permissions": ["P_SPACE_USERS_READ", "P_SPACE_USERS_MANAGE"],
  "takibo_scope_level": "SPACE",
  "org_id": "...",
  "space_id": "...",
  "subject_type": "HUMAN",
  "takibo_tenant_source": "human_space_selection"
}
```

Règles par plan :

| Plan du token | `org_id` | `space_id` | Permissions portées |
| --- | --- | --- | --- |
| `PLATFORM` | absent | absent | `P_PLATFORM_*` uniquement |
| `ORGANIZATION` | requis | absent | `P_ORG_*` uniquement |
| `SPACE` | requis | requis | `P_SPACE_*` uniquement |

Le token conserve les **rôles réels** du sujet, jamais des rôles fabriqués.

## Hors périmètre

- L'enforcement des routes (RBAC-05, RBAC-06).
- Le flux d'intervention plateforme et `PLATFORM_IMPERSONATION` (RBAC-08).
- La révocation et la rotation des sessions.

## Critères d'acceptation

### AC-01 — Cohérence plan / permissions

Aucun token ne porte de permission d'un autre plan que le sien.

### AC-02 — Frontière complète

Un token `SPACE` porte toujours `org_id` **et** `space_id`. Un token `PLATFORM` ne porte
aucun `org_id` ni `space_id` fabriqué.

### AC-03 — Pas d'accès transversal implicite

Un token `ORGANIZATION` ne donne accès à aucun Space arbitraire sans passage par un token
situé sur ce Space.

### AC-04 — Rôles réels préservés

Un Org Admin travaillant dans un Space voit `roles = [R_ORG_ADMIN]` et des permissions
`P_SPACE_*`. Le claim `roles` ne contient jamais `R_SPACE_ADMIN` fabriqué.

### AC-05 — Recalcul au changement de contexte

Changer de Space produit un nouveau token situé. Il n'existe aucun chemin où seul
`space_id` change en conservant les permissions de l'ancien Space.

### AC-06 — TAS ne fabrique rien

Le serveur d'autorisation signe un sujet déjà résolu. Aucun calcul de permission n'a lieu
côté TAS.

## Vérifications

- `:takibo-identity-core:test`
- `:takibo-authorization-server:test`
- `:takibo-iam-boot:test`
- Test d'intégration : login, ouverture de Space, vérification des claims émis.

## Validation locale

- `:takibo-identity-core:test` : 348 tests réussis.
- `:takibo-authorization-server:test` : 46 tests réussis.
- `:takibo-iam-boot:test` : 30 tests recensés, dont 29 réussis et 1 ignoré.
- Test d'intégration du pipeline TIS-CORE → TAS : un changement de Space recalcule les
  permissions, conserve les rôles réels et signe une nouvelle frontière complète.

La clôture documentaire reste subordonnée à la validation de livraison (CI/PR), conformément
à la règle du backlog.

## Branche

`feat/rbac-situated-token-claims`

## Commit proposé

`feat(rbac): project effective permissions into situated tokens`

## Clôture documentaire

Lorsque ce récit est terminé et validé :

1. passer son statut à `TERMINÉ` ;
2. déplacer ce fichier de `docs/backlog` vers `docs/terminer` avec `git mv` ;
3. mettre à jour les index et liens concernés ;
4. inclure ce déplacement dans la PR de clôture du récit.
