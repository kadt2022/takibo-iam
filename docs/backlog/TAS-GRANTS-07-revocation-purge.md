# TAS-GRANTS-07 — Révocation, invalidation et purge

**Statut :** À FAIRE  
**Branche :** `feat/tas-revocation-purge-07`  
**Dépendances :** TAS-GRANTS-05 et TAS-GRANTS-06

## Récit

En tant qu’équipe sécurité, nous voulons révoquer les autorisations persistées et purger les secrets expirés afin de limiter l’impact d’un token compromis et la conservation inutile de données sensibles.

## Périmètre

- Câbler l’endpoint standard de révocation sur la persistance OAuth 2.0.
- Révoquer un token ou une famille selon le type de compromission.
- Utiliser le mécanisme déjà présent `account_security_state` et son `current_epoch` pour l’invalidation globale d’un compte; ne pas concevoir un second registre d’époque.
- Ajouter un job idempotent de purge des codes, tokens et autorisations expirés.
- Produire des événements d’audit exploitables sans inclure de secret.

## Critères d’acceptation

- [ ] Un refresh token révoqué ne peut plus renouveler un access token.
- [ ] La révocation d’une famille bloque tous ses refresh tokens actifs.
- [ ] Une hausse de l’époque de sécurité invalide les sessions/refresh concernés.
- [ ] La lecture et l’incrément de l’époque utilisent `account_security_state` ainsi que son entité/record existant; aucune table ou notion concurrente n’est ajoutée.
- [ ] Les access tokens JWT restent courts, typiquement 5 à 10 minutes, et sont validables localement jusqu’à expiration sauf contrôle sensible explicite.
- [ ] Le job de purge supprime uniquement les enregistrements expirés admissibles et peut être rejoué sans dommage.
- [ ] Les vérifications DB de révocation sont réservées aux points sensibles documentés.
- [ ] Les métriques couvrent révocations, rejeux détectés, purges et erreurs.
- [ ] Les logs et événements ne contiennent aucun token, code ou secret en clair.

## Tests attendus

- Révocation individuelle, familiale et par époque de sécurité.
- Lecture, incrément concurrent et propagation de `account_security_state.current_epoch`.
- Purge avec données actives, expirées et déjà supprimées.
- Concurrence entre refresh, révocation et purge.
- Non-régression des trois grants et de `client_credentials`.

## Hors périmètre

- Introspection distante obligatoire à chaque requête métier.
- Redis obligatoire.
- Allongement de la durée de vie des access tokens.
