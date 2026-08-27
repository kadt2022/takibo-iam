# Récit ORG-REG-02 — Vérifier le courriel et provisionner l’organisation fondatrice

## Statut

À faire

## Domaines

- plan de contrôle `ORGANIZATION` ;
- identité humaine ;
- RBAC.

## Dépendance

`ORG-REG-01 — Créer une demande publique d’inscription d’organisation`

## Intention

En tant que fondateur ayant reçu le courriel de vérification,  
je veux valider ma demande,  
afin que TAKIBO crée une seule fois mon organisation, mon identité fondatrice et mon rôle `R_ORG_OWNER`.

La vérification ne remet aucun token tenant.

Le premier token `HUMAN / ORGANIZATION` est obtenu ensuite par le login normal.

---

## Doctrine TAKIBO

> La vérification transforme une demande temporaire en vérité tenant.

> Le provisioning est atomique ou orchestré de manière à ne jamais publier un état partiel mensonger.

> `R_ORG_OWNER` est le résultat du parcours validé, jamais un prérequis public.

> TAS signe les tokens. Il ne décide pas qui devient Owner et ne provisionne pas l’organisation.

> Le provisioning d’inscription ne crée aucun Space. Une organisation active avec zéro Space est un état valide.

> Le premier Space est une décision explicite du fondateur après son premier login `ORGANIZATION`.

> Le jeton de vérification suffit à lui seul. La vérification fonctionne depuis n’importe quel appareil, y compris un appareil différent de celui utilisé pour créer la demande.

> Une preuve de courriel acceptée est définitive. Elle ne peut jamais être invalidée par l’expiration de la demande ni par une panne technique survenue après son acceptation.

> L’Organization ne devient `ACTIVE` qu’au terme complet du provisioning. Aucune Organization active sans Account Owner fonctionnel, même de manière transitoire.

> Le provisioning d’inscription crée l’`Account` fondateur au niveau de l’organisation, mais ne crée aucun `User`.

> Un `User` est une identité située dans un Space. Le `userId` n’existe qu’au moment où l’Account est inscrit dans un Space.

---

## Périmètre

Ce récit couvre :

1. la validation de la capacité d’inscription ;
2. la validation du jeton de courriel ;
3. la création de `EmailVerification` ;
4. la transition définitive vers `EMAIL_VERIFIED`, indépendante du succès du provisioning ;
5. la création de l’Organization ;
6. la création de l’Account fondateur ;
7. l’attribution organisationnelle de `R_ORG_OWNER` à cet Account ;
8. la transition vers `VERIFIED`, avec l’Organization qui devient `ACTIVE` au même instant atomique ;
9. la reprise interne du provisioning après un échec technique (`PROVISIONING_RETRY_PENDING`) ;
10. l’idempotence du rejeu ;
11. le premier login du fondateur ;
12. la vérification du token `HUMAN / ORGANIZATION` ;
13. la reconnaissance de l’état valide `Organization ACTIVE` avec zéro Space et zéro User.

---

# 1. Vérifier le courriel

## Endpoint

```http
POST /api/v1/org-registrations/{registrationId}/email-verifications
Content-Type: application/json
```

## Requête

```json
{
  "token": "one-time-email-verification-token"
}
```

Cet endpoint ne requiert **aucune** `Authorization: Bearer {registrationAccessToken}`.

Le jeton de vérification à usage unique, transmis dans le corps, est à lui seul une preuve suffisante de contrôle du courriel. Exiger en plus la capacité liée à l’appareil d’origine casserait le cas d’usage le plus courant : un fondateur qui s’inscrit sur un ordinateur et ouvre le courriel de vérification sur son téléphone.

```text
registrationId (dans l’URI)
→ identifie la demande ciblée

verification token (dans le corps)
→ prouve le contrôle du courriel, suffit à autoriser l’opération
```

Le jeton doit correspondre au `registrationId` fourni dans l’URI ; sinon, voir section 10, « Jeton valide syntaxiquement mais non applicable ».

## Protection contre le brute-force

Cet endpoint étant public (sans capacité), il est protégé par une limitation de fréquence par `registrationId` et par IP :

```http
429 Too Many Requests
Retry-After: 60
```

```json
{
  "error": "verification_attempt_rate_limited"
}
```

Cette limitation ne bloque jamais une tentative légitime unique ; elle protège uniquement contre le devinage systématique du jeton.

---

# 2. Première vérification réussie

Lors de la première vérification valide, la demande transite immédiatement et **définitivement** vers `EMAIL_VERIFIED`.

