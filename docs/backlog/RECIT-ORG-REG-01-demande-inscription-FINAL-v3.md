# Récit ORG-REG-01 — Créer une demande publique d’inscription d’organisation

## Statut

À faire

## Domaine

Plan de contrôle `ORGANIZATION`

## Intention

En tant que visiteur public souhaitant créer une organisation TAKIBO,  
je veux soumettre le nom de mon organisation ainsi que les informations du fondateur,  
afin de créer une demande d’inscription temporaire et de recevoir un courriel de vérification,  
sans créer immédiatement un tenant actif et sans recevoir de token `PLATFORM`, `ORGANIZATION` ou `SPACE`.

---

## Décision REST

L’ancienne route orientée action :

```http
POST /api/v1/orgs/signup
```

est remplacée par la création d’une ressource :

```http
POST /api/v1/org-registrations
```

La route historique peut rester temporairement comme alias déprécié, mais elle doit déléguer au même cas d’usage applicatif.

---

## Doctrine TAKIBO

> Une inscription publique ne crée pas directement une organisation active.

> Elle crée une ressource temporaire `OrganizationRegistration`.

> Le visiteur choisit le nom métier de l’organisation. TAKIBO génère le `orgCode`.

> Le parcours public ne remet jamais un token de plateforme ou de tenant.

> Le signup ne crée aucun Space. Le premier Space est créé volontairement par le fondateur après son premier login `ORGANIZATION`.

> Le signup crée plus tard un `Account` au niveau de l’organisation, mais ne crée jamais de `User`.

> Un `User` est une identité située dans un Space. Il n’existe qu’au moment où un `Account` est inscrit dans ce Space.

## Modèle d’identité retenu

```text
Organization
└── Account
    ├── User dans Space A
    ├── User dans Space B
    └── User dans Space C
```

Règles :

- `Account` est global à l’organisation ;
- `Account` porte l’identité de connexion et les autorités organisationnelles ;
- `User` est situé dans un Space ;
- un même `Account` peut être associé à plusieurs `User`, un par Space ;
- le signup ne produit aucun `userId` ;
- la création ou l’inscription dans un Space produit le `userId` correspondant ;
- la contrainte attendue est conceptuellement `UNIQUE(account_id, space_id)`.

## Décision sur les Spaces

Le formulaire public reste limité à :

```text
nom de l’organisation
courriel du fondateur
mot de passe du fondateur
```

La demande d’inscription ne contient aucun nom, code ou paramètre de Space.

Après la vérification réalisée dans le récit suivant, l’état ci-dessous est valide et attendu :

```text
Organization : ACTIVE
Account      : ACTIVE
Rôle org     : R_ORG_OWNER
Spaces       : 0
Users        : 0
```

À ce stade :

```text
organizationId
accountId
```

existent.

La réponse finale du provisioning réalisé dans `ORG-REG-02` doit donc contenir explicitement :

```json
{
  "registrationId": "86993ad2-caf5-4c0a-870e-c55c68952cca",
  "status": "VERIFIED",
  "organizationId": "4c0c74c3-3119-4d7b-86bc-19d0260a58ca",
  "accountId": "3dcd571f-e02a-4ac6-aaeb-872c93e23771"
}
```

Elle peut également représenter l’organisation sous une structure enrichie, mais `accountId` reste obligatoire :

```json
{
  "registrationId": "86993ad2-caf5-4c0a-870e-c55c68952cca",
  "status": "VERIFIED",
  "organization": {
    "id": "4c0c74c3-3119-4d7b-86bc-19d0260a58ca",
    "name": "Acme Corporation",
    "orgCode": "acme-corporation-550e8400-e29b-41d4-a716-446655440000",
    "uri": "/api/v1/orgs/4c0c74c3-3119-4d7b-86bc-19d0260a58ca"
  },
  "accountId": "3dcd571f-e02a-4ac6-aaeb-872c93e23771"
}
```

En revanche :

```text
spaceId
userId
```

n’existent pas encore et ne doivent jamais apparaître dans cette réponse.

Le premier Space sera créé explicitement après le login avec une autorité située au niveau `ORGANIZATION`, par exemple :

```http
POST /api/v1/orgs/{orgId}/spaces
```

Cette création volontaire et son écran d’onboarding ne font pas partie du présent récit.

---

## Périmètre

Ce récit couvre :

1. la création de `OrganizationRegistration` ;
2. la génération du `orgCode` ;
3. la réservation du `orgCode` pendant la durée de la demande ;
4. la génération d’une capacité temporaire d’inscription ;
5. la génération d’un jeton de vérification du courriel ;
6. l’envoi initial du courriel ;
7. la consultation sécurisée de la demande ;
8. le renvoi contrôlé du courriel ;
9. l’expiration de la demande ;
10. la dépréciation de `/api/v1/orgs/signup`.

Ce récit ne crée pas encore :

```text
Organization
Account fondateur
R_ORG_OWNER
```

Le parcours d’inscription, y compris `ORG-REG-02`, ne crée aucun `User`.

Il ne crée également aucun Space, ni maintenant ni implicitement pour le récit suivant.

---

# 1. Créer la demande d’inscription

## Endpoint

