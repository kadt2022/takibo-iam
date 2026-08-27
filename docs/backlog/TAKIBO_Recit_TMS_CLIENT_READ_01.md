# TAKIBO — Récit TMS-CLIENT-READ-01

## Lire les clients OAuth2 sans jamais réexposer leurs secrets

### Statut

```text
Proposé
```

### Branche proposée

```text
feat/tms-oauth-client-read-side
```

### Commit proposé

```text
feat(tms): add situated OAuth client read-side without secret exposure
```

---

# 1. Contexte

La collection canonique `Takibo-Postman-Collection` exécute désormais avec succès :

```text
113 requêtes
155 assertions
0 échec
```

La création d’un client OAuth2 et la rotation de son secret fonctionnent déjà.

Cependant, la collection ne peut pas encore prouver complètement la règle :

> Le secret d’un client OAuth2 est livré une seule fois, puis n’est jamais réexposé.

La raison est structurelle : l’API TAKIBO ne possède actuellement aucun endpoint de lecture ou de liste des clients OAuth2.

Le contrôleur actuel expose seulement :

```text
POST /api/v1/orgs/{orgId}/spaces/{spaceId}/clients
POST /api/v1/orgs/{orgId}/spaces/{spaceId}/clients/{id}/rotate-secret
```

Le repository permet une recherche interne par identifiant, mais ne propose aucun read-side paginé par organisation et Space.

---

# 2. Constat vérifié

## 2.1 Ce qui existe déjà

TAKIBO sait :

```text
créer un client OAuth2/OIDC
générer un secret aléatoire
hacher le secret avec PasswordEncoder
persister uniquement le hash
livrer le secret en clair dans la réponse de création
tourner le secret avec contrôle optimiste de version
livrer le nouveau secret uniquement dans la réponse de rotation
refuser les tokens PLATFORM et ORGANIZATION sur la surface située SPACE
```

La réponse métier publique actuelle ne contient ni le hash du secret ni le secret en clair. Le secret en clair est ajouté uniquement dans l’enveloppe de création ou dans la réponse de rotation.

## 2.2 Ce qui n’existe pas

TAKIBO ne sait pas encore exposer :

```text
la liste des clients d’un Space
le détail d’un client
une recherche paginée
une lecture située permettant de vérifier l’absence du secret
```

Il n’existe pas non plus actuellement de véritable lifecycle de client :

```text
pas de statut ACTIVE / SUSPENDED / REVOKED
pas d’endpoint suspend
pas d’endpoint reactivate
pas d’endpoint revoke
```

Le lifecycle devra donc faire l’objet d’un récit distinct.

## 2.3 Décision sur les codes Space

La génération d’un code voisin lorsqu’un code Space est déjà utilisé est un comportement volontaire du code actuel et possède déjà un test unitaire dédié.

Ce récit ne modifie pas cette politique.

Une décision séparée devra trancher ultérieurement :

```text
code explicitement fourni et déjà utilisé → 409
ou
code considéré comme suggestion → génération d’un code voisin
```

---

# 3. Objectif du récit

Introduire un read-side OAuth2/OIDC strictement situé permettant :

```text
de lister les clients du Space courant
de consulter le détail d’un client du Space courant
de ne jamais exposer le secret en clair
de ne jamais exposer le hash du secret
de ne jamais exposer les données sensibles internes
de conserver les frontières orgId + spaceId
de produire les preuves E2E du secret one-time
```

---

# 4. Doctrine

## 4.1 Le secret n’est pas une propriété lisible

Un secret OAuth2 n’est pas une donnée du read-side.

```text
création → secret livré une fois
rotation → nouveau secret livré une fois
lecture → aucun secret
liste → aucun secret
audit → aucun secret
logs → aucun secret
```

Le serveur ne doit jamais tenter de « relire » ou reconstruire un secret.

## 4.2 Le hash n’est jamais une donnée d’API

Les champs suivants sont strictement internes :

```text
clientSecretHash
secretHistory
version technique
```

Ils ne doivent apparaître dans aucun DTO REST.

## 4.3 Toute lecture est située

Pour toute lecture :

```text
token.org_id == path.orgId
token.space_id == path.spaceId
spaceId appartient à orgId
```

Aucun rôle n’élargit cette frontière.

## 4.4 Anti-énumération

La distinction doit rester :

