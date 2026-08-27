# SEC-TMS-04 — Traduire les erreurs de configuration client au lieu de renvoyer 500

**Statut** : à faire
**Origine** : découvert par la BVT unifiée (TR3, saga « 10 - Clients OAuth2 et OIDC »)
**Dépend de** : —
**Risque** : moyen

## Contexte

Une requête de création de client OAuth2 portant un `grantTypes` invalide renvoie
aujourd'hui **`500 INTERNAL_ERROR`** au lieu d'une erreur de validation :

```json
POST /api/v1/orgs/{orgId}/spaces/{spaceId}/clients
{ "clientId": "...", "clientType": "CONFIDENTIAL", "grantTypes": ["password"] }

→ 500  {"code":"INTERNAL_ERROR","message":"Une erreur inattendue est survenue."}
```

Cause : `ClientGrantType.ofAll()` lève `InvalidGrantTypeException`, qui étend
`RuntimeException` et **n'est enregistrée dans aucune règle Sentinel**. Elle tombe donc
dans le handler générique.

Un balayage statique du paquet `domain/exception` recense onze exceptions absentes de
`SentinelRuleRegistrar`. **Ce critère s'est révélé non prédictif** : le sondage empirique
de la surface `POST .../clients` donne trois comportements distincts.

> **Piège méthodologique à connaître.** Une première campagne de sondage utilisait le grant
> `client_credentials` pour tester les URI. Elle concluait à tort que trois validations
> étaient conformes. En réalité `OAuthClientCredentialsProfilePolicy.assertClientCredentialsShape`
> rejette **toute** URI non vide sur ce grant, **quelle que soit sa syntaxe** :
>
> ```java
> if (hasValues(registration.redirectUris())
>         || hasValues(registration.postLogoutRedirectUris())
>         || hasValues(registration.corsOrigins())) {
>     throw new InvalidClientConfigurationException(
>             "client_credentials must not include redirect/cors/post-logout URIs");
> }
> ```
>
> Les `400` observés venaient de cette règle de forme, jamais de la validation de syntaxe.
> **Toute sonde d'URI doit utiliser un grant autorisant la redirection** (`authorization_code`).

Sondage corrigé de `POST .../clients` :

| Payload invalide | Grant utilisé | HTTP réel | Verdict |
| --- | --- | --- | --- |
| `grantTypes: ["password"]` | — | **500** | à traduire |
| `authorization_code` sans `redirectUris` | `authorization_code` | **500** | à traduire |
| `PUBLIC` + `requirePkce: false` | `authorization_code` | **500** | à traduire |
| `redirectUris: ["pas-une-uri"]` | `authorization_code` | **500** | à traduire |
| `corsOrigins: ["pas-une-origine"]` | `authorization_code` | **500** | à traduire |
| `postLogoutRedirectUris: ["pas-une-uri"]` | `authorization_code` | **500** | à traduire |
| `redirectUris: ["https://valide.example/cb"]` *(témoin)* | `authorization_code` | 201 ✅ | conforme |
| `scopes: ["scope invalide!"]` | `client_credentials` | **201** | **à trancher** |
| `PUBLIC` + `requireClientSecret: true` | `client_credentials` | **201** | **à trancher** |

Trois enseignements :

1. **Six chemins produisent un `500`** sur une entrée fournie par l'appelant. C'est
   pratiquement toute la validation de configuration client qui échoue en erreur serveur.
2. Le témoin (URI valide → `201`) prouve que le chemin nominal fonctionne : le défaut est
   bien dans la **traduction de l'erreur**, pas dans la création de client.
3. **Deux configurations sont acceptées (`201`)** alors que le domaine déclare des
   exceptions pour les refuser. C'est potentiellement plus grave que les `500` : un `500`
   refuse l'appelant, un `201` laisse entrer une configuration interdite.

Les exceptions non sondées ici (`OrganizationDisabledException`, `SpaceQuotaExceededException`,
`PublicClientAuthMethodNotNoneException`) n'ont pas de payload trivial et restent à établir.

## Loi du récit

Une entrée invalide fournie par un client ne produit jamais `500`. Le code `500` est
réservé aux défaillances du serveur ; toute erreur de configuration fournie par l'appelant
est un `400`, et tout conflit d'état un `409`.

## Pourquoi cela compte

