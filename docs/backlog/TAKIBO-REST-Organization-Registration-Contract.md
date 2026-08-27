# TAKIBO — Contrat REST d’inscription et de création d’une organisation

**Statut :** Proposition consolidée  
**Date :** 26 juillet 2026  
**Périmètre :** inscription publique, vérification du courriel, création administrative directe et premier login du fondateur

---

## 1. Décision d’architecture

TAKIBO distingue deux parcours qui ne portent pas la même autorité.

### Parcours public

Un visiteur ne crée pas directement une organisation active.

Il crée une **demande d’inscription d’organisation**. Après vérification du courriel, TAKIBO provisionne atomiquement l’organisation, le compte et l’utilisateur fondateur, son rôle `R_ORG_OWNER` et le Space initial.

```text
OrganizationRegistration
        ↓
EmailVerification
        ↓
Organization + Account + Founder User + R_ORG_OWNER + Initial Space
        ↓
POST /api/v1/auth/login
        ↓
Premier token HUMAN / ORGANIZATION
```

### Parcours administratif

Une autorité interne ou administrative TAKIBO peut créer directement une organisation avec :

```http
POST /api/v1/orgs
```

Cette route n’est jamais utilisée par le formulaire public.

---

## 2. Loi REST retenue

Une URI représente une ressource. La méthode HTTP exprime l’opération.

L’ancienne route :

```http
POST /api/v1/orgs/signup
```

fonctionne techniquement, mais elle exprime une action RPC avec le mot `signup`.

La route REST retenue pour le parcours public est :

```http
POST /api/v1/org-registrations
```

La loi TAKIBO devient :

> On ne lance pas une action `signup` sur une organisation.  
> On crée une demande d’inscription qui peut ensuite produire une organisation active.

---

# 3. Création publique d’une demande d’inscription

## Requête

```http
POST /api/v1/org-registrations
Content-Type: application/json
```

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

Le demandeur fournit :

- le nom métier de l’organisation ;
- l’adresse électronique du fondateur ;
- le mot de passe initial du fondateur.

Le demandeur ne choisit jamais le `orgCode`.

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

Le code correct est `201 Created`, car la ressource `OrganizationRegistration` existe dès la réponse.

`202 Accepted` ne serait approprié que si TAKIBO acceptait une commande asynchrone sans créer immédiatement la ressource représentant la demande.

---

# 4. Génération du `orgCode`

## Règle

Le `orgCode` est généré exclusivement par TAKIBO selon la forme :

```text
{nom-normalisé}-{uuid}
```

Exemple :

```text
Nom métier :
Acme Corporation

Nom normalisé :
acme-corporation

UUID :
550e8400-e29b-41d4-a716-446655440000

orgCode :
acme-corporation-550e8400-e29b-41d4-a716-446655440000
```

## Conséquence métier

Plusieurs organisations peuvent avoir le même nom :

```text
Acme Corporation
Acme Corporation
Acme Corporation
```

Elles reçoivent cependant des codes techniques différents :

```text
acme-corporation-550e8400-e29b-41d4-a716-446655440000
acme-corporation-a18c735c-1225-46c9-92f9-0ccbfa75dd65
acme-corporation-f41c76ac-ec79-4e90-8c95-f4a91d22099c
```

Le nom est une donnée métier non nécessairement unique.

Le `orgCode` est un identifiant technique unique.

## Normalisation

Le générateur applique notamment les règles suivantes :

```text
minuscules uniquement
suppression ou translittération des accents
espaces remplacés par des tirets
suppression des caractères non autorisés
réduction des tirets consécutifs
suppression des tirets en début et fin
ajout d’un UUID sans accolades
```

Exemple :

```text
École du Fleuve inc.
↓
ecole-du-fleuve-inc-550e8400-e29b-41d4-a716-446655440000
```

## Contrainte de base de données

TAKIBO conserve une contrainte unique :

```sql
UNIQUE (org_code)
```

