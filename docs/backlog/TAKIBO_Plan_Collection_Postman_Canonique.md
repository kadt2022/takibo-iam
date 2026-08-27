# TAKIBO — Plan canonique de la collection Postman

## Statut

**Plan validé**, avec corrections intégrées.

Cette collection devient la **source unique de vérité E2E** de TAKIBO. Les trois tranches décrivent l’ordre d’implémentation, mais elles vivent dans une seule collection canonique :

```text
postman/Takibo-Postman-Collection.json
```

## Règles de construction

1. Toute requête HTTP commence par le code attendu : `200 —`, `201 —`, `400 —`, `403 —`, etc.
2. Les assertions Postman reprennent également le code HTTP dans leur nom.
3. Aucun code marqué `⚠` n’est implémenté avant vérification du contrôleur et du contrat réel.
4. Le `SETUP` est un script de collection, pas une fausse requête HTTP.
5. Les scénarios différés restent visibles dans la collection, mais sans requête fictive.
6. Un endpoint inexistant retournant `404` prouve le routage, pas à lui seul le *default-deny* de l’autorisation.
7. Le contrat actuel du token machine reste `subject_type=CLIENT_APP`. Une évolution vers `SERVICE_ACCOUNT`, `DEVICE` ou `WORKLOAD` devra faire l’objet d’un récit doctrinal distinct.
8. `/me/spaces` décrit les appartenances du compte : un employé membre d’un Space doit voir ce Space même sans rôle administratif.

---

## TR1 — Socle, authentification, utilisateurs, lifecycle

```text
Takibo-Postman-Collection
│
├── 00 — Boot et environnement                       [2 req / 4 tests + 1 script]
│   ├── SCRIPT COLLECTION — Helpers globaux et runId unique             (hors requête)
│   ├── 200 — Health — Application disponible                            (1)
│   └── 200 — Token PLATFORM — Client machine authentifié                (3)  claims: subject_type=CLIENT_APP, scope_level=PLATFORM, aucun org_id
│
├── 01 — Signup et validations                                    [11 req / 20 tests]
│   ├── 201 — Signup — Organisation, space, fondateur créés              (3)  orgId+spaceId+accountId+userId retournés
│   ├── 201 — Signup — Deuxième organisation, même nom, code différent   (2)  ← « deux Takibo » : name dupliqué autorisé
│   ├── 409 — Signup — Code organisation déjà pris                       (2)  + contrat Sentinel
│   ├── 409 — Signup — Code identique à casse différente                 (2)  ← unicité insensible à la casse
│   ├── 403 — Signup — organization.id renseigné refusé                  (2)  ← preuve E2E de SEC-TMS-01
│   ├── 200 — Login de contrôle — Aucun effet de bord après signup refusé (1)
│   ├── 400 — Signup — Code organisation trop court                      (2)
│   ├── 400 — Signup — Email invalide                                    (2)
│   ├── 400 — Signup — Mot de passe hors politique                       (2)
│   ├── 400 — Signup — Payload imbriqué incomplet                        (2)
│   └── 400 — Signup — Nom d'organisation absent                         (2)
│
├── 02 — Authentification humaine                                 [9 req / 22 tests]
│   ├── 200 — Login fondateur — Triplet avec spaceCode → token SPACE     (5)  claims: roles[R_ORG_OWNER], org_id, space_id, scope_level=SPACE, subject_type=HUMAN
│   ├── 200 — Login fondateur — Sans spaceCode → token ORGANIZATION      (4)  claims: org_id présent, space_id absent, scope_level=ORGANIZATION
│   ├── 401 — Login — Organisation inexistante                           (3)  + réponse de référence uniforme
│   ├── 401 — Login — Email inconnu — réponse identique                  (2)  ← comparaison stricte au corps de référence
│   ├── 401 — Login — Mot de passe erroné — réponse identique            (2)  ← idem
│   ├── 400 — Login — orgCode absent                                     (2)
│   ├── 200 — Mes spaces — Fondateur voit son space initial              (2)
│   ├── 200 — Mes spaces — Employé voit son space sans rôle administratif (1)
│   └── 401 — Mes spaces — Sans token refusé                            (1)
│
├── 03 — Utilisateurs                                             [8 req / 15 tests]
│   ├── 201 — Utilisateur — Employé créé par le fondateur                (3)
│   ├── 200 — Utilisateurs — Liste consultée par le fondateur            (2)
│   ├── 200 — Utilisateur — Détail employé consulté                      (2)
│   ├── 200 — Utilisateur — Profil mis à jour                            (2)
│   ├── 200 — Login employé — Aucun pouvoir, claims vides                (3)  claims: roles[] et permissions[] vides
│   ├── 403 — Utilisateurs — Liste refusée à l'employé sans pouvoir      (2)
│   ├── 404 — Utilisateur — Identifiant inconnu non énumérable           (2)
│   └── 404 — Utilisateur — Employé d'un autre space n'existe pas        (2)  ← 404 anti-énum, pas 403
│
└── 04 — Lifecycle utilisateur                                    [10 req / 19 tests]
    ├── 200 — Utilisateur — Suspension réussie                          ⚠(2)
    ├── 403 — Login — Utilisateur suspendu refusé                        (3)  code USER_NOT_ACTIVE
    ├── 200 — Utilisateur — Réactivation réussie                        ⚠(2)
    ├── 200 — Login — Utilisateur réactivé accepté                       (2)
    ├── 200 — Utilisateur — Verrouillage réussi                         ⚠(2)
    ├── 403 — Login — Utilisateur verrouillé refusé                      (2)
    ├── 200 — Utilisateur — Réactivation après verrouillage              (2)
    ├── 200 — Utilisateur — Désactivation réussie (état terminal)        ⚠(2)
    ├── 409 — Utilisateur — Réactivation après désactivation refusée     (2)  ← état terminal
    └── 404 — Lifecycle — Utilisateur inconnu non énumérable             (2)
```

