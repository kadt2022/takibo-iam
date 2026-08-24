# TAS-GRANTS-02 — Persistance OAuth 2.0

**Statut :** À FAIRE  
**Branche :** `feat/tas-oauth-persistence-02`  
**Dépendances :** TAS-GRANTS-01

## Récit

En tant que TAS, nous voulons persister les autorisations et consentements OAuth 2.0 afin de supporter les codes, refresh tokens et device codes de manière durable et sécurisée.

## Périmètre

- Implémenter des services persistants `OAuth2AuthorizationService` et `OAuth2AuthorizationConsentService`.
- Ajouter les migrations Flyway compatibles avec les plans PLATFORM/ORGANIZATION/SPACE et les sujets CLIENT_APP/HUMAN.
- Configurer les modules Jackson de Spring Authorization Server pour les attributs persistés.
- Compléter le mapping `OAuthClient` TMS vers `RegisteredClient`, notamment les `TokenSettings` et le consentement.
- Rendre le stockage compatible avec `client_credentials`; TAS persiste l’autorisation, mais ne crée ni ne modifie le client détenu par TMS.

## Règles de données

- `registered_client_id` contient l’identifiant technique stable `RegisteredClient.id`, pas le `client_id` public. La FK composite actuelle sur `(org_id, space_id, client_id)` est supprimée; aucune nouvelle FK ne doit exclure la fixture PLATFORM dev in-memory. À la lecture, TAS exige néanmoins que le client soit encore résolvable par `ResolvedOAuthClientResolver`.
- `org_id` est nullable pour PLATFORM.
- `space_id` est nullable pour PLATFORM et ORGANIZATION.
- `principal_account_id` est nullable avant l’approbation humaine d’un device.
- `subject_type` et `principal_name` sont explicites.
- Des contraintes SQL interdisent les combinaisons de plan, sujet et frontière invalides.
- Chaque hash utilisé par `OAuth2AuthorizationService.findByToken(...)`, dont `user_code`, `device_code`, authorization code, access token et refresh token, est globalement unique et ne dépend pas de `(org_id, space_id)` pour être retrouvé.
- Les codes et tokens sensibles sont stockés sous forme chiffrée récupérable et indexés par un hash SHA-256; jamais en clair ni sous forme hash-only.
- À la lecture, SAS reçoit la valeur originale déchiffrée. Une invalidation suivie d’un `save()` recalcule le hash une seule fois depuis cette valeur, jamais depuis un hash précédent.
- `access_token_ttl_seconds`, `refresh_token_ttl_seconds`, `id_token_ttl_seconds` et `require_consent` sont mappés dans les settings SAS.
- Toute valeur TTL nulle applique un défaut documenté identique au comportement machine antérieur; elle ne modifie pas silencieusement la durée des tokens `client_credentials`.
- `reuseRefreshTokens(false)` est imposé aux clients confidentiels autorisés au refresh.

## Critères d’acceptation

- [ ] Une autorisation survit au redémarrage de TAS.
- [ ] Le schéma refuse les combinaisons invalides de plan et de frontière.
- [ ] Une autorisation PLATFORM sans `org_id`/`space_id` et une autorisation SPACE complète peuvent être sauvegardées sans violer de FK.
- [ ] La recherche par code/token utilise un hash, sans journaliser ni exposer le secret.
- [ ] `findByToken` retrouve chaque type de token sans paramètre tenant grâce à son hash globalement unique.
- [ ] La valeur du device code reste récupérable lors de la validation du `user_code`, puis après `invalidate()` et un nouveau `save()`.
- [ ] Les refresh tokens ne sont jamais stockés en clair.
- [ ] Les attributs SAS sont sérialisés et relus sans perte.
- [ ] Les TTL et le consentement proviennent de la configuration du client TMS; les valeurs nulles conservent les valeurs par défaut préexistantes.
- [ ] Le mapping produit `reuseRefreshTokens(false)` pour un client confidentiel autorisé au refresh.
- [ ] Le registre de clients reste en lecture seule côté TAS; `RegisteredClientRepository.save` n’est pas utilisé/supporté.
- [ ] `client_credentials` continue à fonctionner avec la persistance active.
- [ ] Le test transversal défini en TAS-GRANTS-00 constate bien une ligne `oauth2_authorization` pour PLATFORM et SPACE; aucun service composite ne contourne la persistance PLATFORM.

## Tests attendus

- Tests repository avec PostgreSQL/Testcontainers et Flyway.
- Round-trip de sérialisation des attributs SAS.
- Tests de contraintes pour chaque plan et type de sujet.
- Test explicite du remplacement de `fk_oauth2_authz_client_scope` et de l’identifiant technique SAS.
- Recherche globale pour chaque hash, y compris `user_code` sans `client_id` ni tenant.
- Round-trip `findByToken` → `invalidate` → `save` → `findByToken`, sans hash de hash.
- Tests de mapping des `TokenSettings`, valeurs explicites et nulles, avec non-régression des TTL machine.
- Vérification qu’aucune colonne ni log ne contient un token en clair.
- Redémarrage simulé et relecture d’une autorisation.

## Hors périmètre

- Pages de login, consentement ou device.
- Activation d’un nouveau grant.
- Gestion complète de la révocation.