Le UUID rend la collision extrêmement improbable, mais la base de données reste l’autorité finale.

En cas de collision technique exceptionnelle, TAKIBO régénère automatiquement un nouvel UUID. Le visiteur ne reçoit pas un conflit qu’il devrait résoudre.

## Champ interdit dans la requête publique

Le contrat public n’accepte pas :

```json
{
  "organization": {
    "name": "Acme Corporation",
    "orgCode": "code-choisi-par-le-client"
  }
}
```

La préférence est de refuser une propriété non autorisée :

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

# 5. Réservation du `orgCode`

Le `orgCode` est généré et réservé lors de la création de la demande d’inscription.

```text
PENDING_EMAIL_VERIFICATION
→ orgCode réservé

VERIFIED
→ orgCode attribué définitivement à Organization

EXPIRED
→ réservation libérée ou archivée selon la politique TAKIBO
```

La réservation garantit que le même code est conservé entre la demande initiale et la création définitive de l’organisation.

Il n’existe donc pas de réponse publique :

```http
409 Conflict
```

```json
{
  "error": "org_code_already_registered"
}
```

Une collision de `orgCode` est un problème interne de génération et de concurrence, pas un problème métier exposé au visiteur.

---

# 6. Capacité temporaire d’inscription

La réponse de création contient un `registrationAccessToken`.

Ce jeton est une capacité temporaire strictement limitée au parcours d’inscription.

Il :

- n’est pas un token `PLATFORM` ;
- n’est pas un token `ORGANIZATION` ;
- n’est pas un token `SPACE` ;
- ne permet aucun accès aux API métier tenant ;
- permet uniquement de consulter ou poursuivre l’inscription concernée ;
- expire avec la demande d’inscription.

Le jeton doit être opaque ou fortement signé, à durée de vie limitée et lié au `registrationId`.

Il ne doit pas être persisté en clair côté serveur. TAKIBO conserve uniquement une empreinte sécurisée lorsque le modèle choisi le permet.

Un UUID aléatoire ne constitue pas à lui seul un mécanisme d’autorisation.

---

# 7. Consultation de la demande d’inscription

## Requête

```http
GET /api/v1/org-registrations/{registrationId}
Authorization: Bearer {registrationAccessToken}
```

## Réponse

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
  "expiresAt": "2026-07-27T20:00:00Z"
}
```

Avant vérification, l’API ne doit pas exposer :

- le mot de passe ;
- le jeton de vérification ;
- un secret interne ;
- une adresse électronique complète si elle n’est pas nécessaire ;
- les données d’une autre inscription.

## Capacité absente ou invalide

TAKIBO peut retourner une réponse uniforme :

```http
404 Not Found
```

afin de ne pas confirmer l’existence de l’inscription.

## Inscription expirée

```http
410 Gone
```

```json
{
  "error": "org_registration_expired",
  "status": "EXPIRED"
}
```

`410 Gone` indique que la ressource a existé, mais qu’elle n’est plus utilisable.

---

# 8. Vérification du courriel

## Requête

```http
POST /api/v1/org-registrations/{registrationId}/email-verifications
Authorization: Bearer {registrationAccessToken}
Content-Type: application/json
```

```json
{
  "token": "one-time-email-verification-token"
}
```

Le jeton de vérification est placé dans le corps de la requête, pas dans l’URI de l’API, afin de limiter son exposition dans les journaux, historiques et systèmes intermédiaires.

Le lien reçu par courriel peut ouvrir une page TAKIBO qui extrait le jeton, puis appelle cette API.

---

# 9. Provisioning atomique

Lors de la première vérification réussie, TAKIBO crée atomiquement :

```text
Organization
Account du fondateur
User fondateur
Attribution de R_ORG_OWNER
Space initial
EmailVerification
```

L’ensemble doit être couvert par une transaction ou par une orchestration garantissant qu’aucun état partiel mensonger ne demeure visible.

## Réponse nominale

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
  "accountId": "746b85b6-3392-4b79-905a-7cd53194f63d",
  "ownerUserId": "d65f04b7-b9d4-49b2-a7ec-cb312bf34714",
  "initialSpaceId": "0c801fd0-5592-43f3-b22e-a638230b08a5"
}
```