Cette transition est indépendante du succès du provisioning qui suit : une fois atteinte, elle ne peut plus être annulée par une expiration ni par une panne technique (voir section 10).

TAKIBO tente alors, dans la même opération, de provisionner exactement une fois :

```text
Organization
Account fondateur
Attribution organisationnelle de R_ORG_OWNER
EmailVerification
```

## Succès complet (cas nominal)

Si le provisioning aboutit dans le même appel, la demande transite vers `VERIFIED` et l’Organization devient `ACTIVE` au même instant atomique (voir section 5).

```http
201 Created
Location: /api/v1/org-registrations/{registrationId}/email-verifications/{verificationId}
Cache-Control: no-store
```

```json
{
  "verificationId": "40cc7241-a77a-4890-bb3d-e595ed782c92",
  "registrationId": "86993ad2-caf5-4c0a-870e-c55c68952cca",
  "status": "VERIFIED",
  "organization": {
    "id": "8ca7a98e-8854-4ac4-8143-b30463dd9525",
    "name": "Acme Corporation",
    "orgCode": "acme-corporation-550e8400-e29b-41d4-a716-446655440000",
    "uri": "/api/v1/orgs/8ca7a98e-8854-4ac4-8143-b30463dd9525"
  },
  "accountId": "746b85b6-3392-4b79-905a-7cd53194f63d"
}
```

La réponse ne contient aucun access token ou refresh token.

## Provisioning non terminé dans le même appel

Si le provisioning rencontre un échec technique après la transition vers `EMAIL_VERIFIED`, la demande transite vers `PROVISIONING_RETRY_PENDING` (voir section 10). TAKIBO retourne :

```http
202 Accepted
Cache-Control: no-store
```

```json
{
  "verificationId": "40cc7241-a77a-4890-bb3d-e595ed782c92",
  "registrationId": "86993ad2-caf5-4c0a-870e-c55c68952cca",
  "status": "PROVISIONING_RETRY_PENDING"
}
```

Aucun identifiant d’Organization ou d’Account n’est exposé tant que le provisioning n’est pas complet — conformément à l’invariant « aucun état partiel mensonger ».

Le même jeton peut être présenté à nouveau, y compris depuis un autre appareil, pour connaître l’avancement ou déclencher une nouvelle tentative (voir section 9, Idempotence).

## Consultation de la vérification

```http
GET /api/v1/org-registrations/{registrationId}/email-verifications/{verificationId}
Authorization: Bearer {registrationAccessToken}
```

```http
200 OK
Cache-Control: no-store
```

Le corps retourné reflète l’état courant : identique à la création réussie si `VERIFIED`, ou limité au `status` si le provisioning n’est pas encore complet.

Cette consultation est une commodité de dé-référencement REST (le `Location` émis à la création doit rester consultable). Le client n’en a pas besoin pour connaître le statut de son inscription : `GET /api/v1/org-registrations/{registrationId}` suffit à cet effet et reste la source de vérité recommandée pour l’UI.

---

# 3. Répartition des responsabilités

## `takibo-management-service`

Il orchestre :

- le chargement et le verrouillage de la demande ;
- la validation du statut et de l’expiration ;
- la validation de la preuve de courriel ;
- la création de l’Organization ;
- la conservation du `reservedOrgCode` ;
- la finalisation de la demande ;
- l’enregistrement de `EmailVerification` ;
- l’audit du provisioning.

## `takibo-identity-core`

Il assure :

- la création de l’Account fondateur ;
- l’installation sécurisée de la crédentialité ;
- l’attribution organisationnelle de `R_ORG_OWNER` à l’Account ;
- le calcul du RBAC effectif au niveau de l’organisation ;
- le login humain situé dans l’organisation.

Il ne crée aucun `User` dans ce récit. La création d’un `User` appartient à l’inscription d’un Account dans un Space.

## TAS

TAS ne provisionne aucune ressource métier.

Il reste responsable de la signature des tokens produits par le login normal.

---

# 4. Provisioning atomique

Le système ne doit jamais exposer :

```text
Organization active sans Account Owner
Account Owner sans Organization
R_ORG_OWNER sans Account fondateur
Registration VERIFIED avec provisioning incomplet
```

## Stratégie préférée

Si les modules participent à la même transaction et à la même base, utiliser une transaction applicative unique.

Sinon, utiliser une saga explicite avec :

- étapes idempotentes ;
- états intermédiaires non actifs ;
- reprise après incident ;
- compensations sûres ;
- corrélation par `registrationId`.