**TR1 : 40 requêtes / 80 tests + 1 script de collection**

---

## TR2 — Rôles, groupes, RBAC effectif

```text
├── 05 — Catalogue RBAC                                           [9 req / 16 tests]
│   ├── 200 — Rôles — Catalogue consulté par le fondateur                (3)  ORGANIZATION+SPACE seuls, aucun R_TAKIBO_*, aucun R_SELF
│   ├── 200 — Rôle — R_SPACE_ADMIN détaillé avec ses permissions         (2)
│   ├── 404 — Rôle — Rôle plateforme non énumérable                      (2)  R_TAKIBO_PLATFORM_ADMIN
│   ├── 404 — Rôle — R_SELF non énumérable                               (2)  ← garde selfService de RBAC-01
│   ├── 403 — Rôles — Catalogue refusé à l'employé sans pouvoir          (2)
│   ├── 200 — Groupes — Catalogue consulté                               (2)
│   ├── 404 — Groupe — Groupe hors frontière non énumérable              ⚠(1)
│   ├── 200 — Permissions — Catalogue sans plan PLATFORM                 (2)
│   └── 404 — Permission — Permission plateforme non énumérable         ⚠(2)
│
├── 06 — Attribution de rôles                                     [12 req / 24 tests]
│   ├── 200 — Rôle — R_SPACE_ADMIN attribué à l'employé                  (2)  état courant retourné
│   ├── 200 — Rôle — Attribution idempotente (rejeu)                     (2)
│   ├── 200 — Rôle — Assignations directes listées                       (2)
│   ├── 200 — Login employé — R_SPACE_ADMIN présent dans le token        (3)  claims + permissions non vides
│   ├── 200 — Utilisateurs — Liste autorisée par rôle direct             (2)
│   ├── 403 — Rôle — Auto-attribution de R_ORG_ADMIN refusée             (3)  ROLE_SCOPE_ESCALATION_DENIED
│   ├── 404 — Rôle — Attribution générique de R_ORG_OWNER refusée        (3)  ← garde assignable (RBAC-01) + aucune assignation créée
│   ├── 404 — Rôle — Code fantôme PLATFORM_ADMIN refusé                  (2)  ← RBAC-00
│   ├── 404 — Rôle — Code inconnu refusé                                 (2)
│   ├── 409 — Rôle — Auto-rétrogradation refusée                         (2)  SELF_DEMOTION_DENIED
│   ├── 200 — Rôle — R_SPACE_ADMIN retiré de l'employé                   (1)
│   └── 200 — Login employé — Pouvoir disparu après retrait              (2)  roles[] vides
│
├── 07 — Groupes et héritage                                      [8 req / 17 tests]
│   ├── 200 — Groupe — Employé ajouté à G_SPACE_ADMINS                   (2)
│   ├── 200 — Login employé — R_SPACE_ADMIN hérité du groupe             (3)  claims: rôle hérité présent
│   ├── 200 — Utilisateurs — Liste autorisée par héritage de groupe      (2)
│   ├── 200 — Groupe — Employé ajouté à G_ORG_ADMINS                    ⚠(2)
│   ├── 200 — Login employé — R_ORG_ADMIN hérité SANS R_ORG_OWNER        (4)  ← filtre inheritable (RBAC-01), l'assertion clé
│   ├── 200 — Groupe — Employé retiré de G_ORG_ADMINS                    (1)
│   ├── 200 — Groupe — Employé retiré de G_SPACE_ADMINS                  (1)
│   └── 403 — Utilisateurs — Liste refusée après retrait des groupes     (2)
│
└── 08 — Frontières et permissions effectives                     [11 req / 24 tests]
    ├── 403 — Frontière — Token ORGANIZATION refusé sur route SPACE      (3)
    ├── 403 — Frontière — Token de l'org B refusé sur l'org A            (3)  ← étanchéité multi-tenant
    ├── 404 — Frontière — Space de l'org B invisible depuis l'org A      ⚠(2)
    ├── 403 — Frontière — org_id du token différent du chemin            (3)
    ├── 403 — Frontière — space_id du token différent du chemin          (3)
    ├── 403 — Plateforme — Token PLATFORM refusé sur lifecycle tenant    (3)
    ├── 403 — Plateforme — Token PLATFORM refusé sur users tenant        (2)
    ├── 200 — Dashboard — Compteurs organisation cohérents               (3)  usersTotal, activeUsersTotal, spacesTotal, oauthClientsTotal
    ├── 403 — Dashboard — Refusé à l'employé sans pouvoir                (2)
    ├── 403 — Dashboard — Token SPACE refusé                             (2)  POL_ORG_DASHBOARD_ORG_HUMAN_REQUIRED
    └── 403 — Dashboard — Cross-organisation refusé                      (2)
```

