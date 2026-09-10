# TAS-GRANTS-02B — Rétention des autorisations OAuth 2.0 expirées

**Statut :** TERMINÉ — 9/9 critères vérifiés le 2026-09-09 sur PostgreSQL réel.  
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

- [x] Une autorisation n'est supprimée que lorsque tous ses codes et tokens sont expirés.
- [x] Un authorization code ou un device flow encore actif n'est jamais supprimé, y compris si
      un autre token de la même autorisation est déjà expiré.
- [x] Le job traite par lots bornés ; aucune requête ne charge la table entière.
- [x] Deux instances TAKIBO exécutant le job simultanément ne dupliquent pas le travail et ne se
      bloquent pas mutuellement au point de bloquer indéfiniment l'une des deux.
- [x] Un index PostgreSQL soutient la sélection des lignes purgeables sans scan complet de
      table.
- [x] Le job est idempotent : une exécution sur une base déjà purgée ne produit aucune erreur ni
      effet de bord.
- [x] La suppression physique n'efface aucune trace d'audit ; la politique de conservation de
      l'audit reste distincte de la purge et hors du périmètre de ce récit.
- [x] Le comportement est prouvé sur PostgreSQL réel (Testcontainers), pas seulement en mémoire.
- [x] La configuration (intervalle, taille de lot) est générique et fonctionne sans supposition
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

## Décisions de conception prises à la livraison

**Échéance consolidée plutôt que six conditions dispersées.** Une colonne générée
`purge_after = GREATEST(...)` porte l'invariant dans la base. `GREATEST` ignorant les
valeurs nulles sous PostgreSQL, l'échéance vaut la dernière expiration réellement présente :
tant qu'un artefact la repousse, la ligne reste. Les index d'expiration existants commencent
tous par `(org_id, space_id)` et ne pouvaient pas soutenir une purge globale.

**Invariant : un artefact présent porte son expiration.** Six contraintes `CHECK` l'imposent.
Sans elles, `GREATEST` ignorerait le nul d'un jeton réellement présent et l'autorisation
partirait alors qu'un artefact d'expiration inconnue existe encore. Conséquence assumée : un
jeton de rafraîchissement sans expiration devient un refus d'écriture. Spring Authorization
Server en fixe toujours une en pratique, mais son modèle autorise le nul — un jeton qui
n'expire jamais ne pourrait jamais être purgé, et cette posture doit être un choix explicite.

**Le `CASE` fail-closed n'est pas redondant avec les `CHECK`.** Il en est délibérément
indépendant. La purge est destructrice : sa sûreté ne doit pas reposer sur la survie d'une
contrainte déclarée ailleurs. Si une migration future en affaiblissait une, l'échéance
deviendrait nulle plutôt que fausse, et la ligne cesserait d'être purgeable au lieu de
disparaître à tort.

**Horloge PostgreSQL, jamais celle de la JVM.** `CURRENT_TIMESTAMP` est la seule horloge que
toutes les instances partagent. Deux répliques dont les horloges diffèrent de quelques
secondes prendraient sinon des décisions différentes sur les mêmes lignes.

**Une seule instruction SQL** sélectionne, verrouille, ignore ce qu'une autre instance tient
déjà, puis supprime. Aucune fenêtre entre la sélection et la suppression, aucun verrou
applicatif, aucune coordination externe.

**Anomalies signalées, jamais supprimées.** Une ligne d'échéance nulle est comptée et
rapportée de façon agrégée. Assez étrange pour mériter une enquête, pas assez comprise pour
qu'un travail de fond décide de la détruire. Un index partiel miroir rend ce comptage
gratuit.

**Les anciens index `(org_id, space_id, expires_at)` sont conservés.** Ce récit ajoute son
index spécialisé, il ne fait pas de ménage opportuniste sur des chemins qu'il ne connaît pas.

## Défaut découvert hors périmètre

La clé étrangère `fk_tae_space_scope` de `tas_audit_events` est en `ON DELETE SET NULL` sur
le couple `(org_id, space_id)`, alors que `org_id` est `NOT NULL`. PostgreSQL nullant toutes
les colonnes de la clé, **supprimer un space porteur d'événements d'audit TAS échoue**.
Défaut antérieur à ce récit, contourné dans son montage de test et signalé pour traitement
séparé.
