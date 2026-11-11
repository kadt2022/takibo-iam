# SEC-HUNT-06 — Un Space garde toujours un administrateur effectif, même sous concurrence

**Statut** : EN REVUE — branche `fix/sec-hunt-06-last-admin-race`  
**Lot** : SECURITY FIX 01–05  
**Risque** : faible à moyen — deux administrateurs agissant au même instant  
**Origine** : SECURITY HUNT 02, découverte HUNT-06  
**Révision vérifiée** : `main@19998c4`

## 1. Récit

En tant que propriétaire d'une Organization, je veux qu'aucun Space ne perde son dernier
administrateur, même si deux administrateurs se retirent mutuellement ce pouvoir au même
instant, afin qu'un Space reste toujours administrable.

## 2. Défaut constaté

`UserRoleGovernanceService.removeRole` comptait les détenteurs directs de `R_SPACE_ADMIN`, puis
supprimait la ligne, sans verrou. `UserGroupGovernanceService.removeFromGroup` faisait de même
avec les membres de `G_SPACE_ADMINS`. Deux retraits croisés concurrents lisaient tous deux
« 2 détenteurs » et réussissaient tous deux : `200` / `200` dès le premier essai, aucun
administrateur restant.

Les deux comptes étaient par ailleurs **séparés** : le rôle direct et le groupe étaient protégés
chacun pour soi. On ne pouvait donc pas retirer le dernier membre du groupe d'un administrateur
qui restait administrateur par son rôle direct, et l'inverse non plus.

## 3. Invariant défini

> Un Space garde toujours au moins un **administrateur effectif** : une identité dont le User de
> ce Space est `ACTIVE` et qui détient `R_SPACE_ADMIN`, **directement ou par `G_SPACE_ADMINS`**.

Retirer l'une des deux voies n'est refusé (409 `LAST_SPACE_ADMIN_REMOVAL_DENIED`) que si la cible
cesse alors d'être administrateur effectif **et** qu'aucun autre ne demeure. Les rôles
ORGANIZATION (`R_ORG_OWNER`, `R_ORG_ADMIN`) ne comptent pas : ils ne donnent pas à eux seuls
de session dans un Space où leur titulaire n'a pas de User.

Ce que l'invariant change, sans concurrence :
- **plus permissif** : retirer le rôle direct d'un administrateur qui reste membre du groupe,
  ou le dernier membre du groupe quand un détenteur direct demeure ;
- **plus strict** : un administrateur désactivé ne compte plus.

## 4. Réalisation

- `LastSpaceAdministratorGuard` (nouveau) : verrouille le Space, puis évalue l'autre voie de la
  cible et compte les autres administrateurs effectifs.
- `SpaceAdministrationRepository` (port) et son adaptateur PostgreSQL :
  `pg_advisory_xact_lock(hashtextextended('takibo:space-administration:' || spaceId, 0))`. Ce
  verrou consultatif est tenu jusqu'à la fin de la transaction du service : il sérialise tous les
  retraits d'administration d'un même Space, rôle comme groupe. Il ne verrouille aucune ligne
  métier et ne couple pas identity-core à la table `spaces`. L'adaptateur exige une transaction
  ouverte (`Propagation.MANDATORY`).
- `UserRoleGovernanceService.removeRole` et `UserGroupGovernanceService.removeFromGroup`
  appellent la garde à la place de leurs comptes séparés. La suppression a lieu dans la même
  transaction, après la décision : aucune suppression partielle.

Coordination avec SEC-HUNT-01 (#128), sans dépendance de branche : #128 empêche un délégué de
restreindre un administrateur plus privilégié, ou de se restreindre lui-même. Ce récit protège
l'administration effective contre les retraits de pouvoir.

## 5. Tests

| Niveau | Test | Ce qu'il prouve |
|---|---|---|
| Unitaire | `LastSpaceAdministratorGuardTest` (5) | dernier administrateur, autre administrateur, autre voie, cible inactive, verrou avant toute lecture |
| Unitaire | `UserRoleGovernanceServiceTest`, `UserGroupGovernanceServiceTest` | garde appelée avec la bonne autre voie ; rien n'est supprimé après un refus |
| Unitaire | `SpaceAdministrationRepositoryAdapterTest` (2) | clé du verrou, compte excluant la cible |
| Bout en bout PostgreSQL | `LastSpaceAdministratorConcurrencyPostgresIntegrationTest` (7) | 8 tours de retraits croisés concurrents, par rôle, par groupe et mixtes : toujours un 200 et un 409, un administrateur restant ; dernier administrateur effectif : 409 sans suppression ; héritage par le groupe ; administrateur désactivé |
| Newman | dossier « 06 » (+11 requêtes) | retrait du groupe quand d'autres administrateurs demeurent ; retraits croisés concurrents ; restauration |

Rouge sur `main` : 6 des 7 tests de bout en bout échouent (dont `200`/`200` au premier tour).
Contre le jar de `main`, la collection modifiée échoue sur 2 assertions.

Seule précondition posée en base dans le test de bout en bout : retirer au fondateur ses
pouvoirs d'administration du Space, qu'il ne peut pas se retirer lui-même par l'API.

## 6. Limites et risques résiduels

- Le cycle de vie (`deactivate`) ne passe pas par cette garde : désactiver le dernier
  administrateur reste possible pour un auteur de rang égal ou supérieur (après #128). À
  arbitrer : étendre l'invariant au cycle de vie.
- Le verrou est propre à PostgreSQL ; le dépôt ne migre que vers PostgreSQL.
- `countIdentitiesHoldingRole` et `countIdentitiesInGroup` ne sont plus appelés par les services ;
  ils ne sont pas supprimés dans ce récit.