```text
token ou chemin hors frontière → 403
client extérieur recherché à l’intérieur de ma frontière → 404
client inconnu dans ma frontière → 404
```

---

# 5. API cible

## 5.1 Liste paginée

```http
GET /api/v1/orgs/{orgId}/spaces/{spaceId}/clients
```

Paramètres proposés :

```text
page=0
size=20
search=
clientType=
sort=clientName,asc
```

Réponse :

```http
200 OK
```

Exemple :

```json
{
  "items": [
    {
      "id": "9fd58047-609b-4726-9bdd-6adf93d51c81",
      "orgId": "d11d3862-b0b5-48a2-8abf-81cded86cdf2",
      "spaceId": "b3fe5ce8-2858-439a-b47b-e5b7429353cc",
      "clientId": "finance-worker",
      "clientName": "Finance Worker",
      "clientType": "CONFIDENTIAL",
      "requireClientSecret": true,
      "requirePkce": false,
      "clientSecretExpiresAt": null,
      "scopes": ["finance.read"],
      "grantTypes": ["client_credentials"]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Champs interdits :

```text
clientSecret
oneTimePlainSecret
clientSecretHash
jwksJson
secretHistory
```

## 5.2 Détail

```http
GET /api/v1/orgs/{orgId}/spaces/{spaceId}/clients/{clientId}
```

Réponse :

```http
200 OK
```

Le détail peut exposer la configuration non secrète :

```text
id
orgId
spaceId
clientId
clientName
clientType
requireClientSecret
tokenEndpointAuthMethod
requirePkce
requireConsent
jwksUri
idTokenSignedAlg
durées de tokens
expiration du secret
scopes
grantTypes
redirectUris
postLogoutRedirectUris
corsOrigins
createdAt
updatedAt
```

Il ne doit jamais exposer :

```text
secret en clair
hash du secret
JWK privé brut
historique des secrets
```

---

# 6. Modèle d’application proposé

## 6.1 Port d’entrée

```java
public interface OAuthClientQueryCase {

    OAuthClientPageResult listClients(
            UUID orgId,
            UUID spaceId,
            String search,
            ClientType clientType,
            int page,
            int size,
            String sort
    );

    OAuthClientDetailResult getClient(
            UUID orgId,
            UUID spaceId,
            UUID clientId
    );
}
```

## 6.2 Port de sortie

```java
public interface OAuthClientQueryRepository {

    OAuthClientPageResult findByOrganizationAndSpace(
            UUID orgId,
            UUID spaceId,
            OAuthClientSearchCriteria criteria
    );

    Optional<OAuthClientDetailResult> findDetailByScopeAndId(
            UUID orgId,
            UUID spaceId,
            UUID clientId
    );
}
```

Le read-side ne doit pas retourner l’entité JPA directement.

## 6.3 Service

```java
@Service
@Transactional(readOnly = true)
public class OAuthClientQueryService implements OAuthClientQueryCase {
    // validation de la pagination
    // validation du tri
    // lecture strictement bornée par orgId + spaceId
    // 404 uniforme si absent dans cette frontière
}
```

---

# 7. Contrôleur

Ajouter au contrôleur situé existant :

```java
@GetMapping
public ResponseEntity<OAuthClientPageResponse> list(...)