Une simple succession d’appels non protégés n’est pas acceptable.

## Ce que signifie « reprise après incident »

Un échec **technique ou transitoire** (timeout, dépendance indisponible, conflit de verrouillage) ne remet jamais en cause la transition déjà acquise vers `EMAIL_VERIFIED` : la preuve de courriel reste valide, quelle que soit l’issue du provisioning.

La demande transite vers `PROVISIONING_RETRY_PENDING`. Le même jeton — déjà consommé comme preuve, mais toujours reconnu par TAKIBO — peut être présenté à nouveau, y compris depuis un autre appareil, pour connaître l’avancement ou déclencher une nouvelle tentative. La saga reprend à l’étape idempotente suivante : elle ne recrée jamais une Organization ou un Account déjà provisionnés lors d’une tentative précédente.

Seul un échec jugé **non récupérable** — après épuisement d’une politique interne de reprise, ou face à un échec métier réellement irréversible — fait transiter la demande vers l’état terminal `PROVISIONING_FAILED` (voir section 10).

---

# 5. Création de l’Organization

L’Organization reprend exactement :

```text
organizationName
reservedOrgCode
```

de la demande.

Le code ne doit pas être recalculé.

```text
registration.reservedOrgCode
= organization.orgCode
```

## Statut intermédiaire, activation atomique

L’Organization est créée dans un statut intermédiaire, non actif (`PROVISIONING`), tant que l’Account fondateur et l’attribution organisationnelle `R_ORG_OWNER` ne sont pas tous confirmés.

```text
Organization : PROVISIONING → ACTIVE
```

Cette transition vers `ACTIVE` a lieu dans la même étape atomique que la confirmation de `R_ORG_OWNER` — jamais avant, jamais séparément. Un incident survenant entre la création de l’Organization et l’attribution de `R_ORG_OWNER` laisse l’Organization en `PROVISIONING`, jamais en `ACTIVE`, quelle que soit la durée de l’incident.

Invariant : aucune Organization `ACTIVE` sans Owner fonctionnel — y compris de manière transitoire ou observable pendant une panne.

La contrainte `UNIQUE(org_code)` reste active dès la création, quel que soit le statut.

---

# 6. Création de l’Account fondateur

TAKIBO crée uniquement :

```text
Account
→ identité humaine et d’authentification au niveau de l’organisation
```

Le courriel normalisé de la demande devient l’identité de connexion de cet Account.

`takibo-identity-core` est l’unique propriétaire de l’algorithme, de la version et des paramètres de hachage du mot de passe fondateur.

Il résout la référence opaque (`founderCredentialReference`) qu’il a lui-même émise lors de la préparation de la crédentialité (`ORG-REG-01`, section « Préparation de la crédentialité par Identity Core »), et installe directement la crédentialité déjà préparée dans le stockage définitif de l’Account. `takibo-management-service` n’a jamais transmis ni retransmis le mot de passe ou un hash à cette étape : il n’a fait que transporter la référence opaque.

L’Account fondateur est créé avec un statut permettant le login après la vérification.

Aucun `User` n’est créé :

```text
Account
→ existe dans l’Organization

User
→ n’existe que dans un Space
→ sera créé lors de l’inscription de l’Account dans ce Space
```

À l’issue du signup :

```text
organizationId : présent
accountId      : présent
spaceId        : absent
userId         : absent
```

---

# 7. Attribution de `R_ORG_OWNER`

Le fondateur reçoit :

```text
R_ORG_OWNER
```

L’attribution est :

- située dans l’organisation créée ;
- liée à l’Account fondateur ;
- créée une seule fois ;
- visible dans le RBAC effectif ;
- projetée dans le token après login.

Aucun rôle `PLATFORM` n’est attribué.

`R_ORG_OWNER` n’est pas remplacé par un simple rôle de Space.

---

# 8. Aucun Space créé

Le provisioning s’arrête après la création de l’Organization, de l’Account fondateur et de l’attribution organisationnelle `R_ORG_OWNER`. Aucun Space et aucun `User` ne sont créés.

L’état suivant est valide et attendu à l’issue du provisioning :

```text
Organization : ACTIVE
Account      : ACTIVE
Rôle org     : R_ORG_OWNER
Spaces       : 0
Users        : 0
```

L’onboarding, la route de création volontaire du premier Space, son autorisation par permission située et la génération du `spaceCode` sont définis dans `ORG-SPACE-01`. Ce récit ne les redéfinit pas.

---

# 9. Idempotence

## Premier appel

```http
201 Created
```

