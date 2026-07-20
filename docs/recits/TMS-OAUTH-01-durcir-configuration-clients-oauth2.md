# TMS-OAUTH-01 — Durcir la configuration des clients OAuth2

## Contexte

L'enregistrement des clients accepte des configurations trop permissives : grant
`password`, URN arbitraires, URI HTTP distantes ou avec fragment, durées négatives,
expiration de secret passée et JWK Set pouvant contenir du matériel privé.

Les champs et collections du contrat REST ne sont pas suffisamment bornés, ce qui
laisse également les contraintes de stockage et de sécurité être découvertes trop
tard dans le traitement.

## Loi du récit

Un client OAuth2 n'est persisté que si sa configuration est explicitement supportée,
cohérente avec son profil et sûre pour un usage protocolaire. Toute configuration
ambiguë ou dangereuse est refusée avec un statut `400` avant l'accès au repository.

## Périmètre

- Remplacer l'acceptation ouverte des grants par une allowlist explicite et retirer
  le grant `password`.
- Exiger au moins un grant type.
- Exiger HTTPS pour les redirect URIs, post-logout URIs et origines CORS, avec une
  exception limitée aux hôtes loopback de développement.
- Refuser les fragments et user-info dans les redirect URIs.
- Borner les identifiants, textes, collections, URI et JWK Sets au niveau REST et
  applicatif.
- Exiger des TTL positifs et bornés, ainsi qu'une expiration future pour les secrets.
- Refuser les algorithmes de signature faibles ou non signés.
- Valider `jwksUri`/`jwksJson`, leur exclusivité et l'absence de clé privée ou
  symétrique dans le JSON persisté.
- Analyser les JWK embarquées avec Nimbus JOSE, imposer RSA >= 2048 bits ou une
  courbe EC supportée, et vérifier la compatibilité clé/algorithme.
- Charger les paramètres JWK dans le serveur d'autorisation afin que les clients
  `private_key_jwt` enregistrés puissent réellement être authentifiés.
- Masquer `jwksJson` dans la représentation textuelle du DTO.

## Critères d'acceptation

### AC-01 — Grants supportés uniquement

`password`, les URN non reconnus, les valeurs vides et une liste de grants vide sont
refusés. Les quatre grants documentés restent acceptés.

### AC-02 — URI sûres

Une URI HTTP distante, une redirect URI avec fragment ou user-info et une origine
CORS HTTP distante sont refusées. HTTPS et les URI HTTP loopback restent acceptées.

### AC-03 — Secrets et durées cohérents

Les TTL non positifs, un refresh TTL inférieur ou égal à l'access TTL et une
expiration de secret passée sont refusés. Un client public ou `private_key_jwt` ne
peut pas porter d'expiration de secret.

### AC-04 — JWK publics uniquement

`jwksUri` exige HTTPS. `jwksJson` doit être un JWK Set valide de taille bornée,
contenir uniquement des clés publiques asymétriques et ne peut pas être fourni avec
`jwksUri`. Pour `private_key_jwt`, `idTokenSignedAlg` est obligatoire et doit être
compatible avec au moins une clé du JWK Set.

### AC-05 — Frontière REST bornée

Un identifiant non sûr, une valeur trop longue ou une collection trop grande répond
`400` avant l'appel du service applicatif.

## Branche

`security/tms-oauth-client-validation`

## Commit proposé

`fix(management): harden oauth client configuration validation`