**TR2 : 40 requêtes / 81 tests**

---

## TR3 — Spaces, clients OAuth2, plateforme, CI

```text
├── 09 — Spaces                                                   [10 req / 19 tests]
│   ├── 201 — Space — Créé par autorité organisationnelle                (3)  ownerAccountId = compte du fondateur
│   ├── 409 — Space — Code dupliqué dans l'organisation refusé          ⚠(2)
│   ├── 200 — Spaces — Liste paginée de l'organisation                   (3)  page, size, totalElements
│   ├── 200 — Spaces — Liste filtrée par statut                          (2)
│   ├── 200 — Spaces — Recherche par terme                               (2)
│   ├── 400 — Spaces — Tri sur champ hors liste blanche refusé          ⚠(2)
│   ├── 200 — Space — Détail lu par le R_SPACE_ADMIN local               (2)  ← exception READ locale (PR #30)
│   ├── 403 — Space — R_SPACE_ADMIN d'un autre space refusé              (2)
│   ├── 403 — Space — Création refusée à l'employé sans pouvoir          (2)
│   └── 404 — Space — Identifiant inconnu non énumérable                 (2)
│
├── 10 — Clients OAuth2 et OIDC                                   [12 req / 25 tests]
│   ├── 201 — Client — Créé avec secret initial livré une seule fois     (4)  clientId + clientSecret présents
│   ├── 200 — Client — Consulté sans jamais re-livrer le secret          (3)  ← assertion transversale
│   ├── 200 — Clients — Liste de l'organisation                          (2)
│   ├── 200 — Client — Rotation du secret réussie                        (3)  nouveau secret ≠ ancien
│   ├── 409 — Client — Rotation concurrente refusée                     ⚠(2)
│   ├── 400 — Client — URI de redirection invalide refusée                (2)
│   ├── 400 — Client — Grant type non autorisé refusé                    (2)
│   ├── 400 — Client — Scope invalide refusé                             (2)
│   ├── 400 — Client — Algorithme de token non sûr refusé                (2)
│   ├── 409 — Client — client_id déjà utilisé globalement                ⚠(2)
│   ├── 403 — Client — Token PLATFORM refusé à la création tenant        (3)  ← PR #33
│   └── 403 — Client — Token PLATFORM refusé à la rotation tenant        (3)  ← aucun secret livré
│
├── 11 — Surface plateforme et actuator                           [5 req / 9 tests]
│   ├── 200 — Actuator — Health public sans authentification             (2)
│   ├── 401 — Actuator — Env refusé sans token                           (2)
│   ├── 403 — Actuator — Env refusé au fondateur tenant                  (2)  ← SEC-TMS-03
│   ├── 403 — Plateforme — Route /api/platform refusée au tenant         ⚠(2)
│   └── 404 — Route — Endpoint inexistant                            (1)  ← preuve de routage, pas preuve du default-deny
│
├── 12 — Organisations (différé)                                  [0 req]
│   └── (suspension et réactivation d'organisation — en attente du récit dédié)
│
├── 13 — Audit (différé)                                          [0 req]
│   └── (read-side audit org et space — en attente du récit dédié)
│
└── 99 — Nettoyage                                                [2 req / 2 tests]
    ├── 200 — Nettoyage — Attributions temporaires retirées              (1)
    └── 200 — Nettoyage — Employés de test désactivés                    (1)
```