@GetMapping("/{id}")
public ResponseEntity<OAuthClientDetailResponse> get(...)
```

Chaque méthode doit appeler la même défense en profondeur que les commandes :

```java
assertCallerBoundToSpace(orgId, spaceId);
```

Le contrôleur ne doit jamais utiliser une recherche globale `findById(id)` puis vérifier la frontière après coup.

La requête repository doit porter la frontière dans sa signature :

```text
findByIdAndOrgIdAndSpaceId
```

---

# 8. Sécurité

## 8.1 Réponses attendues

```text
401 — aucun token
403 — token PLATFORM
403 — token ORGANIZATION sur surface SPACE
403 — token d’un autre Space
403 — orgId du chemin différent du token
404 — client inconnu dans la frontière courante
404 — client d’un autre Space recherché dans le Space courant
200 — client présent dans la frontière exacte
```

## 8.2 Cache

Les réponses de création et rotation conservent :

```http
Cache-Control: no-store, no-cache
Pragma: no-cache
X-Content-Type-Options: nosniff
```

Les réponses de lecture ne contiennent aucun secret. Elles peuvent néanmoins utiliser une politique de cache prudente :

```http
Cache-Control: no-store
```

## 8.3 Logs

Les logs ne doivent contenir aucun :

```text
clientSecret
clientSecretHash
jwksJson
Authorization header
```

---

# 9. Tests unitaires et d’intégration

## 9.1 Query service

Tester :

```text
liste uniquement les clients de orgId + spaceId
ne retourne aucun client d’un autre Space
retourne 404 pour un client absent de la frontière
refuse page négative
refuse size hors limite
refuse un champ de tri non autorisé
```

## 9.2 Repository

Tester avec PostgreSQL :

```text
pagination
filtre clientType
recherche par clientId ou clientName
frontière composite orgId + spaceId
chargement des collections sans fuite de secret
```

## 9.3 Mapper

Tester explicitement l’absence des champs :

```text
clientSecret
clientSecretHash
jwksJson
secretHistory
```

## 9.4 Persistance du secret

Conserver ou ajouter un test d’intégration vérifiant :

```text
secret en clair retourné par le service
secret en clair différent de la valeur persistée
PasswordEncoder.matches(secretClair, hashPersisté) == true
```

Cette preuve appartient aux tests d’intégration, pas à Postman.

---

# 10. Scénarios Postman à ajouter

Dans :

```text
10 — Clients OAuth2 et OIDC
```

Ajouter après la création :

```text
200 — Client — Détail consulté sans réexposition du secret
200 — Clients — Liste du Space consultée sans réexposition du secret
```

Après la rotation :

```text
200 — Client — Détail après rotation sans réexposition du nouveau secret
```

Négatifs :

```text
404 — Client — Identifiant inconnu non énumérable
404 — Client — Client d’un autre Space invisible dans le Space courant
403 — Client — Token d’un autre Space refusé
403 — Client — Token PLATFORM refusé à la lecture
403 — Client — Token ORGANIZATION refusé à la lecture
401 — Client — Lecture sans token refusée
```

Assertions obligatoires sur les réponses `200` :

```javascript
pm.expect(body).to.not.have.property("clientSecret");
pm.expect(body).to.not.have.property("oneTimePlainSecret");
pm.expect(body).to.not.have.property("clientSecretHash");
pm.expect(body).to.not.have.property("jwksJson");
pm.expect(JSON.stringify(body)).to.not.include(previousPlainSecret);
pm.expect(JSON.stringify(body)).to.not.include(rotatedPlainSecret);
```

---

# 11. Critères d’acceptation

Le récit est terminé lorsque :

- `GET /clients` existe et est paginé ;
- `GET /clients/{id}` existe ;
- les deux endpoints sont strictement bornés par `orgId + spaceId` ;
- un client extérieur à la frontière n’est jamais retourné ;
- aucune réponse de lecture ne contient de secret en clair ;
- aucune réponse de lecture ne contient de hash ;
- la création continue de livrer le secret une seule fois ;
- la rotation continue de livrer le nouveau secret une seule fois ;
- les tests repository prouvent que seul un hash vérifiable est persisté ;
- les scénarios Postman de lecture passent ;
- le run complet de `Takibo-Postman-Collection` reste vert ;
- aucun endpoint de lifecycle fictif n’est ajouté dans ce récit.

---

# 12. Hors périmètre

Ce récit ne traite pas :

```text
suspension d’un client
réactivation d’un client
révocation d’un client
suppression d’un client
historique fonctionnel des rotations
modification PATCH d’un client
politique des codes Space dupliqués
migration RBAC-04 des permissions canoniques
```

Ces capacités feront l’objet de récits séparés.

---

# 13. Récits suivants recommandés

## TMS-CLIENT-LIFECYCLE-01

```text
ACTIVE
SUSPENDED
REVOKED
```

Avec enforcement réel lors de l’émission de token.

## TMS-SPACE-CODE-01

Trancher explicitement :

```text
code fourni en collision → 409
code absent → génération automatique
```

ou conserver la doctrine actuelle du code considéré comme suggestion.

---

# 14. Résultat attendu

Après ce récit, TAKIBO pourra démontrer honnêtement :

> Un secret OAuth2 est créé ou tourné une seule fois, livré une seule fois, stocké uniquement sous forme de hash et impossible à relire par les API d’administration.
