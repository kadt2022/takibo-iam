# TAS-GRANTS-02A — Clés de signature persistantes et rotation

**Statut :** À FAIRE  
**Branche :** `feat/tas-signing-keys-02a`  
**Dépendances :** TAS-GRANTS-00; développement parallèle possible avec TAS-GRANTS-01

## Récit

En tant qu’équipe sécurité, nous voulons que TAS utilise des clés de signature persistantes et rotatives afin qu’un redémarrage ou un déploiement n’invalide pas les JWT encore valides.

## Décision actée — portée des clés : single-issuer

TAS signe avec **une clé de plateforme**, pas une par organisation.

Le mot compte. TAKIBO **reste multi-tenant au sens métier** — organisations, spaces,
frontières situées dans chaque token. Ce qui est unique, c'est l'**émetteur** : une clé de
signature, un `iss`, un JWKS. Dire « mono-tenant » laisserait croire que TAS ne sert qu'un
client, ce qui est faux et masquerait la vraie décision.

`TakiboAuthorizationServerConfiguration` appelle `.issuer(...)`, ce qui force explicitement
une configuration single-issuer côté Spring Authorization Server ; `/oauth2/jwks` est un
endpoint global unique ; et les tokens humains et machine partagent le même `JwtEncoder`.
Passer en multi-issuer changerait le claim `iss` de tous les JWT en circulation et
obligerait chaque resource server à résoudre un JWKS par organisation — hors de portée de
ce récit, et contraire au besoin immédiat.

Le schéma d'origine était pourtant org-scopé de bout en bout, avec `org_id NOT NULL`.
Plutôt que de loger la clé de plateforme dans une organisation fictive, **la portée devient
explicite : `org_id NULL` signifie « clé de plateforme »**. Des clés org-scopées pourront
coexister le jour où le multi-issuer sera décidé, sans migration de données.

Ce choix a un prix, payé dans la migration `V202608270001__tas__platform_signing_key_scope` :
PostgreSQL considère les `NULL` comme distincts, donc l'index partiel d'origine porté par
`(org_id)` n'aurait plus rien empêché — deux clés de plateforme actives auraient coexisté en
silence et le JWKS aurait exposé deux émetteurs. L'unicité est donc scindée en deux index
partiels, et `kid` devient globalement unique puisqu'un JWKS unique ne peut pas exposer deux
clés homonymes.

## Décision actée — format du chiffre : versionné et identifié

```text
v1$<keyId>$<base64(iv || chiffre || tag)>
```

Trois parties, chacune répondant à un manque qui serait irrattrapable une fois des secrets
stockés.

**La version** permet au format d'évoluer. Sans elle, changer d'algorithme ou de taille d'IV
rendrait illisible tout ce qui a déjà été écrit : rien ne distinguerait un ancien chiffre
d'un nouveau. Une version inconnue est refusée plutôt que lue avec les règles d'aujourd'hui.

**L'identifiant de clé** permet à la clé de *chiffrement* de tourner — distincte de la clé de
signature, qui est l'objet du reste du récit. Sans lui, on ignorerait quelle clé a scellé
quelle ligne : il faudrait tout rechiffrer d'un seul tenant, ou ne jamais en changer. Avec
lui, l'ancienne clé reste acceptée en lecture pendant que la nouvelle chiffre, et les lignes
migrent à leur rythme. Il sert aussi après coup : si une clé fuit, l'identifiant dit
exactement quelles lignes sont concernées.

**L'IV tiré au hasard à chaque appel** rend la sortie autoportante et non déterministe : sans
cela, l'égalité de deux chiffres trahirait l'égalité de deux secrets.

Le séparateur `$` est hors de l'alphabet base64 et interdit dans un identifiant de clé
(`[A-Za-z0-9_-]{1,64}`), donc le découpage est sans ambiguïté. Deux clés partageant un
identifiant sont refusées à la construction : leurs chiffres deviendraient indéchiffrables
pour l'une des deux, sans qu'on sache laquelle.

## Décision actée — amorçage distinct de la rotation, génération portée par le domaine

Revue de PR #54 (Capitaine Pi). Trois clarifications, actées dans le code :

**Génération derrière un port de domaine.** `SigningKeyRotationService` appartenait au
domaine mais importait directement le générateur RSA et le JWK Nimbus — une inversion de
l'architecture hexagonale qui aurait compliqué le remplacement futur de RSA local par un KMS
ou un HSM. `SigningKeyMaterialGenerator` est désormais le seul point d'entrée de la matière
neuve dans le domaine ; `RsaSigningKeyGenerator` l'implémente en infrastructure et traduit
immédiatement vers le type de domaine `GeneratedSigningKeyMaterial`, sans laisser Nimbus
franchir la frontière.

