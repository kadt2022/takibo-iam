# SEC-TMS-07 — L'absence de politique n'est jamais une autorisation

**Statut** : EN COURS — implémenté sur `feat/sec-tms-07-refus-deterministe`, PR à ouvrir  
**Lot** : Durcissement TMS et sécurité  
**Risque** : élevé — décision d'autorisation de toute la surface `/api/v1`  
**Doctrine** : décision du Capitaine (novembre 2026) ; RBAC-05/06 ; SEC-TMS-02 (default-deny des routes)  
**Dépend de** : aucun récit ouvert  
**Révision vérifiée** : `main@1589f71`

## 1. Doctrine

> **TAKIBO sécurise par lui-même. L'ADP est une protection complémentaire, facultative et
> configurable. Il ne crée jamais l'autorité.**

TAKIBO est conçu, développé, testé et sécurisé comme si l'ADP n'existait pas. Le socle assure
seul l'authentification des humains et des machines, la validité et la révocation des identités
et des preuves, l'isolation des Organizations et des Spaces, les autorisations RBAC, le respect des
statuts et le refus par défaut de toute opération non explicitement autorisée. Une requête n'est
jamais autorisée parce que son score de risque est favorable.

Lorsque le socle a déjà autorisé une opération, l'ADP peut confirmer, refuser une opération à risque
élevé ou demander une authentification renforcée. Il ne crée jamais une autorisation, n'accorde
jamais une permission absente et n'annule jamais un refus du socle.

Ordre des décisions pour une requête `/api/v1/**` :

1. authentification — JWT valide, identité admissible ;
2. frontières — Organization, Space et sujet vérifiés ;
3. autorisation déterministe — politique explicite et permissions requises ;
4. ADP (facultatif) — analyse du risque, restrictions supplémentaires.

Une route sans politique explicite est refusée à l'étape 3 : **l'ADP n'est jamais consulté pour un
refus**. La même opération dépourvue d'autorisation déterministe est refusée, que l'ADP soit absent,
désactivé, activé pour certains Spaces ou pour l'Organization.

**Orientation future, hors de ce récit** : activation de l'ADP configurable par Organization et par
Space (désactivation Organization prioritaire, héritage Space, règle explicite pour les opérations
de niveau Organization), et distinction entre désactivation volontaire et panne du moteur activé,
dont la politique sera décidée séparément.

## 2. Problème

`PolicyEvaluator.evaluate` termine par `orElseGet(PolicyEvaluator::defaultAllow)` : quand
aucune politique ne décide, la décision est `PERMIT` (`POL_DEFAULT_ALLOW`) et l'ADP adaptatif
reste seul juge. `UngovernedTmsPolicy` verrouille les routes TMS à UUID, mais exclut
expressément les routes lisibles et toute autre forme de chemin.

## 3. Preuves (sonde d'exécution sur PostgreSQL, non versionnée)

| # | Constat | Preuve |
|---|---|---|
| P1 | 41 mappings MVC à l'exécution, dont 29 routes `/api/v1` de production et 8 routes `/debug/secure/**` (profil `test` uniquement) | `RequestMappingHandlerMapping` de l'application démarrée |
| P2 | Les 28 routes de production servies par l'ADP sont toutes décidées par une politique nommée ; aucune n'atteint `POL_DEFAULT_ALLOW` | `PolicyEvaluator` réel, sujets HUMAN (SPACE, ORGANIZATION) et SERVICE |
| P3 | `POL_DEFAULT_ALLOW` n'est atteint que par les 8 routes `/debug/secure/**` (deux répondent 200 à un token sans pouvoir) et par `POST /api/v1/auth/login`, public dans `SecurityConfig` et jamais évalué par l'ADP | idem |
| P4 | Une route `/api/v1` sans contrôleur ni politique franchit l'autorisation et reçoit **404** de Spring MVC au lieu d'un refus | test rouge §6 |
| P5 | Avec un ADP qui autorise tout, l'isolation tient déjà sur les routes existantes : autre Organization, autre Space, token ORGANIZATION sur une route SPACE, permission absente, token PLATFORM, route TMS d'une autre Organization | garde-fous verts §6 |
| P6 | `POST /api/v1/orgs/signup` est `PERMIT` aussi pour un sujet SERVICE | `POL_ORG_SIGNUP_AUTHENTICATED_CREATE` |

Conclusion : risque **structurel** (toute nouvelle route non gouvernée serait autorisée par
défaut), pas faille actuelle des routes de production.

## 4. Périmètre

1. Remplacer `POL_DEFAULT_ALLOW` par un refus déterministe `POL_DEFAULT_DENY` lorsqu'aucune
   politique ne décide.