## Rejeu du même jeton consommé

```http
200 OK
Content-Location: /api/v1/org-registrations/{registrationId}/email-verifications/{verificationId}
Cache-Control: no-store
```

```json
{
  "verificationId": "40cc7241-a77a-4890-bb3d-e595ed782c92",
  "registrationId": "86993ad2-caf5-4c0a-870e-c55c68952cca",
  "status": "VERIFIED",
  "organization": {
    "id": "8ca7a98e-8854-4ac4-8143-b30463dd9525",
    "name": "Acme Corporation",
    "orgCode": "acme-corporation-550e8400-e29b-41d4-a716-446655440000",
    "uri": "/api/v1/orgs/8ca7a98e-8854-4ac4-8143-b30463dd9525"
  },
  "accountId": "746b85b6-3392-4b79-905a-7cd53194f63d"
}
```

Le rejeu ne crée aucune nouvelle ressource.

Le corps du rejeu est identique à celui de la première réponse réussie : le même `accountId` est retourné, sans `spaceId` ni `userId`.

## Rejeu pendant une reprise (`PROVISIONING_RETRY_PENDING`)

```http
202 Accepted
Cache-Control: no-store
```

```json
{
  "verificationId": "40cc7241-a77a-4890-bb3d-e595ed782c92",
  "registrationId": "86993ad2-caf5-4c0a-870e-c55c68952cca",
  "status": "PROVISIONING_RETRY_PENDING"
}
```

Ce rejeu ne relance jamais une tentative concurrente si une reprise est déjà planifiée ; il permet à n’importe quel détenteur du jeton — y compris depuis un autre appareil que celui de la première tentative — de connaître l’avancement.

## Rejeu après échec non récupérable (`PROVISIONING_FAILED`)

```http
409 Conflict
```

```json
{
  "error": "org_registration_provisioning_failed",
  "status": "PROVISIONING_FAILED"
}
```

Ce statut est terminal : aucun rejeu, quel que soit le jeton présenté, ne relance le provisioning.

## Contraintes minimales

```text
une seule vérification réussie par registration
une seule Organization par registration
un seul Account fondateur par registration
une seule attribution organisationnelle initiale R_ORG_OWNER
aucun User créé par registration
```

La consommation doit être verrouillée ou sérialisée pour empêcher deux requêtes concurrentes de provisionner deux fois.

---

# 10. Erreurs

## Jeton absent ou mal formé

```http
400 Bad Request
```

```json
{
  "error": "verification_token_malformed"
}
```

## Jeton valide syntaxiquement mais non applicable

```http
422 Unprocessable Content
```

```json
{
  "error": "verification_token_invalid"
}
```

La réponse ne révèle pas si le jeton appartient à une autre demande.

## Demande expirée (avant preuve acceptée uniquement)

Si `registration.expiresAt` est dépassé **avant** que la demande n’ait atteint `EMAIL_VERIFIED`, TAKIBO retourne toujours cette erreur, indépendamment de la validité propre du jeton fourni :

```http
410 Gone
```

```json
{
  "error": "org_registration_expired",
  "status": "EXPIRED"
}
```

Aucune ressource tenant n’est créée. Aucun renvoi n’est possible dans cet état (voir `ORG-REG-01`).

Cette erreur ne peut jamais survenir une fois `EMAIL_VERIFIED` atteint : `expiresAt` cesse alors d’avoir tout effet (voir `ORG-REG-01`, section « Après l’acceptation d’une preuve »).

## Jeton expiré, demande encore active

Si seul le jeton a dépassé sa propre expiration alors que la demande est encore `PENDING_EMAIL_VERIFICATION` :

```http
410 Gone
```

```json
{
  "error": "verification_token_expired"
}
```

La demande reste valide. Un renvoi (`POST .../verification-emails`) produit un nouveau jeton et permet de reprendre la vérification.

## Ordre de priorité

```text
1. registration.expiresAt dépassé, statut encore PENDING_EMAIL_VERIFICATION → org_registration_expired (terminal)
2. jeton expiré seul, demande active → verification_token_expired (reprise possible via renvoi)
3. demande déjà EMAIL_VERIFIED ou au-delà → expiresAt sans effet, quel que soit le délai écoulé
```

La vérification évalue toujours `registration.expiresAt` en premier, et uniquement tant que `EMAIL_VERIFIED` n’est pas atteint (voir `ORG-REG-01`, section « Horloge d’expiration »).

## Demande déjà avancée, autre preuve présentée

```http
409 Conflict
```

