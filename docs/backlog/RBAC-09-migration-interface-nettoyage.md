# RBAC-09 — Migrations, interface et suppression de l'ancien modèle

**Statut** : à faire
**Doctrine** : [ADR 0003 §9, §12](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : RBAC-07, RBAC-08
**Risque** : élevé

## Contexte

Les moteurs et l'enforcement sont stabilisés. Restent les données existantes, l'interface
et l'ancien modèle encore présent en compatibilité.

## Loi du récit

On ne convertit jamais automatiquement un ancien rôle ambigu en rôle plus puissant. Une
assignation sans cible équivalente est supprimée et signalée, jamais promue.

## Périmètre

Ce récit se livre en **trois PR distinctes**.

### PR 1 — Migration du catalogue et des données

Ce qui est persisté, et ce qui ne l'est pas :

| Élément | Persistance | Conséquence |
| --- | --- | --- |
| Permissions techniques | **aucune** — pas d'`INSERT INTO permissions` | renommage Java seul, sans migration |
| Codes de rôles | `role_assignments.role_code` + deux migrations de classification | tout retrait exige une migration |
| `user_roles` | référence `role_id`, pas `role_code` | ne pas cibler cette table par code |

> Il n'existe **pas** de table `technical_roles` : les rôles techniques vivent dans l'enum
> Java. Toute migration ciblant une telle table est erronée.

Table de migration :

| Ancien | Nouveau | Action |
| --- | --- | --- |
| `TechnicalScope.SYSTEM` | `PLATFORM` | renommage d'enum (non persisté) |
| `TechnicalScope.USER` | — | suppression |
| `SYSTEM_ADMIN` / `SYSTEM_AUDITOR` (constantes) | `PLATFORM_ADMIN` / `PLATFORM_AUDITOR` | renommage ; codes `R_TAKIBO_*` inchangés |
| `R_ORG_VIEWER` | **supprimé** | aucune cible équivalente |
| `R_SPACE_VIEWER` | **supprimé** | aucune cible équivalente |
| `R_SELF` | **supprimé** | remplacé par la politique de libre-service |
| — | `R_SPACE_AUDITOR` | création |

**Sort des assignations Viewer.** Les rôles `READER` n'étant pas retenus, il n'existe
aucune cible équivalente. Mapper `R_ORG_VIEWER` vers `R_ORG_AUDITOR` accorderait la
lecture **et l'export** d'audit : élévation silencieuse, interdite.

Décision : les assignations `R_ORG_VIEWER` et `R_SPACE_VIEWER` sont **supprimées sans
remplacement**, et la migration produit un **rapport des assignations supprimées par
organisation**, à charge des administrateurs de réattribuer.

Les migrations doivent être idempotentes, rejouables et compatibles avec les
environnements existants.

### PR 2 — Interface fondée sur les permissions

L'interface masque ou désactive une action selon les permissions du token, le plan actuel
et la frontière actuelle. Elle affiche le **rôle réel** et le contexte courant.

L'interface ne constitue jamais l'enforcement de sécurité.

### PR 3 — Suppression de l'ancien modèle

Supprimer : anciens codes de rôles, compatibilité transitoire, branches historiques du
`PolicyEvaluator`, mappings temporaires, tests de l'ancien modèle.

## Libre-service — préalable à la suppression de `R_SELF`

`R_SELF` ne peut être retiré qu'**après** avoir remplacé tous ses usages par une politique
explicite de relation à soi (`SelfRelationshipPolicy`) :

```text
subject.accountId == target.accountId
subject.userId    == target.userId
```

Couvrant : lire son profil, changer son mot de passe, gérer son MFA, consulter et révoquer
ses sessions, consulter ses propres événements.

Ces opérations ne sont jamais des permissions attribuables.

## Hors périmètre

- Toute nouvelle capacité fonctionnelle.
- La réattribution des personnes touchées par la suppression des Viewers, qui relève des
  administrateurs d'organisation.

## Critères d'acceptation

### AC-01 — Migrations rejouables

Chaque migration est idempotente et peut être rejouée sans erreur ni doublon.

### AC-02 — Aucune promotion silencieuse

Aucune assignation d'un rôle supprimé n'est convertie en un rôle accordant davantage de
permissions.

### AC-03 — Rapport de suppression

La migration produit la liste des assignations supprimées, par organisation.

### AC-04 — `R_SELF` retiré partout

`R_SELF` n'apparaît plus dans les rôles techniques, les migrations, les groupes, les
tokens, les tests ni l'interface, et le libre-service fonctionne sans lui.

### AC-05 — Interface non normative

Aucune décision de sécurité ne dépend de l'interface : masquer un bouton ne protège pas
l'endpoint correspondant.

### AC-06 — Ancien modèle absent

Aucun code de rôle hérité ni branche de compatibilité ne subsiste dans le
`PolicyEvaluator`.

## Vérifications

- `:takibo-iam-boot:test --tests "*Migration*"`
- Suite complète de tous les modules.
- BVT complet.
- Rejeu des migrations sur une base existante non vide.

## Branches

- `chore/rbac-data-migration`
- `feat/rbac-permission-driven-ui`
- `chore/rbac-remove-legacy-model`

## Commits proposés

- `chore(rbac): migrate role assignments to the v2 catalog`
- `feat(rbac): drive the console from effective permissions`
- `chore(rbac): remove the legacy role model`

## Clôture documentaire

Lorsque ce récit est terminé et validé :

1. passer son statut à `TERMINÉ` ;
2. déplacer ce fichier de `docs/backlog` vers `docs/terminer` avec `git mv` ;
3. mettre à jour les index et liens concernés ;
4. inclure ce déplacement dans la PR de clôture du récit.
