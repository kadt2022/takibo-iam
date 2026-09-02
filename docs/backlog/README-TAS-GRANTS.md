# Backlog TAS — nouveaux grant types OAuth 2.0

## Objectif

Préparer l’intégration progressive de `authorization_code`, `refresh_token` et `urn:ietf:params:oauth:grant-type:device_code` dans Takibo Authorization Server (TAS), sans régression du flux `client_credentials`.

## Frontières d’architecture

- TAS porte les endpoints OAuth 2.0, les sessions, l’émission, le refresh et la révocation des tokens.
- TMS reste propriétaire des tenants, des clients OAuth 2.0 et de la résolution du contexte.
- TIS-Core reste la source de vérité pour l’identité et les permissions effectives.
- TAS valide la cohérence du sujet, du tenant et du client, puis signe les claims reçus. Il ne calcule et ne fabrique jamais les permissions.
- Les adaptations entre modules sont câblées dans `takibo-iam-boot`; TAS ne dépend pas directement des implémentations de TIS-Core ou TMS.
- L’architecture cible de ce lot est un monolithe modulaire. Les ports définis ici pourront recevoir des adaptateurs réseau ultérieurement.

## Ordre d’exécution

À la finition validée d'un récit, passer son **Statut** à `TERMINÉ`, déplacer son fichier
avec `git mv` vers `docs/terminer`, puis mettre à jour la ligne correspondante ci-dessous.
Une PR ouverte ne suffit pas à clôturer.