Cette opération ne retourne aucun token tenant.

---

# 10. Idempotence de la vérification

## Premier appel réussi

```http
201 Created
```

Une nouvelle ressource `EmailVerification` est créée.

## Rejeu du même jeton déjà consommé

TAKIBO ne crée aucune nouvelle organisation, aucun nouveau fondateur, aucun nouveau rôle et aucun nouveau Space.

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
  }
}
```

## Garanties techniques

L’idempotence doit être garantie en base de données par des contraintes telles que :

```text
une seule consommation réussie par OrganizationRegistration
une seule Organization produite par OrganizationRegistration
une seule attribution initiale R_ORG_OWNER
une seule création du Space initial
une seule EmailVerification réussie
```

Une clé d’idempotence interne ou une empreinte du jeton consommé permet de reconnaître un rejeu légitime.

---

# 11. Erreurs de vérification

## Jeton absent ou syntaxiquement mal formé

```http
400 Bad Request
```

```json
{
  "error": "verification_token_malformed"
}
```

## Jeton bien formé, mais invalide pour cette inscription

```http
422 Unprocessable Content
```

```json
{
  "error": "verification_token_invalid"
}
```

La réponse ne doit pas préciser si le jeton appartient à une autre inscription.

## Demande ou jeton expiré

```http
410 Gone
```

```json
{
  "error": "verification_expired",
  "status": "EXPIRED"
}
```

## Inscription déjà terminée avec une autre preuve

```http
409 Conflict
```

```json
{
  "error": "org_registration_already_completed",
  "status": "VERIFIED"
}
```

Le conflit porte ici sur l’état de la demande, pas sur le nom ou le `orgCode`.

---

# 12. Renvoi du courriel de vérification

Le renvoi est modélisé comme la création d’une ressource de livraison.

## Requête

```http
POST /api/v1/org-registrations/{registrationId}/verification-emails
Authorization: Bearer {registrationAccessToken}
```

## Réponse avec suivi de livraison

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

Même si l’envoi réel est asynchrone, `201 Created` est correct lorsque la ressource de livraison existe immédiatement.

Si TAKIBO ne crée aucune ressource de suivi et accepte seulement une commande asynchrone, il peut répondre :

```http
202 Accepted
```

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

Un nouvel envoi doit idéalement invalider les anciens jetons non consommés afin qu’un seul jeton demeure valide.

La réponse ne doit pas révéler inutilement si une adresse précise est enregistrée.

---

# 13. Premier login du fondateur

Après la vérification et le provisioning, le fondateur s’authentifie normalement.

## Requête

```http
POST /api/v1/auth/login
Content-Type: application/json
```

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

Le premier token est donc :

```text
HUMAN
PASSWORD
ORGANIZATION
R_ORG_OWNER
```

Aucun visiteur externe ne reçoit un token `PLATFORM`.

`R_ORG_OWNER` est le résultat sécurisé du provisioning. Il n’est pas une condition permettant d’entrer dans le parcours public.

---

# 14. Création administrative directe

Une autorité interne ou administrative TAKIBO peut créer directement une organisation.

## Requête

```http
POST /api/v1/orgs
Authorization: Bearer {platform-authority-token}
Content-Type: application/json
```

Exemple :

```json
{
  "name": "Acme Corporation"
}
```

TAKIBO génère également le `orgCode`.

## Réponse

```http
201 Created
Location: /api/v1/orgs/{orgId}
```

```json
{
  "organizationId": "8ca7a98e-8854-4ac4-8143-b30463dd9525",
  "name": "Acme Corporation",
  "orgCode": "acme-corporation-550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE"
}
```

Cette route doit être protégée par une politique explicite d’autorité plateforme ou par une identité machine interne dédiée.

Elle ne doit jamais être ouverte anonymement.

---

# 15. Dépréciation de l’ancienne route

L’ancienne route :

```http
POST /api/v1/orgs/signup
```

peut être conservée temporairement comme alias de compatibilité, mais elle doit être marquée comme dépréciée.

Elle est remplacée par :

```http
POST /api/v1/org-registrations
```

La console, les tests BVT et les nouvelles collections Postman ne doivent plus utiliser `/api/v1/orgs/signup`.

L’alias temporaire doit appeler le même cas d’usage applicatif afin d’éviter deux implémentations divergentes.

Il doit être supprimé après la période de migration prévue.

---

# 16. Statuts de la demande d’inscription

Une machine d’état minimale peut être :

```text
PENDING_EMAIL_VERIFICATION
VERIFIED
EXPIRED
CANCELLED
FAILED
```

Transitions principales :

```text
PENDING_EMAIL_VERIFICATION
    ├── vérification réussie ──→ VERIFIED
    ├── date limite dépassée ──→ EXPIRED
    ├── annulation explicite ──→ CANCELLED
    └── échec irrécupérable ───→ FAILED
