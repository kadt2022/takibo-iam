# RBAC-08 — Audit situé et intervention plateforme

**Statut** : à faire
**Doctrine** : [ADR 0003 §5, §9](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : RBAC-06
**Risque** : élevé

## Contexte

L'audit existe dans les trois plans, mais rien ne borne aujourd'hui la lecture à la
frontière de l'acteur. Par ailleurs, un administrateur plateforme n'a aucun chemin
explicite pour intervenir dans une organisation : soit il n'y accède pas, soit il y
accède sans trace distinctive.

## Loi du récit

L'audit est une capacité de chaque plan, jamais un quatrième plan. La lecture est
**toujours** filtrée par la frontière du token.

Un administrateur plateforme n'entre jamais automatiquement dans une organisation.
L'intervention est un acte explicite, motivé, limité dans le temps et tracé comme tel.

## Périmètre

### Audit situé

Filtres obligatoires :

| Plan | Filtre |
| --- | --- |
| `PLATFORM` | événements produits par le plan plateforme |
| `ORGANIZATION` | `organization_id = token.org_id`, agrégation autorisée de ses Spaces |
| `SPACE` | `organization_id = token.org_id` **et** `space_id = token.space_id` |

### Intervention plateforme

```text
demande d'intervention
motif obligatoire
organisation cible
Space cible éventuel
durée limitée
émission d'un nouveau token situé
audit spécial
```

Ajouter `PLATFORM_IMPERSONATION` à `ActorSource`, dans la continuité de la doctrine de
provenance (`HUMAN` / `SERVICE_ACCOUNT` / `SYSTEM`).

Conserver séparément :

```text
acteur réel
acteur effectif
motif
tenant cible
date de début
date d'expiration
```

## Hors périmètre

- La rétention et les destinations d'audit (`P_AUDIT_MANAGE_RETENTION`,
  `P_AUDIT_CONFIGURE_DESTINATIONS`), réservées à un récit ultérieur et **jamais**
  accordées à un auditeur.
- L'interface d'intervention (RBAC-09).

## Conséquence assumée

Le Platform Auditor ne voyant pas les journaux internes des tenants (ADR 0003 §2), **toute
investigation d'incident portant sur les données d'une organisation passe obligatoirement
par le flux d'intervention tracée**. C'est un choix d'isolation, pas un oubli.

## Critères d'acceptation

### AC-01 — Audit borné par la frontière

Un `R_ORG_AUDITOR` ne lit que l'audit de son organisation et de ses Spaces. Un
`R_SPACE_AUDITOR` ne lit que celui de son Space.

### AC-02 — Auditeur strictement en lecture

Aucun rôle d'auditeur ne peut modifier utilisateurs, clients, rôles, politiques ni
secrets.

### AC-03 — Export borné

`P_ORG_AUDIT_EXPORT` n'exporte que le périmètre de l'organisation ; `P_SPACE_AUDIT_EXPORT`
que celui du Space. Chaque export est tracé avec l'identité de l'exportateur.

### AC-04 — Aucun accès tenant par défaut

Un token `PLATFORM` ne donne accès à aucune donnée interne de tenant.

### AC-05 — Intervention explicite et motivée

L'entrée dans une organisation exige une action dédiée et un motif non vide. Sans motif,
l'opération échoue.

### AC-06 — Token d'intervention borné

Le token émis est limité au tenant ciblé et expire. Il ne permet aucun accès à un autre
tenant.

### AC-07 — Traçabilité distinctive

Toutes les actions effectuées sous intervention sont identifiables par
`ActorSource = PLATFORM_IMPERSONATION`, avec acteur réel et motif conservés.

## Vérifications

- `:takibo-audit:test`
- `:takibo-security-management:test`
- `:takibo-identity-core:test`
- BVT : lecture d'audit refusée hors frontière, parcours d'intervention complet.

## Branche

`feat/rbac-situated-audit-and-platform-intervention`

## Commit proposé

`feat(rbac): scope audit reads and trace platform interventions`

## Clôture documentaire

Lorsque ce récit est terminé et validé :

1. passer son statut à `TERMINÉ` ;
2. déplacer ce fichier de `docs/backlog` vers `docs/terminer` avec `git mv` ;
3. mettre à jour les index et liens concernés ;
4. inclure ce déplacement dans la PR de clôture du récit.
