# TAS-GRANTS-07 — Révocation et invalidation

**Statut :** À FAIRE  
**Branche :** `feat/tas-revocation-purge-07`  
**Dépendances :** TAS-GRANTS-02B, TAS-GRANTS-05 et TAS-GRANTS-06

## Récit

En tant qu’équipe sécurité, nous voulons révoquer les autorisations persistées avant leur expiration naturelle afin de limiter l’impact d’un token compromis.

## Distinction avec TAS-GRANTS-02B

```text
Révocation anticipée -> 07 invalide
Expiration           -> 02B nettoie
```

07 ne supprime jamais de ligne : il invalide un token, une famille de refresh tokens ou une
époque de sécurité, avant leur terme naturel. Les lignes qu'il invalide restent en base, comme
n'importe quelle autorisation expirée, et sont physiquement éliminées plus tard par la politique
de rétention de TAS-GRANTS-02B — 07 ne porte aucun mécanisme de suppression qui lui soit propre.

## Périmètre

- Câbler l’endpoint standard de révocation sur la persistance OAuth 2.0.
- Révoquer un token ou une famille selon le type de compromission.
- Utiliser le mécanisme déjà présent `account_security_state` et son `current_epoch` pour l’invalidation globale d’un compte; ne pas concevoir un second registre d’époque.
- Produire des événements d’audit exploitables sans inclure de secret.

## Critères d’acceptation

- [ ] Un refresh token révoqué ne peut plus renouveler un access token.
- [ ] La révocation d’une famille bloque tous ses refresh tokens actifs.
- [ ] Une hausse de l’époque de sécurité invalide les sessions/refresh concernés.
- [ ] La lecture et l’incrément de l’époque utilisent `account_security_state` ainsi que son entité/record existant; aucune table ou notion concurrente n’est ajoutée.
- [ ] Les access tokens JWT restent courts, typiquement 5 à 10 minutes, et sont validables localement jusqu’à expiration sauf contrôle sensible explicite.
- [ ] Une autorisation révoquée n'est pas supprimée par 07 lui-même; elle reste éligible à la purge de TAS-GRANTS-02B comme n'importe quelle ligne expirée.
- [ ] Les vérifications DB de révocation sont réservées aux points sensibles documentés.
- [ ] Les métriques couvrent révocations, rejeux détectés et erreurs.
- [ ] Les logs et événements ne contiennent aucun token, code ou secret en clair.

## Tests attendus

- Révocation individuelle, familiale et par époque de sécurité.
- Lecture, incrément concurrent et propagation de `account_security_state.current_epoch`.
- Concurrence entre un refresh, une révocation et une exécution du job de purge de TAS-GRANTS-02B.
- Non-régression des trois grants et de `client_credentials`.

## Hors périmètre

- Suppression physique des données expirées — TAS-GRANTS-02B.
- Introspection distante obligatoire à chaque requête métier.
- Redis obligatoire.
- Allongement de la durée de vie des access tokens.
