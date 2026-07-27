# RBAC-01 — Catalogue canonique des rôles et permissions situés

**Statut** : TERMINÉ
**Doctrine** : [ADR 0003 §1–§5](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : RBAC-00
**Risque** : faible

## Contexte

Le catalogue actuel a été construit par accumulation : quatre scopes (`SYSTEM`,
`ORGANIZATION`, `SPACE`, `USER`), 13 rôles, 13 permissions dont le champ `scope` porte
deux sémantiques contradictoires — « autorité plateforme » pour `CREATE_ORG`, « visible
par un tenant » pour `MANAGE_USERS`, alors que cette dernière s'applique aussi au niveau
Space.

Sans vocabulaire canonique, le token ne sait pas quoi transporter, le `PolicyEvaluator`
ne sait pas quoi vérifier, les migrations ne savent pas quoi créer.

## Loi du récit

Trois plans d'autorité, et trois seulement. Chaque rôle appartient à un seul plan. Chaque
permission nomme explicitement son plan, sa ressource et son action.

Ce récit introduit le vocabulaire **sans changer aucun comportement**.

## Périmètre

- Introduire `AuthorityPlan { PLATFORM, ORGANIZATION, SPACE }` (remplace `TechnicalScope`,
  dont `SYSTEM` devient `PLATFORM` et `USER` disparaît).
- Déclarer les **11 rôles techniques** de l'ADR 0003 §2, chacun rattaché à un plan.
- Déclarer les **45 permissions** de l'ADR 0003 §3–§5, chacune portant au minimum :
  `code`, `plan`, `ressource`, `action`, `description`.
- Créer `R_SPACE_AUDITOR`, absent du catalogue actuel.
- Doter chaque rôle de ses caractéristiques : `assignable`, `inheritable`,
  `administrator`. `R_ORG_OWNER` est déclaré `assignable = false`, `inheritable = false`.
- Marquer `R_SELF`, `R_ORG_VIEWER` et `R_SPACE_VIEWER` comme **dépréciés**, sans les
  supprimer.

## Hors périmètre

- La matrice rôle → permissions (RBAC-02).
- Le calcul des permissions effectives (RBAC-03).
- Toute modification de contrôleur, de token, de migration ou d'interface.
- La suppression effective de `R_SELF` et des Viewers (RBAC-09).

Aucune route existante ne doit être cassée par ce récit.

## Critères d'acceptation

### AC-01 — Les trois plans existent

`AuthorityPlan` ne contient que `PLATFORM`, `ORGANIZATION`, `SPACE`. Aucun code du
catalogue ne référence un plan `USER`.

### AC-02 — Onze rôles, chacun sur un plan

Le catalogue expose exactement 11 rôles : 2 `PLATFORM`, 5 `ORGANIZATION`, 4 `SPACE`.
`R_SPACE_AUDITOR` en fait partie.

### AC-03 — Quarante-cinq permissions, chacune sur un plan

Le catalogue expose 8 permissions `P_PLATFORM_*`, 22 `P_ORG_*` et 15 `P_SPACE_*`. Aucune
permission n'a de plan ambigu ou nul.

### AC-04 — Unicité des codes

Aucun code de rôle ni de permission n'est dupliqué. Un test le garantit sur l'ensemble du
catalogue.

### AC-05 — Ownership non délégable au niveau du modèle

`R_ORG_OWNER` déclare `assignable = false` et `inheritable = false`.

### AC-06 — Dépréciations signalées, non appliquées

`R_SELF`, `R_ORG_VIEWER` et `R_SPACE_VIEWER` sont marqués dépréciés et restent
fonctionnels. Aucune assignation existante n'est modifiée.

### AC-07 — Aucune régression

La suite complète passe sans modification de comportement observable.

## Vérifications

- `:takibo-identity-core:test`
- Test dédié d'unicité et de complétude du catalogue.
- Compilation de tous les modules.

## Branche

`refactor/rbac-canonical-catalog`

## Commit proposé

`refactor(rbac): introduce canonical three-plane role and permission catalog`