```json
{
  "error": "org_registration_already_completed",
  "status": "EMAIL_VERIFIED"
}
```

Ce conflit porte sur l’état de la demande, jamais sur le nom ou le `orgCode`. Il s’applique dès que `EMAIL_VERIFIED` est atteint — pas seulement une fois `VERIFIED`.

## Distinction entre rejeu et conflit

Deux situations impliquent une demande ayant déjà atteint `EMAIL_VERIFIED` (quel que soit son sous-état ensuite : `PROVISIONING`, `PROVISIONING_RETRY_PENDING`, `VERIFIED` ou `PROVISIONING_FAILED`), mais produisent des réponses différentes :

```text
même jeton, celui qui a produit EMAIL_VERIFIED
→ rejeu idempotent (voir section 9)
→ 200 OK si VERIFIED, 202 Accepted si encore en cours, 409 si PROVISIONING_FAILED

jeton différent (ancien jeton invalidé par un renvoi, ou jeton d’une autre tentative)
→ 409 Conflict (org_registration_already_completed)
→ ne révèle jamais si ce jeton aurait été valide ailleurs
```

La comparaison porte sur l’identité du jeton fourni, pas seulement sur le statut de la demande.

## Capacité invalide

```http
404 Not Found
```

S’applique uniquement à `GET /api/v1/org-registrations/{id}` et à `POST .../verification-emails`, qui restent protégés par `registrationAccessToken`. Ne s’applique jamais à `POST .../email-verifications`, qui n’exige aucune capacité (voir section 1).

## Échec du provisioning

L’erreur est renvoyée dans le format `ProblemDetail` du projet.

La demande ne doit jamais être publiée comme `VERIFIED`, et l’Organization jamais comme `ACTIVE`, si le provisioning n’est pas cohérent — que l’échec soit récupérable ou non.

### Échec technique (transitoire, récupérable)

Si le provisioning échoue pour une cause jugée transitoire (timeout, dépendance indisponible, conflit de verrouillage), TAKIBO :

- ne publie jamais un état `VERIFIED` partiel, ni une Organization `ACTIVE` incomplète ;
- ne remet jamais en cause la transition déjà acquise vers `EMAIL_VERIFIED` ;
- fait transiter la demande vers `PROVISIONING_RETRY_PENDING` ;
- planifie une reprise automatique, interne et idempotente ;
- produit l’événement `ORG_PROVISIONING_FAILED` avec `recoverable: true`.

```http
202 Accepted
```

```json
{
  "status": "PROVISIONING_RETRY_PENDING"
}
```

N’importe quel détenteur du jeton — y compris depuis un autre appareil — peut soumettre à nouveau exactement la même vérification pour connaître l’avancement ou déclencher une nouvelle tentative. La saga reprend à l’étape idempotente suivante sans recréer les ressources déjà provisionnées.

### Échec non récupérable (rare)

Atteint uniquement après épuisement d’une politique interne de reprise, ou face à un échec métier réellement irréversible (conflit de données irréconciliable, décision explicite d’un opérateur) — jamais sur une simple panne technique isolée. TAKIBO :

- fait transiter la demande vers `PROVISIONING_FAILED`, un état terminal ;
- ne révèle, dans la réponse d’erreur, aucun identifiant partiel d’Organization ou d’Account déjà créé avant l’échec ;
- produit l’événement `ORG_PROVISIONING_FAILED` avec `recoverable: false`.

```http
409 Conflict
```

```json
{
  "error": "org_registration_provisioning_failed",
  "status": "PROVISIONING_FAILED"
}
```

Une demande `PROVISIONING_FAILED` ne peut pas être relancée avec le même jeton, ni bénéficier d’un renvoi de courriel (voir `ORG-REG-01`, section « Statuts non atteints par ce récit »). Le visiteur doit soumettre une nouvelle demande d’inscription.

Une consultation (`GET /api/v1/org-registrations/{id}`) d’une demande `PROVISIONING_FAILED` retourne `200 OK` avec ce statut, sans détail interne sur la cause de l’échec.

---

# 11. Premier login du fondateur

## Endpoint

```http
POST /api/v1/auth/login
Content-Type: application/json
```

## Requête

```json
{
  "orgCode": "acme-corporation-550e8400-e29b-41d4-a716-446655440000",
  "email": "owner@acme.example",
  "password": "********"
}
```

## Token attendu

