# SEC-TMS-02 — Passer les routes TMS en default-deny

## Contexte

Le `PolicyEvaluator` termine par `POL_DEFAULT_ALLOW` lorsqu'aucune politique ne
reconnaît une route. Les surfaces TMS connues possèdent déjà des contrôles dédiés,
mais leurs chemins autorisés retombent encore sur ce fallback et toute nouvelle route
TMS sous `/api/v1/orgs/{orgId}/...` peut être ouverte par défaut avant l'ajout de sa
politique.

TMS identifie les organisations par UUID. Les routes TIS-CORE utilisant des codes
lisibles partagent le préfixe `/api/v1/orgs` et ne doivent pas être affectées par ce
récit.

## Loi du récit

Une route TMS n'est accessible que si une politique explicite gouverne sa paire
route/action. Toute autre route TMS UUID est refusée, y compris pour un propriétaire
d'organisation.

## Matrice gouvernée

| Route | Action | Décision explicite |
|---|---|---|
| `/api/v1/orgs/signup` | CREATE | bootstrap authentifié autorisé |
| `/api/v1/orgs/{orgId}/dashboard/summary` | READ | politique dashboard existante |
| `/api/v1/orgs/{orgId}/spaces` | READ, CREATE | politiques spaces existantes |
| `/api/v1/orgs/{orgId}/spaces/{spaceId}` | READ | politique détail space existante |
| `/api/v1/orgs/{orgId}/spaces/{spaceId}/clients` | CREATE | politique clients existante |
| `/api/v1/orgs/{orgId}/spaces/{spaceId}/clients/{clientId}/rotate-secret` | CREATE | politique rotation existante |
| toute autre route TMS UUID | toute action | `POL_TMS_ROUTE_NOT_GOVERNED` |

Les exigences de sujet, scope, organisation, space et rôle des politiques existantes
restent inchangées.

## Périmètre

- Rendre explicites les décisions PERMIT des routes TMS gouvernées.
- Rendre explicite l'autorisation CREATE du signup authentifié.
- Refuser les autres actions sur le signup.
- Ajouter un verrou terminal sur `/api/v1/orgs/{UUID}[/**]`.
- Prouver que les routes TIS-CORE à codes lisibles restent hors de ce verrou.

## Hors périmètre

- Remplacer le fallback global de toutes les APIs par un default-deny.
- Rendre le signup anonyme.
- Modifier les rôles ou les frontières tenant des routes existantes.
- Restreindre les endpoints Actuator.

## Critères d'acceptation

### AC-01 — Aucun PERMIT TMS implicite

Chaque route TMS autorisée retourne une politique explicite différente de
`POL_DEFAULT_ALLOW` et `POL_RESOURCE_ALLOW`.

### AC-02 — Route TMS inconnue refusée

Toute paire route/action inconnue sous `/api/v1/orgs/{UUID}` retourne DENY avec
`POL_TMS_ROUTE_NOT_GOVERNED`, même avec `R_ORG_OWNER`.

### AC-03 — Signup explicitement gouverné

CREATE sur `/api/v1/orgs/signup` conserve le comportement authentifié existant. Les
actions READ, UPDATE, DELETE et OTHER sont refusées.

### AC-04 — Compatibilité TIS-CORE

Une route utilisant un code d'organisation lisible ne correspond pas au verrou TMS
UUID et conserve sa politique actuelle.

## Vérifications

- Tests ciblés `PolicyEvaluatorTest` et `PolicyBasedAuthorizationManagerTest`.
- Suite complète `:takibo-security-management:test`.
- Suite complète `:takibo-management-service:test`.
- Compilation des modules concernés.

## Branche

`security/tms-default-deny`

## Commit proposé

`fix(security): make TMS routes default deny`