```http
POST /api/v1/org-registrations
Content-Type: application/json
```

## Requête

```json
{
  "organization": {
    "name": "Acme Corporation"
  },
  "owner": {
    "email": "owner@acme.example",
    "password": "********"
  }
}
```

Le client ne fournit pas `orgCode`.

## Réponse nominale

```http
201 Created
Location: /api/v1/org-registrations/{registrationId}
Cache-Control: no-store
```

```json
{
  "registrationId": "86993ad2-caf5-4c0a-870e-c55c68952cca",
  "organizationName": "Acme Corporation",
  "reservedOrgCode": "acme-corporation-550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING_EMAIL_VERIFICATION",
  "expiresAt": "2026-07-27T20:00:00Z",
  "registrationAccessToken": "opaque-temporary-capability"
}
```

`201 Created` est utilisé parce que la ressource `OrganizationRegistration` existe immédiatement.

## Limitation de fréquence à la création

`POST /api/v1/org-registrations` applique deux mécanismes distincts, aux effets différents.

### Limitation par IP (bloquante)

Un volume excessif de créations depuis la même adresse IP est refusé :

```http
429 Too Many Requests
Retry-After: 60
```

```json
{
  "error": "org_registration_creation_rate_limited"
}
```

### Limitation par `owner.email` (non bloquante)

Un volume excessif de créations ciblant le même `owner.email` normalisé — potentiellement réparti sur plusieurs adresses IP — ne doit jamais bloquer la création de la ressource elle-même.

Bloquer la création reviendrait à permettre à un acteur malveillant de priver le véritable propriétaire de l’adresse de sa capacité à s’inscrire, en épuisant volontairement le quota associé à sa propre adresse.

Au regard du seul critère `owner.email`, TAKIBO retourne donc `201 Created`, quel que soit le volume déjà observé. Cette règle reste soumise à la validation du corps et à la limitation bloquante par IP.

Au-delà d’un seuil défini, TAKIBO continue de créer la demande mais **suspend uniquement l’envoi effectif du courriel de vérification** pour cet `owner.email`, pendant une fenêtre glissante :

```text
ORG_REGISTRATION_EMAIL_THROTTLED
```

Cette suspension n’est pas exposée dans la réponse publique de création : la réponse HTTP reste `201 Created` avec un statut `PENDING_EMAIL_VERIFICATION` inchangé.

Elle peut toutefois être consultée ultérieurement par le détenteur du `registrationAccessToken` au moyen du `GET` protégé, sous la forme de `verificationEmailStatus: THROTTLED`.

Le renvoi (`POST .../verification-emails`) est soumis à la même suspension d’envoi, indépendamment de sa propre limitation de fréquence par capacité.

Ce choix accepte un risque résiduel (un envoi légitime peut être retardé si l’adresse a été récemment inondée) plutôt qu’un déni de service certain : par ce mécanisme, le propriétaire réel de l’adresse ne peut jamais être empêché de créer une demande.

---

# 2. Génération du `orgCode`

TAKIBO génère le code selon la forme :

```text
{nom-normalisé}-{uuid}
```

Exemple :

```text
Acme Corporation
↓
acme-corporation-550e8400-e29b-41d4-a716-446655440000
```

## Règles de normalisation

Le générateur doit :

- convertir le nom en minuscules ;
- translittérer ou supprimer les accents ;
- remplacer les espaces et séparateurs par `-` ;
- supprimer les caractères interdits ;
- réduire les tirets consécutifs ;
- retirer les tirets en début et en fin ;
- utiliser `org` comme base de repli si le résultat est vide ;
- ajouter un UUID sans accolades.

## Invariants

- le nom métier n’est pas unique ;
- plusieurs organisations peuvent porter exactement le même nom ;
- le `orgCode` est unique ;
- le `orgCode` est généré exclusivement par TAKIBO ;
- le visiteur ne résout jamais une collision de code ;
- la base impose `UNIQUE(org_code)` ;
- une collision technique exceptionnelle entraîne une nouvelle génération interne.

Le contrat public doit refuser un `orgCode` fourni par le client :

```http
400 Bad Request
```

```json
{
  "error": "unknown_property",
  "property": "organization.orgCode"
}
```

---

# 3. Modèle et cycle de vie de la demande

La ressource doit porter au minimum :

```text
id
organizationName
reservedOrgCode
normalizedOwnerEmail
founderCredentialReference
founderCredentialExpiresAt
status
expiresAt
emailVerifiedAt
createdAt
updatedAt
registrationAccessTokenHash
registrationAccessTokenExpiresAt
verificationTokenHash
verificationTokenExpiresAt
verificationEmailStatus
verificationEmailLastSentAt
verificationEmailSendCount
```

## Statut initial

```text
PENDING_EMAIL_VERIFICATION
```

## États du cycle de vie

```text
PENDING_EMAIL_VERIFICATION
EMAIL_VERIFIED
PROVISIONING
PROVISIONING_RETRY_PENDING
VERIFIED
PROVISIONING_FAILED
EXPIRED
CANCELLED
```

## Machine d’état

