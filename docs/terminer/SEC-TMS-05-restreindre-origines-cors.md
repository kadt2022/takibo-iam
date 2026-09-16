# SEC-TMS-05 — Restreindre les origines CORS à une liste configurée

**Statut** : TERMINÉ
**Origine** : relecture de `CorsConfig`, prérequis du lot Mbuyamba `LOT-ACCES` (ACCES-04, ACCES-11)
**Dépend de** : —
**Bloque** : TAS-GRANTS-03 (authentification humaine SAS et session navigateur)
**Risque** : moyen

## Contexte

Takibo autorise actuellement les requêtes CORS credentialed depuis toute origine via
`allowedOriginPattern("*")`. Cette configuration doit être restreinte avant une exposition
de production.

### Ce que fait le code aujourd'hui

`takibo-iam-boot/src/main/java/com/takibo/iamboot/config/CorsConfig.java` déclare un
unique `CorsConfigurationSource` enregistré sur `/**` :

```java
// En dev, on ouvre large
config.addAllowedOriginPattern("*");
config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
config.setAllowedHeaders(List.of("*"));
config.setAllowCredentials(true);
```

- La classe ne porte aucun `@Profile`. Le commentaire parle du développement, mais la
  configuration s'applique à tous les profils : `test`, `ci`, et une installation sans
  profil, donc la production.
- `SecurityConfig` (`takibo-security-management`) l'active sur la chaîne API par
  `.cors(Customizer.withDefaults())`.
- Spring refuse `addAllowedOrigin("*")` combiné à `setAllowCredentials(true)`.
  `addAllowedOriginPattern("*")` contourne ce garde-fou : le serveur recopie l'en-tête
  `Origin` de la requête dans `Access-Control-Allow-Origin` et ajoute
  `Access-Control-Allow-Credentials: true`. C'est un reflet d'origine, pas une liste.
- La liste des méthodes omet `PATCH`, alors que `ReadableUserLifecycleController` en
  expose. Un frontend web tiers verrait donc ses `PATCH` refusés au preflight. La liste
  actuelle est à la fois trop large et incomplète.

### Portée réelle aujourd'hui

La chaîne API est `STATELESS`, CSRF désactivé, et s'authentifie par l'en-tête
`Authorization: Bearer`. Le navigateur n'ajoute jamais cet en-tête de lui-même : seul le
code du frontend qui détient le jeton peut le poser. Ce récit n'affirme donc aucune
exploitation concrète par cookie aujourd'hui.

