# SEC-TMS-01 — Fermer l'élévation de privilèges du signup

## Contexte

`POST /api/v1/orgs/signup` est le flux de bootstrap d'une nouvelle organisation.
Avant ce récit, le payload pouvait aussi désigner une organisation existante avec
`organization.id`. Le service vérifiait uniquement que cet identifiant correspondait
à l'organisation du token, puis créait un compte et lui attribuait `R_ORG_OWNER` via
le provisioning fondateur.

Cette comparaison d'identifiants ne prouvait aucun pouvoir administratif. Un membre
authentifié de l'organisation pouvait donc créer un nouveau propriétaire.

## Loi du récit

Le signup est exclusivement un bootstrap de nouvelle organisation. Il ne peut jamais
ajouter un fondateur, un propriétaire ou un compte à une organisation existante.

Toute administration d'une organisation existante devra utiliser une route distincte,
protégée par une autorisation explicite, et ne devra pas appeler le provisioning
fondateur.

## Périmètre

- Refuser tout signup dont `organization.id` est renseigné.
- Refuser avant la première écriture ou attribution RBAC.
- Conserver le bootstrap lorsque `organization.id` est absent.
- Retirer la dépendance au contexte d'organisation, devenue inutile dans ce service.
- Ajouter un test de non-régression prouvant l'absence totale d'effets de bord.

## Hors périmètre

- Rendre le bootstrap accessible anonymement.
- Créer la future route d'administration d'une organisation existante.
- Passer toutes les politiques TMS en default-deny.
- Revoir la validation imbriquée du payload.

Ces éléments relèvent de récits séparés afin de garder le correctif de sécurité petit
et déployable rapidement.

## Critères d'acceptation

### AC-01 — Organisation existante interdite

Étant donné un payload contenant `organization.id`, lorsque le signup est exécuté,
alors l'opération échoue avec `EXISTING_ORGANIZATION_SIGNUP_FORBIDDEN`.

### AC-02 — Aucun effet de bord

Après ce refus :

- aucune organisation n'est créée ;
- aucun compte n'est créé ;
- aucun Space n'est créé ;
- aucun utilisateur fondateur n'est provisionné ;
- aucun rôle ou groupe fondateur n'est attribué.

### AC-03 — Bootstrap préservé

Étant donné un payload sans `organization.id`, le service crée l'organisation, le
compte, le Space, l'utilisateur fondateur et les attributions RBAC comme auparavant.

## Vérifications

- Test ciblé `OrganizationSignupServiceTest`.
- Suite complète `:takibo-management-service:test`.
- Compilation `:takibo-management-service:compileJava`.

## Branche

`security/tms-signup-boundary`

## Commit proposé

`fix(security): close organization signup privilege escalation`