2. Conserver toutes les décisions explicites existantes et leurs `policyId` (aucune régression).
3. Rendre la couverture vérifiable : l'inventaire des routes est lu à l'exécution, et toute route
   servie par l'ADP doit être décidée par une politique nommée.
4. Prouver l'isolation Organization/Space avec un ADP systématiquement permissif.

## 5. Décisions du Capitaine

- **D1 — Routes `/debug/secure/**`** : supprimées, après remplacement démontré de leurs tests (§6).
- **D2 — Inscription par un sujet SERVICE (P6)** : comportement actuel conservé ; examen séparé.
- **D3 — Route authentifiée sans politique explicite** : refus déterministe `403` (`POL_DEFAULT_DENY`).
- **D4 — `PolicyEvaluatorTest.tmsSpaceRules_ignoreReadableCodeRoutes`** : sa finalité est conservée
  (aucune règle TMS ne décide sur une route en codes) ; la décision attendue devient `POL_DEFAULT_DENY`.

## 6. Réalisation

**Socle d'autorisation**
- `PolicyEvaluator` : `defaultAllow` (`PERMIT`, `POL_DEFAULT_ALLOW`) remplacé par `defaultDeny`
  (`DENY`, `POL_DEFAULT_DENY`). `PolicyBasedAuthorizationManager` refusait déjà sur tout DENY
  avant l'appel à l'ADP : un refus du socle ne consulte jamais l'ADP.
- Constat de câblage : `takibo.adp.enabled` vaut `false` par défaut, et un ADP désactivé répond
  `ALLOW` (`SpringAdaptiveDecisionService`). Avant ce récit, toute route décidée par défaut était
  donc autorisée sans aucun filet dans la configuration par défaut.

**Endpoints `/debug/secure/**` (D1), dans l'ordre exigé**
1. Recensement : `SecurityAccessTestController` (8 endpoints, `@Profile("test")`), `SecurityDebugService`
   (`@Service` sans profil, chargé aussi en production, sans autre consommateur), la règle
   `/debug/secure/**` de `SecurityConfig`. Seul `TokenPermissionBoundaryPostgresIntegrationTest`
   les utilisait ; aucune référence Postman, documentaire ou dans un autre profil.
2. Garanties : G1 — une permission hors catalogue ne devient jamais une autorité ; G2 — les
   permissions ORG et PLATFORM sont retirées d'un token SPACE ; G3 — une permission SPACE est
   retirée d'un token ORG. Les six autres endpoints ne portaient aucune garantie testée.
3. Remplacement : les autorités sont lues en passant le token au vrai bean `JwtAuthenticationFilter`
   de l'application (validation, filtres de rôles et de permissions, `AuthorityFactory`), sans
   endpoint ; G1 est en outre prouvée en HTTP sur une route métier (permissions brutes → `403`).
4. Équivalence prouvée **avant** suppression : un test temporaire a comparé, sur les trois
   profils de token, les autorités vues par le filtre réel et par `/debug/secure/context` :
   identiques. Il a été retiré une fois l'endpoint supprimé.
5. Suppression : contrôleur, `SecurityDebugService` devenu sans consommateur, règle de `SecurityConfig`.
   Ces chemins retombent sur `anyRequest().denyAll()`.
6. Non-régression : aucun mapping `/debug` à l'exécution avec le profil `test`
   (`ExplicitPolicyRouteInventoryPostgresIntegrationTest`) ni avec `test` + `dev`
   (`NoDebugEndpointInDevProfilePostgresIntegrationTest`), et les anciens chemins répondent
   `401` anonyme / `403` authentifié, jamais `200`.

**Tests fiabilisés** (`PolicyBasedAuthorizationManagerTest`) : sans vélocité simulée, une décision
atteignant l'ADP échouait avant lui puis était refusée par repli, si bien que les tests « refusé
avant l'ADP » restaient verts même si la politique laissait passer. Un assistant
`givenTheAdpWouldAllowIfConsulted` rend le chemin praticable et l'ADP permissif ; les quatre tests
concernés et le test fondamental échouent désormais si le socle laisse passer (preuve par mutation).

## 7. Tests

**Test fondamental** : une opération non autorisée est refusée **sans appeler l'ADP**.

**Rouges sur `main`, verts avec le correctif**