```text
PENDING_EMAIL_VERIFICATION
        ├── preuve acceptée avant expiresAt
        │       ↓
        │  EMAIL_VERIFIED
        │       ↓
        │  PROVISIONING
        │       ├── succès complet
        │       │       ↓
        │       │    VERIFIED
        │       │
        │       ├── échec technique transitoire
        │       │       ↓
        │       │  PROVISIONING_RETRY_PENDING
        │       │       ↓ reprise idempotente
        │       │  PROVISIONING
        │       │
        │       └── échec irréversible ou reprises épuisées
        │               ↓
        │        PROVISIONING_FAILED
        │
        ├── expiresAt dépassé avant preuve
        │       ↓
        │    EXPIRED
        │
        └── annulation explicite future
                ↓
             CANCELLED
```

Seule une demande `PENDING_EMAIL_VERIFICATION` peut devenir `EXPIRED`.

## Responsabilité de ce récit

Le présent récit crée uniquement l’état :

```text
PENDING_EMAIL_VERIFICATION
```

Il peut également faire constater la transition temporelle :

```text
PENDING_EMAIL_VERIFICATION → EXPIRED
```

Les états suivants appartiennent principalement à `ORG-REG-02` :

```text
EMAIL_VERIFIED
PROVISIONING
PROVISIONING_RETRY_PENDING
VERIFIED
PROVISIONING_FAILED
```

`CANCELLED` est réservé à un récit futur d’annulation explicite.

## Sémantique des états

`EMAIL_VERIFIED` signifie que la preuve de contrôle du courriel a été acceptée et enregistrée durablement. Cette transition est définitive : la demande ne revient jamais à `PENDING_EMAIL_VERIFICATION`.

`PROVISIONING` signifie qu’une tentative de création de l’Organization, de l’Account fondateur et de l’attribution organisationnelle `R_ORG_OWNER` est en cours. Aucun `User` n’est créé dans cette saga.

`PROVISIONING_RETRY_PENDING` signifie qu’un incident technique ou transitoire empêche temporairement la fin du provisioning. La preuve de courriel reste acquise et TAKIBO reprend la saga de manière idempotente.

`PROVISIONING_FAILED` est terminal. Il n’est atteint qu’après épuisement de la politique interne de reprise ou face à un échec métier réellement irréversible. Une panne technique isolée ne produit jamais directement cet état.

`EXPIRED` ne peut être atteint que depuis `PENDING_EMAIL_VERIFICATION`, avant toute preuve acceptée.

`CANCELLED` représente une annulation explicite future. Il ne doit pas être utilisé comme synonyme d’expiration ou d’échec technique.

## Consultation selon l’état

Avec une capacité valide :

```text
PENDING_EMAIL_VERIFICATION non expirée
EMAIL_VERIFIED
PROVISIONING
PROVISIONING_RETRY_PENDING
VERIFIED
PROVISIONING_FAILED
CANCELLED
→ 200 OK avec le statut correspondant
```

```text
EXPIRED
→ 410 Gone
```

Une capacité absente ou invalide continue de produire une réponse uniforme `404 Not Found`.

## Renvoi du courriel

Le renvoi n’est autorisé que pour :

```text
PENDING_EMAIL_VERIFICATION
```

Tout autre état retourne :

```http
409 Conflict
```

```json
{
  "error": "registration_not_pending",
  "status": "EMAIL_VERIFIED"
}
```

## Nouvelle demande distincte

Une nouvelle requête `POST /api/v1/org-registrations` crée toujours une nouvelle ressource indépendante. Elle ne rouvre, ne remplace et ne réinitialise jamais une demande existante.

Elle peut être utilisée après un état terminal tel que :

```text
EXPIRED
CANCELLED
PROVISIONING_FAILED
```

Elle ne doit pas être utilisée comme mécanisme de reprise d’une demande encore :

```text
EMAIL_VERIFIED
PROVISIONING
PROVISIONING_RETRY_PENDING
```

La reprise de ces états appartient à la saga de `ORG-REG-02`.

Le même courriel peut légitimement participer à plusieurs organisations distinctes ; ni le nom de l’organisation ni `owner.email` ne constituent donc une clé de déduplication métier globale.

# 4. Protection du mot de passe et cycle de vie de la crédentialité

Le mot de passe du fondateur :

- ne doit jamais être journalisé ;
- ne doit jamais être stocké en clair ni sous une forme réversible par `takibo-management-service` ;
- ne doit jamais être placé dans un événement ;
- doit respecter la politique de mot de passe TAKIBO.

Le récit est refusé si un mot de passe en clair, réversible ou un hash de mot de passe apparaît dans la base, les logs, les événements ou les réponses de `takibo-management-service`.

## Préparation exclusive par Identity Core

Dès la création de la demande, dans le même appel synchrone, `takibo-management-service` transmet le mot de passe à un port interne appartenant à `takibo-identity-core`.

`takibo-identity-core` :

- valide la politique de mot de passe ;
- prépare et protège la crédentialité selon son format interne ;
- stocke la préparation dans un coffre ou composant qu’il contrôle ;
- retourne uniquement une référence opaque.

`takibo-management-service` conserve seulement :

```text
founderCredentialReference
```

Il ne connaît :

