# Récit IAM 31 — Établir l'authentification humaine de portée organisationnelle

**Statut** : TERMINÉ (PR #31, 2026-07-12)

```text
L'organisation identifie le compte.
Le space situe l'action.
```

## Loi du récit

```text
Une autorité d'organisation ne doit jamais dépendre de l'existence d'un space.
```

Ce récit ne se réduit pas à un nouveau login : il répare la vérité RBAC qui rend ce
login possible. Le cœur du travail est le reclassement des autorités organisationnelles
à leur vrai niveau, puis seulement l'émission d'une preuve de portée `ORGANIZATION`.

---

## 1. Contexte

Le parcours de connexion cible de TAKIBO a été acté (juillet 2026) :

```text
Organisation + identifiants
        ↓
Console Organisation
        ↓
Choix d'un space
        ↓
Token situé SPACE
        ↓
Console Space
```

Le couple d'authentification de TAKIBO est `(orgCode, email, password)` — car un
Account appartient à une organisation, pas à la plateforme. Le space n'est pas un
élément d'identification : c'est un contexte de travail choisi après connexion.

Ce récit est le premier de la séquence :

```text
IAM 31 — Authentification humaine de portée organisationnelle   (ce récit)
IAM 32 — Consulter mes spaces accessibles
IAM 33 — Établir et changer le contexte space
UI 01.6 — Connexion organisationnelle et sélection du space
UI 02  — BFF et session web durable
```

Le BFF ne doit pas être construit autour d'un parcours provisoire : IAM 31, 32 et 33
stabilisent le parcours définitif avant lui.

## 2. Problème observé

Trois faits, constatés dans le code au 11 juillet 2026 :

**a) Le login humain exige un space.**
`AuthController` (`/api/v1/auth/login`) exige `orgCode` **et** `spaceCode` ;
`HumanLoginService` refuse de produire une preuve sans user local actif dans le
space demandé ; `HumanTokenSigner` (TAS) lève
`HUMAN_TOKEN_REQUIRES_FULL_TENANT_IDENTITY` si l'un de org/space/account/user
manque. Il n'existe aucun token humain de portée `ORGANIZATION`.

**b) L'autorité du fondateur est accidentellement située dans son premier space.**
`TechnicalRbacProvision.provisionFounder(...)` écrit `R_ORG_OWNER` **et**
`G_ORG_ADMINS` avec `initialSpaceId` — rattachés au space initial, alors que le
schéma sait représenter une attribution organisationnelle (`space_id IS NULL`).
Conséquences :

- un login ORG qui lirait proprement les seules lignes `space_id IS NULL`
  rendrait un fondateur **sans aucun rôle** ;
- la suppression du space initial emporterait l'autorité du propriétaire
  (`ON DELETE CASCADE` sur `fk_ra_space_scope`) ;
- le pouvoir « organisationnel » observé dans les tokens SPACE actuels est dérivé
  d'un contexte space invisible — doctrinalement faux.

**c) La lecture, elle, est déjà prête.**
`GovernanceRoleAssignmentRepositoryAdapter.findDirectAssignments` inclut déjà
`space_id IS NULL OR space_id = :spaceId` : une attribution org-level rayonne dans
tous les spaces de l'org. Les index d'unicité org-level existent déjà :
`uq_ra_org_technical_role` (V202605301700) et `uq_ga_org_code_membership`
(V202607061200). Le schéma et la lecture sont en avance sur l'écriture.

## 3. Décision doctrinale

### Actée par ce récit

1. **Niveau d'une attribution.** Une attribution d'autorité organisationnelle vit
   dans les tables existantes `role_assignments` / `group_assignments` avec
   `space_id IS NULL`. Aucune nouvelle table. Le scope du code au catalogue
   (`TechnicalRole.scope`, `TechnicalGroup.scope`) et le niveau de la ligne
   doivent coïncider : un code de scope `ORGANIZATION` s'assigne org-level, un
   code de scope `SPACE` s'assigne space-level.