```json
{
  "subject_type": "HUMAN",
  "auth_method": "PASSWORD",
  "takibo_scope_level": "ORGANIZATION",
  "takibo_tenant_source": "human_login",
  "org_id": "8ca7a98e-8854-4ac4-8143-b30463dd9525",
  "account_id": "746b85b6-3392-4b79-905a-7cd53194f63d",
  "roles": [
    "R_ORG_OWNER"
  ]
}
```

Le login :

- ne demande pas de `spaceCode` ;
- ne retourne pas de faux scope `PLATFORM` ;
- ne retourne pas de faux `space_id` ;
- produit un token ORGANIZATION véridique ;
- accepte que `/api/v1/me/spaces` retourne une liste vide ;
- permet au fondateur de créer volontairement son premier Space après le login.

---

# 12. Audit

Événements attendus :

```text
ORG_REGISTRATION_VERIFICATION_SUCCEEDED
ORG_REGISTRATION_VERIFICATION_REPLAYED
ORG_REGISTRATION_VERIFICATION_FAILED
ORG_PROVISIONING_STARTED
ORG_CREATED
FOUNDER_ACCOUNT_CREATED
ORG_OWNER_ASSIGNED
ORG_PROVISIONING_SUCCEEDED
ORG_PROVISIONING_FAILED
```

Tous partagent :

```text
registrationId
traceId
```

Après création, les événements pertinents portent également :

```text
organizationId
accountId
```

Aucun événement de ce récit ne porte de `userId`, puisqu’aucun User de Space n’existe encore.

Aucun secret complet n’est journalisé.

`ORG_REGISTRATION_VERIFICATION_FAILED` couvre un jeton absent, mal formé, invalide ou expiré — aucune ressource tenant n’a été touchée.

`ORG_PROVISIONING_FAILED` couvre un échec survenu après une preuve de courriel valide, pendant la saga de création. Il porte un indicateur `recoverable` :

```text
recoverable: true
→ échec technique transitoire ; la demande reste EMAIL_VERIFIED puis passe à PROVISIONING_RETRY_PENDING, reprenable avec le même jeton

recoverable: false
→ échec non récupérable ; la demande transite vers PROVISIONING_FAILED, terminale
```

---

# 13. Critères d’acceptation

## AC1 — Vérification nominale

Une inscription active et un jeton valide produisent `201 Created` et le statut `VERIFIED`.

## AC2 — Provisioning complet

Après succès, il existe exactement :

```text
1 Organization
1 Account fondateur
1 attribution organisationnelle R_ORG_OWNER
1 EmailVerification réussie
0 Space créé par le signup
0 User créé par le signup
```

## AC2A — Réponse sans User situé

Après succès, la réponse contient obligatoirement :

```text
organizationId
accountId
```

Elle ne contient jamais :

```text
spaceId
userId
ownerUserId
```

Le `userId` sera créé uniquement lors de l’inscription de l’Account dans un Space.

## AC3 — Code stable

L’Organization possède exactement le `reservedOrgCode` de la demande.

## AC4 — Aucun token à la vérification

La réponse de vérification ne contient aucun access token ni refresh token.

## AC5 — Rejeu idempotent

Le même jeton rejoué retourne `200 OK` avec les mêmes `organizationId` et `accountId`, sans `spaceId` ni `userId`, et ne crée rien.

## AC6 — Concurrence

Deux vérifications concurrentes ne produisent qu’un seul tenant.

## AC7 — Expiration

Une demande dont `registration.expiresAt` est dépassé **avant** `EMAIL_VERIFIED` retourne `410 Gone` (`org_registration_expired`) et ne crée rien, quel que soit l’état du jeton.

Un jeton dont seule sa propre expiration est dépassée, sur une demande encore active, retourne `410 Gone` (`verification_token_expired`) ; la demande reste reprenable via un renvoi.

Une fois `EMAIL_VERIFIED` atteint, `expiresAt` ne produit plus jamais `410 Gone`, quel que soit le délai écoulé.

## AC8 — Jeton invalide

Un jeton non applicable retourne `422 Unprocessable Content` sans révéler son origine.

## AC9 — Owner situé

Après login, le token est `HUMAN / ORGANIZATION`, contient `R_ORG_OWNER` et aucun rôle `PLATFORM`.

## AC10 — Organisation sans Space après login

Après le premier login,  
alors `GET /api/v1/me/spaces` retourne `200 OK` avec `[]`,  
sans erreur et sans création automatique d’un Space ou d’un User.

L’Account fondateur conserve l’autorité `ORGANIZATION` nécessaire pour créer volontairement son premier Space.

## AC11 — Aucun état partiel mensonger