```text
ni l’algorithme
ni la version
ni le coût
ni le sel
ni le format du hash
ni le contenu protégé
```

Le mot de passe en clair n’est jamais persisté. Après le retour du port Identity Core, il n’est plus manipulé par le parcours d’inscription.

## Cycle de vie de `founderCredentialReference`

La référence :

- appartient exclusivement à un coffre ou port contrôlé par `takibo-identity-core` ;
- est liée au `registrationId` ;
- est opaque pour `takibo-management-service` ;
- n’est jamais exposée dans une réponse, un log ou un événement ;
- est utilisable uniquement pour créer l’Account fondateur associé à cette inscription ;
- est consommée de manière idempotente : une reprise ne peut jamais créer un deuxième compte ;
- possède une expiration initiale alignée sur la phase publique de la demande.

Avant la preuve de courriel :

```text
PENDING_EMAIL_VERIFICATION
→ la référence expire au plus tard avec registration.expiresAt
```

Après la preuve acceptée :

```text
EMAIL_VERIFIED
PROVISIONING
PROVISIONING_RETRY_PENDING
→ Identity Core prolonge ou renouvelle sa rétention interne
  suffisamment pour permettre les reprises de provisioning
```

Cette prolongation ne rend jamais le secret lisible par `management-service`.

## Suppression et révocation

La préparation est supprimée ou rendue définitivement inutilisable après :

```text
VERIFIED
EXPIRED
CANCELLED
PROVISIONING_FAILED
```

Après création réussie de l’Account, une reprise du provisioning retrouve le même Account par idempotence et ne réutilise pas la crédentialité pour créer un doublon. Cette crédentialité ne sert jamais à créer un `User` de Space.

Une tâche de nettoyage doit supprimer les préparations orphelines dont la demande associée n’existe plus ou a dépassé sa politique de rétention.

# 5. Capacité temporaire d’inscription

`registrationAccessToken` autorise uniquement les opérations privées liées à la demande concernée.

Cette capacité :

- n’est pas un token `PLATFORM` ;
- n’est pas un token `ORGANIZATION` ;
- n’est pas un token `SPACE` ;
- ne porte aucun rôle tenant ;
- ne donne aucun accès aux autres API ;
- est liée au `registrationId` ;
- protège la consultation de la demande et le renvoi du courriel ;
- ne conditionne jamais la vérification du courriel.

Le `registrationId` seul n’est pas une preuve d’autorisation.

La capacité opaque ne doit pas être persistée en clair. TAKIBO conserve uniquement son empreinte sécurisée.

## Durée de vie

Avant la preuve de courriel, la capacité ne reste pas valide au-delà de la phase publique de la demande.

Après `EMAIL_VERIFIED`, si TAKIBO souhaite permettre la consultation des états de provisioning avec cette même capacité, sa validité peut être prolongée selon une politique de rétention explicite. Cette prolongation :

- ne lui confère aucune autorité tenant ;
- ne permet aucun renvoi de courriel hors `PENDING_EMAIL_VERIFICATION` ;
- ne participe pas au provisioning ;
- ne permet pas de recréer la capacité si elle a été perdue.

## Perte de la capacité

Aucune API ne permet de retrouver ou de réémettre un `registrationAccessToken` perdu.

Deux situations doivent être distinguées.

### Courriel déjà reçu

```text
capacité perdue
+ jeton de vérification reçu par courriel
→ vérification toujours possible
→ aucune nouvelle inscription nécessaire
```

Le jeton reçu par courriel suffit pour appeler l’endpoint de vérification depuis n’importe quel appareil.

### Courriel non reçu

```text
capacité perdue
+ aucun jeton de vérification disponible
→ consultation impossible
→ renvoi impossible
→ une nouvelle demande distincte peut devenir nécessaire
```

La nouvelle demande n’annule pas automatiquement l’ancienne. L’ancienne suit son cycle de vie normal jusqu’à `EXPIRED` ou un autre état terminal.

## Portée volontairement limitée

`registrationAccessToken` protège :

```http
GET /api/v1/org-registrations/{registrationId}
POST /api/v1/org-registrations/{registrationId}/verification-emails
```

Il n’est jamais requis pour :

```http
POST /api/v1/org-registrations/{registrationId}/email-verifications
```

Le jeton de vérification à usage unique constitue à lui seul la preuve nécessaire à cette opération.

# 6. Consulter la demande

## Endpoint

```http
GET /api/v1/org-registrations/{registrationId}
Authorization: Bearer {registrationAccessToken}
```

## Réponse nominale

```http
200 OK
Cache-Control: no-store
```

```json
{
  "registrationId": "86993ad2-caf5-4c0a-870e-c55c68952cca",
  "organizationName": "Acme Corporation",
  "reservedOrgCode": "acme-corporation-550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING_EMAIL_VERIFICATION",
  "verificationEmailStatus": "THROTTLED",
  "expiresAt": "2026-07-27T20:00:00Z"
}
```

## `verificationEmailStatus`

Le statut de livraison est visible uniquement par le détenteur de la capacité.

Valeurs prévues :

```text
QUEUED
SENT
THROTTLED
DELIVERY_FAILED
```