2. **Seul le catalogue technique peut être organisationnel** dans ce récit.
   Les assignations `GOVERNANCE` et `BUSINESS` restent situées dans un space
   (doctrine PR #26 : « un rôle GOVERNANCE est une ligne tenant d'un space »).
   Une gouvernance org-level éventuelle sera un récit dédié.

3. **Le token ORG ne porte que le pouvoir organisationnel.** Rôles, groupes et
   permissions de scope `ORGANIZATION` exclusivement — jamais l'agrégation des
   pouvoirs de spaces, jamais un code de scope `SYSTEM`, `SPACE` ou `USER`.

4. **Le token ORG n'a pas de user.** Le `User` local est une réalité de space
   (`users(org_id, space_id, account_id)`). Claims : `sub = accountId`,
   `account_id` présent, `org_id` présent, `space_id` absent, `user_id` absent,
   `takibo_scope_level = ORGANIZATION`, `tenant_source = human_login`,
   `subject_type = HUMAN`, `auth_method = PASSWORD`.

5. **Échec uniforme.** Organisation inexistante, compte inexistant dans cette
   organisation, mauvais mot de passe, compte non admissible (verrouillé,
   inactif) → même réponse externe :

   ```text
   401 AUTHENTICATION_FAILED
   « Impossible de valider cette connexion. »
   ```

   Les causes précises vivent dans l'audit serveur, jamais dans la réponse.
   Aucune résolution pré-authentification d'`orgCode` (pas de branding avant
   login) : l'existence d'une organisation ne se teste pas depuis l'extérieur.

6. **Compatibilité SPACE.** Les tokens SPACE continuent d'hériter des autorités
   ORG (la lecture combinée existante est conservée). Après migration, le
   fondateur voit toujours `R_ORG_OWNER` dans ses tokens de space — mais
   désormais pour la bonne raison.

### Arbitrages — ACTÉS le 11 juillet 2026 (« vas-y » de Pi sur les recommandations)

A : `spaceCode` optionnel sur le même endpoint (retrait au récit IAM 33). B : 401
uniforme sur toute la surface login, chemin SPACE compris (saga 90-02 ajustée).
C : TTL dédié `takibo.tas.human-org-token.ttl-seconds`, défaut aligné. D : pas de
contrainte SQL de forme — garde applicative (les cas d'assignation refusent
désormais un `spaceId` pour un code de scope ORGANIZATION) + migration.

Découverte d'implémentation : le BVT a exposé un bug préexistant de
`POST /api/v1/orgs/{orgId}/spaces` — le contrôleur passait le `userId` de
l'acteur comme `owner_account_id` (violation de `fk_spaces_owner_account_scope`).
Corrigé : `CurrentActorProvider.currentAccountId()` délégué au port
`CurrentAccountContextCase` d'identity-core. Le propriétaire d'un space est un
ACCOUNT — cohérent avec la doctrine du récit.

Note AC-08/BVT : la suspension d'un space n'a pas encore de surface API ; la
saga prouve la loi par « création d'un deuxième space puis re-login ORG
inchangé ». La preuve par suspension a été jouée EN BASE le 12 juillet :
space initial de takibo-demo suspendu → login ORG 200 avec R_ORG_OWNER
intact, login SPACE refusé uniformément (401), réactivation → login SPACE
rétabli (après le TTL 5 s du cache de statut). Elle entrera au BVT quand le
lifecycle des spaces sera récité.

### Corrections de la revue du sage (12 juillet 2026)

- **P0 — fail-open éliminé** : `CurrentActorProvider.currentAccountId()` ne
  retombe plus sur l'acteur système en cas d'échec de résolution — l'exception
  remonte et produit un refus explicite. Le fallback système reste réservé aux
  opérations système explicitement appelées comme telles.