| Test | Échec sur `main` |
|---|---|
| `PolicyBasedAuthorizationManagerTest.ungovernedRoute_isDeniedByTheDeterministicCore_withoutEverConsultingTheAdp` (3 routes) | accès accordé dès que l'ADP répond ALLOW |
| `PolicyEvaluatorTest.a_route_that_no_policy_governs_is_denied_deterministically_for_every_action` | `POL_DEFAULT_ALLOW` au lieu de `POL_DEFAULT_DENY` |
| `PolicyEvaluatorTest.no_decision_is_ever_a_default_permit` | idem |
| `DeterministicAuthorizationWithPermissiveAdpPostgresIntegrationTest.a_route_that_no_policy_governs_is_refused_even_with_every_permission` (3 routes) | `404` au lieu de `403`, et l'ADP est consulté |
| `DeterministicAuthorizationWithDisabledAdpPostgresIntegrationTest.a_route_that_no_policy_governs_is_refused_by_the_core_alone` (3 routes), sans aucun mock, configuration par défaut | `404` au lieu de `403` |
| `ExplicitPolicyRouteInventoryPostgresIntegrationTest.every_mapped_route_is_decided_by_a_named_policy_never_by_default` | les 8 routes `/debug/secure/**` |

Preuve par mutation : en rétablissant temporairement l'ancien défaut permissif, les tests de bout
en bout redeviennent rouges, y compris celui qui s'exécute sans aucun mock.

**Garde-fous verts, ADP systématiquement permissif** (`DeterministicAuthorizationWithPermissiveAdpPostgresIntegrationTest`).
L'ADP permissif est un outil de preuve de l'indépendance du socle, pas l'architecture cible.

- requête légitime acceptée et ADP permissif effectivement consulté ;
- refus déterministes (permission absente, mauvais niveau de token, autre Organization) prononcés sans consulter l'ADP ;
- token d'une autre Organization, d'un autre Space de la même Organization : refusés ;
- token ORGANIZATION sur une route SPACE, permission requise absente : `403` ;
- token PLATFORM sur des routes tenant : refusé (`401` dès l'authentification) ;
- route TMS à UUID d'une autre Organization : `403` ;
- absence de token, token falsifié : `401` ;
- routes publiques inchangées : login (`401` sur mauvais mot de passe), santé Actuator (`200`).

**Remplacement des preuves `/debug/secure/**`** (`TokenPermissionBoundaryPostgresIntegrationTest`) :
autorités lues via le vrai `JwtAuthenticationFilter` (G1, G2, G3) ; permissions hors catalogue
refusées en HTTP sur une route métier (G1).

**Absence de surface de diagnostic** : `ExplicitPolicyRouteInventoryPostgresIntegrationTest.no_debug_endpoint_is_mapped`
(profil `test`) et `NoDebugEndpointInDevProfilePostgresIntegrationTest` (profils `test` + `dev`, mappings et HTTP).

**Contrat modifié par D3, et lui seul** : `SentinelHttpTranslationPostgresIntegrationTest` attendait
`404` pour `GET /api/v1/route-inexistante` avec un token valide — la route était autorisée par défaut
puis Spring MVC répondait 404. Elle reçoit désormais `403` (`ACCESS_DENIED`, format Sentinel, sans
détail interne). Les 16 attentes `404` de la collection Newman portent sur des routes gouvernées
(ressource introuvable côté service) et ne changent pas.

**Régression** : `PolicyEvaluatorTest` (décisions explicites), `ReadableUserCreationBoundaryHttpIntegrationTest`,
`TokenPermissionBoundaryPostgresIntegrationTest`, `UnknownTechnicalRoleTokenPostgresIntegrationTest`,
collection Newman.

## 8. Critères d'acceptation

- [x] AC-01 — Aucune décision `PERMIT` n'est produite sans politique explicite.
- [x] AC-02 — Une route `/api/v1` non gouvernée est refusée (`403`, `POL_DEFAULT_DENY`), avant son contrôleur.
- [x] AC-03 — Toute route servie par l'ADP est décidée par une politique nommée, prouvé par l'inventaire d'exécution.
- [x] AC-04 — Avec un ADP systématiquement permissif, aucune opération non autorisée ne passe.
- [x] AC-04bis — Tout refus du socle est prononcé sans consulter l'ADP.
- [x] AC-05 — Toutes les décisions explicites existantes et leurs `policyId` sont inchangées.
- [x] AC-06 — Les décisions D1 à D4 sont tranchées et appliquées.

## 9. Hors périmètre (récits séparés)

- Configuration granulaire de l'ADP (Organization, Space, héritage) et politique de panne du moteur : orientation d'architecture, aucun développement dans ce récit ; aucune refonte du moteur ADP.
- `OrgBoundaryFilter` : aucun appel sur les 37 combinaisons route/méthode réelles (preuve HTTP) ; adaptation ou retrait dans un récit dédié.
- Invariant B de révocation humaine (IAM-REVOKE-02).
- Révocation des preuves machine : un token révoqué ou dont le client est supprimé reste authentifié par TSM.
- `@RequireActiveSpace` : conservé ; seule garde « Space actif » des clients OAuth.
