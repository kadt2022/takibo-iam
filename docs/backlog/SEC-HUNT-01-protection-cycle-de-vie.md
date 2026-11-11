# SEC-HUNT-01 — Un pouvoir délégué de cycle de vie ne restreint pas plus privilégié que lui

**Statut** : EN REVUE — branche `fix/sec-hunt-01-user-lifecycle`  
**Lot** : SECURITY FIX 01–05  
**Risque** : élevé — exploitable par un rôle délégué, effet irréversible  
**Origine** : SECURITY HUNT 01, découverte HUNT-01  
**Révision vérifiée** : `main@19998c4`

## 1. Récit

En tant que propriétaire d'une Organization, je veux qu'un utilisateur à qui j'ai délégué la
gestion des utilisateurs d'un Space ne puisse ni me suspendre, ni me verrouiller, ni me
désactiver, ni le faire à un administrateur plus privilégié que lui, afin qu'une délégation ne
devienne jamais un moyen de prendre le contrôle du Space.

## 2. Défaut constaté

`UserLifecycleService.changeStatus` n'exige que `P_SPACE_USERS_LIFECYCLE`, accordée par la
politique `ReadableSpacePolicy`. Aucune règle ne porte sur la **cible**. La surface de
gouvernance RBAC, elle, protège le propriétaire (`R_ORG_OWNER` non retirable, PR #117),
interdit l'auto-rétrogradation et l'escalade de scope.

Preuve sur une instance locale jetable (`main@19998c4`) :

| Auteur | Action | Cible | Attendu | Observé |
|---|---|---|---|---|
| `R_SPACE_USER_ADMIN` | `deactivate` | fondateur (propriétaire) | refus | **200**, `DEACTIVATED` |
| `R_SPACE_USER_ADMIN` | `suspend`, `lock`, `deactivate` | `R_SPACE_ADMIN` | refus | **200** |
| `R_SPACE_ADMIN` | `deactivate` | fondateur | refus | **200** |
| `R_SPACE_USER_ADMIN` | `deactivate` | lui-même | refus | **200** |

Conséquences mesurées : le fondateur ne peut plus ouvrir de session SPACE ; `DEACTIVATED` est
terminal (`activate` répond 409) ; re-projeter le compte répond 409. Aucune API ne permet de revenir en arrière.

## 3. Doctrine retenue

La permission ouvre la surface ; l'autorité de la cible la borne. Pour toute transition
**restrictive** (`SUSPENDED`, `LOCKED`, `DEACTIVATED`, soit `UserStatus.requiresRevocation()`) :

1. **Le propriétaire n'est jamais restreint sur cette surface**, comme `R_ORG_OWNER` n'y est
   jamais retiré. Sa mise à l'écart relèvera d'un transfert de propriété dédié (RBAC-07).
2. **Personne ne se restreint soi-même**, comme un administrateur ne s'y rétrograde pas
   (même code d'erreur : `SELF_DEMOTION_DENIED`, 409).
3. **L'autorité effective de la cible ne dépasse jamais celle de l'auteur.** Ordre :
   `R_ORG_OWNER` > `R_ORG_ADMIN` > `R_SPACE_ADMIN` > tout autre rôle. Une autorité égale suffit,
   comme sur la surface RBAC où `R_ORG_ADMIN` retire `R_ORG_ADMIN`.

Les autorités sont **effectives et lues en base** : rôles directs, rôles hérités des groupes
techniques (`G_SPACE_ADMINS`, `G_ORG_ADMINS`) et des groupes de gouvernance, rôles
ORGANIZATION. Le calcul est celui qui signe les tokens (`EffectiveRbacQueryCase.effectiveFor`),
jamais les claims du token de l'auteur.

La réactivation (`activate`) n'est pas une restriction : elle reste ouverte à
`P_SPACE_USERS_LIFECYCLE`. La doctrine des rôles n'est pas modifiée ; aucune désactivation
légitime n'est rendue impossible.

## 4. Réalisation

- `UserLifecycleAuthorityGuard` (identity-core, nouveau) : les trois règles ci-dessus.
- `UserLifecycleService.changeStatus` : appelle la garde avant toute transition restrictive,
  dans la transaction, avant la moindre écriture.
- `UserLifecycleAuthorityException` → **403** `USER_LIFECYCLE_AUTHORITY_REQUIRED` (Sentinel).
- Aucune route, politique ni `@PreAuthorize` ajoutés ; `PolicyEvaluator` inchangé.

## 5. Tests

| Niveau | Test | Ce qu'il prouve |
|---|---|---|
| Unitaire | `UserLifecycleAuthorityGuardTest` (16 cas) | matrice auteur × cible, rôle hérité, soi-même |
| Unitaire | `UserLifecycleServiceTest` (+3) | garde appelée sur chaque restriction, jamais sur la réactivation, rien d'écrit après un refus |
| Unitaire | `SentinelRuleRegistrarTest` | 403 `USER_LIFECYCLE_AUTHORITY_REQUIRED` |
| Bout en bout PostgreSQL | `UserLifecycleProtectionPostgresIntegrationTest` (12) | parcours réel par l'API ; statut **et version** inchangés en base après chaque refus |
| Newman | dossier « 04 - Lifecycle utilisateur » (+8 requêtes) | délégué contre propriétaire, soi-même, propriétaire contre délégué |

Rouge avant correctif : 8 des 12 tests de bout en bout échouent sur `main`, les 4 témoins passent ;
contre le jar de `main`, la collection modifiée échoue sur les 5 nouveaux contrôles.

## 6. Limites et risques résiduels

- **Tokens déjà émis** (invariant B, IAM-REVOKE-02) : un utilisateur désactivé ne reçoit plus
  de token, mais celui qu'il détient reste accepté jusqu'à son expiration (300 s). Le test
  `a_token_issued_before_deactivation_keeps_working_until_it_expires_known_limit` fixe ce
  comportement pour que son changement soit visible.
- **Concurrence** : deux administrateurs de même rang peuvent se restreindre mutuellement au
  même instant. La protection du dernier administrateur sous concurrence est l'objet de
  SEC-HUNT-06, sans dépendance de branche.
- **Profil** (`PATCH …/users/{id}`) : hors de ce récit. Un délégué peut modifier le nom
  d'affichage ou les métadonnées du propriétaire ; à arbitrer.

## 7. Critères d'acceptation

- [x] Un délégué ne restreint ni le propriétaire ni un administrateur plus privilégié (403).
- [x] Personne ne se restreint soi-même (409).
- [x] Un utilisateur ordinaire reste suspendu, verrouillé, désactivé et réactivé comme avant.
- [x] Aucun refus ne modifie le statut ni la version en base.
- [x] Tests unitaires, bout en bout PostgreSQL et Newman exécutés.