- **P1 — égalisation temporelle** : hash factice constant
  (`takibo.auth.login.dummy-password-hash`, sinon généré une fois au démarrage)
  exécuté quand l'organisation est inconnue/inactive, le space inaccessible, le
  compte ou ses credentials absents — le temps de réponse ne trahit plus la
  cause derrière le 401 uniforme.
- **P1 — BOM retiré** de la migration (UTF-8 sans BOM) ; historique Flyway
  réparé et migration rejouée proprement sur la base dev (idempotente).
  Cause racine consignée : `Set-Content -Encoding utf8` de PowerShell 5.1
  ajoute un BOM — ne plus éditer de fichier du dépôt par ce chemin.

### Détail des arbitrages

- **A. Transition du login à 4 champs.** Recommandation : sur le même
  `POST /api/v1/auth/login`, `spaceCode` devient **optionnel** — présent, le
  comportement actuel (token SPACE) est inchangé ; absent, le flux ORG s'applique.
  Le chemin 4 champs est déclaré transitoire et son retrait sera acté par le
  récit IAM 33 (quand l'échange ORG → SPACE existera). Alternative écartée :
  un second endpoint, qui doublerait la surface d'authentification.

- **B. Portée de l'uniformisation 401.** Recommandation : elle s'applique à
  **toute** la surface `/api/v1/auth/login`, y compris le chemin SPACE
  transitoire — les réponses actuelles 403 (verrouillé, user non actif) et
  404 (org/space introuvable) deviennent 401 uniforme. Cela prolonge la
  décision « reason audit-only » de PR #24 et impose d'ajuster les BVT
  existants (ex. saga 90-02 « login suspended » attend 403 aujourd'hui).
  Exception défendable : `space not active` sur le chemin SPACE, où
  l'appelant est déjà authentifié par ailleurs — trancher.

- **C. TTL du token ORG.** Recommandation : propriété dédiée
  `takibo.tas.human-org-token-ttl-seconds`, défaut aligné sur le TTL humain
  actuel. Le token ORG est un token de console : il pourra vivre un peu plus
  longtemps que le token SPACE quand le BFF existera, mais pas dans ce récit.

- **D. Verrou de forme en base.** Recommandation : ne pas ajouter de contrainte
  SQL « un code ORGANIZATION ne peut pas être space-level » dans ce récit — la
  garde vit dans l'application (provision + gouvernance) et la migration
  normalise l'existant. Une contrainte partielle pourra venir quand la
  gouvernance org-level sera récitée.

## 4. Périmètre

Dans l'ordre d'exécution :

1. **Doctrine des attributions ORG** : `space_id IS NULL` (décision 1 et 2).
2. **Correction de `TechnicalRbacProvision`** : `provisionFounder` écrit
   `R_ORG_OWNER` et `G_ORG_ADMINS` org-level (`spaceId = null`) ;
   `R_SPACE_ADMIN` et `G_SPACE_ADMINS` restent sur le space initial.
   Les ports d'assignation acceptent un `spaceId` nul pour les codes de scope
   `ORGANIZATION` (source TECHNICAL uniquement) et le refusent pour les autres.
3. **Migration des données existantes** avec dédoublonnage (voir § 7).
4. **Nouveau calcul `effectiveOrgFor(orgId, accountId)`** dans
   `EffectiveRbacQueryService` (ou service dédié) : lit exclusivement les
   lignes `space_id IS NULL` ; rôles directs org-level + groupes org-level +
   héritage des groupes techniques ; filtre strict scope `ORGANIZATION` sur
   rôles, groupes et permissions ; listes dédupliquées et triées (token
   déterministe, invariant PR #27).
5. **Émission du token humain `ORGANIZATION`** : `HumanTokenSigner` accepte deux
   formes — SPACE (invariant actuel intact) et ORGANIZATION (org + account
   requis, space et user absents). Claims selon décision 4.
6. **Login à trois champs** : `HumanLoginService` sans résolution de space —
   résolution d'`orgCode` seul (org active exigée), identité, credentials,
   verrouillage, mot de passe, puis `effectiveOrgFor` et émission ORG.
   `LoginResponse` : `spaceId` et `userId` absents (nullables), `scopeLevel =
   ORGANIZATION`.
7. **Réponse d'échec uniforme** (décision 5, arbitrage B).
8. **Compatibilité des tokens SPACE** : aucune régression du chemin actuel ;
   le pouvoir ORG y reste visible par héritage.
9. **Adaptation minimale de TSM** :
   - le contexte de sécurité tolère un token sans `space_id` quand
     `takibo_scope_level = ORGANIZATION` ;
   - les surfaces **space-scopées** refusent un token ORG (fail-closed : pas de
     `space_id`, pas de surface de space — vérifier `SpaceBoundaryGuard`) ;
   - les seules décisions org-authority existantes (liste/création de spaces,
     PR #30) acceptent un token ORG dont l'`org_id` correspond à la ressource.
   Aucune nouvelle surface organisationnelle n'est ouverte par ce récit.
10. **BVT** démontrant la loi du récit (voir § 10).

## 5. Invariants

```text
I1. Une autorité de scope ORGANIZATION est une ligne space_id IS NULL.
I2. Un token ORGANIZATION ne contient aucun code de scope SYSTEM, SPACE ou USER.
I3. Un token ORGANIZATION ne porte ni space_id ni user_id.
I4. Le pouvoir d'un token ORG ne dépend d'aucun space — création, suspension ou
    suppression d'un space ne le modifie pas.
I5. Les listes roles/groups/permissions sont dédupliquées et triées.
I6. Toute cause d'échec du login est indistincte de l'extérieur (401 uniforme)
    et distincte dans l'audit.
I7. Le chemin SPACE existant reste fonctionnel et hérite des autorités ORG.
I8. Aucune surface space-scopée n'accepte un token ORG.
```

## 6. Flux

```text
POST /api/v1/auth/login
{ "orgCode": "takibo", "email": "founder@takibo.io", "password": "..." }

TIS-CORE
  1. Résout orgCode → orgId (org ACTIVE exigée)        [échec → 401 uniforme]
  2. Account par (orgId, email)                         [échec → 401 uniforme]
  3. Credentials, verrouillage, mot de passe            [échec → 401 uniforme,
     compteur d'échecs inchangé dans son comportement]
  4. effectiveOrgFor(orgId, accountId)
       rôles org-level directs (TECHNICAL, scope ORGANIZATION)
     + groupes org-level directs (TECHNICAL, scope ORGANIZATION)
     + rôles hérités des groupes techniques (scope ORGANIZATION)
     + permissions des rôles effectifs (scope ORGANIZATION)
  5. HumanAccessTokenIssuer.issue(forme ORGANIZATION)

TAS
  6. Signe : sub=accountId, org_id, account_id,
     takibo_scope_level=ORGANIZATION, tenant_source=human_login,
     subject_type=HUMAN, auth_method=PASSWORD,
     roles/groups/permissions — sans space_id, sans user_id.

Réponse 200
{ accessToken, tokenType, expiresIn,
  scopeLevel: "ORGANIZATION",
  organizationId, accountId,
  spaceId: absent, userId: absent }
```

Un compte sans aucune autorité organisationnelle (ex. simple employé d'un space)
**se connecte quand même** : claims vides, comme au niveau space (invariant
PR #27 « claims vides = login OK »). C'est IAM 32 qui lui montrera ses spaces.

## 7. Migrations

Une migration Flyway Postgres (chemin canonique `db/migration` ; le miroir mysql
suit le sort du profil résiduel, hors périmètre) :

```text
V2026xxxxxxxx__rbac_org_authority_reclassification.sql
```

Étapes, dans cet ordre :

1. **Dédoublonnage rôles** : pour les lignes `role_source = 'TECHNICAL'` dont le
   `role_code` est de scope ORGANIZATION (liste en dur : `R_ORG_OWNER`,
   `R_ORG_ADMIN`, `R_ORG_USER_ADMIN`, `R_ORG_CLIENT_ADMIN`, `R_ORG_AUDITOR`,
   `R_ORG_VIEWER`), supprimer les doublons par
   `(org_id, identity_type, identity_id, role_code)` en conservant la plus
   ancienne — sinon le reclassement violerait `uq_ra_org_technical_role`.
2. **Reclassement rôles** : `UPDATE ... SET space_id = NULL` sur ces lignes.
3. **Dédoublonnage puis reclassement groupes** : même logique pour
   `group_assignments`, `group_source = 'TECHNICAL'`, codes de scope
   ORGANIZATION du catalogue `TechnicalGroup` (liste en dur, `G_ORG_ADMINS` a
   minima), unicité cible `uq_ga_org_code_membership`.
4. **Aucun index à créer** : `uq_ra_org_technical_role` et
   `uq_ga_org_code_membership` existent déjà.

La migration est idempotente de fait (après reclassement, plus aucune ligne ne
matche les prédicats). Elle doit être validée sur la base dev réelle (l'org
`takibo-demo` provisionnée le 11 juillet en fait un cas de test naturel).

## 8. Sécurité

- Anti-énumération : 401 uniforme (décision 5), pas de résolution pré-auth
  d'orgCode, pas d'oracle de timing grossier (le coût du hash de mot de passe ne
  doit pas être court-circuité de manière observable quand le compte n'existe
  pas — au besoin, hash factice).
- Le verrouillage par échecs répétés (compteur, `lockedUntil`) s'applique au
  chemin ORG comme au chemin SPACE.
- Les identifiants n'apparaissent jamais dans les logs ni les URLs (le
  masquage du `toString()` des DTO sensibles est traité par la tâche dédiée
  déjà ouverte — indépendante de ce récit).
- L'audit conserve les causes réelles d'échec (org inconnue, compte inconnu,
  verrouillé, mauvais mot de passe) avec `traceId`.
- Fail-closed TSM : un token sans `space_id` ne franchit jamais une frontière
  de space.

## 9. Critères d'acceptation

**AC-01 — Provision org-level.** Après un signup, les lignes `R_ORG_OWNER` et
`G_ORG_ADMINS` du fondateur ont `space_id IS NULL` ; `R_SPACE_ADMIN` et
`G_SPACE_ADMINS` portent le space initial.

**AC-02 — Migration.** Sur une base contenant des autorités ORG situées dans des
spaces (y compris en doublon multi-spaces), la migration les reclasse org-level
sans violation d'unicité ni perte ; les attributions de scope SPACE ne bougent pas.

**AC-03 — Login ORG.** `POST /api/v1/auth/login` avec `orgCode + email +
password` valides retourne 200, `scopeLevel = ORGANIZATION`, un token dont les
claims respectent les décisions 3 et 4 (I2, I3, I5 vérifiés sur le JWT décodé).

**AC-04 — Échec uniforme.** Org inexistante, email inconnu, mauvais mot de
passe, compte verrouillé : quatre requêtes, une seule réponse observable —
même statut 401, même code `AUTHENTICATION_FAILED`, même message, même forme de
corps. L'audit distingue les quatre causes.

**AC-05 — Aucune autorité dérivée d'un space.** Un compte n'ayant que
`R_SPACE_ADMIN` (space-level) obtient un token ORG avec `roles = []`,
`permissions = []`. Ses pouvoirs de space n'apparaissent pas.

**AC-06 — Filtre de scope.** Aucune permission de scope SYSTEM/SPACE/USER dans
un token ORG, même si une ligne org-level anormale existait en base (garde de
lecture, pas seulement d'écriture).

**AC-07 — Héritage SPACE intact.** Après migration, le login SPACE du fondateur
contient toujours `R_ORG_OWNER`, `R_SPACE_ADMIN`, `G_ORG_ADMINS`,
`G_SPACE_ADMINS` et les permissions associées — identique à l'avant-récit.

**AC-08 — La loi du récit.** Le fondateur crée un deuxième space puis son space
initial est suspendu : le login ORG répond 200 et le token contient toujours
`R_ORG_OWNER`. L'autorité organisationnelle n'a dépendu d'aucun space.

**AC-09 — Frontière space fail-closed.** Un token ORG présenté sur une surface
space-scopée (ex. liste des users d'un space) est refusé.

**AC-10 — Surface org accessible.** Un token ORG de fondateur est accepté sur la
surface org-authority existante (liste des spaces de l'org, PR #30) ; un token
ORG d'une autre org y est refusé.

**AC-11 — Compte sans autorité.** Un employé sans attribution org-level se
connecte en ORG : 200, claims vides.

**AC-12 — Déterminisme.** Deux logins ORG successifs du même compte produisent
des listes de claims identiques (ordre compris).

## 10. BVT

Nouvelle section de la saga lisible (après « 00 - Auth & bootstrap ») :

```text
06 - Org login
  200 - 01 - Auth - Login founder ORG - 3 champs, scopeLevel ORGANIZATION
  200 - 02 - Assert - Token ORG : roles org-only, pas de space_id/user_id
  401 - 03 - Security - Org inexistante - réponse uniforme
  401 - 04 - Security - Email inconnu - réponse uniforme
  401 - 05 - Security - Mauvais mot de passe - réponse uniforme
  200 - 06 - Auth - Login employee ORG - claims vides
  403 - 07 - Security - Token ORG refusé sur surface space (list users)
  200 - 08 - Space - Liste des spaces de l'org avec token ORG (PR #30)
  200 - 09 - Space - Créer un deuxième space (contexte fondateur)
  200 - 10 - Space - Suspendre le space initial
  200 - 11 - Auth - Re-login founder ORG - toujours R_ORG_OWNER   ← loi du récit
  200 - 12 - Auth - Login founder SPACE (deuxième space) - hérite R_ORG_OWNER
```

Ajustements des sagas existantes selon l'arbitrage B (ex. 90-02 : 403 → 401).
Les tests unitaires et d'intégration par module couvrent la provision, la
migration (base peuplée avant/après), `effectiveOrgFor`, les deux formes du
signer TAS et les gardes TSM.

## 11. Hors périmètre

```text
GET /me/spaces (projection personnelle)          → IAM 32
Échange de contexte ORG → SPACE                  → IAM 33
Retrait du login 4 champs                        → IAM 33
Sélecteur de space et Console Organisation UI    → UI 01.6
BFF et session web durable                       → UI 02
Humains plateforme, /platform/login, MFA         → récits ultérieurs
Branding pré-authentification par organisation   → décision future
Gouvernance org-level par API (assignation)      → récit dédié
Rôles BUSINESS et GOVERNANCE org-level           → récit dédié
Epoch de sécurité / révocation                   → récit dédié
Miroir de migration MySQL                        → suit le sort du profil résiduel
```

## 12. Définition de terminé

- Les quatre arbitrages A–D sont tranchés et consignés dans ce document.
- Build et tests de tous les modules verts.
- La migration passe sur une base vierge **et** sur la base dev réelle peuplée.
- La saga BVT « 06 - Org login » passe intégralement ; les sagas existantes
  restent vertes (ajustées selon l'arbitrage B).
- Démonstration live : login à 3 champs contre le boot local, token décodé
  conforme (I2, I3, I5), puis AC-08 rejoué manuellement.
- Aucun secret dans le dépôt ; aucun identifiant dans les logs applicatifs.
- Les invariants I1–I8 sont chacun couverts par au moins un test automatisé.