Un échec de provisioning ne publie jamais une demande `VERIFIED` ni une Organization `ACTIVE` liée à un tenant incomplet, quel que soit le sous-état intermédiaire traversé.

## AC12 — Secrets protégés

Aucun mot de passe, jeton ou token de capacité complet n’apparaît dans les logs ou événements.

## AC13 — Échec technique récupérable

Étant donné un échec de provisioning jugé technique et transitoire,  
alors la demande transite vers `PROVISIONING_RETRY_PENDING` sans jamais annuler la transition déjà acquise vers `EMAIL_VERIFIED`,  
et une nouvelle soumission du même jeton reprend la saga sans recréer de ressource déjà provisionnée.

## AC14 — Distinction rejeu vs conflit

Étant donné une demande ayant déjà atteint `EMAIL_VERIFIED` (quel que soit son sous-état ensuite),  
quand le même jeton est présenté, TAKIBO retourne un rejeu idempotent (`200`, `202` ou `409` selon le sous-état) ;  
quand un jeton différent est présenté, TAKIBO retourne `409 Conflict` (`org_registration_already_completed`).

## AC15 — Vérification consultable

Étant donné une vérification réussie,  
quand le fondateur appelle `GET` sur l’URI `Location` retournée,  
alors TAKIBO retourne `200 OK` avec le même contenu que la création.

## AC16 — Échec non récupérable

Étant donné un échec de provisioning jugé non récupérable, après épuisement d’une politique interne de reprise ou face à un échec métier réellement irréversible,  
alors la demande transite vers `PROVISIONING_FAILED`,  
et ni le même jeton ni un renvoi de courriel ne permettent de reprendre le provisioning.

## AC17 — Vérification inter-appareils

Étant donné un jeton de vérification valide,  
quand il est présenté à `POST .../email-verifications` sans `Authorization`,  
alors TAKIBO complète la vérification normalement, y compris depuis un appareil différent de celui de la création.

## AC18 — Hachage exclusif à identity-core

`takibo-management-service` ne calcule, ne stocke ni ne journalise jamais de hash de mot de passe. Il transmet le mot de passe à un port synchrone de `takibo-identity-core` dès la création de la demande (`ORG-REG-01`) et ne conserve que la référence opaque retournée.

## AC19 — Organization jamais active sans Owner

Étant donné un incident survenant entre la création de l’Organization et l’attribution de `R_ORG_OWNER`,  
alors l’Organization reste en `PROVISIONING`, jamais `ACTIVE`, quelle que soit la durée de l’incident.

## AC20 — Preuve préservée malgré expiration

Étant donné une demande ayant atteint `EMAIL_VERIFIED`,  
quand `registration.expiresAt` est ensuite dépassé pendant une reprise,  
alors TAKIBO ne fait jamais revenir la demande à `EXPIRED` et poursuit la reprise normalement.

---

# 14. Tests obligatoires

## Tests unitaires

- capacité valide et invalide (pour `GET` et le renvoi uniquement) ;
- jeton valide sans capacité (endpoint de vérification) ;
- jeton mal formé ;
- jeton non applicable ;
- jeton expiré seul, demande active (reprise via renvoi) ;
- demande expirée (terminal) ;
- priorité de `registration.expiresAt` sur `verificationTokenExpiresAt` ;
- transition vers `VERIFIED` ;
- transitions interdites ;
- rejeu reconnu ;
- conservation du `reservedOrgCode` ;
- distinction entre rejeu du même jeton (`200`) et jeton différent (`409`) ;
- reprise réussie après un échec technique avec le même jeton, sans double création ;
- transition vers `PROVISIONING_FAILED` uniquement après épuisement de la politique de reprise ou échec métier irréversible ;
- refus de reprise d’une demande `PROVISIONING_FAILED` avec le même jeton ;
- préservation de `EMAIL_VERIFIED` malgré une expiration de `expiresAt` survenant pendant une reprise ;
- Organization jamais `ACTIVE` avant l’attribution confirmée de `R_ORG_OWNER` ;
- absence totale de calcul de hash côté `management-service` ;
- absence de création de `User` pendant le signup ;
- réponse nominale contenant `organizationId` et `accountId` ;
- absence de `spaceId`, `userId` et `userId` dans la réponse ;
- rejeu retournant le même `accountId` sans User situé.

## Tests d’intégration

- création complète de l’Organization, de l’Account et de `R_ORG_OWNER`, sans Space ni User ;
- rollback ou compensation sur chaque étape en échec ;
- contraintes d’unicité ;
- deux requêtes concurrentes ;
- rejeu après succès ;
- aucun état partiel actif.