```

Une inscription `VERIFIED` ne peut jamais être consommée une deuxième fois.

Une inscription `EXPIRED`, `CANCELLED` ou `FAILED` ne peut pas produire une nouvelle organisation sans un nouveau parcours explicitement prévu.

---

# 17. Sécurité

## Mots de passe

Le mot de passe du fondateur doit être traité comme un secret dès sa réception.

TAKIBO doit :

- appliquer la politique de mot de passe avant de créer la demande ;
- ne jamais journaliser le mot de passe ;
- le chiffrer temporairement ou éviter de le conserver sous forme réversible ;
- privilégier une création différée sécurisée ou une stratégie de secret temporaire ;
- supprimer toute donnée temporaire inutile à l’expiration.

## Jetons

Les jetons de vérification et de capacité doivent :

- avoir une durée de vie courte ;
- être à usage limité ;
- être liés à une inscription précise ;
- être protégés contre le rejeu non autorisé ;
- ne jamais être journalisés en clair ;
- être comparés de manière sûre ;
- pouvoir être révoqués.

## Protection contre l’énumération

TAKIBO doit éviter de révéler :

- si une adresse électronique possède déjà un compte ;
- si une inscription précise existe ;
- si un jeton appartient à une autre demande ;
- si un nom métier est déjà utilisé ;
- des informations tenant non nécessaires.

## Limitation de fréquence

Des limites doivent exister au minimum sur :

```text
POST /org-registrations
POST /org-registrations/{id}/email-verifications
POST /org-registrations/{id}/verification-emails
POST /auth/login
```

## Cache

Les réponses contenant une capacité, un jeton ou l’état privé d’une inscription utilisent :

```http
Cache-Control: no-store
```

---

# 18. Audit

TAKIBO doit produire des événements d’audit situés et lisibles, sans exposer les secrets.

Exemples :

```text
ORG_REGISTRATION_CREATED
ORG_REGISTRATION_EMAIL_SENT
ORG_REGISTRATION_EMAIL_RESENT
ORG_REGISTRATION_VERIFICATION_SUCCEEDED
ORG_REGISTRATION_VERIFICATION_FAILED
ORG_REGISTRATION_EXPIRED
ORG_PROVISIONING_SUCCEEDED
ORG_PROVISIONING_FAILED
FOUNDER_LOGIN_SUCCEEDED
FOUNDER_LOGIN_FAILED
```

Les journaux ne doivent jamais contenir :

```text
mot de passe
registrationAccessToken
jeton de vérification complet
client secret
access token complet
```

Avant la création de l’organisation, les événements sont rattachés au `registrationId`.

Après provisioning, ils peuvent également être corrélés au `organizationId`.

---

# 19. Tableau récapitulatif des endpoints

| Besoin | Méthode et URI | Réponse nominale |
|---|---|---:|
| Créer une demande publique | `POST /api/v1/org-registrations` | `201 Created` |
| Consulter une demande | `GET /api/v1/org-registrations/{registrationId}` | `200 OK` |
| Vérifier le courriel | `POST /api/v1/org-registrations/{registrationId}/email-verifications` | `201 Created` |
| Rejouer la même vérification | même URI | `200 OK` |
| Renvoyer le courriel | `POST /api/v1/org-registrations/{registrationId}/verification-emails` | `201 Created` |
| Demande ou jeton expiré | — | `410 Gone` |
| Jeton mal formé | — | `400 Bad Request` |
| Jeton non applicable | — | `422 Unprocessable Content` |
| Inscription déjà finalisée avec une autre preuve | — | `409 Conflict` |
| Trop de tentatives ou de renvois | — | `429 Too Many Requests` |
| Obtenir le premier token du fondateur | `POST /api/v1/auth/login` | `200 OK` |
| Créer directement une organisation | `POST /api/v1/orgs` | `201 Created` |

---

# 20. Invariants TAKIBO

## Invariant 1 — Le client ne choisit pas le `orgCode`

```text
organization.name  → fourni par le client
organization.orgCode → généré par TAKIBO
```

## Invariant 2 — Le nom n’est pas l’identité technique

Deux organisations peuvent avoir le même nom.

Elles ne peuvent jamais avoir le même `orgCode`.

## Invariant 3 — Le code est stable

Le `orgCode` généré lors de l’inscription reste le même après l’activation.

## Invariant 4 — Pas de token plateforme public

Le parcours public ne remet jamais de token `PLATFORM`.

## Invariant 5 — Le rôle Owner est un résultat

`R_ORG_OWNER` est créé et attribué après vérification réussie.

Il n’est jamais exigé pour soumettre la première inscription.

## Invariant 6 — Le login est séparé du provisioning

La vérification crée les ressources métier.

Le login crée la session et remet le token humain situé.

## Invariant 7 — Le rejeu ne duplique rien

Un même jeton consommé ne peut produire qu’une seule organisation, un seul fondateur, une seule attribution Owner et un seul Space initial.

## Invariant 8 — La base reste l’autorité finale

Les contraintes d’unicité et les transactions protègent les invariants même en cas de concurrence.

---

# 21. Contrat final retenu

```text
POST /api/v1/org-registrations
→ crée une demande publique
→ TAKIBO génère {nom-normalisé}-{uuid}
→ 201 PENDING_EMAIL_VERIFICATION
```

```text
GET /api/v1/org-registrations/{registrationId}
→ consulte l’état avec une capacité temporaire
```

```text
POST /api/v1/org-registrations/{registrationId}/email-verifications
→ vérifie le courriel
→ crée atomiquement Organization, Account, Founder User,
  R_ORG_OWNER, Initial Space et EmailVerification
```

```text
POST /api/v1/org-registrations/{registrationId}/verification-emails
→ crée une nouvelle demande de livraison du courriel
```

```text
POST /api/v1/auth/login
→ remet le premier token HUMAN / ORGANIZATION au fondateur
```

```text
POST /api/v1/orgs
→ création directe réservée à une autorité TAKIBO habilitée
```

---

# 22. Lois finales TAKIBO

> Le nom d’organisation appartient au métier et peut être partagé.

> Le `orgCode` appartient à TAKIBO et doit être unique.

> Sa forme est `{nom-normalisé}-{uuid}`.

> Une collision technique de code ne devient jamais un problème à résoudre par le visiteur.

> Une inscription publique crée d’abord une ressource temporaire et limitée.

> La vérification transforme cette demande en organisation active.

> `R_ORG_OWNER` est le résultat du parcours sécurisé, jamais son prérequis.

> Le premier token `ORGANIZATION` est obtenu uniquement par le login normal du fondateur.

> Toute frontière TAKIBO doit rester réelle.