Le défaut devient structurant dès qu'un état d'authentification est porté par le
navigateur lui-même. C'est précisément ce qu'introduit TAS-GRANTS-03 avec la session
navigateur de SAS. Voir [Lien avec TAS-GRANTS-03](#lien-avec-tas-grants-03).

### La chaîne TAS : un comportement qui ne se lit pas dans le code

`TakiboAuthorizationServerConfiguration.authorizationServerSecurityFilterChain`
(`@Order(1)`, endpoints SAS dont `/oauth2/token` et `/oauth2/jwks`) n'appelle pas
`.cors()`. Elle peut pourtant hériter du joker.

Dans Spring Security 7.0.3, `HttpSecurityConfiguration.applyCorsIfAvailable` applique
`cors(withDefaults())` à toute chaîne lorsque
`getBeanNamesForType(UrlBasedCorsConfigurationSource.class)` trouve un bean. `CorsConfig`
déclare le sien avec le type de retour `CorsConfigurationSource`. La correspondance dépend
donc de l'instanciation ou non du singleton au moment où la chaîne TAS est construite.

Conséquence : on ne sait pas, en lisant le code, si les endpoints SAS répondent au CORS
avec le joker. Ce récit ne le présume pas. Il impose une déclaration explicite sur les deux
chaînes et un test qui fixe le comportement.

### Des origines par client existent déjà, sans consommateur

TMS stocke des origines par client OAuth2 (table `oauth2_client_cors_origins`, valeur
validée par `ClientCorsOrigin`) :

- HTTPS obligatoire hors loopback ;
- ni chemin, ni requête, ni fragment, ni information d'utilisateur ;
- origine exigée pour un client `PUBLIC` par `OAuthClientGrantPolicy`.

Aucun composant d'exécution ne les lit : la source globale les ignore. Ce récit ne les
branche pas (voir [D5](#d5--origines-par-client-tms--différé-à-tas-grants-04)).

### Consommateurs

- **Takibo-ui** passe en développement par le relais Vite `/api` vers
  `takibo-iam-boot`. Ses appels sont donc de même origine et ne dépendent pas de CORS. Le
  BFF prévu par TAKIBO UI 02 conserve cette propriété.
- **Mbuyamba** (dépôt `portail-math`, `backlog-recits/LOT-ACCES-web-android-sous-controle.md`,
  section « Prérequis côté Takibo-IAM ») appellera Takibo directement :
  - depuis son frontend web (ACCES-04, `POST /api/v1/auth/login` jusqu'à TAS-GRANTS-04) ;
  - puis depuis une application Android en WebView Capacitor (ACCES-11).

  Il lui faut une liste d'origines explicite, fournie par l'installation.

## Loi du récit

Une origine croisée n'est acceptée que si l'installation l'a nommée. Hors du profil `dev`,
aucun joker, aucun motif, aucun reflet d'origine. Une liste absente ou vide signifie
qu'aucune origine croisée n'est acceptée, jamais que toutes le sont. Aucune réponse CORS
n'autorise les credentials.

## Décisions portées par le récit

### D1 — Une liste par environnement, contrat d'installation

La liste est une propriété, alimentée par une variable d'environnement, vide par défaut :

```yaml
takibo:
  cors:
    allowed-origins: ${TAKIBO_CORS_ALLOWED_ORIGINS:}
```

Elle suit la doctrine d'installation du backlog : le cœur exprime un contrat générique de
configuration et refuse de démarrer si ce contrat n'est pas satisfait. Hors profil `dev`,
chaque valeur est validée au démarrage, et une seule valeur invalide fait échouer le
démarrage :

| Valeur | Exemple | Verdict |
| --- | --- | --- |
| Joker | `*` | refus de démarrer |
| Motif | `https://*.example.test`, `http://localhost:*` | refus de démarrer |
| Origine opaque | `null` | refus de démarrer |
| Chemin, requête, fragment, utilisateur | `https://app.example/cb` | refus de démarrer |
| HTTP hors loopback | `http://app.example` | refus de démarrer |
| Origine HTTPS exacte | `https://portail.example` | acceptée |

- Les règles de forme sont celles de `ClientCorsOrigin`. Mettre la validation en commun
  ou la dupliquer est un choix d'implémentation, qui respecte les frontières vérifiées par
  ArchUnit.
- La comparaison est exacte après normalisation (casse du schéma et de l'hôte, slash
  final). Aucune correspondance par préfixe ni par suffixe.
- Le message d'échec nomme la propriété et la valeur refusée. Les origines ne sont pas des
  secrets : la liste effective est journalisée au démarrage.
- Une liste vide démarre normalement. Une installation consommée uniquement de serveur à
  serveur n'a besoin d'aucune origine.

### D2 — Seul le profil `dev` peut rester ouvert

L'ouverture est un choix déclaré, jamais un défaut :

- **`dev`** : `application-dev.yml` peut déclarer un motif ou le joker. Recommandation :
  préférer des motifs loopback (`http://localhost:*`, `http://127.0.0.1:*`) au joker, pour
  qu'un site visité depuis le poste de développement ne puisse pas appeler le Takibo local.
- **`test` et `ci`** : liste exacte. Les tests doivent prouver le comportement de
  production, pas celui du poste de développement.
- **Aucun profil** : règles de production.

La règle est « `dev` peut ouvrir », et non « tout ce qui n'est pas la production est
ouvert ». Le précédent `@Profile({"dev", "test", "ci"})` de
`ResolvedOAuthClientResolverConfig` ne s'applique pas ici.

### D3 — Credentials CORS désactivés, sans interrupteur

`allowCredentials` vaut `false`, dans tous les profils, `dev` compris.

- L'API s'authentifie par l'en-tête `Authorization`, que le frontend pose lui-même. Le
  mode credentials de CORS concerne les cookies, l'authentification HTTP native et les
  certificats client. Le désactiver ne gêne pas un appel porteur d'un jeton, à condition
  qu'`Authorization` figure dans les en-têtes autorisés.
- Ce récit ne crée pas de propriété pour réactiver les credentials. Si TAS-GRANTS-03 ou
  un BFF en démontre le besoin, ce récit-là l'ajoute, limité à ses routes et à une liste
  exacte.

### D4 — Déclaration explicite sur les deux chaînes

- La chaîne API et la chaîne TAS déclarent explicitement la même source, construite depuis
  la liste validée. Le comportement ne dépend plus de l'ordre d'instanciation des beans.
- Les méthodes sont listées explicitement et incluent `PATCH`.
- Les en-têtes autorisés sont listés explicitement, pas `*`. La base est `Authorization`,
  `Content-Type`, `Accept` et `Accept-Language`. L'implémentation tranche l'ajout des
  en-têtes de corrélation que l'API lit réellement : `X-Request-Id`, `X-Correlation-Id`,
  `X-Client-Id` et `X-KRYPTION-ID`.
- Les en-têtes exposés au JavaScript sont listés explicitement, par exemple `Location`,
  `X-KRYPTION-ID` et `X-Trace-Id`, selon l'usage réel.
- Le commentaire « En dev, on ouvre large » disparaît.

### D5 — Origines par client TMS : différé à TAS-GRANTS-04

Repère Keycloak : les « Web Origins » y sont portés par client. Verdict : **DIFFÉRER**.

- Un preflight ne porte ni jeton ni `client_id`. Une vérification par client ne peut donc
  s'appliquer qu'à la requête réelle, pas au preflight.
- Le premier appel navigateur qui résout un client est l'échange de code d'un client
  public sur `/oauth2/token`, qui appartient à TAS-GRANTS-04.
- D'ici là, la liste globale reste le plafond fixé par l'installation. Une origine par
  client ne pourra jamais l'élargir.

### D6 — Ce que la liste fait, et ne fait pas, pour Android

- L'origine d'une WebView Capacitor dépend de sa configuration (`server.androidScheme`,
  `server.hostname`). Dans les versions récentes, elle vaut par défaut `https://localhost`.
  À vérifier sur la version retenue par ACCES-11.
- `https://localhost` n'est pas distinctif : toute application Capacitor laissée par
  défaut présente la même origine. Un nom d'hôte dédié est recommandé, décision qui
  appartient à ACCES-11 (D7 du lot Mbuyamba).
- CORS n'authentifie pas une application. Un client HTTP natif, y compris le greffon HTTP
  natif de Capacitor, n'envoie pas d'`Origin` de navigateur et n'est pas soumis à CORS.
  L'entrée Android de la liste autorise le JavaScript de la WebView. Elle ne prouve pas que
  l'appelant est l'application Mbuyamba.

## Lien avec TAS-GRANTS-03

TAS-GRANTS-03 (« Authentification humaine SAS », branche
`feat/tas-human-authentication-03`) est ordonnancé dans
[README-TAS-GRANTS.md](README-TAS-GRANTS.md) mais pas encore rédigé. Il introduit la
première session navigateur de Takibo, portée par un cookie. À partir de là, une
configuration CORS qui reflète toute origine avec les credentials cesserait d'être latente.

Ce récit doit donc être fusionné avant que TAS-GRANTS-03 démarre. Les contraintes suivantes
sont à reprendre à la rédaction de TAS-GRANTS-03 :

1. SEC-TMS-05 figure dans la colonne « Dépend de » de TAS-GRANTS-03.
2. La page de connexion et `/oauth2/authorize` sont atteintes par navigation de premier
   niveau, pas par `fetch`. Elles n'ont pas besoin de CORS.
3. Aucune route porteuse du cookie de session n'émet `Access-Control-Allow-Credentials:
   true`. Si un besoin contraire est démontré, TAS-GRANTS-03 l'ajoute route par route,
   avec une liste exacte et jamais un motif.
4. CORS n'est pas une protection CSRF. La chaîne de session garde CSRF actif, et
   TAS-GRANTS-03 fixe l'attribut `SameSite` de son cookie.

## Périmètre

- Remplacer la configuration codée en dur de `CorsConfig` par une configuration pilotée
  par `takibo.cors.allowed-origins`.
- Valider la liste au démarrage selon D1 et D2, avec refus de démarrer hors `dev`.
- `application.yml` : liste vide par défaut, variable `TAKIBO_CORS_ALLOWED_ORIGINS`
  documentée en commentaire, comme les clés TAS.
- `application-dev.yml` : ouverture explicite du poste de développement.
- Profils `test` et `ci` : liste exacte d'origines de test.
- `allowCredentials` à `false` sans interrupteur (D3).
- Méthodes, en-têtes autorisés et en-têtes exposés explicites (D4).
- Déclaration explicite de la même source sur la chaîne API et la chaîne TAS (D4).
- Tests HTTP traversant les vraies chaînes Spring Security, et tests de validation au
  démarrage.

## Hors périmètre

- Brancher les origines par client TMS (`oauth2_client_cors_origins`) : TAS-GRANTS-04.
- Session navigateur, cookie, CSRF et `SameSite` de SAS : TAS-GRANTS-03.
- Toute réactivation des credentials CORS.
- Le BFF de Takibo-ui.
- La configuration Capacitor et le nom d'hôte de la WebView : ACCES-11, côté Mbuyamba.
- Distinguer le destinataire d'un jeton (audience) : autre prérequis Mbuyamba, à vérifier
  en ACCES-03.
- Étendre la CLI `takibo-install-keys` : les origines ne sont pas de la matière
  cryptographique.
- En-têtes de sécurité HTTP (CSP, HSTS) et limitation de débit.

## Critères d'acceptation

Les origines d'exemple ci-dessous sont celles du profil `test`. Aucune n'est un domaine réel.

### AC-01 — Preflight d'une origine configurée accepté

`OPTIONS /api/v1/auth/login` avec `Origin: https://portail.example.test`,
`Access-Control-Request-Method: POST` et
`Access-Control-Request-Headers: authorization, content-type` renvoie `200` avec :

- `Access-Control-Allow-Origin: https://portail.example.test`, jamais `*` ;
- `Vary` contenant `Origin` ;
- aucun `Access-Control-Allow-Credentials: true`.

### AC-02 — Requête réelle d'une origine configurée acceptée

Une route authentifiée de la chaîne API, appelée avec l'origine configurée et un jeton
simulé comme dans `ActuatorSecurityIntegrationTest`, rend son statut nominal. La réponse
porte `Access-Control-Allow-Origin` égal à cette origine.

### AC-03 — Preflight d'une origine inconnue refusé

Le même preflight qu'AC-01 avec `Origin: https://inconnu.example.test` renvoie `403`, sans
aucun en-tête `Access-Control-Allow-Origin`.

### AC-04 — Requête réelle d'une origine inconnue refusée

La requête d'AC-02, avec un jeton valide mais `Origin: https://inconnu.example.test`,
renvoie `403` sans `Access-Control-Allow-Origin`. Le jeton valide ne compense pas l'origine
refusée.

### AC-05 — Les voisins d'une origine configurée sont refusés

Chacune de ces origines est refusée comme en AC-03 :

```text
https://portail.example.test.inconnu.test   suffixe ajouté
https://faux-portail.example.test           préfixe ajouté
https://example.test                        domaine parent
http://portail.example.test                 schéma
https://portail.example.test:8443           port
null                                        origine opaque
```

### AC-06 — Les appels sans `Origin` ne changent pas

Une requête sans en-tête `Origin` (BVT, appel de serveur à serveur, récupération des JWKS
par un serveur de ressources) obtient le même statut qu'avant le récit.

### AC-07 — La chaîne TAS applique la même liste, explicitement

**Amendé le 2026-09-16, à l'implémentation.** Formulation initiale : « Un preflight sur
`/oauth2/token` est accepté pour l'origine configurée et refusé pour une origine inconnue. »

Un `POST /oauth2/token` en formulaire, sans en-tête `Authorization`, est refusé (`403`, sans
`Access-Control-Allow-Origin`) pour une origine inconnue, et reçoit
`Access-Control-Allow-Origin` pour l'origine configurée. C'est une requête CORS simple, sans
preflight : la forme de l'échange de code d'une SPA publique (TAS-GRANTS-04). Le test prouve
que la chaîne TAS déclare la source elle-même et ne dépend plus de `applyCorsIfAvailable` :
aucun bean `UrlBasedCorsConfigurationSource` n'existe, et chaque chaîne porte un `CorsFilter`.

Pourquoi l'amendement : le preflight `OPTIONS /oauth2/token` n'atteint jamais le CORS.
`TenantResolutionFilter` (TAS-GRANTS-01), filtre servlet exécuté avant Spring Security, le
rejette en `401 invalid_client` faute de `client_id`, sans en-tête CORS, quelle que soit
l'origine. Le navigateur refuse donc tout appel `/oauth2/token` qui exigerait un preflight,
dont un secret client envoyé en `Basic`. Ce récit consigne ce comportement, le fige par un
test sur un vrai port, et ne le modifie pas : TAS-GRANTS-04 décide s'il faut l'ouvrir.

### AC-08 — Hors `dev`, une valeur interdite empêche le démarrage

Sans profil, puis sous `test` et sous `ci`, chacune des valeurs interdites du tableau D1
fait échouer le démarrage du contexte. Le message nomme `takibo.cors.allowed-origins` et la
valeur refusée.

### AC-09 — Le profil `dev` peut rester ouvert

Sous le profil `dev`, un motif ou le joker est accepté au démarrage, et une origine qui
correspond au motif est acceptée en preflight.

### AC-10 — Une liste vide démarre et ferme les origines croisées

Avec une liste vide, le contexte démarre. Toute requête portant un `Origin` croisé est
refusée comme en AC-03, et AC-06 reste vrai.

### AC-11 — Jamais de credentials

Dans aucun profil, `dev` compris, et sur aucune des deux chaînes, une réponse ne porte
`Access-Control-Allow-Credentials: true`.

### AC-12 — `PATCH` est autorisé en preflight

Un preflight avec `Access-Control-Request-Method: PATCH` depuis l'origine configurée est
accepté.

### AC-13 — Aucune régression

Les suites existantes et la BVT restent vertes.

## Vérifications

- Test HTTP de la politique CORS dans `takibo-iam-boot/src/test/java/com/takibo/iamboot/security`,
  à côté d'`ActuatorSecurityIntegrationTest`.
- Tests de validation de la liste au démarrage, par profil.
- Suite complète `:takibo-iam-boot:test`.
- Suite complète `:takibo-security-management:test`.
- Suite complète `:takibo-authorization-server:test`.
- CI complète verte : la PR touche du code, le filtre documentaire ne s'applique pas.

## Livraison

Choix tranchés à l'implémentation, là où le récit les laissait ouverts :

- **Validation reprise, pas importée.** `CorsAllowedOrigins` (`takibo-iam-boot`) applique
  les règles de forme de `ClientCorsOrigin`. `takibo-iam-boot` n'importait aucune classe du
  domaine TMS, et le contrat d'installation n'appartient pas à ce domaine.
- **Port par défaut omis à la normalisation.** Un navigateur n'envoie jamais `:443` ni
  `:80` dans `Origin` : `https://portail.example:443` configuré ne correspondrait à aucune
  requête. Il est normalisé en `https://portail.example`.
- **La source n'est pas un `UrlBasedCorsConfigurationSource`.** Aucune chaîne n'hérite
  donc la politique d'office ; la chaîne API et la chaîne TAS la déclarent par
  `cors.configurationSource(...)`.
- **Motifs du profil `dev`** : `http://localhost:[*]` et `http://127.0.0.1:[*]`, syntaxe de
  port de Spring, plus stricte que `http://localhost:*`.
- **En-têtes autorisés** : `Authorization`, `Content-Type`, `Accept`, `Accept-Language`,
  `X-Request-Id`, `X-Correlation-Id`, `X-Client-Id`, `X-KRYPTION-ID` — ceux que l'API lit.
- **En-têtes exposés** : `Location`, `X-KRYPTION-ID`, `X-Trace-Id`. `X-Client-Ip` n'est pas
  exposé : il rendrait au script l'adresse vue par le serveur.
- **Méthodes** : `GET`, `HEAD`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`. `HEAD` est
  ajouté : le processeur CORS vérifie aussi la méthode des requêtes réelles.
- **Profil `ci`** : aucun fichier dédié, donc liste vide. La BVT n'envoie pas d'`Origin`.

Tests : `CorsAllowedOriginsTest` (20), `CorsConfigStartupTest` (5, AC-08 à AC-11),
`CorsPolicyIntegrationTest` (8, vrai port, filtres servlet compris), plus la déclaration
CORS vérifiée dans `SecurityConfigTest` et `TakiboAuthorizationServerConfigurationTest`.

## Branche

`security/restrict-cors-origins`

## Commit proposé

`fix(security): restrict cors to configured origins`
