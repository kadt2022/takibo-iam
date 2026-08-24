# TAS-GRANTS-00 — Filet de sécurité `client_credentials`

Référence figée du comportement actuel du TAS, avant l'introduction de
`authorization_code`, `refresh_token` et `device_code`. Aucun comportement de production
n'est modifié par ce lot de tests.

## Exécuter

Tests unitaires des filtres, sans Docker :

```bash
./gradlew :takibo-authorization-server:test
```

Tests d'intégration sur PostgreSQL réel, Docker requis :

```bash
./gradlew :takibo-iam-boot:test --tests "com.takibo.iamboot.tas.*"
```

Les deux modules, comme en CI :

```bash
./gradlew :takibo-authorization-server:test :takibo-iam-boot:test
```

Sans Docker, les classes d'intégration sont **ignorées** et non en échec
(`@EnabledIf("dockerIsAvailable")`). Les runners `ubuntu-latest` utilisés par la CI
disposent de Docker : elles s'y exécutent réellement.

## Pourquoi PostgreSQL réel

Le profil `test` du module tourne sur H2, Flyway désactivé, `ddl-auto: none` — aucune
table TAS n'y existe. Le schéma TAS repose sur `jsonb` et sur des index uniques partiels,
que H2 ne porte pas. `TasPostgresBaseline` démarre donc un PostgreSQL Testcontainers,
applique les migrations du dépôt et bascule Hibernate en `validate`, ce qui confronte au
passage les entités JPA au schéma réel. Le conteneur est unique pour la JVM et partagé par
toutes les classes du paquet.

## Jeu de données minimal

Semé par `TasBaselineDataset`, remis à zéro avant chaque test. Les empreintes sont produites
par le `PasswordEncoder` du contexte, jamais figées en dur.

| Table | Valeur |
|---|---|
| `organizations` | `aaaaaaaa-0000-0000-0000-000000000001`, code `baseline-org` |
| `spaces` | `bbbbbbbb-0000-0000-0000-000000000002`, code `baseline-space` |
| `accounts` | `cccccccc-0000-0000-0000-000000000003`, `baseline@takibo.test` |
| `account_credentials` | mot de passe `Baseline!Pass1` |
| `oauth2_clients` | `dddddddd-0000-0000-0000-000000000005`, client_id `baseline-space-client`, CONFIDENTIAL, secret `baseline-space-secret` |
| `oauth2_client_grant_types` | `client_credentials` |
| `oauth2_client_scopes` | `api.read` |

Les codes d'organisation et de space sont stockés sous leur **forme canonique** — minuscules
en kebab-case. La résolution normalise l'entrée avant de chercher : un code stocké autrement
serait introuvable.

Le client PLATFORM n'est pas en base. `postman-client` est déclaré in-memory par
`InMemoryDevRegisteredClientConfiguration` et n'a, par construction, ni organisation ni
space ; son secret vient de `takibo.dev.postman-client.secret`.

## Ce qui est figé

**`ClientCredentialsBaselineIntegrationTest`** — `/oauth2/token` de bout en bout, par HTTP
sur un port réel. MockMvc ne conviendrait pas : `TenantResolutionFilter` et
`PkceEnforcementFilter` sont des filtres de servlet, hors chaîne Spring Security, et ne s'y
exécuteraient pas. Le client HTTP du JDK remplace `RestTemplate`, qui perd le corps des
réponses d'erreur.

**`AuthorizationSaveContractBaselineIntegrationTest`** — ce que Spring Authorization Server
confie à `OAuth2AuthorizationService.save()`. Comme aucun bean n'existe, le test en déclare
un enregistreur **côté test uniquement** ; SAS traite un bean fourni exactement comme son
implémentation par défaut, donc ce qui est observé est bien le contrat de production. C'est
ce contrat que le service persistant du récit 02 devra honorer à l'identique.

**`HumanLoginBaselineIntegrationTest`** — `/api/v1/auth/login`, portée ORGANIZATION, et
l'indistinction des cinq causes de refus.

**`ClientIdExtractorTest`, `TenantResolutionFilterTest`, `PkceEnforcementFilterTest`** —
les trois composants du TAS qui n'avaient aucune couverture et que le récit 01 remplace.

## Trois constats qui appellent les récits suivants

**Aucun bean `OAuth2AuthorizationService` n'est déclaré.** Spring Authorization Server
fabrique lui-même un `InMemoryOAuth2AuthorizationService` et le conserve comme objet partagé
de son configurer, hors du contexte applicatif. Le récit 02 devra donc **introduire le bean
manquant**, pas seulement remplacer une implémentation. Rien n'atterrit aujourd'hui dans
`oauth2_authorization`.

Deux obstacles au récit 02 sont mesurés par le test de contrat de sauvegarde, et non
seulement déduits du schéma : `registeredClientId` porte l'identifiant **technique** du
client alors que `fk_oauth2_authz_client_scope` référence `oauth2_clients.client_id` ; et
l'autorisation du client PLATFORM référence un identifiant qui ne correspond à **aucune
ligne** de `oauth2_clients`, en plus de n'avoir ni organisation ni space là où `org_id` est
`NOT NULL`.

**`authorization_code` est déjà bloqué par le résolveur factice.** Avec
`grant_type=authorization_code`, `PkceEnforcementFilter` n'emprunte plus son court-circuit :
il cherche le client dans le couple org/space fixe rendu par `StubTenantResolver` et répond
`invalid_client / Client not found` alors que le client existe en base. Tout client
`authorization_code` réel subira ce refus tant que le résolveur factice est en place. C'est
la cible directe du récit 01.

**Le court-circuit du filtre PKCE est ce qui protège `client_credentials`.** Sur
`/oauth2/token`, tout grant autre que `authorization_code` traverse le filtre sans aucun
accès au registre des clients. Vérifié par mutation : retirer ce court-circuit fait tomber
exactement trois tests de garde, et eux seuls. Le récit 01 remplace ce chemin ; il ne doit
pas en changer le résultat.

## Tests dont la chute est attendue

Deux tests sont des sentinelles de transition, pas des garde-fous. Leur échec au récit
concerné est le signal recherché :

- `given_current_wiring_then_no_authorization_service_bean_is_declared`
- `given_successful_token_when_database_inspected_then_nothing_is_persisted`

Le récit 02 devra les mettre à jour pour constater la ligne `oauth2_authorization`, PLATFORM
comprise — et non les contourner par un service composite.