- **Contrat** : un client ne peut pas distinguer « ma requête est mauvaise » de
  « le serveur est en panne », donc ne peut ni corriger ni réessayer intelligemment.
- **Exploitation** : les `500` polluent la supervision et masquent les vraies pannes.
- **Sécurité** : un chemin d'exception non traité est un chemin non testé ; le message
  générique est correct aujourd'hui, mais rien ne garantit qu'une future exception de la
  même famille ne fuite pas de détail interne.

## Périmètre

- Enregistrer les exceptions de configuration client dans `SentinelRuleRegistrar` avec le
  statut correct (`400` pour une entrée invalide, `409` pour un conflit d'état).
- Établir, pour chacune des onze exceptions listées, si elle est atteignable par une route
  REST ; documenter celles qui ne le sont pas plutôt que de les enregistrer à l'aveugle.
- Choisir un `SentinelErrorCode` explicite plutôt que `BAD_REQUEST` générique lorsque
  l'appelant a besoin de savoir *quel* champ est en cause
  (`OAUTH_CLIENT_PROFILE_INVALID` existe déjà).
- Ne jamais faire fuiter dans le message la liste exhaustive des valeurs acceptées si
  celle-ci révèle des capacités non publiques.

## Hors périmètre

- Refondre la validation de configuration client elle-même.
- Modifier le contrat des surfaces autres que les clients OAuth2.

## Critères d'acceptation

### AC-01 — Grant type invalide traduit

`POST .../clients` avec `grantTypes: ["password"]` renvoie **`400`** et un corps Sentinel
complet (`code`, `traceId`, `path`, `status` cohérent).

### AC-02 — Grant `authorization_code` sans URI de redirection traduit

`grantTypes: ["authorization_code"]` sans `redirectUris` : `400`, plus `PUBLIC` +
`requirePkce: false` sur ce même grant : `400`.

### AC-02b — Scope invalide : décision explicite

`scopes: ["scope invalide!"]` est aujourd'hui **accepté (`201`)** alors que
`InvalidScopeException` existe. Deux issues possibles, à trancher :

- soit les scopes sont volontairement libres (chaîne opaque du tenant) → l'exception est
  morte et doit être supprimée, et le fait documenté ;
- soit un format est exigé → la validation doit être appliquée et rendre `400`.

Ne pas laisser le code déclarer une règle que la surface n'applique pas.

### AC-02c — Client public avec secret : décision explicite

`clientType: "PUBLIC"` + `requireClientSecret: true` est aujourd'hui **accepté (`201`)**
alors que `PublicClientMustNotHaveSecretException` existe. Vérifier si la valeur est
normalisée silencieusement (auquel cas le documenter) ou simplement ignorée (auquel cas
la refuser en `400`). Un client public porteur d'un secret est une contradiction du
modèle OAuth2 et ne doit jamais exister en base.

### AC-03 — Aucune fuite

Le corps d'erreur ne contient ni trace d'exception, ni nom de classe Java, ni détail
d'implémentation.

### AC-04 — Atteignabilité documentée

Chaque exception de la liste est soit enregistrée, soit documentée comme non atteignable
par une route REST, avec la raison.

### AC-05 — Validation de syntaxe d'URI traduite

Sur un grant autorisant la redirection (`authorization_code`), chacun de ces payloads
renvoie `400` et non `500` :

```text
redirectUris:           ["pas-une-uri"]
corsOrigins:            ["pas-une-origine"]
postLogoutRedirectUris: ["pas-une-uri"]
```

Ces scénarios rejoindront la saga « 10 - Clients OAuth2 et OIDC » de la BVT.

### AC-06 — Aucune régression

Les scénarios déjà verts de la saga restent verts, notamment
`400 — Client — client_credentials refuse toute URI de redirection` (règle de forme) et
`400 — Client — Identifiant de client absent`.

## Vérifications

- `:takibo-management-service:test`
- `:takibo-security-management:test`
- BVT : réintégrer dans la saga « 10 - Clients OAuth2 et OIDC » le scénario

  ```text
  400 - Client - Grant type non autorise refuse
  ```

  retiré de la collection tant que ce défaut existe.

## Branche

`fix/tms-client-configuration-error-translation`

## Commit proposé

`fix(tms): translate client configuration errors instead of failing with 500`