Il décrit la dernière situation connue de livraison et ne modifie pas le statut métier `PENDING_EMAIL_VERIFICATION`.

L’exposition de `THROTTLED` dans ce `GET` évite que le visiteur attende indéfiniment alors que TAKIBO a suspendu l’envoi. Cette information n’est pas retournée par le `POST` public initial.

## Données interdites

La réponse ne doit jamais exposer :

- le mot de passe ou son hash ;
- `founderCredentialReference` ;
- le jeton de vérification ;
- le hash d’un jeton ;
- la capacité temporaire ;
- les données d’une autre demande ;
- la cause technique interne d’un échec de provisioning.

## Capacité invalide

```http
404 Not Found
```

La réponse uniforme évite de confirmer l’existence de la demande.

## Demande expirée avant preuve

```http
410 Gone
```

```json
{
  "error": "org_registration_expired",
  "status": "EXPIRED"
}
```

Seule une demande ayant expiré depuis `PENDING_EMAIL_VERIFICATION` produit cette réponse.

Les états `EMAIL_VERIFIED`, `PROVISIONING`, `PROVISIONING_RETRY_PENDING`, `VERIFIED`, `PROVISIONING_FAILED` et `CANCELLED` restent consultables en `200 OK` avec une capacité encore valide.

# 7. Envoyer le courriel initial

Après la création de la demande, TAKIBO :

1. génère un jeton de vérification à usage unique ;
2. lie ce jeton à `registrationId` ;
3. fixe sa date d’expiration ;
4. conserve uniquement une empreinte sécurisée ;
5. demande la livraison du courriel ;
6. produit un événement d’audit.

Le jeton de vérification ne porte aucune autorité tenant.

Un échec de livraison ne doit jamais créer l’organisation.

---

# 8. Renvoyer le courriel

## Endpoint

```http
POST /api/v1/org-registrations/{registrationId}/verification-emails
Authorization: Bearer {registrationAccessToken}
```

## Réponse nominale avec suivi de livraison

```http
201 Created
Location: /api/v1/org-registrations/{registrationId}/verification-emails/{deliveryId}
```

```json
{
  "deliveryId": "4043787a-1421-48dc-b5eb-efca0fec6adf",
  "status": "QUEUED",
  "requestedAt": "2026-07-26T19:30:00Z"
}
```

Lorsque la livraison est suspendue par la politique anti-abus sur `owner.email`, la ressource de livraison peut porter :

```json
{
  "deliveryId": "4043787a-1421-48dc-b5eb-efca0fec6adf",
  "status": "THROTTLED",
  "requestedAt": "2026-07-26T19:30:00Z"
}
```

## Règles

- seule la capacité liée à la demande peut demander le renvoi ;
- seul l’état `PENDING_EMAIL_VERIFICATION` autorise le renvoi ;
- une demande `EXPIRED` retourne `410 Gone` ;
- un état non pending retourne `409 Conflict` ;
- un nouvel envoi invalide tous les anciens jetons non consommés ;
- un seul jeton de vérification reste actif ;
- le renvoi est soumis à une limitation de fréquence par capacité et à la suspension non bloquante par `owner.email`.

## Horloge d’expiration

`expiresAt` limite exclusivement le délai permettant d’apporter une preuve de contrôle du courriel.

Le renvoi ne prolonge jamais `expiresAt`.

Avant la preuve, deux horloges coexistent :

```text
registration.expiresAt
→ limite absolue pour accepter une preuve

verificationTokenExpiresAt
→ limite propre au jeton actuellement actif
```

## Ordre de priorité avant preuve

```text
status = PENDING_EMAIL_VERIFICATION
et registration.expiresAt dépassé
→ transition vers EXPIRED
→ 410 Gone
→ error: org_registration_expired
→ aucun renvoi possible
```

```text
status = PENDING_EMAIL_VERIFICATION
registration.expiresAt non dépassé
verificationTokenExpiresAt dépassé
→ la demande reste PENDING_EMAIL_VERIFICATION
→ le jeton est caduc
→ 410 Gone sur la vérification
→ error: verification_token_expired
→ un renvoi reste possible
```

## Après la preuve acceptée

Dès que la demande atteint `EMAIL_VERIFIED` :

- `expiresAt` n’a plus aucun effet sur cette demande ;
- la preuve ne peut jamais être annulée par le temps ;
- la demande ne peut jamais revenir à `PENDING_EMAIL_VERIFICATION` ;
- la demande ne peut jamais devenir `EXPIRED` ;
- les reprises de provisioning restent possibles après dépassement de l’ancienne date.

Exemple :

```text
preuve acceptée à 11:59
incident technique à 12:00
ancienne expiresAt dépassée à 12:01
→ preuve toujours acquise
→ reprise du provisioning
```

## Nouvelle fenêtre d’inscription

Une nouvelle demande possède toujours son propre `registrationId`, son propre `orgCode`, ses propres jetons et sa propre fenêtre `expiresAt`.

Elle peut être soumise après un état terminal comme `EXPIRED`, `CANCELLED` ou `PROVISIONING_FAILED`.

Elle ne constitue jamais une reprise technique d’un état `EMAIL_VERIFIED`, `PROVISIONING` ou `PROVISIONING_RETRY_PENDING`.

