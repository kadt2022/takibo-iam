# TAS-GRANTS-01 — Résolution réelle du tenant

**Statut :** À FAIRE  
**Branche :** `feat/tas-tenant-resolution-01`  
**Dépendances :** TAS-GRANTS-00

## Récit

En tant que TAS, nous voulons résoudre le tenant et la frontière d’un client depuis la source détenue par TMS afin de supprimer le résolveur factice et de refuser toute requête ambiguë.

## Périmètre

- Remplacer `StubTenantResolver` par un port unique `ResolvedOAuthClientResolver`.
- Fournir un adaptateur direct dans le monolithe modulaire, câblé par `takibo-iam-boot`.
- Définir un contrat pouvant recevoir plus tard un adaptateur d’API interne protégé par mTLS ou API key.
- Introduire un résultat unique `ResolvedOAuthClient`, obtenu par `client_id`, qui porte le client, son plan, sa frontière et sa politique OAuth.
- Faire consommer ce même résultat par `TakiboRegisteredClientRepository`, `TenantResolutionFilter` et la politique PKCE; supprimer les lookups concurrents.
- Implémenter ce port avec une composition explicite : source PLATFORM in-memory uniquement en profil dev, puis source TMS; en production, seule la source TMS est active.
- Concevoir `ResolvedOAuthClientResolver` de sorte qu’un décorateur de cache puisse s’intercaler plus tard entre les consommateurs et le résolveur TMS sans modifier ni les uns ni l’autre — sans construire ce décorateur dans ce récit (voir « Hors périmètre »).
- Conserver `postman-client` comme fixture PLATFORM in-memory de développement sans lui fabriquer `org_id`, `space_id`, `SCOPE_SPACE` ou `SOURCE_OAUTH2_CLIENT`.
- Réutiliser les fixtures fonctionnelles du récit 00; seules leur injection et leurs assertions de passage par le nouveau résolveur peuvent évoluer.
- Projeter fidèlement la politique TMS existante dans `ResolvedOAuthClient` — `client_type` (jamais déduit de `require_client_secret`, un client confidentiel en `private_key_jwt` n'exige aucun secret sans devenir public), `require_consent`, les trois TTL et `client_secret_expires_at` — plutôt que de la remplacer silencieusement par des valeurs par défaut au prétexte d'une « source unique de vérité ». Un secret expiré est refusé.
- Garantir que les cinq lectures qui composent un `ResolvedOAuthClient` (client, grants, scopes, URI, URI de post-déconnexion) portent sur un instantané cohérent — une transaction PostgreSQL `REPEATABLE READ`, vérifiée par un test d'intégration réel.

## Critères d’acceptation

- [ ] Aucun fallback vers un tenant par défaut n’existe en profil de production.
- [ ] Un client inconnu ou incohérent échoue en mode fail-closed. « Désactivé » en est délibérément absent — voir « Hors périmètre ».
- [ ] Un contexte `PLATFORM` ne fabrique ni organisation ni espace.
- [ ] Un contexte `ORGANIZATION` exige `org_id` et interdit un `space_id` implicite.
- [ ] Un contexte `SPACE` exige des `org_id` et `space_id` cohérents.
- [ ] La résolution lit directement PostgreSQL, sans cache : toute modification d’un client via TMS est donc reflétée à la résolution suivante, sans fenêtre à invalider.
- [ ] `ResolvedOAuthClientResolver` est la seule dépendance des trois consommateurs vis-à-vis de la résolution : un décorateur de cache pourra s’intercaler plus tard sans les modifier.
- [ ] `TakiboRegisteredClientRepository.findByClientId`, `TenantResolutionFilter` et PKCE utilisent le même port et le même `ResolvedOAuthClient`; aucun lookup parallèle par `(org_id, space_id, client_id)` ne subsiste.
- [ ] `PkceEnforcementFilter` applique la politique issue du client résolu globalement par `client_id`; il n’exige pas que le tenant ait déjà été fourni ou deviné par la requête.
- [ ] `postman-client` est résolu par la seule source in-memory dev comme PLATFORM; aucun accès à `oauth2_clients`, fallback générique ou source in-memory de production n’est introduit.
- [ ] Un client TMS marqué PLATFORM alors que le schéma/mapping ne sait pas le représenter échoue explicitement; il n’est jamais converti silencieusement en SPACE.
- [ ] Le test négatif « tenant/frontière incohérent », inatteignable dans le récit 00, est actif et vert dans ce récit.
- [ ] Les scénarios PLATFORM et SPACE du récit 00 restent identiques et passent via `ResolvedOAuthClientResolver`; le récit ne remplace pas leur vérité attendue en même temps que le composant testé.
- [ ] TAS ne dépend pas directement de l’implémentation TMS.
- [ ] `client_credentials` reste vert pour PLATFORM et SPACE.

## Tests attendus

- Matrice PLATFORM/ORGANIZATION/SPACE valide et invalide.
- Client inconnu et frontières incompatibles.
- Cohérence entre repository, filtre tenant et PKCE pour un même `client_id`.
- Instantané cohérent des cinq lectures (client, grants, scopes, URI, URI de post-déconnexion) sous modification concurrente, sur PostgreSQL réel.
- PKCE sans tenant fourni par le client; priorité de la source dev PLATFORM puis TMS; source dev absente en production.
- Rejeu intégral des tests du récit 00 et nouveau cas de frontière incohérente.

## Hors périmètre

- Calcul des permissions.
- Exposition publique de `resolveTenant`.
- Migration vers un microservice TMS.
- Recherche de `user_code` ou de tokens SAS, traitée dans TAS-GRANTS-02.
- Migration de `postman-client` ou d’un client PLATFORM vers `oauth2_clients`; elle nécessite un récit dédié de schéma et de mapping.
- **Le cycle de vie du client (désactivation).** `oauth2_clients` ne porte aujourd'hui aucune colonne de statut actif/désactivé — le critère « client désactivé échoue en fail-closed » est donc irréalisable tel quel dans ce récit. L'introduire exigerait une migration assumée (colonne de statut, valeurs, index) et la logique de résolution qui en découle ; ce sera un récit de cycle de vie client séparé, pas une extension silencieuse de celui-ci.
- **Le cache de résolution.** Après vérification de Keycloak (qui cache ses clients, rôles et groupes) : il le fait avec une infrastructure de cohérence que TAKIBO ne possède pas encore — caches Infinispan bornés, cache répliqué `work` propageant les invalidations entre nœuds, et une expiration de secours si une invalidation est perdue. Un simple `Caffeine` avec TTL n'offre aucune de ces garanties ; un client désactivé, un secret révoqué ou une frontière `org_id`/`space_id` périmée resteraient acceptés jusqu'à expiration. Ce que Keycloak cache est indissociable de comment il le fait ; copier seulement le cache introduirait plus de risque que de bénéfice. Ce qui justifierait un cache est le débit de résolutions par seconde et la charge PostgreSQL, pas le nombre de tenants : `client_id` est indexé et unique, PostgreSQL est local au monolithe, et aucune mesure ne montre aujourd'hui un problème. `ResolvedOAuthClientResolver` est conçu pour qu'un décorateur de cache s'intercale plus tard entre les consommateurs et le résolveur TMS sans modifier ni les uns ni l'autre. Ce cache fera l'objet d'un récit séparé, après mesures de charge, avec taille maximale (jamais un cache non borné) et invalidation distribuée — jamais une clé composée avec `org_id`/`space_id`, qui obligerait à connaître le tenant avant d'avoir authentifié le client.
