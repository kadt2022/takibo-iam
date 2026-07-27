# RBAC-07 — Ownership et gouvernance des attributions

**Statut** : à faire
**Doctrine** : [ADR 0003 §9, §10](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : RBAC-06
**Risque** : élevé

## Contexte

`R_ORG_OWNER` représente la propriété d'une organisation. Aujourd'hui, il transite par les
mêmes chemins que n'importe quel rôle : endpoint générique d'attribution, groupe
`G_ORG_ADMINS`. Rien n'empêche structurellement qu'un propriétaire soit créé par erreur.

## Loi du récit

La propriété ne s'attribue pas, elle se transfère. `R_ORG_OWNER` n'est obtenu que par
création initiale de l'organisation ou par `transferOwnership` — jamais par un endpoint
générique, jamais par héritage de groupe, jamais par une invitation.

Une organisation possède **exactement un** propriétaire actif.

## Périmètre

- Créer une commande dédiée `TransferOrganizationOwnershipCommand`, atomique :

  ```text
  vérifier le propriétaire actuel
  vérifier l'éligibilité du nouveau propriétaire
  retirer R_ORG_OWNER à l'ancien
  accorder R_ORG_OWNER au nouveau
  garantir qu'il reste exactement un propriétaire
  auditer l'opération
  valider la transaction
  ```

  La détection de course concurrente (nombre de lignes affectées) provoque un rollback.

- Refuser `R_ORG_OWNER` sur l'endpoint générique d'attribution.
- Retirer `R_ORG_OWNER` de `G_ORG_ADMINS` : le groupe ne transmet plus que `R_ORG_ADMIN`.
- Empêcher un administrateur spécialisé d'attribuer un rôle plus puissant que le sien.
- Refuser la désactivation, la suppression ou le retrait d'appartenance du **dernier**
  propriétaire tant qu'un transfert n'a pas eu lieu.
- Implémenter `P_ORG_DEACTIVATE` et `P_ORG_DELETION_REQUEST`, l'exécution de la
  suppression relevant de `P_PLATFORM_ORGS_DELETE`.

## Hors périmètre

- L'exécution de la suppression côté plateforme et son workflow de rétention.
- L'intervention plateforme (RBAC-08).
- L'interface d'attribution (RBAC-09).

## Note de vérification

Le retrait de `R_ORG_OWNER` de `G_ORG_ADMINS` est **sans effet de bord sur le fondateur** :
le provisioning fondateur assigne `R_ORG_OWNER` directement, en plus du groupe.

## Critères d'acceptation

### AC-01 — Un seul propriétaire

À tout instant, une organisation possède exactement un `R_ORG_OWNER` actif.

### AC-02 — Endpoint générique fermé

`POST /users/{id}/roles` avec `R_ORG_OWNER` échoue, quel que soit l'appelant.

### AC-03 — Aucun héritage de groupe

Appartenir à `G_ORG_ADMINS` ne confère jamais `R_ORG_OWNER`.

### AC-04 — Transfert transactionnel

Le transfert est atomique : l'ancien propriétaire perd le rôle exactement quand le
nouveau le reçoit. Une course concurrente provoque un rollback, jamais deux propriétaires
ni zéro.

### AC-05 — Dernier propriétaire protégé

La désactivation, la suppression ou le retrait d'appartenance du dernier propriétaire est
refusée tant qu'aucun transfert n'a eu lieu.

### AC-06 — Pas d'élévation par délégation

Un administrateur spécialisé ne peut pas attribuer un rôle dont il ne détient pas
lui-même les permissions.

### AC-07 — Tentatives auditées

Toute tentative invalide d'attribution ou de transfert d'ownership est tracée.

## Vérifications

- `:takibo-identity-core:test`
- `:takibo-management-service:test`
- Test de concurrence sur le transfert.
- BVT : parcours complet de transfert et refus du dernier propriétaire.

## Branche

`feat/rbac-organization-ownership`

## Commit proposé

`feat(rbac): isolate organization ownership from generic role assignment`

## Clôture documentaire

Lorsque ce récit est terminé et validé :

1. passer son statut à `TERMINÉ` ;
2. déplacer ce fichier de `docs/backlog` vers `docs/terminer` avec `git mv` ;
3. mettre à jour les index et liens concernés ;
4. inclure ce déplacement dans la PR de clôture du récit.