## Limitation de fréquence

```http
429 Too Many Requests
Retry-After: 60
```

```json
{
  "error": "verification_email_rate_limited"
}
```

# 9. Dépréciation de l’ancienne route

```http
POST /api/v1/orgs/signup
```

peut être conservée temporairement comme alias de compatibilité.

Elle doit :

- être documentée comme dépréciée ;
- appeler le même cas d’usage que `POST /api/v1/org-registrations` ;
- retourner le nouveau contrat ;
- ne contenir aucune logique métier parallèle ;
- disparaître des nouveaux tests, de la nouvelle UI et des nouvelles collections Postman.

---

# 10. Sécurité et politique d’accès

```text
POST /api/v1/org-registrations
→ public et validé
→ limitation bloquante par IP
→ throttling de livraison non bloquant par owner.email

GET /api/v1/org-registrations/{id}
→ capacité correspondante requise

POST /api/v1/org-registrations/{id}/verification-emails
→ capacité correspondante requise
```

Aucune absence d’authentification ne doit provoquer un fallback vers `SYSTEM_ACTOR_ID`.

Les événements pré-tenant utilisent `registrationId` comme corrélation. Ils ne doivent pas inventer de faux `orgId`.

---

# 11. Audit minimal

Événements attendus :

```text
ORG_REGISTRATION_CREATED
ORG_REGISTRATION_CREATION_RATE_LIMITED
ORG_REGISTRATION_EMAIL_THROTTLED
ORG_REGISTRATION_EMAIL_QUEUED
ORG_REGISTRATION_EMAIL_SENT
ORG_REGISTRATION_EMAIL_DELIVERY_FAILED
ORG_REGISTRATION_EMAIL_RESENT
ORG_REGISTRATION_RESEND_REJECTED_NOT_PENDING
ORG_REGISTRATION_RATE_LIMITED
ORG_REGISTRATION_EXPIRED
```

`ORG_REGISTRATION_CREATION_RATE_LIMITED` couvre uniquement la limitation par IP (requête refusée).

`ORG_REGISTRATION_EMAIL_THROTTLED` couvre la suspension d’envoi par `owner.email` : la requête HTTP reste `201 Created`, seul l’envoi du courriel est différé.

Aucun événement ne doit contenir :

```text
mot de passe
hash du mot de passe
registrationAccessToken
jeton de vérification complet
hash d’un jeton
```

---

# 12. Critères d’acceptation

## AC1 — Création nominale

Étant donné un nom, un courriel et un mot de passe valides,  
quand le visiteur appelle `POST /api/v1/org-registrations`,  
alors TAKIBO retourne `201 Created`,  
et la demande est `PENDING_EMAIL_VERIFICATION`.

## AC2 — Code généré

Étant donné le nom `Acme Corporation`,  
quand la demande est créée,  
alors le code commence par `acme-corporation-`  
et se termine par un UUID valide.

## AC3 — Noms identiques permis

Étant donné deux demandes portant le même nom,  
quand elles sont créées,  
alors les deux réussissent  
et leurs `orgCode` sont différents.

## AC4 — Code client interdit

Étant donné une requête contenant `organization.orgCode`,  
quand elle est soumise,  
alors TAKIBO retourne `400 Bad Request`  
et ne crée aucune demande.

## AC5 — Aucun tenant prématuré

Étant donné une demande non vérifiée,  
alors aucune Organization, aucun Account et aucun `R_ORG_OWNER` ne sont créés.

Aucun Space et aucun `User` ne sont créés par le parcours d’inscription.

## AC5A — Réponse finale avec Account

Étant donné une vérification et un provisioning réussis dans `ORG-REG-02`,  
alors TAKIBO retourne obligatoirement `organizationId` et `accountId`.

La réponse ne contient ni `spaceId` ni `userId`.

Le `userId` sera créé uniquement lorsque cet Account sera inscrit dans un Space.

## AC6 — Aucun token tenant

La réponse ne contient aucun token `PLATFORM`, `ORGANIZATION` ou `SPACE`.

## AC7 — Consultation protégée

Une consultation sans capacité valide ne révèle pas la demande.

## AC8 — Expiration uniquement avant preuve

Étant donné une demande au statut `PENDING_EMAIL_VERIFICATION` dont `expiresAt` est dépassé,  
quand elle est consultée ou vérifiée,  
alors elle devient `EXPIRED` et TAKIBO retourne `410 Gone`.

Aucun autre statut ne devient `EXPIRED`.

## AC9 — Renvoi sécurisé

Un renvoi effectué sur une demande pending produit un nouveau jeton et invalide les précédents jetons non consommés.

## AC10 — Secrets absents

Aucun secret, hash, `founderCredentialReference` ou jeton complet n’apparaît dans les logs, événements ou réponses.

## AC11 — États postérieurs à la preuve consultables

Étant donné une demande au statut `EMAIL_VERIFIED`, `PROVISIONING`, `PROVISIONING_RETRY_PENDING`, `VERIFIED`, `PROVISIONING_FAILED` ou `CANCELLED`,  
quand elle est consultée avec une capacité valide,  
alors TAKIBO retourne `200 OK` avec le statut correspondant.

