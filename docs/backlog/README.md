# Backlog

Ce dossier contient **tous les récits ouverts**, quel que soit leur lot. Un récit terminé
part dans [`../terminer`](../terminer), sans changer de nom. Il n'existe pas d'autre
emplacement : le préfixe du nom de fichier distingue les lots, l'arborescence ne les sépare
pas.

## Lots

| Lot | Préfixe | Index |
| --- | --- | --- |
| RBAC v2 | `RBAC-` | tableau [État](#état) ci-dessous |
| Nouveaux grant types OAuth 2.0 | `TAS-GRANTS-` | [README-TAS-GRANTS.md](README-TAS-GRANTS.md) |
| Durcissement TMS et sécurité | `SEC-TMS-`, `TMS-` | [liste](#durcissement-tms-et-sécurité) ci-dessous |
| Identité et inscription d'organisation | `IAM-`, `RECIT-ORG-` | [liste](#identité-et-inscription-dorganisation) ci-dessous |
| Installation et bootstrap TAKIBO | `TAKIBO-INSTALL-` | [liste](#installation-et-bootstrap-takibo) ci-dessous |

Les documents de travail — analyses, brouillons de corps de PR — ne sont pas versionnés :
`.gitignore` les exclut nommément, document par document. Seuls les récits, les contrats et
les plans le sont, parce qu'ils sont relus.

---

## RBAC v2

Découpage exécutable de la [doctrine RBAC v2 (ADR 0003)](../adr/0003-doctrine-rbac-v2.md).

Le plan d'ensemble, les principes de séquencement et les pièges à éviter restent dans
[TAKIBO_Plan_Ordre_Implementation_RBAC_v2.md](TAKIBO_Plan_Ordre_Implementation_RBAC_v2.md).

### Nomenclature

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

### État

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

### Règle de livraison

> Une PR introduit une capacité cohérente, testable et réversible. Aucune PR ne mélange
> catalogue, migration massive, tokens, contrôleurs et interface.

### Ordre

```text
RBAC-00  (sécurité, préalable — indépendant de la doctrine)
   ↓
RBAC-01 → RBAC-02 → RBAC-03 → RBAC-04 → RBAC-05 → RBAC-06
                                                      ├→ RBAC-07
                                                      └→ RBAC-08
                                                            ↓
                                                        RBAC-09
```

---

## Durcissement TMS et sécurité

Récits sans lot numéroté, regroupés ici depuis `docs/recits` lors de la consolidation du
backlog. Ils ne partagent pas de séquencement : chacun se prend indépendamment.

| Récit | Sujet |
| --- | --- |
| [SEC-TMS-01](SEC-TMS-01-fermer-elevation-privileges-signup.md) | Fermer l'élévation de privilèges au signup |
| [SEC-TMS-02](SEC-TMS-02-passer-routes-tms-default-deny.md) | Passer les routes TMS en default-deny |
| [SEC-TMS-03](SEC-TMS-03-restreindre-actuator.md) | Restreindre Actuator |
| [SEC-TMS-04](SEC-TMS-04-traduire-les-erreurs-de-configuration-client.md) | Traduire les erreurs de configuration client au lieu de renvoyer 500 |
| [TMS-OAUTH-01](TMS-OAUTH-01-durcir-configuration-clients-oauth2.md) | Durcir la configuration des clients OAuth2 |
| [TMS-VAL-01](TMS-VAL-01-validation-rest-contracts.md) | Validation des contrats REST |
| [TMS-CLIENT-READ-01](TAKIBO_Recit_TMS_CLIENT_READ_01.md) | Lire les clients OAuth2 sans réexposer leurs secrets |

Outillage de vérification associé :
[TAKIBO_Plan_Collection_Postman_Canonique.md](TAKIBO_Plan_Collection_Postman_Canonique.md).

---

## Identité et inscription d'organisation

Comment une organisation entre dans TAKIBO, et comment son premier compte s'authentifie.
`IAM-31` est le socle : l'organisation identifie le compte, le space situe l'action.

| Récit | Titre | Statut |
| --- | --- | --- |
| [IAM-31](../terminer/IAM-31-authentification-humaine-organisationnelle.md) | Authentification humaine de portée organisationnelle | **TERMINÉ** (PR #31, 2026-07-12) |
| [ORG-REG-01](RECIT-ORG-REG-01-demande-inscription-FINAL-v3.md) | Créer une demande publique d'inscription d'organisation | à faire |
| [ORG-REG-02](RECIT-ORG-REG-02-verification-provisioning-FINAL-sans-userId.md) | Vérifier le courriel et provisionner l'organisation fondatrice | à faire |
| [ORG-SPACE-01](RECIT-ORG-SPACE-01-onboarding-premier-space-FINAL-userId.md) | Onboarding sans Space, puis création volontaire du premier Space | à faire |

Contrat de référence des trois récits d'inscription :
[TAKIBO-REST-Organization-Registration-Contract.md](TAKIBO-REST-Organization-Registration-Contract.md).

---

## Installation et bootstrap TAKIBO

Le cœur TAKIBO n'importe aucune API de plateforme de déploiement — pas d'OpenShift, pas de
Kubernetes, pas de Helm, pas de Docker. Il exprime un contrat générique de configuration et
refuse de démarrer si ce contrat n'est pas satisfait. Ces récits couvrent ce qui aide une
organisation à le satisfaire, sans jamais faire entrer une plateforme dans le cœur.

| Récit | Titre | Statut |
| --- | --- | --- |
| [TAKIBO-INSTALL-KEYS-01](TAKIBO-INSTALL-KEYS-01-bootstrap-cryptographique-portable.md) | Bootstrap cryptographique portable | à faire |
| [TAS-KEYS-BOOTSTRAP-01](../terminer/TAS-KEYS-BOOTSTRAP-01-amorcage-premiere-cle-signature.md) | Amorçage automatique de la première clé de signature | **TERMINÉ** (PR #58, 2026-09-02) |