**`initializeFirstIssuer()` distinct de `rotate(Duration)`.** L'ancienne opération unique
acceptait un chevauchement nul, ce qui aurait retiré l'ancienne émettrice avant l'expiration
des JWT encore valides qu'elle a signés. `rotate(Duration)` exige désormais un chevauchement
strictement positif ; l'amorçage d'une installation sans émettrice passe par
`initializeFirstIssuer()`, qui n'a rien à retirer et échoue — au lieu de retirer
silencieusement — si une émettrice existe déjà.

**`publish_until` distinct de `expires_at`.** La même colonne bornait à la fois la période de
validité d'une clé (cryptopériode) et la durée pendant laquelle une clé retirée reste publiée
pour la vérification — deux échéances qui ne coïncident pas nécessairement. `publish_until`
porte désormais seule la fin de publication, écrite uniquement par la rotation ; `expires_at`
reste réservée à une future politique de cryptopériode, non utilisée aujourd'hui.

## Périmètre

- Remplacer `DevJwkSourceConfiguration` hors profil de développement par un `JWKSource` adossé à `tas_signing_keys`.
- Charger la clé émettrice active avec un `kid` stable et exposer dans le JWKS les clés encore nécessaires à la vérification.
- Chiffrer la matière privée au repos; ne jamais la journaliser ni l’exposer dans le JWKS.
- Définir la rotation : création d’une nouvelle clé, activation atomique, période de chevauchement, retrait après expiration du dernier JWT signé par l’ancienne clé.
- Conserver la génération éphémère uniquement dans un profil de développement explicitement activé.
- Préparer un port de stockage permettant plus tard un KMS/HSM sans changer le domaine TAS.
- **Trancher la portée des clés : single-issuer ou multi-issuer.** `tas_signing_keys` est entièrement org-scopée — `org_id NOT NULL`, `uk_tas_sk_org_kid UNIQUE (org_id, kid)`, et un index unique partiel qui garantit un émetteur actif **par organisation**. Or `TakiboAuthorizationServerConfiguration` appelle `.issuer(...)`, ce qui force explicitement une configuration single-issuer côté Spring Authorization Server, et `/oauth2/jwks` est un endpoint global unique. Le schéma a donc été conçu pour un modèle que la configuration ferme.
- **Porter le chiffrement au repos pour tout le lot**, pas seulement pour la matière privée des clés : définir le port que TAS-GRANTS-02 consommera pour les valeurs de codes et de tokens.

## Critères d’acceptation

- [ ] Deux démarrages successifs de TAS chargent la même clé active et le même `kid` tant qu’aucune rotation n’a lieu.
- [ ] Un JWT émis avant redémarrage reste vérifiable jusqu’à son expiration.
- [ ] La contrainte d’un unique émetteur actif est respectée lors d’activations concurrentes.
- [ ] Après rotation, TAS signe avec la nouvelle clé et publie encore l’ancienne clé publique pendant la période de chevauchement.
- [ ] Une clé privée n’apparaît jamais dans le JWKS, les logs, les métriques ou une erreur.
- [ ] En profil non-dev, l’absence de clé active provoque un démarrage fail-closed avec un diagnostic exploitable.
- [ ] Les dates `not_before`, `expires_at` et `publish_until`, le statut et `is_issuer` sont appliqués.
- [ ] `client_credentials` PLATFORM et SPACE reste vérifiable avant et après redémarrage/rotation.
- [ ] La portée des clés est tranchée et écrite : soit single-issuer, et `tas_signing_keys.org_id` accueille la clé de plateforme sans organisation fabriquée ; soit multi-issuer, et le retrait de `.issuer(...)` ainsi que le changement d'URL d'issuer sont assumés avec leurs conséquences sur les JWT en circulation et la configuration des resource servers. Aucune organisation fictive n'est créée pour loger une clé globale.
- [ ] Le port de chiffrement au repos est défini et documenté pour TAS-GRANTS-02 ; aucun secret de chiffrement n'est figé dans la configuration.
- [ ] Le parcours humain `/api/v1/auth/login` reste vérifiable avant et après redémarrage : les tokens humains et machine partagent la même clé, propriété que ce récit ne doit pas rompre.

## Tests attendus

- Test d’intégration PostgreSQL : émission, fermeture complète du contexte Spring, démarrage d’un second contexte indépendant et vérification du JWT initial.
- Rotation avec chevauchement de deux clés et validation des deux signatures.
- Concurrence lors de l’activation et respect de l’unicité de l’émetteur actif.
- Clé absente, expirée, pas encore valide ou corrompue.
- Vérification que le JWKS ne contient que la matière publique.

## Hors périmètre

- Déploiement immédiat d’un KMS ou HSM.
- Rotation des secrets clients OAuth 2.0.
- Allongement de la durée de vie des access tokens.
- Persistance des autorisations OAuth, indépendante et portée par TAS-GRANTS-02.
