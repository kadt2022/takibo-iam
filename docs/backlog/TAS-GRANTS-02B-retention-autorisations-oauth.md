# TAS-GRANTS-02B — Rétention des autorisations OAuth 2.0 expirées

**Statut :** À FAIRE  
**Branche :** `feat/tas-oauth-retention-02b`  
**Dépendances :** TAS-GRANTS-02  
**Obligatoire avant production.** Aucun déploiement durable ne doit activer la persistance de TAS-GRANTS-02 sans cette politique de rétention.

## Récit

En tant que TAS, nous voulons supprimer automatiquement les autorisations OAuth 2.0 dont tous les codes et tokens sont expirés, afin que la persistance introduite par TAS-GRANTS-02 reste exploitable en production sans croissance illimitée de la table `oauth2_authorization`.

## Distinction avec TAS-GRANTS-07

```text
Expiration        -> 02B nettoie (rétention physique)
Révocation anticipée -> 07 invalide (révocation fonctionnelle)
```

02B ne décide jamais qu'un token est invalide avant son terme naturel ; il élimine ce qui est
déjà devenu inutilisable par expiration. La révocation anticipée (token, famille de refresh
tokens, époque de sécurité) reste entièrement portée par TAS-GRANTS-07, qui dépend de 02B pour
l'élimination physique des lignes qu'il invalide.

## Périmètre

- Job de purge périodique, idempotent, rejouable sans dommage.
- Supprimer une autorisation uniquement lorsque tous ses codes et tokens sont expirés — jamais
  un authorization code ou un device flow encore actif, même si un autre token de la même
  autorisation est déjà expiré.
- Suppression par lots bornés (taille configurable), sans jamais charger la table entière en
  mémoire.
- Exécution sûre avec plusieurs instances TAKIBO actives simultanément : pas de double
  traitement, pas de verrou applicatif fragile.
- Index PostgreSQL adaptés aux colonnes de date d'expiration utilisées par la sélection des
  lignes purgeables.
- Configuration générique (intervalle, taille de lot) sans dépendance à l'orchestrateur de
  déploiement — aucune supposition OpenShift.

## Critères d'acceptation

- [ ] Une autorisation n'est supprimée que lorsque tous ses codes et tokens sont expirés.
- [ ] Un authorization code ou un device flow encore actif n'est jamais supprimé, y compris si
      un autre token de la même autorisation est déjà expiré.
- [ ] Le job traite par lots bornés ; aucune requête ne charge la table entière.
- [ ] Deux instances TAKIBO exécutant le job simultanément ne dupliquent pas le travail et ne se
      bloquent pas mutuellement au point de bloquer indéfiniment l'une des deux.
- [ ] Un index PostgreSQL soutient la sélection des lignes purgeables sans scan complet de
      table.
- [ ] Le job est idempotent : une exécution sur une base déjà purgée ne produit aucune erreur ni
      effet de bord.
- [ ] La suppression physique n'efface aucune trace d'audit ; la politique de conservation de
      l'audit reste distincte de la purge et hors du périmètre de ce récit.
- [ ] Le comportement est prouvé sur PostgreSQL réel (Testcontainers), pas seulement en mémoire.
- [ ] La configuration (intervalle, taille de lot) est générique et fonctionne sans supposition
      sur l'environnement d'exécution.

## Tests attendus

- Purge avec un mélange d'autorisations actives, partiellement expirées et intégralement
  expirées.
- Non-suppression d'une autorisation portant un authorization code ou un device flow encore
  actif.
- Comportement par lots avec un volume dépassant la taille d'un lot.
- Concurrence entre deux exécutions simultanées du job (deux instances).
- Rejeu du job sur une base déjà purgée.
- Vérification que le plan d'exécution PostgreSQL utilise l'index d'expiration attendu.

## Hors périmètre

- Révocation anticipée d'un token, d'une famille de refresh tokens ou par époque de sécurité —
  TAS-GRANTS-07.
- Politique de conservation de l'audit lui-même (durée, format, entrepôt).
- Introspection distante obligatoire, Redis obligatoire.
