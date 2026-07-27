# RBAC-02 — Matrice rôles-permissions

**Statut** : TERMINÉ
**Doctrine** : [ADR 0003 §6](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : RBAC-01
**Risque** : faible

## Contexte

RBAC-01 a introduit les 11 rôles et les 45 permissions, mais rien ne dit encore quel rôle
accorde quelle permission. Aujourd'hui, cette information est dispersée : une partie dans
l'enum `TechnicalRole`, une partie dans les branches du `PolicyEvaluator`.

## Loi du récit

La matrice rôle → permissions vit dans **un composant central unique**. Elle n'est jamais
dispersée dans les contrôleurs, ni déduite d'un nom de rôle.

## Périmètre

- Créer le registre central (`RolePermissionCatalog` ou équivalent) portant la table
  normative de l'ADR 0003 §6.
- Renseigner les 11 rôles avec leur jeu exact de permissions.
- Garantir par construction que `permissions(R_ORG_OWNER)` = `permissions(R_ORG_ADMIN)`
  ∪ {`P_ORG_OWNERSHIP_TRANSFER`, `P_ORG_DEACTIVATE`, `P_ORG_DELETION_REQUEST`}.
- Couvrir la matrice entière par des tests.

## Hors périmètre

- Le calcul contextuel des permissions effectives (RBAC-03).
- La projection dans les tokens (RBAC-04).
- Toute décision d'autorisation (RBAC-05, RBAC-06).

## Table normative

| Rôle | Permissions | Total |
| --- | --- | ---: |
| `R_TAKIBO_PLATFORM_ADMIN` | toutes les `P_PLATFORM_*` | 8 |
| `R_TAKIBO_PLATFORM_AUDITOR` | `P_PLATFORM_ORGS_READ`, `P_PLATFORM_POLICY_READ`, `P_PLATFORM_AUDIT_READ` | 3 |
| `R_ORG_OWNER` | `R_ORG_ADMIN` **+** `P_ORG_OWNERSHIP_TRANSFER`, `P_ORG_DEACTIVATE`, `P_ORG_DELETION_REQUEST` | 22 |
| `R_ORG_ADMIN` | `P_ORG_READ/UPDATE`, `P_ORG_SPACES_READ/CREATE/MANAGE/DELETE`, `P_ORG_USERS_READ/MANAGE/LIFECYCLE`, `P_ORG_CLIENTS_READ/MANAGE/ROTATE_SECRET/LIFECYCLE`, `P_ORG_RBAC_READ/ASSIGN`, `P_ORG_POLICY_READ/UPDATE`, `P_ORG_AUDIT_READ/EXPORT` | 19 |
| `R_ORG_USER_ADMIN` | `P_ORG_READ`, `P_ORG_USERS_READ/MANAGE/LIFECYCLE`, `P_ORG_RBAC_READ` | 5 |
| `R_ORG_CLIENT_ADMIN` | `P_ORG_READ`, `P_ORG_CLIENTS_READ/MANAGE/ROTATE_SECRET/LIFECYCLE` | 5 |
| `R_ORG_AUDITOR` | `P_ORG_READ`, `P_ORG_POLICY_READ`, `P_ORG_AUDIT_READ/EXPORT` | 4 |
| `R_SPACE_ADMIN` | `P_SPACE_READ/UPDATE`, `P_SPACE_USERS_READ/MANAGE/LIFECYCLE`, `P_SPACE_CLIENTS_READ/MANAGE/ROTATE_SECRET/LIFECYCLE`, `P_SPACE_RBAC_READ/ASSIGN`, `P_SPACE_POLICY_READ/UPDATE`, `P_SPACE_AUDIT_READ` | 14 |
| `R_SPACE_USER_ADMIN` | `P_SPACE_READ`, `P_SPACE_USERS_READ/MANAGE/LIFECYCLE` | 4 |
| `R_SPACE_CLIENT_ADMIN` | `P_SPACE_READ`, `P_SPACE_CLIENTS_READ/MANAGE/ROTATE_SECRET/LIFECYCLE` | 5 |
| `R_SPACE_AUDITOR` | `P_SPACE_READ`, `P_SPACE_AUDIT_READ/EXPORT` | 3 |

## Critères d'acceptation

### AC-01 — Matrice déterministe

Chaque rôle retourne un jeu de permissions stable et identique à la table ci-dessus.

### AC-02 — Étanchéité des plans

Aucun rôle de plan `SPACE` ne détient une permission `P_ORG_*` ou `P_PLATFORM_*`. Aucun
rôle `ORGANIZATION` ne détient une permission `P_SPACE_*` ou `P_PLATFORM_*`.

### AC-03 — Owner strictement supérieur à Admin

`permissions(R_ORG_ADMIN)` est strictement inclus dans `permissions(R_ORG_OWNER)`, et la
différence est exactement les trois permissions d'ownership.

### AC-04 — Exclusions explicites

- `R_TAKIBO_PLATFORM_AUDITOR` ne détient pas `P_PLATFORM_AUDIT_EXPORT`.
- `R_SPACE_ADMIN` ne détient ni `P_ORG_SPACES_CREATE` ni `P_ORG_SPACES_DELETE`.
- `R_SPACE_ADMIN` ne détient pas `P_SPACE_AUDIT_EXPORT`.
- `R_ORG_USER_ADMIN` ne détient pas `P_ORG_RBAC_ASSIGN`.

### AC-05 — Refus par défaut

Une permission absente de la matrice d'un rôle est refusée ; il n'existe aucun chemin
implicite d'octroi.

## Vérifications

- `:takibo-identity-core:test`
- Test exhaustif parcourant les 11 rôles et vérifiant l'égalité stricte des jeux.

## Branche

`refactor/rbac-role-permission-matrix`

## Commit proposé

`refactor(rbac): centralize the role-permission matrix`