## Tests de sécurité

- capacité obligatoire pour `GET` et le renvoi, absente par conception pour la vérification ;
- jeton lié à la demande ;
- limitation de fréquence sur la vérification (protection anti-brute-force) ;
- aucun fallback `SYSTEM_ACTOR_ID` ;
- aucun rôle `PLATFORM` ;
- aucun secret dans les logs ;
- aucune fuite d’existence par les erreurs.

## Tests du login

- login réussi avec le `orgCode` généré ;
- token `HUMAN` ;
- scope `ORGANIZATION` ;
- rôle `R_ORG_OWNER` ;
- absence de `space_id` au login organisation ;
- accès à `/api/v1/me/spaces` ;
- réponse `200 OK` avec `[]` lorsque l’organisation ne contient encore aucun Space.

## BVT

```text
201 - verify organization registration
201 - verify organization registration from a different device (no Authorization header)
200 - replay successful email verification
202 - accept verification when provisioning is still in progress (EMAIL_VERIFIED/PROVISIONING_RETRY_PENDING)
200 - read verification resource by its Location URI
400 - reject malformed verification token
404 - hide registration with invalid capability on GET
410 - reject verification on expired registration, before EMAIL_VERIFIED (terminal)
410 - reject verification on expired token only, registration still active (resend still possible)
422 - reject invalid verification token
409 - reject already completed registration with another proof
429 - rate limit verification attempts
202 - retry verification after transient technical failure, no duplicate created
409 - reject already PROVISIONING_FAILED registration (non-recoverable)
200 - login founder as organization owner
200 - list zero founder spaces after signup
```

---

# 15. Hors périmètre

Ce récit ne couvre pas :

- une console d’administration plateforme ;
- la création administrative directe avec `POST /api/v1/orgs` ;
- l’invitation d’autres utilisateurs ;
- toute création automatique de Space pendant le signup ;
- l’écran d’onboarding du premier Space ;
- la création volontaire du premier Space après login ;
- le changement du `orgCode` ;
- la suppression de l’Organization ;
- une authentification automatique après vérification ;
- la remise directe d’un token dans le lien de courriel.

---

# 16. Definition of Done

Le récit est terminé lorsque :

- l’endpoint de vérification est implémenté ;
- le provisioning est atomique ou correctement orchestré ;
- toutes les contraintes d’unicité sont présentes ;
- le rejeu est idempotent ;
- le `reservedOrgCode` devient le code définitif ;
- l’Account fondateur reçoit l’attribution organisationnelle `R_ORG_OWNER` ;
- la réponse nominale contient `organizationId` et `accountId` ;
- la réponse ne contient aucun `spaceId`, `userId` ou `userId` ;
- aucun Space ni User n’est créé pendant le signup ;
- l’état `Organization ACTIVE` avec zéro Space et zéro User est accepté ;
- `/api/v1/me/spaces` retourne `200 OK` avec une liste vide dans cet état ;
- le login produit un token ORGANIZATION véridique ;
- aucun token tenant n’est remis par la vérification ;
- les tests unitaires, d’intégration, de sécurité, de concurrence et BVT passent ;
- OpenAPI décrit les statuts et erreurs ;
- les événements d’audit sont corrélés et exempts de secrets ;
- le rejeu retourne exactement les mêmes `organizationId` et `accountId` que la première réponse réussie ;
- la distinction entre rejeu (`200`/`202`/`409` selon le sous-état) et conflit de jeton (`409`) est implémentée et testée ;
- la vérification du courriel fonctionne sans `registrationAccessToken`, y compris depuis un appareil différent de celui de la création ;
- `takibo-management-service` transmet le mot de passe à un port synchrone de `takibo-identity-core` et ne conserve qu’une référence opaque ;
- `EMAIL_VERIFIED` est une transition définitive, jamais annulée par une expiration ou une panne technique ultérieure ;
- un échec technique transite vers `PROVISIONING_RETRY_PENDING` et reste reprenable avec le même jeton ; seul un échec non récupérable transite vers `PROVISIONING_FAILED`, terminal ;
- l’Organization ne devient `ACTIVE` que dans la même étape atomique que la confirmation de `R_ORG_OWNER`, jamais avant ;
- `registration.expiresAt` prime sur `verificationTokenExpiresAt` avant `EMAIL_VERIFIED`, et devient sans effet après ;
- la section Space référence `ORG-SPACE-01` sans redéfinir route, rôle ou autorisation.
- la ressource de vérification est consultable via son URI `Location`.
