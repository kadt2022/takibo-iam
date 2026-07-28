# Backlog RBAC v2

Découpage exécutable de la [doctrine RBAC v2 (ADR 0003)](../adr/0003-doctrine-rbac-v2.md).

Le plan d'ensemble, les principes de séquencement et les pièges à éviter restent dans
[TAKIBO_Plan_Ordre_Implementation_RBAC_v2.md](TAKIBO_Plan_Ordre_Implementation_RBAC_v2.md).
Ce dossier en est la décomposition en récits livrables.

## Nomenclature

```text
RBAC-NN-<slug-kebab>.md
```

Chaque récit porte un bloc d'en-tête :

```text
**Statut**      : À FAIRE | EN COURS | TERMINÉ
**Doctrine**    : section de l'ADR 0003 qui le fonde
**Dépend de**   : récits prérequis
**Risque**      : faible | moyen | élevé
```

À la finition validée d'un récit, passer **Statut** à `TERMINÉ`, déplacer son fichier
avec `git mv` de `docs/backlog` vers `docs/terminer`, puis mettre à jour la ligne
correspondante du tableau ci-dessous. Une PR ouverte ou des tests locaux incomplets ne
suffisent pas à clôturer un récit.

## État

| Récit | Titre | Statut | Risque | Dépend de |
| --- | --- | --- | --- | --- |
| [RBAC-00](../terminer/RBAC-00-fermer-escalade-par-nommage-de-role.md) | Fermer l'escalade par nommage de rôle | **TERMINÉ** (PR #44, 2026-07-26) | élevé | — |
| [RBAC-01](../terminer/RBAC-01-catalogue-canonique-roles-permissions.md) | Catalogue canonique des rôles et permissions situés | **TERMINÉ** (PR #45, 2026-07-26) | faible | RBAC-00 |
| [RBAC-02](../terminer/RBAC-02-matrice-roles-permissions.md) | Matrice rôles-permissions | **TERMINÉ** (PR #46, 2026-07-26) | faible | RBAC-01 |
| [RBAC-03](../terminer/RBAC-03-permissions-effectives.md) | Calcul des permissions effectives | **TERMINÉ** (PR #47, 2026-07-26) | moyen | RBAC-02 |
| [RBAC-04](../terminer/RBAC-04-tokens-situes.md) | Projection des permissions dans les tokens | **TERMINÉ** (PR #50, 2026-07-27) | élevé | RBAC-03 |
| [RBAC-05](RBAC-05-enforcement-space.md) | Enforcement des routes SPACE | à faire | élevé | RBAC-04 |
| [RBAC-06](RBAC-06-enforcement-organization.md) | Enforcement des routes ORGANIZATION | à faire | élevé | RBAC-05 |
| [RBAC-07](RBAC-07-ownership-et-gouvernance.md) | Ownership et gouvernance des attributions | à faire | élevé | RBAC-06 |
| [RBAC-08](RBAC-08-audit-situe-et-intervention-plateforme.md) | Audit situé et intervention plateforme | à faire | élevé | RBAC-06 |
| [RBAC-09](RBAC-09-migration-interface-nettoyage.md) | Migrations, interface et suppression de l'ancien modèle | à faire | élevé | RBAC-07, RBAC-08 |

## Règle de livraison

> Une PR introduit une capacité cohérente, testable et réversible. Aucune PR ne mélange
> catalogue, migration massive, tokens, contrôleurs et interface.

## Ordre

```text
RBAC-00  (sécurité, préalable — indépendant de la doctrine)
   ↓
RBAC-01 → RBAC-02 → RBAC-03 → RBAC-04 → RBAC-05 → RBAC-06
                                                      ├→ RBAC-07
                                                      └→ RBAC-08
                                                            ↓
                                                        RBAC-09
```