## AC12 — Renvoi refusé hors pending

Étant donné une demande dans tout état autre que `PENDING_EMAIL_VERIFICATION`,  
quand un renvoi est demandé,  
alors TAKIBO retourne `409 Conflict`, sauf pour `EXPIRED` qui retourne `410 Gone`.

## AC13 — Limitation bloquante par IP

Étant donné plus de N créations provenant de la même IP dans la fenêtre définie,  
quand une nouvelle demande est soumise depuis cette IP,  
alors TAKIBO retourne `429 Too Many Requests` sans créer de demande.

## AC14 — Capacité perdue avec courriel reçu

Étant donné une capacité perdue mais un jeton de vérification reçu par courriel,  
quand ce jeton est présenté sans `registrationAccessToken`,  
alors la vérification reste possible et aucune nouvelle inscription n’est nécessaire.

## AC15 — Capacité perdue sans courriel

Étant donné une capacité perdue et aucun jeton de vérification disponible,  
alors la consultation et le renvoi sont impossibles.

Une nouvelle demande distincte peut devenir nécessaire, sans annuler automatiquement l’ancienne.

## AC16 — Priorité des deux horloges avant preuve

Étant donné une demande encore pending dont `registration.expiresAt` est dépassé,  
alors TAKIBO retourne `org_registration_expired`, quel que soit l’état du jeton.

Étant donné une demande pending encore active dont seul `verificationTokenExpiresAt` est dépassé,  
alors TAKIBO retourne `verification_token_expired` sur la vérification et autorise encore un renvoi.

## AC17 — Ciblage d’un courriel non bloquant

Étant donné plus de N créations ciblant le même `owner.email` depuis plusieurs IP,  
quand une nouvelle demande valide est soumise sans dépasser la limite IP,  
alors TAKIBO retourne `201 Created` et peut placer `verificationEmailStatus` à `THROTTLED`.

## AC18 — Vérification multi-appareil

Étant donné un jeton valide reçu par courriel,  
quand il est présenté sans capacité depuis un autre appareil,  
alors TAKIBO accepte la preuve normalement.

## AC19 — Preuve durable

Étant donné une demande ayant atteint `EMAIL_VERIFIED`,  
quand l’ancienne valeur `expiresAt` est ensuite dépassée,  
alors TAKIBO ne revient jamais à `EXPIRED` et poursuit ou reprend le provisioning.

## AC20 — Crédentialité sous propriété Identity Core

Étant donné une demande créée,  
alors `management-service` ne conserve que `founderCredentialReference`,  
et Identity Core reste l’unique propriétaire du secret préparé et du hachage.

## AC21 — Nettoyage de la crédentialité

Étant donné une demande devenue `VERIFIED`, `EXPIRED`, `CANCELLED` ou `PROVISIONING_FAILED`,  
alors la préparation temporaire est supprimée ou définitivement révoquée.

## AC22 — Rétention après preuve

Étant donné une demande `EMAIL_VERIFIED`, `PROVISIONING` ou `PROVISIONING_RETRY_PENDING`,  
alors la crédentialité préparée reste disponible suffisamment longtemps pour permettre une reprise idempotente.

## AC23 — Statut de livraison visible

Étant donné une capacité valide,  
quand le visiteur consulte sa demande,  
alors le `GET` expose `verificationEmailStatus` avec une valeur parmi `QUEUED`, `SENT`, `THROTTLED` ou `DELIVERY_FAILED`.

## AC24 — Nouvelle demande indépendante

Étant donné une demande terminale,  
quand le visiteur soumet une nouvelle inscription,  
alors TAKIBO crée une nouvelle ressource indépendante sans réouvrir l’ancienne.

# 13. Tests obligatoires

## Tests unitaires

- normalisation du nom ;
- accents et caractères spéciaux ;
- base vide après normalisation ;
- génération `{slug}-{uuid}` ;
- noms identiques avec codes distincts ;
- validation de la politique de mot de passe par Identity Core ;
- transition `PENDING_EMAIL_VERIFICATION → EXPIRED` ;
- impossibilité d’expirer après `EMAIL_VERIFIED` ;
- validation et perte de la capacité ;
- rotation du jeton ;
- refus du renvoi hors pending ;
- priorité de `registration.expiresAt` avant preuve ;
- expiration isolée du jeton ;
- throttling de livraison par courriel ;
- lecture de `verificationEmailStatus` ;
- création acceptée malgré un volume élevé sur le même courriel ;
- création d’une nouvelle ressource après un état terminal.

## Tests de la crédentialité temporaire

- transmission synchrone au port Identity Core ;
- aucune persistance locale du mot de passe ou de son hash ;
- référence opaque liée au `registrationId` ;
- référence absente des réponses, logs et événements ;
- consommation idempotente ;
- rétention prolongée après `EMAIL_VERIFIED` ;
- nettoyage après `VERIFIED` ;
- nettoyage après `EXPIRED` ;
- nettoyage après `CANCELLED` ;
- nettoyage après `PROVISIONING_FAILED` ;
- nettoyage des références orphelines.

## Tests du modèle Account/User

