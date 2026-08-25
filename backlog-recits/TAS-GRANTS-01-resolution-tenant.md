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
- Prévoir un cache Caffeine court, configurable, de 5 à 15 minutes, invalidé immédiatement par un événement de changement/désactivation de client publié via l’outbox TMS.
- Conserver `postman-client` comme fixture PLATFORM in-memory de développement sans lui fabriquer `org_id`, `space_id`, `SCOPE_SPACE` ou `SOURCE_OAUTH2_CLIENT`.
- Réutiliser les fixtures fonctionnelles du récit 00; seules leur injection et leurs assertions de passage par le nouveau résolveur peuvent évoluer.

## Critères d’acceptation

- [ ] Aucun fallback vers un tenant par défaut n’existe en profil de production.
- [ ] Un client inconnu, désactivé ou incohérent échoue en mode fail-closed.
- [ ] Un contexte `PLATFORM` ne fabrique ni organisation ni espace.
- [ ] Un contexte `ORGANIZATION` exige `org_id` et interdit un `space_id` implicite.
- [ ] Un contexte `SPACE` exige des `org_id` et `space_id` cohérents.
- [ ] `TakiboRegisteredClientRepository.findByClientId`, `TenantResolutionFilter` et PKCE utilisent le même port et le même `ResolvedOAuthClient`; aucun lookup parallèle par `(org_id, space_id, client_id)` ne subsiste.
- [ ] `PkceEnforcementFilter` applique la politique issue du client résolu globalement par `client_id`; il n’exige pas que le tenant ait déjà été fourni ou deviné par la requête.
- [ ] Un événement TMS de modification, révocation ou désactivation évince immédiatement l’entrée de cache concernée; le TTL n’est qu’un filet de rattrapage.
- [ ] `postman-client` est résolu par la seule source in-memory dev comme PLATFORM; aucun accès à `oauth2_clients`, fallback générique ou source in-memory de production n’est introduit.
- [ ] Un client TMS marqué PLATFORM alors que le schéma/mapping ne sait pas le représenter échoue explicitement; il n’est jamais converti silencieusement en SPACE.
- [ ] Le test négatif « tenant/frontière incohérent », inatteignable dans le récit 00, est actif et vert dans ce récit.
- [ ] Les scénarios PLATFORM et SPACE du récit 00 restent identiques et passent via `ResolvedOAuthClientResolver`; le récit ne remplace pas leur vérité attendue en même temps que le composant testé.
- [ ] TAS ne dépend pas directement de l’implémentation TMS.
- [ ] `client_credentials` reste vert pour PLATFORM et SPACE.

## Tests attendus

- Matrice PLATFORM/ORGANIZATION/SPACE valide et invalide.
- Client désactivé, inconnu et frontières incompatibles.
- Cohérence entre repository, filtre tenant et PKCE pour un même `client_id`.
- Cache : hit, expiration, événement d’invalidation et désactivation immédiate.
- PKCE sans tenant fourni par le client; priorité de la source dev PLATFORM puis TMS; source dev absente en production.
- Rejeu intégral des tests du récit 00 et nouveau cas de frontière incohérente.

## Hors périmètre

- Calcul des permissions.
- Exposition publique de `resolveTenant`.
- Migration vers un microservice TMS.
- Recherche de `user_code` ou de tokens SAS, traitée dans TAS-GRANTS-02.
- Migration de `postman-client` ou d’un client PLATFORM vers `oauth2_clients`; elle nécessite un récit dédié de schéma et de mapping.
