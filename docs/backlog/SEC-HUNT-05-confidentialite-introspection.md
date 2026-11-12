# SEC-HUNT-05 — Confidentialité de l'introspection OAuth2

**Statut** : EN REVUE — branche `fix/sec-hunt-05-introspection-boundary`  
**Lot** : SECURITY FIX 01–05  
**Conclusion** : **aucune divulgation non autorisée démontrée** ; aucun changement de comportement  
**Décision de sécurité** : **OUVERTE** — droits d'introspection inter-tenant à trancher avant TAS-GRANTS-03-B / 04  
**Origine** : SECURITY HUNT 01, découverte HUNT-05 (classée faible)  
**Révision vérifiée** : `main@19998c4`

## 1. Question posée

Un client d'une Organization B peut-il, par `POST /oauth2/introspect`, apprendre sur un token de
l'Organization A une information qu'il ne devrait pas connaître ?

## 2. Faits établis (instance locale jetable, données synthétiques)

| Scénario | Réponse |
|---|---|
| Client propriétaire du token | 200, `active: true`, claims du token |
| Autre client du même Space | 200, `active: true`, mêmes champs |
| Client d'une autre Organization | 200, `active: true`, mêmes champs |
| `postman-client` (plateforme) | 200, `active: true`, mêmes champs |
| Token inconnu, révoqué, expiré | 200, `{"active": false}` seul |
| Token humain (login REST) | 200, `{"active": false}` seul (non stocké par le TAS) |
| Sans authentification, mauvais secret | 401 `invalid_client` |
| Révocation par un client d'une autre Organization | 400 `invalid_client`, le token reste actif |

Champs rendus pour un token actif : `active`, `client_id`, `token_type`, puis `aud`, `auth_method`,
`exp`, `iat`, `iss`, `jti`, `nbf`, `org_id`, `scope`, `space_id`, `sub`, `subject_type`,
`takibo_scope_level`, `takibo_tenant_source`. **Tous ces claims figurent déjà dans le JWT**, signé
mais non chiffré, et lisible par quiconque le détient. Seuls diffèrent la forme de `aud` et de
`scope` (liste contre chaîne) et les trois champs d'enveloppe de la RFC 7662.

## 3. Analyse

**Préconditions de l'attaque** : posséder un client authentifié **et** le token visé.

**Ce que le client étranger apprend en plus du token qu'il détient** : le seul état `active`,
c'est-à-dire si ce token a été révoqué ou non.

**Ce que cela lui permet** : rien de plus. Le token volé est de toute façon accepté par TSM tant
qu'il n'est pas expiré, car la révocation des tokens machine n'y est pas encore appliquée (récit
ouvert). Il ne peut ni révoquer le token d'un autre client ni sonder des tokens inconnus : ceux-ci
ne rendent que `active: false`.

**Protocole** (RFC 7662, rappelé de mémoire : le site de la RFC n'était pas joignable depuis
l'environnement de travail, le texte exact reste à vérifier par le relecteur) :
- §2.1 et §4 : l'endpoint **doit** exiger l'authentification des ressources protégées, et
  **devrait** n'autoriser que des ressources spécifiquement habilitées à l'appeler ;
- §2.2 : le serveur **peut** répondre différemment selon la ressource qui interroge ;
- §2.2 : pour un token inactif, seul `active: false` est attendu.

TAKIBO respecte les deux obligations : authentification exigée et réponse minimale pour un token
inactif. La recommandation (« spécifiquement habilitées ») n'est pas suivie : **tout** client
`client_credentials` peut introspecter, quelle que soit son Organization.

**Doctrine TAKIBO** : un écart existe avec l'isolation multi-tenant, puisqu'un client de B voit
qu'un token de A est encore actif. Il ne constitue pas aujourd'hui une divulgation non autorisée :
ce que B apprend se limite à ce que le token qu'il détient lui dit déjà.

## 4. Décision proposée

Conformément à l'ordre (« si aucune violation n'est démontrée, ne fabrique pas de correctif
artificiel ») : **aucun changement de comportement**. Cette PR apporte :
- les tests qui fixent le contrat actuel, pour qu'une régression soit visible ;
- cette justification.

**Recommandation au Capitaine**, pour le récit « révocation des tokens machine », quand des
serveurs de ressources consulteront réellement l'introspection : faire du droit d'introspecter une
capacité explicite d'un client (RFC 7662 §4), puis restreindre les réponses à la frontière du
client (§2.2). Le faire maintenant ajouterait du code et une règle sans aucun appelant légitime à
protéger.

### Décision laissée ouverte (revue du Sage)

Cette PR ne ferme pas l'introspection inter-tenant ; elle fixe le contrat actuel. La doctrine
n'est **pas** acceptée par défaut : la question « quels clients peuvent introspecter quels
tokens » reste une décision de sécurité ouverte, à trancher avant les grants humains
(TAS-GRANTS-03-B / 04), qui introduiront des refresh tokens opaques. Les tests de cette PR
signaleront tout changement de comportement en attendant.

## 5. Tests

| Niveau | Test | Ce qu'il fixe |
|---|---|---|
| Bout en bout PostgreSQL | `TokenIntrospectionBoundaryPostgresIntegrationTest` (5) | propriétaire ; client étranger : champs ⊆ claims du JWT + enveloppe, valeurs identiques ; révocation croisée refusée ; inconnu, révoqué, expiré, humain → `{"active":false}` exact ; authentification exigée |
| Newman | dossier « 12 » (+4 requêtes) | client tenant contre token plateforme : rien hors du JWT ; révocation croisée refusée (400) ; token toujours actif |

Ces tests sont verts sur `main` **par construction** : ils caractérisent un comportement jugé
acceptable. Ils échoueraient si l'introspection se mettait à rendre un champ absent du token, à
détailler un token inactif ou à accepter une révocation croisée.

## 6. Limites

- Texte de la RFC non revérifié en ligne (accès bloqué par le réseau de l'environnement).
- Refresh tokens et tokens d'autorisation humains non éprouvés : aucun flux ne les émet encore
  (TAS-GRANTS-03-B / 04). Un refresh token étant opaque, l'introspection en révélerait davantage :
  à réexaminer à ce moment-là.