- aucun `User` créé lors de `ORG-REG-01` ;
- aucun `User` créé lors du provisioning `ORG-REG-02` ;
- réponse finale de provisioning contenant obligatoirement `organizationId` et `accountId` ;
- présence explicite de `accountId` dans le corps retourné par `ORG-REG-02` ;
- absence de `spaceId` et de `userId` après signup ;
- impossibilité de produire un `User` sans `spaceId` ;
- préparation de la contrainte future `UNIQUE(account_id, space_id)` lors de l’inscription dans un Space.

## Tests de persistance

- unicité de `org_code` ;
- persistance du statut et des horloges ;
- persistance de `verificationEmailStatus` ;
- absence de secret en clair ;
- concurrence sur deux créations ;
- nouvelle génération après collision forcée.

## Tests REST

```text
201 - create organization registration
200 - read organization registration with verificationEmailStatus
200 - read EMAIL_VERIFIED, PROVISIONING_RETRY_PENDING, VERIFIED, PROVISIONING_FAILED or CANCELLED
201 - create registration despite email flood with email delivery throttled
400 - reject supplied orgCode
404 - hide registration without capability
409 - reject resend on non-pending registration
410 - reject expired pending registration
410 - reject expired token on active pending registration
429 - rate limit organization registration creation by IP
201 - resend verification email
201 - create throttled verification email delivery
429 - rate limit verification email resend
```

## Tests de politique

- création accessible sans token tenant ;
- consultation inaccessible sans capacité ;
- renvoi inaccessible sans capacité ;
- vérification acceptée sans capacité depuis le même appareil ;
- vérification acceptée sans capacité depuis un autre appareil ;
- aucun fallback vers `SYSTEM_ACTOR_ID` ;
- limitation IP bloquante ;
- throttling par courriel non bloquant ;
- aucune information de livraison exposée sans capacité.

## BVT

```text
201 - create organization registration
200 - read pending registration and email delivery status
404 - hide registration without capability
400 - reject supplied orgCode
429 - rate limit registration creation by IP
201 - create registration while email dispatch is throttled
201 - resend verification email
409 - reject resend outside pending status
410 - reject expired pending registration
```

# 14. Hors périmètre

Ce récit ne réalise pas :

- la consommation du jeton ;
- la création de l’Organization ;
- la création de l’Account fondateur ;
- l’attribution organisationnelle de `R_ORG_OWNER` ;
- la création d’un `User` de Space ;
- toute création automatique ou implicite de Space ;
- l’onboarding de création volontaire du premier Space après login ;
- le premier login ;
- l’émission d’un token tenant ;
- la suppression définitive de l’ancienne route.

---

# 15. Definition of Done

Le récit est terminé lorsque :

- les trois endpoints du périmètre sont implémentés ;
- la migration de base est versionnée ;
- le `orgCode` est généré exclusivement par TAKIBO ;
- aucune donnée secrète n’est stockée ou journalisée en clair ;
- les politiques sont fail-closed ;
- l’ancienne route délègue au nouveau cas d’usage et est dépréciée ;
- OpenAPI décrit les contrats, erreurs et statuts de livraison ;
- aucune ressource tenant n’existe avant la preuve de courriel ;
- après provisioning, la réponse contient explicitement `organizationId` et `accountId` ;
- `accountId` est obligatoire dans le contrat de sortie de `ORG-REG-02` ;
- aucun `spaceId` ni `userId` n’est créé ou retourné par le signup ;
- `Account` est reconnu comme identité globale à l’organisation ;
- `User` est reconnu comme identité située dans un Space ;
- aucun Space n’est créé ou demandé par le signup ;
- la machine d’état complète est documentée et testée ;
- seule une demande `PENDING_EMAIL_VERIFICATION` peut devenir `EXPIRED` ;
- `EMAIL_VERIFIED` constitue une preuve durable ;
- les incidents techniques conduisent à une reprise via `PROVISIONING_RETRY_PENDING`, pas à un retour vers pending ;
- `PROVISIONING_FAILED` est réservé aux échecs terminaux ;
- le renvoi est interdit hors pending ;
- la limitation par IP est bloquante ;
- le throttling par `owner.email` ne bloque pas la création ;
- le `GET` protégé expose `verificationEmailStatus` ;
- la vérification ne requiert jamais `registrationAccessToken` ;
- la vérification fonctionne depuis un autre appareil ;
- la perte de capacité n’empêche pas la vérification lorsque le courriel a été reçu ;
- une nouvelle demande est une ressource indépendante et ne réouvre jamais l’ancienne ;
- `takibo-identity-core` est l’unique propriétaire de la crédentialité et du hachage ;
- `takibo-management-service` ne conserve que `founderCredentialReference` ;
- la référence est idempotente, temporaire, non exposée et correctement nettoyée ;
- sa rétention est prolongée après preuve pour permettre les reprises ;
- un renvoi ne prolonge jamais `expiresAt` ;
- `registration.expiresAt` prime sur l’expiration du jeton uniquement avant preuve ;
- après `EMAIL_VERIFIED`, l’ancienne valeur `expiresAt` devient sans effet ;
- les tests unitaires, d’intégration, de persistance, de politique et BVT passent.