**TR3 : 29 requêtes / 55 tests**

---

## Le compte

| Tranche | Requêtes | Tests |
|---|---:|---:|
| TR1 — socle, auth, users, lifecycle | 40 | 80 |
| TR2 — rôles, groupes, RBAC, frontières | 40 | 81 |
| TR3 — spaces, OAuth, plateforme | 29 | 55 |
| **Total** | **109** | **216** |

On dépasse largement la cible de 140-160 — parce que chaque 4xx porte le contrat Sentinel et chaque login porte ses claims. Si tu veux atterrir plus près de 160, le levier propre est de n'asserter le contrat Sentinel complet qu'une fois par famille d'erreur au lieu de systématiquement (~-40 tests).

## Les 12 codes marqués ⚠ à trancher au contrôleur

Lifecycle (4) : suspend/activate/lock/deactivate renvoient-ils `200` avec corps ou `204` ? — Catalogue (2) : groupe/permission hors frontière en `404` ou `403` ? — Spaces (3) : code dupliqué `409`, tri invalide `400`, Space cross-org `404` ou `403` ? — OAuth (2) : rotation concurrente et `client_id` global en `409` ? — Plateforme (1) : `/api/platform` en `403` ou `404` ?

**Aucun ne sera écrit avant vérification** — c'est ta règle, et c'est aussi là qu'on découvrira des écarts contrat/doctrine.

## Prochaine action

Implémenter **TR1** : script de collection, 40 requêtes HTTP et 80 assertions, puis exécuter un `newman run` local avant le branchement CI.
---

## Critères de fermeture

La consolidation est terminée lorsque :

- les anciennes collections ont une destination documentée pour chacun de leurs scénarios ;
- aucune preuve n’est perdue ou dupliquée sans justification ;
- la CI n’exécute qu’une seule collection canonique ;
- le rapport Newman expose clairement les dossiers et les codes HTTP attendus ;
- les 12 contrats marqués `⚠` ont été vérifiés dans les contrôleurs ;
- la collection complète passe localement puis dans GitHub Actions ;
- les anciennes collections sont supprimées seulement après cette preuve.

## Branche et commit proposés

```text
Branche : ci/takibo-canonical-postman-collection
Commit  : ci(postman): consolidate TAKIBO tests into one canonical collection
```
