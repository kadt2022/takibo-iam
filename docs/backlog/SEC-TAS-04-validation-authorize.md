# SEC-TAS-04 — `/oauth2/authorize` ne redirige jamais vers une URI non enregistrée

**Statut** : EN REVUE — branche `fix/sec-tas-04-authorize-validation`  
**Lot** : SECURITY FIX 01–05  
**Risque** : moyen — redirection ouverte sans authentification (hameçonnage), erreur 500 associée  
**Origine** : NEWMAN-SEC-01, découverte signalée au Capitaine et au Sage  
**Révision vérifiée** : `main@19998c4` ; Spring Authorization Server 7.0.3

## 1. Récit

En tant que responsable du serveur d'autorisation, je veux qu'aucune réponse de
`/oauth2/authorize`, erreur comprise, ne soit envoyée vers une URI que le client n'a pas
enregistrée, et qu'un client qui ne peut pas recevoir de code soit refusé sur place, afin
qu'un lien TAKIBO légitime ne serve jamais de tremplin vers un site piégé.

## 2. Défaut constaté

```
GET /oauth2/authorize?response_type=code&client_id=<client machine>&redirect_uri=https://evil.example/phish&state=abc
→ 302 Location: https://evil.example/phish?error=unauthorized_client&…&state=abc
```

Reproduit avec un client créé par l'API, un client inséré en base et `postman-client`. Sans
`redirect_uri`, la même requête répond **500**.

**Cause** (bytecode de SAS 7.0.3) : `OAuth2AuthorizationEndpointFilter` valide la requête dans un
filtre interne, `OAuth2AuthorizationCodeRequestValidatingFilter`, **avant** l'authentification de
l'utilisateur. L'ordre des validateurs est : grant `authorization_code`, puis `redirect_uri`,
puis scope, PKCE, prompt. L'erreur `unauthorized_client` est construite avec l'URI **brute** de la
requête. Sans URI, `throwError` prend le premier élément des URI enregistrées : un ensemble vide
lève `NoSuchElementException`.

Non touchés sur `main` : client web avec une URI non enregistrée ou une variante (400), client
inconnu (400), client web anonyme avec son URI (401).

Variante relevée par lecture : le fournisseur SAS redirige aussi une erreur `request_uri` vers
l'URI brute. Elle n'est pas atteignable anonymement aujourd'hui (401), mais le deviendra avec une
session utilisateur (TAS-GRANTS-03-B, 04).

## 3. Correctif : deux couches indépendantes

1. **`AuthorizationCodeClientFilter`** (avant SAS, entre la résolution du client et PKCE) : un
   client sans grant `authorization_code` ou sans URI enregistrée reçoit
   **400 `unauthorized_client`**, écrit sur place. Cette couche corrige la redirection ouverte
   des clients machine et l'erreur 500.
2. **`RegisteredRedirectUriErrorHandler`** (gestionnaire d'erreurs de l'endpoint SAS) : une
   erreur n'est redirigée que vers une URI **enregistrée** pour ce client (égalité exacte ; pour
   `127.0.0.1` et `[::1]`, le port est ignoré, comme SAS le fait, RFC 8252 §7.3). Sinon :
   **400** au format OAuth2. Cette couche est la garantie générale : elle ne dépend plus de
   l'ordre interne des contrôles de SAS et couvre la variante `request_uri`.

La validation de SAS reste intacte, dont la restriction OIDC qu'il ajoute lui-même (vérifié :
remplacer son validateur la supprimait). Une erreur postérieure à la validation part toujours
vers l'URI enregistrée, conformément à la RFC 6749 §4.1.2.1.

Compatibilité Authorization Code + PKCE : les clients `authorization_code` traversent la
première couche sans changement ; la seconde n'altère pas les redirections vers une URI
enregistrée.

Les erreurs 400 de cet endpoint sont désormais écrites au format OAuth2
(`{"error","error_description","error_uri"}`), au lieu de la page d'erreur générique de Spring Boot.

## 4. Tests

| Niveau | Test | Ce qu'il prouve |
|---|---|---|
| Unitaire | `AuthorizationCodeClientFilterTest` (5) | client machine, client sans URI, client web inchangé, contexte absent, autres endpoints |
| Unitaire | `RegisteredRedirectUriErrorHandlerTest` (7) | URI non enregistrée, client inconnu, sans URI, exception non OAuth2, URI enregistrée avec `state`, bouclage, variantes refusées |
| Unitaire | `TakiboAuthorizationServerConfigurationTest` | chaîne construite avec le gestionnaire |
| Bout en bout PostgreSQL | `AuthorizeEndpointRedirectPostgresIntegrationTest` (8) | client machine et plateforme jamais redirigés, sans `redirect_uri` : 400 ; client inconnu ; client web : URI non enregistrée ou variantes → 400 ; URI enregistrée : 401 anonyme, erreur postérieure vers l'URI enregistrée ; `client_credentials` inchangé |
| Newman | dossier « 10 » (+8 requêtes, `followRedirects: false`) | mêmes scénarios par HTTP |

Rouge avant correctif : 3 tests de bout en bout échouent sur `main` (deux 302 vers l'attaquant,
un 500). Contre le jar de `main`, la collection modifiée échoue sur 6 assertions.

## 5. Vérifications complémentaires (revue du Sage)

- **Bouclage** : `127.0.0.1` et `[::1]` acceptent tout port (règle SAS, RFC 8252) ; `localhost`
  est un nom, pas une adresse de bouclage : son port reste exigé.
- **Encodage et casse** : `%63allback`, `callback%2F..%2Fphish`, `callback%20`, `callback/.`,
  casse différente, port par défaut explicite, `?` ou fragment vides, `user@hôte` : tous refusés
  (correspondance exacte, aucune normalisation).
- **Redirection d'erreur** : un `state` hostile (`&error=…&redirect_uri=…#…`) est encodé ;
  il ne peut ni ajouter de paramètre, ni remplacer `error`, ni introduire un fragment, et il
  revient intact au client.

Tests : `RegisteredRedirectUriErrorHandlerTest` (11), `AuthorizeEndpointRedirectPostgresIntegrationTest` (9).
Aucun changement de production.

## 5. Limites et risques résiduels

- L'oracle d'existence d'un `client_id` (400 « client inconnu » contre 400 `unauthorized_client`)
  subsiste ; les deux réponses sont désormais des 400 sur place, mais leurs corps diffèrent.
- Après validation, une URI de bouclage IP enregistrée sans port accepte tout port (comportement
  SAS conservé, RFC 8252).
- La variante `request_uri` n'est couverte que par la seconde couche, testée unitairement :
  aucun parcours authentifié n'existe encore pour l'éprouver de bout en bout.