| Ordre | Récit | Statut | Branche | Dépend de |
|---:|---|---|---|---|
| 00 | [Filet de sécurité `client_credentials`](../terminer/TAS-GRANTS-00-filet-securite-client-credentials.md) | **TERMINÉ** (PR #51, 2026-08-24) | `test/tas-client-credentials-baseline-00` | — |
| 01 | [Résolution réelle du tenant](../terminer/TAS-GRANTS-01-resolution-tenant.md) | **TERMINÉ** (PR #56, 2026-08-29) | `feat/tas-tenant-resolution-01` | 00 |
| 02 | [Persistance OAuth 2.0](../terminer/TAS-GRANTS-02-persistance-oauth.md) | **TERMINÉ** (PR #57, 2026-09-01) | `feat/tas-oauth-persistence-02` | 01, 02A |
| 02A | [Clés de signature persistantes](TAS-GRANTS-02A-cles-signature-persistantes.md) | code fusionné (PR #53, #54), **non clôturé** : 9/11 critères vérifiés | `feat/tas-signing-keys-02a` | 00; parallèle à 01 |
| 02B | [Rétention des autorisations expirées](TAS-GRANTS-02B-retention-autorisations-oauth.md) | à faire | `feat/tas-oauth-retention-02b` | 02 — obligatoire avant production |
| 03 | Authentification humaine SAS | à rédiger | `feat/tas-human-authentication-03` | 02, TIS-Core stable |
| 04 | Authorization Code + PKCE | à rédiger | `feat/tas-authorization-code-pkce-04` | 03, 02A |
| 05 | Refresh Token | à rédiger | `feat/tas-refresh-token-05` | 04, 02A |
| 06 | Device Authorization Grant | à rédiger | `feat/tas-device-code-06` | 03, 02, 02A |
| 07 | [Révocation](TAS-GRANTS-07-revocation-purge.md) | à faire | `feat/tas-revocation-purge-07` | 02B, 05, 06 |

Les récits 03 à 06 sont ordonnancés mais pas encore rédigés. Le récit 04 ne peut pas
démarrer avant que 03 existe : c'est lui qui décide de la forme du principal humain.

## Règles de livraison

- Un récit = une branche = une PR créée depuis un `main` à jour.
- Aucun récit fonctionnel ne commence avant la validation du récit 00.
- Chaque PR inclut migrations, tests, documentation et observabilité nécessaires à son périmètre.
- La CI complète doit être verte avant fusion.
- Après validation, le récit passe au statut `TERMINÉ` et peut être déplacé vers `docs/terminer` avec l’index documentaire mis à jour dans la même PR.

## Décisions transversales

- En production, TMS crée et administre les clients OAuth 2.0. TAS les consomme en lecture et ne sauvegarde jamais un `RegisteredClient` dans le registre TMS. Une source in-memory explicitement limitée au profil dev peut fournir le client PLATFORM de test.
- `client_id` est globalement unique et permet une résolution unique vers un `ResolvedOAuthClient` contenant plan, frontière et politique OAuth.
- La persistance SAS utilise l’identifiant technique `RegisteredClient.id`; elle ne remplace pas le registre des clients TMS.
- Toute valeur secrète nécessaire à SAS est chiffrée de façon récupérable et possède un hash de recherche globalement unique.
- Les clés de signature survivent aux redémarrages et disposent d’une stratégie de rotation avant l’activation des nouveaux grants en production.
- Une SPA publique utilise `authorization_code` + PKCE sans refresh token : SAS peut retourner silencieusement `null` pour le refresh avec `ClientAuthenticationMethod.NONE`. Un besoin de refresh impose un client confidentiel/BFF et un profil client TMS distinct.
- TAS-GRANTS-02 porte explicitement le mapping des `TokenSettings` et du consentement. Les colonnes TTL nulles doivent conserver les durées machine actuelles; la rotation impose `reuseRefreshTokens(false)` aux clients confidentiels autorisés.
- PostgreSQL est la seule base cible de ce lot. Les migrations utilisent notamment `jsonb` et des index uniques partiels; le profil MySQL sans répertoire Flyway complet n’est pas déclaré supporté.
- **TAS-GRANTS-02A se développe en parallèle de 01, mais précède 02.** Deux indépendances distinctes, à ne pas confondre. Côté données, `tas_signing_keys` est sans rapport avec `oauth2_authorization` : rien n'oblige à les migrer dans l'ordre. Côté code, 02A définit le port de chiffrement au repos que 02 consomme, ce qui crée une dépendance réelle. Le coût sur le chemin critique reste faible, 02A pouvant avancer pendant 01 ; 02 attend simplement les deux. Les grants 04, 05 et 06 attendent également la fusion de 02A.
- **Le chiffrement au repos appartient à TAS-GRANTS-02A.** Les récits 02 et 02A en ont tous deux besoin — valeurs de codes et de tokens pour l'un, matière privée des clés pour l'autre. Sans propriétaire désigné, deux mécanismes seraient construits, ou un secret de configuration serait figé par le premier arrivé. 02A porte déjà la colonne `private_key_encrypted` et prévoit un port de stockage ouvert à un KMS/HSM : il définit le port, 02 le consomme. C'est cette décision, et non une contrainte de schéma, qui fait de 02A un prérequis de 02.
- **Le claim d'époque de sécurité se décide en 03 ou 04, jamais en 07.** `TakiboTokenClaims` n'en porte aucun aujourd'hui. Comparer l'époque d'un token à l'époque courante suppose qu'elle figure dans le JWT ; l'introduire au récit 07 reviendrait à modifier le contrat de token alors que des tokens circulent déjà. La décision appartient au récit qui définit les claims humains, même si son exploitation attend 07.
- **La purge (expiration) et la révocation (invalidation anticipée) sont deux récits distincts, jamais un seul scheduler.** TAS-GRANTS-02B élimine physiquement ce qui est déjà expiré, sans dépendre des grants refresh/device qui n'existent pas encore ; TAS-GRANTS-07 invalide un token avant son terme naturel mais ne supprime rien lui-même — les lignes qu'il invalide restent éligibles à la purge de 02B comme n'importe quelle ligne expirée. Cette séparation permet à 02B de rendre la persistance de 02 exploitable en production sans attendre 04/05/06/07.

## Hors lot

- Ajout ou extension d’OIDC.
- Calcul du RBAC dans TAS.
- Découpage immédiat en microservices.
- Redis obligatoire pour les sessions.
- Resource Owner Password Credentials Grant.
- Support et migrations MySQL.
- Migration du client PLATFORM de développement vers `oauth2_clients`. Cette évolution exige un récit dédié couvrant la nullabilité du registre, le mapping du plan et les claims; elle ne fait pas partie de la résolution de tenant du récit 01.
