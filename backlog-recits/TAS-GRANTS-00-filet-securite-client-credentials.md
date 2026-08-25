# TAS-GRANTS-00 — Filet de sécurité `client_credentials`

**Statut :** À FAIRE  
**Branche :** `test/tas-client-credentials-baseline-00`  
**Dépendances :** aucune

## Récit

En tant qu’équipe IAM, nous voulons figer par des tests le comportement actuel de `client_credentials` afin d’intégrer de nouveaux grant types sans régression sur les clients machine.

## Périmètre

- Exécuter PostgreSQL avec Testcontainers et les migrations Flyway réelles.
- Couvrir en bout en bout `/oauth2/token` pour au moins un client `PLATFORM` et un client `SPACE`.
- Vérifier la signature et les claims situés du JWT émis.
- Figer le contrat de sauvegarde dans `OAuth2AuthorizationService` après chaque émission réussie.
- Ajouter une baseline du parcours humain existant `/api/v1/auth/login` avant son refactor au récit 03.
- Ajouter les tests unitaires manquants de `TenantResolutionFilter`, `ClientIdExtractor` et `PkceEnforcementFilter`.
- Documenter le jeu minimal de données et la commande de test.

## Critères d’acceptation

- [ ] Un client `PLATFORM` obtient un JWT signé sans `org_id` ou `space_id` fabriqué.
- [ ] Un client `SPACE` obtient un JWT signé contenant les `org_id` et `space_id` attendus.
- [ ] Après chaque succès PLATFORM et SPACE, `OAuth2AuthorizationService` contient une autorisation avec l’identifiant technique du client, le principal, le grant, les scopes et les métadonnées du token attendus.
- [ ] Ce même test reste applicable lorsque le service in-memory sera remplacé par le service JPA; il devra alors constater la ligne `oauth2_authorization` correspondante, y compris pour PLATFORM. Ce critère contraint TAS-GRANTS-02 à rendre cette persistance possible plutôt qu’à l’éviter avec un service composite.
- [ ] Un secret invalide, un client inconnu, un scope refusé ou un grant non autorisé est refusé avec une erreur OAuth 2.0 stable.
- [ ] Un client TMS sans grant type exploitable est refusé explicitement et le chemin où `toRegisteredClient()` retourne `null` est couvert.
- [ ] `/api/v1/auth/login` possède une baseline positive et négative couvrant identité, frontière et claims humains actuels.
- [ ] Les tests utilisent PostgreSQL réel via Testcontainers et appliquent Flyway.
- [ ] Les trois composants ciblés disposent de tests positifs et négatifs.
- [ ] Toute régression de `client_credentials` fait échouer la CI.
- [ ] Aucun comportement de production n’est modifié par ce récit.
- [ ] Le test de signature vérifie le JWT avec la clé publique du contexte courant; la stabilité de la clé après redémarrage est explicitement déléguée à TAS-GRANTS-02A.

## Tests attendus

- Tests E2E `client_credentials` PLATFORM et SPACE.
- Tests d’échec : secret invalide, client inconnu, scope refusé et grant non autorisé.
- Client sans grant type et mapping `RegisteredClient` impossible.
- État de `OAuth2AuthorizationService` après émission PLATFORM et SPACE.
- Baseline `/api/v1/auth/login` valide/invalide et claims situés.
- Tests unitaires des extracteurs et filtres, y compris requêtes sans `client_id`.
- Documenter que `PkceEnforcementFilter` sort actuellement sans résolution PKCE lorsque `grant_type=client_credentials`; le récit 01 remplacera ce chemin sans changer son résultat.
- Le scénario « tenant incohérent » est volontairement absent de la baseline : il est inatteignable avec `StubTenantResolver` et devient obligatoire dans TAS-GRANTS-01.

## Hors périmètre

- Nouveau grant type.
- Modification des claims ou des règles RBAC.
- Remplacement du résolveur de tenant.
