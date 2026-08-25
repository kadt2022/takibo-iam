# TAS-GRANTS-02A — Clés de signature persistantes et rotation

**Statut :** À FAIRE  
**Branche :** `feat/tas-signing-keys-02a`  
**Dépendances :** TAS-GRANTS-00; développement parallèle possible avec TAS-GRANTS-01 et 02

## Récit

En tant qu’équipe sécurité, nous voulons que TAS utilise des clés de signature persistantes et rotatives afin qu’un redémarrage ou un déploiement n’invalide pas les JWT encore valides.

## Périmètre

- Remplacer `DevJwkSourceConfiguration` hors profil de développement par un `JWKSource` adossé à `tas_signing_keys`.
- Charger la clé émettrice active avec un `kid` stable et exposer dans le JWKS les clés encore nécessaires à la vérification.
- Chiffrer la matière privée au repos; ne jamais la journaliser ni l’exposer dans le JWKS.
- Définir la rotation : création d’une nouvelle clé, activation atomique, période de chevauchement, retrait après expiration du dernier JWT signé par l’ancienne clé.
- Conserver la génération éphémère uniquement dans un profil de développement explicitement activé.
- Préparer un port de stockage permettant plus tard un KMS/HSM sans changer le domaine TAS.

## Critères d’acceptation

- [ ] Deux démarrages successifs de TAS chargent la même clé active et le même `kid` tant qu’aucune rotation n’a lieu.
- [ ] Un JWT émis avant redémarrage reste vérifiable jusqu’à son expiration.
- [ ] La contrainte d’un unique émetteur actif est respectée lors d’activations concurrentes.
- [ ] Après rotation, TAS signe avec la nouvelle clé et publie encore l’ancienne clé publique pendant la période de chevauchement.
- [ ] Une clé privée n’apparaît jamais dans le JWKS, les logs, les métriques ou une erreur.
- [ ] En profil non-dev, l’absence de clé active provoque un démarrage fail-closed avec un diagnostic exploitable.
- [ ] Les dates `not_before` et `expires_at`, le statut et `is_issuer` sont appliqués.
- [ ] `client_credentials` PLATFORM et SPACE reste vérifiable avant et après redémarrage/rotation.

## Tests attendus

- Test d’intégration PostgreSQL : émission, redémarrage du contexte et vérification du JWT initial.
- Rotation avec chevauchement de deux clés et validation des deux signatures.
- Concurrence lors de l’activation et respect de l’unicité de l’émetteur actif.
- Clé absente, expirée, pas encore valide ou corrompue.
- Vérification que le JWKS ne contient que la matière publique.

## Hors périmètre

- Déploiement immédiat d’un KMS ou HSM.
- Rotation des secrets clients OAuth 2.0.
- Allongement de la durée de vie des access tokens.
- Persistance des autorisations OAuth, indépendante et portée par TAS-GRANTS-02.
