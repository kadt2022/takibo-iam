# ADR 0003 — Doctrine RBAC v2 (version retenue)

- **Statut** : accepté comme cible de conception
- **Date** : 2026-07-21
- **Remplace** : [ADR 0002](0002-doctrine-rbac-v2-plans-roles-permissions.md), dont la §3
  (« permissions : l'action, jamais la frontière ») est **écartée** au profit de
  permissions nommant explicitement leur plan (voir §1 et §13)
- **Voir aussi** : [ADR 0001](0001-separation-account-user-identitylink.md), [PHILOSOPHY](../PHILOSOPHY.md)

Cette doctrine reste une **cible de conception** : elle n'est appliquée ni dans
`TechnicalRole`, ni dans `PolicyEvaluator`, ni dans les migrations.

---

# 1. Principes fondamentaux

TAKIBO possède seulement trois plans administratifs :

| Plan | Frontière |
| --- | --- |
| `PLATFORM` | Toute l'installation TAKIBO |
| `ORGANIZATION` | Une organisation précise |
| `SPACE` | Un Space précis dans une organisation |

L'audit n'est pas un quatrième plan. Il existe dans chacun des trois plans.

La règle complète d'autorisation est :

```text
Rôle
= origine et niveau maximal de l'autorité

Permission
= action effective autorisée

org_id / space_id du token
= frontière concrète où l'action est autorisée
```

Une permission seule ne suffit jamais :

```text
P_SPACE_USERS_MANAGE
+
org_id = A
+
space_id = A1
```

L'utilisateur peut gérer les utilisateurs de `A1`, jamais ceux de `A2` ou d'une autre
organisation.

> **Aucun contrôle d'autorisation ne doit reposer sur la seule présence d'un code de
> permission.** Le plan nommé dans la permission dit le *niveau* ; les claims `org_id`
> et `space_id` disent *lequel*. Un `hasAuthority('P_SPACE_USERS_MANAGE')` isolé, sans
> lecture de la frontière du token, est interdit.

---

# 2. Résumé des rôles retenus

Nous retenons **11 rôles techniques** :

```text
2 PLATFORM
5 ORGANIZATION
4 SPACE
```

Nous ne retenons pas encore les rôles `READER`. Ils pourront être ajoutés lorsqu'on aura
défini précisément ce qu'un simple lecteur peut consulter.

## Rôles PLATFORM

| Code | Nom | Description |
| --- | --- | --- |
| `R_TAKIBO_PLATFORM_ADMIN` | Administrateur de plateforme | Administre l'installation TAKIBO : configuration globale, catalogue et cycle de vie des organisations, politiques et opérations propres à la plateforme. Il n'entre pas automatiquement dans les données internes des tenants. |
| `R_TAKIBO_PLATFORM_AUDITOR` | Auditeur de plateforme | Consulte les événements, politiques et opérations propres au plan plateforme. Il ne voit pas automatiquement les journaux internes des organisations et des Spaces. |

## Rôles ORGANIZATION

| Code | Nom | Description |
| --- | --- | --- |
| `R_ORG_OWNER` | Propriétaire de l'organisation | Détient juridiquement et fonctionnellement l'organisation. Possède les pouvoirs de l'Org Admin, plus le transfert de propriété et les opérations de désactivation ou de demande de suppression. |
| `R_ORG_ADMIN` | Administrateur de l'organisation | Administre quotidiennement l'organisation et tous ses Spaces : utilisateurs, clients, RBAC, politiques, configuration et audit. Ne peut pas transférer l'ownership. |
| `R_ORG_USER_ADMIN` | Administrateur des utilisateurs | Gère les identités humaines et leur cycle de vie dans l'organisation et ses Spaces. Peut lire le catalogue RBAC, mais ne peut pas attribuer des rôles administratifs. |
| `R_ORG_CLIENT_ADMIN` | Administrateur des clients | Gère les clients OAuth2/OIDC de l'organisation et de ses Spaces, y compris leur cycle de vie et la rotation de leurs secrets. |
| `R_ORG_AUDITOR` | Auditeur de l'organisation | Consulte et exporte les événements de son organisation et de ses Spaces. Peut lire les politiques, mais ne modifie aucune ressource. |

## Rôles SPACE

| Code | Nom | Description |
| --- | --- | --- |
| `R_SPACE_ADMIN` | Administrateur du Space | Administre toutes les ressources d'un Space précis : utilisateurs, clients, rôles, groupes, politiques et consultation de l'audit. Il ne peut ni créer ni supprimer un Space. |
| `R_SPACE_USER_ADMIN` | Administrateur des utilisateurs du Space | Gère uniquement les utilisateurs et leur cycle de vie dans le Space ciblé. |
| `R_SPACE_CLIENT_ADMIN` | Administrateur des clients du Space | Gère uniquement les clients OAuth2/OIDC du Space ciblé, y compris la rotation des secrets. |
| `R_SPACE_AUDITOR` | Auditeur du Space | Consulte et exporte uniquement les événements du Space qui lui est attribué. Ne modifie ni utilisateurs, ni clients, ni politiques. |

---

# 3. Permissions PLATFORM

> **Convention de nommage** : les documents parlent toujours en **codes canoniques**
> (`P_PLATFORM_ORGS_READ`, `R_ORG_OWNER`) — ce qui circule dans les tokens, l'API et la
> base. Les constantes Java omettent le préfixe de type (`PLATFORM_ORGS_READ`,
> `ORG_OWNER`), déjà porté par l'enum ; le code canonique est exposé par `code()`.
> Aucun consommateur ne compare jamais un littéral de chaîne : tout passe par l'enum.

Les permissions plateforme décrivent uniquement les opérations du plan de contrôle TAKIBO.

| Permission | Description |
| --- | --- |
| `P_PLATFORM_ORGS_READ` | Consulter le catalogue et les informations administratives des organisations depuis la console plateforme. |
| `P_PLATFORM_ORGS_CREATE` | Créer administrativement une organisation depuis le plan plateforme. |
| `P_PLATFORM_ORGS_SUSPEND` | Suspendre ou réactiver une organisation au niveau plateforme. |
| `P_PLATFORM_ORGS_DELETE` | Supprimer définitivement une organisation selon les règles de rétention et de sécurité. **Contrepartie de `P_ORG_DELETION_REQUEST`** : l'organisation demande, la plateforme exécute. |
| `P_PLATFORM_POLICY_READ` | Consulter les politiques propres à la plateforme. |
| `P_PLATFORM_POLICY_UPDATE` | Modifier les politiques propres à la plateforme. |
| `P_PLATFORM_AUDIT_READ` | Consulter les événements générés par le plan plateforme. |
| `P_PLATFORM_AUDIT_EXPORT` | Exporter les événements du plan plateforme. Réservée au Platform Admin dans la première version. |

---

# 4. Permissions ORGANIZATION

## Organisation et ownership

| Permission | Description |
| --- | --- |
| `P_ORG_READ` | Consulter les informations et la configuration visible de son organisation. |
| `P_ORG_UPDATE` | Modifier les paramètres administratifs de son organisation. |
| `P_ORG_OWNERSHIP_TRANSFER` | Transférer atomiquement la propriété de l'organisation à un autre membre autorisé. |
| `P_ORG_DEACTIVATE` | Désactiver volontairement son organisation. |
| `P_ORG_DELETION_REQUEST` | Demander la suppression définitive de son organisation selon le workflow de rétention. L'exécution relève de `P_PLATFORM_ORGS_DELETE`. |

Nous ne retenons pas une suppression immédiate par simple `P_ORG_DELETE`. La suppression
irréversible doit être contrôlée.

## Gestion des Spaces

| Permission | Description |
| --- | --- |
| `P_ORG_SPACES_READ` | Consulter tous les Spaces de l'organisation. |
| `P_ORG_SPACES_CREATE` | Créer un nouveau Space dans l'organisation. |
| `P_ORG_SPACES_MANAGE` | Modifier, suspendre ou réactiver les Spaces de l'organisation. |
| `P_ORG_SPACES_DELETE` | Supprimer un Space selon les règles de cycle de vie. |

## Gestion des utilisateurs

| Permission | Description |
| --- | --- |
| `P_ORG_USERS_READ` | Consulter les utilisateurs de l'organisation et de ses Spaces. |
| `P_ORG_USERS_MANAGE` | Créer et modifier les utilisateurs dans l'organisation et ses Spaces. |
| `P_ORG_USERS_LIFECYCLE` | Suspendre, réactiver, verrouiller ou désactiver des utilisateurs. |

## Gestion des clients OAuth2/OIDC

| Permission | Description |
| --- | --- |
| `P_ORG_CLIENTS_READ` | Consulter les clients de l'organisation et de ses Spaces. |
| `P_ORG_CLIENTS_MANAGE` | Créer ou modifier les clients OAuth2/OIDC. |
| `P_ORG_CLIENTS_ROTATE_SECRET` | Effectuer la rotation d'un secret client. |
| `P_ORG_CLIENTS_LIFECYCLE` | Suspendre, réactiver ou révoquer un client. |

## RBAC

| Permission | Description |
| --- | --- |
| `P_ORG_RBAC_READ` | Consulter les rôles, groupes et permissions disponibles dans l'organisation. |
| `P_ORG_RBAC_ASSIGN` | Attribuer ou retirer des rôles et groupes dans les frontières autorisées. |

## Politiques et audit

| Permission | Description |
| --- | --- |
| `P_ORG_POLICY_READ` | Consulter les politiques de sécurité de l'organisation. |
| `P_ORG_POLICY_UPDATE` | Modifier les politiques de sécurité de l'organisation. |
| `P_ORG_AUDIT_READ` | Consulter l'audit de l'organisation et l'agrégation autorisée de ses Spaces. |
| `P_ORG_AUDIT_EXPORT` | Exporter les événements de l'organisation et de ses Spaces. |

---

# 5. Permissions SPACE

## Space

| Permission | Description |
| --- | --- |
| `P_SPACE_READ` | Consulter les informations et la configuration visible du Space ciblé. |
| `P_SPACE_UPDATE` | Modifier les paramètres du Space ciblé. |

Créer ou supprimer un Space reste une opération d'organisation. Il n'existe donc pas de
pouvoir de création ou suppression de Space pour un rôle `SPACE`.

## Utilisateurs

| Permission | Description |
| --- | --- |
| `P_SPACE_USERS_READ` | Consulter les utilisateurs du Space ciblé. |
| `P_SPACE_USERS_MANAGE` | Créer et modifier les utilisateurs du Space ciblé. |
| `P_SPACE_USERS_LIFECYCLE` | Suspendre, réactiver, verrouiller ou désactiver les utilisateurs du Space ciblé. |

## Clients OAuth2/OIDC

| Permission | Description |
| --- | --- |
| `P_SPACE_CLIENTS_READ` | Consulter les clients OAuth2/OIDC du Space ciblé. |
| `P_SPACE_CLIENTS_MANAGE` | Créer ou modifier les clients du Space ciblé. |
| `P_SPACE_CLIENTS_ROTATE_SECRET` | Effectuer la rotation du secret d'un client du Space ciblé. |
| `P_SPACE_CLIENTS_LIFECYCLE` | Suspendre, réactiver ou révoquer un client du Space ciblé. |

## RBAC

| Permission | Description |
| --- | --- |
| `P_SPACE_RBAC_READ` | Consulter les rôles, groupes et permissions disponibles dans le Space. |
| `P_SPACE_RBAC_ASSIGN` | Attribuer ou retirer les rôles et groupes autorisés dans le Space. |

## Politiques et audit

| Permission | Description |
| --- | --- |
| `P_SPACE_POLICY_READ` | Consulter les politiques de sécurité du Space. |
| `P_SPACE_POLICY_UPDATE` | Modifier les politiques de sécurité du Space. |
| `P_SPACE_AUDIT_READ` | Consulter les événements du Space ciblé. |
| `P_SPACE_AUDIT_EXPORT` | Exporter les événements du Space ciblé. |

**Total : 45 permissions** (8 PLATFORM, 22 ORGANIZATION, 15 SPACE).

---

# 6. Répartition exacte des permissions par rôle

C'est la table normative pour l'implémentation. La §8 en donne la lecture qualitative.

## Plan PLATFORM

| Rôle | Permissions |
| --- | --- |
| `R_TAKIBO_PLATFORM_ADMIN` | toutes les `P_PLATFORM_*` (8) |
| `R_TAKIBO_PLATFORM_AUDITOR` | `P_PLATFORM_ORGS_READ`, `P_PLATFORM_POLICY_READ`, `P_PLATFORM_AUDIT_READ` (3) |

Le Platform Auditor ne reçoit pas `P_PLATFORM_AUDIT_EXPORT`.

## Plan ORGANIZATION

| Rôle | Permissions | Total |
| --- | --- | ---: |
| `R_ORG_OWNER` | toutes celles de `R_ORG_ADMIN` **+** `P_ORG_OWNERSHIP_TRANSFER`, `P_ORG_DEACTIVATE`, `P_ORG_DELETION_REQUEST` | 22 |
| `R_ORG_ADMIN` | `P_ORG_READ`, `P_ORG_UPDATE`, `P_ORG_SPACES_READ/CREATE/MANAGE/DELETE`, `P_ORG_USERS_READ/MANAGE/LIFECYCLE`, `P_ORG_CLIENTS_READ/MANAGE/ROTATE_SECRET/LIFECYCLE`, `P_ORG_RBAC_READ/ASSIGN`, `P_ORG_POLICY_READ/UPDATE`, `P_ORG_AUDIT_READ/EXPORT` | 19 |
| `R_ORG_USER_ADMIN` | `P_ORG_READ`, `P_ORG_USERS_READ/MANAGE/LIFECYCLE`, `P_ORG_RBAC_READ` | 5 |
| `R_ORG_CLIENT_ADMIN` | `P_ORG_READ`, `P_ORG_CLIENTS_READ/MANAGE/ROTATE_SECRET/LIFECYCLE` | 5 |
| `R_ORG_AUDITOR` | `P_ORG_READ`, `P_ORG_POLICY_READ`, `P_ORG_AUDIT_READ`, `P_ORG_AUDIT_EXPORT` | 4 |

`R_ORG_USER_ADMIN` détient `P_ORG_RBAC_READ` mais **jamais** `P_ORG_RBAC_ASSIGN` : gérer
des identités n'est pas déléguer du pouvoir.

## Plan SPACE

| Rôle | Permissions | Total |
| --- | --- | ---: |
| `R_SPACE_ADMIN` | `P_SPACE_READ/UPDATE`, `P_SPACE_USERS_READ/MANAGE/LIFECYCLE`, `P_SPACE_CLIENTS_READ/MANAGE/ROTATE_SECRET/LIFECYCLE`, `P_SPACE_RBAC_READ/ASSIGN`, `P_SPACE_POLICY_READ/UPDATE`, `P_SPACE_AUDIT_READ` | 14 |
| `R_SPACE_USER_ADMIN` | `P_SPACE_READ`, `P_SPACE_USERS_READ/MANAGE/LIFECYCLE` | 4 |
| `R_SPACE_CLIENT_ADMIN` | `P_SPACE_READ`, `P_SPACE_CLIENTS_READ/MANAGE/ROTATE_SECRET/LIFECYCLE` | 5 |
| `R_SPACE_AUDITOR` | `P_SPACE_READ`, `P_SPACE_AUDIT_READ`, `P_SPACE_AUDIT_EXPORT` | 3 |

`R_SPACE_ADMIN` **n'a pas** `P_SPACE_AUDIT_EXPORT` : l'export de preuves est une capacité
d'auditeur, séparée de l'administration (séparation des devoirs).

---

# 7. Héritage Organisation vers Space

Un Org Admin qui ouvre un Space conserve son véritable rôle :

```text
roles = [R_ORG_ADMIN]
```

Il ne reçoit pas artificiellement `R_SPACE_ADMIN`. Il reçoit, dans le token Space, les
permissions de plan SPACE dérivées de ses permissions de plan ORGANIZATION :

| Permission ORGANIZATION détenue | Permission SPACE reçue dans un token Space de son organisation |
| --- | --- |
| `P_ORG_SPACES_READ` | `P_SPACE_READ` |
| `P_ORG_SPACES_MANAGE` | `P_SPACE_UPDATE` |
| `P_ORG_USERS_READ` | `P_SPACE_USERS_READ` |
| `P_ORG_USERS_MANAGE` | `P_SPACE_USERS_MANAGE` |
| `P_ORG_USERS_LIFECYCLE` | `P_SPACE_USERS_LIFECYCLE` |
| `P_ORG_CLIENTS_READ` | `P_SPACE_CLIENTS_READ` |
| `P_ORG_CLIENTS_MANAGE` | `P_SPACE_CLIENTS_MANAGE` |
| `P_ORG_CLIENTS_ROTATE_SECRET` | `P_SPACE_CLIENTS_ROTATE_SECRET` |
| `P_ORG_CLIENTS_LIFECYCLE` | `P_SPACE_CLIENTS_LIFECYCLE` |
| `P_ORG_RBAC_READ` | `P_SPACE_RBAC_READ` |
| `P_ORG_RBAC_ASSIGN` | `P_SPACE_RBAC_ASSIGN` |
| `P_ORG_POLICY_READ` | `P_SPACE_POLICY_READ` |
| `P_ORG_POLICY_UPDATE` | `P_SPACE_POLICY_UPDATE` |
| `P_ORG_AUDIT_READ` | `P_SPACE_AUDIT_READ` |
| `P_ORG_AUDIT_EXPORT` | `P_SPACE_AUDIT_EXPORT` |

Règles de la dérivation :

1. Elle s'applique **uniquement** aux Spaces de l'organisation portée par l'attribution.
2. Elle est **unidirectionnelle** : aucune permission de plan SPACE ne produit jamais une
   permission de plan ORGANIZATION.
3. `P_ORG_SPACES_CREATE` et `P_ORG_SPACES_DELETE` **n'ont pas d'équivalent SPACE** :
   créer ou supprimer un Space reste un acte d'organisation.
4. Le token Space résultant porte l'union des permissions dérivées et des permissions
   des rôles Space éventuellement détenus.

---

# 8. Matrice synthétique des rôles

| Rôle | Administration | Utilisateurs | Clients | RBAC | Politiques | Audit | Ownership |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Platform Admin | Plateforme | Selon intervention explicite | Selon intervention explicite | Plateforme | Lecture/écriture | Lecture/export plateforme | Non |
| Platform Auditor | Lecture plateforme | Non | Non | Non | Lecture | Lecture plateforme | Non |
| Org Owner | Organisation complète | Oui | Oui | Oui | Lecture/écriture | Lecture/export | Transfert, désactivation, suppression demandée |
| Org Admin | Organisation complète | Oui | Oui | Oui | Lecture/écriture | Lecture/export | Non |
| Org User Admin | Contexte minimal | Oui | Non | Lecture seulement | Non | Non | Non |
| Org Client Admin | Contexte minimal | Non | Oui | Non | Non | Non | Non |
| Org Auditor | Lecture organisation | Non | Non | Non | Lecture | Lecture/export org + Spaces | Non |
| Space Admin | Space complet | Oui | Oui | Oui | Lecture/écriture | Lecture | Non |
| Space User Admin | Lecture du Space | Oui | Non | Non | Non | Non | Non |
| Space Client Admin | Lecture du Space | Non | Oui | Non | Non | Non | Non |
| Space Auditor | Lecture du Space | Non | Non | Non | Non | Lecture/export Space | Non |

---

# 9. Règles spéciales

## Owner

`R_ORG_OWNER` ne peut être obtenu que par :

```text
création initiale de l'organisation
ou
transferOwnership
```

Il ne peut jamais être :

```text
attribué par un endpoint générique
hérité d'un groupe
accordé par une invitation ordinaire
créé comme rôle métier
```

`G_ORG_ADMINS` doit donc transmettre uniquement `R_ORG_ADMIN`, et jamais `R_ORG_OWNER`.

## Libre-service

`R_SELF` disparaît. Tout utilisateur authentifié peut intrinsèquement :

```text
lire son profil
changer son mot de passe
gérer son MFA
consulter et révoquer ses sessions
consulter ses propres événements
```

Ces opérations relèvent d'une politique de relation à soi, pas d'un rôle administratif.

## Plateforme vers tenant

Un Platform Admin n'entre jamais automatiquement dans une organisation. L'intervention
exige :

```text
action explicite
motif obligatoire
nouveau token situé
audit spécial
ActorSource = PLATFORM_IMPERSONATION
```

Conséquence assumée : le Platform Auditor ne voyant pas les journaux internes des
tenants (§2), **toute investigation d'incident portant sur les données d'une organisation
passe obligatoirement par ce flux d'intervention tracée**.

---

# 10. Groupes techniques

Les groupes suivent les mêmes plans que les rôles.

| Groupe | Plan | Rôles transmis | Décision |
| --- | --- | --- | --- |
| `G_ORG_ADMINS` | ORGANIZATION | `R_ORG_ADMIN` | **`R_ORG_OWNER` retiré** — la propriété ne s'hérite pas d'un groupe (§9) |
| `G_SPACE_ADMINS` | SPACE | `R_SPACE_ADMIN` | inchangé |
| `G_ORG_USERS` | ORGANIZATION | aucun | marqueur d'appartenance |
| `G_ORG_CLIENTS` | ORGANIZATION | aucun | marqueur d'appartenance |
| `G_SPACE_USERS` | SPACE | aucun | marqueur d'appartenance |
| `G_SPACE_CLIENTS` | SPACE | aucun | marqueur d'appartenance |

Les quatre groupes sans rôle sont **conservés comme marqueurs d'appartenance purs, et
non dépréciés**. L'intention est explicite : appartenir à une organisation ou à un Space
ne confère aucun pouvoir. Le pouvoir passe toujours par un rôle nommé.

Le retrait de `R_ORG_OWNER` de `G_ORG_ADMINS` est **sans effet de bord sur le fondateur** :
le provisioning fondateur assigne `R_ORG_OWNER` **directement** en plus du groupe.

---

# 11. Correctif de sécurité préalable

**À traiter avant toute application de cette doctrine.**

Quatre codes d'administrateur plateforme circulent, dont un seul existe au catalogue :

| Code | Emplacement | Au catalogue ? |
| --- | --- | --- |
| `R_TAKIBO_PLATFORM_ADMIN` | `TechnicalRole` | **oui** |
| `R_PLATFORM_ADMIN` | `PolicyEvaluator:112`, `SecurityConfig:27` | non |
| `PLATFORM_ADMIN` | `PolicyEvaluator:112`, `SecurityConfig:28`, `DefaultThresholdPolicy:37` | non |
| `ROLE_PLATFORM_ADMIN` | `SecurityConfig:76`, `BoundaryMembershipService:286` | non |

`PolicyEvaluator.isTenantAdmin()` accepte les codes fantômes `R_PLATFORM_ADMIN` et
`PLATFORM_ADMIN`, **mais pas** `R_TAKIBO_PLATFORM_ADMIN`, le seul que l'enum produise.

**Chaîne d'escalade par nommage** (établie par lecture du code) :

```text
UserRoleGovernanceService.resolveGovernableRole()
  « PLATFORM_ADMIN » n'est pas un TechnicalRole  →  bascule sur la recherche DB
  rôle GOVERNANCE trouvé  →  ResolvedRole(scope = SPACE, forcé)
  assertNoScopeEscalation() ne s'applique qu'au scope ORGANIZATION  →  aucun contrôle
  assignation enregistrée avec le code « PLATFORM_ADMIN »
        ↓
EffectiveRbacQueryService : un code non technique n'est jamais « caché »  →  entre au token
        ↓
PolicyEvaluator.isTenantAdmin()  →  vrai
```

Un rôle `GOVERNANCE` nommé `PLATFORM_ADMIN` accorde donc le statut d'administrateur
tenant. `RoleApplicationService:33` expose un chemin de création (`roleRepository.save`),
et aucune réservation de préfixe n'existe dans `takibo-identity-core`.

**Reste à vérifier** : si ce chemin de création est exposé aux tenants via REST et sous
quelle autorisation. Si oui, la faille est exploitable aujourd'hui.

**Correctifs** :

1. `isTenantAdmin()` ne reconnaît que les codes réels du catalogue technique — suppression
   des alias fantômes dans `PolicyEvaluator`, `SecurityConfig`, `BoundaryMembershipService`
   et `DefaultThresholdPolicy`.
2. Un code de rôle tenant (`GOVERNANCE` / `BUSINESS`) ne peut jamais emprunter un préfixe
   réservé (`R_TAKIBO_`, `R_ORG_`, `R_SPACE_`, `PLATFORM`) ni collisionner avec un code
   technique.
3. Un rôle `GOVERNANCE` n'entre au token que sous un préfixe non ambigu.

---

# 12. Migration

## Ce qui est persisté, et ce qui ne l'est pas

| Élément | Persistance | Conséquence |
| --- | --- | --- |
| Permissions techniques | **aucune** — pas d'`INSERT INTO permissions` dans les migrations | renommer les codes de permissions est un changement **Java seul**, sans migration de données |
| Codes de rôles techniques | `role_assignments.role_code`, plus deux migrations de classification | tout retrait ou renommage de rôle exige une migration |
| `user_roles` | référence `role_id`, pas `role_code` | ne pas cibler cette table par code |

## Table de migration

| Ancien | Nouveau | Action |
| --- | --- | --- |
| `TechnicalScope.SYSTEM` | `PLATFORM` | renommage d'enum (non persisté) |
| `TechnicalScope.USER` | — | suppression, aucun rôle restant |
| `SYSTEM_ADMIN` (constante) | `PLATFORM_ADMIN` | renommage ; le code `R_TAKIBO_PLATFORM_ADMIN` **ne change pas** |
| `SYSTEM_AUDITOR` (constante) | `PLATFORM_AUDITOR` | idem, code inchangé |
| `R_ORG_OWNER`, `R_ORG_ADMIN`, `R_ORG_USER_ADMIN`, `R_ORG_CLIENT_ADMIN`, `R_ORG_AUDITOR` | inchangés | permissions redéfinies (§6) |
| `R_SPACE_ADMIN`, `R_SPACE_USER_ADMIN`, `R_SPACE_CLIENT_ADMIN` | inchangés | permissions redéfinies (§6) |
| — | `R_SPACE_AUDITOR` | **création** |
| `R_ORG_VIEWER` | **supprimé** | aucun rôle cible équivalent (voir ci-dessous) |
| `R_SPACE_VIEWER` | **supprimé** | aucun rôle cible équivalent (voir ci-dessous) |
| `R_SELF` | **supprimé** | suppression des assignations ; remplacé par le libre-service (§9) |
| `P_*` (13 permissions) | 45 nouvelles (§3–§5) | remplacement complet, Java seul |

## Sort des assignations Viewer

Les rôles `READER` n'étant pas retenus (§2), il n'existe **aucune cible équivalente** :

- `R_ORG_VIEWER` (lecture org + politiques) → le mapper vers `R_ORG_AUDITOR` **accorderait
  la lecture et l'export d'audit** : ce serait une élévation de privilège silencieuse.
- `R_SPACE_VIEWER` (lecture audit + politiques) → `R_SPACE_AUDITOR` est le plus proche,
  mais ajoute `P_SPACE_AUDIT_EXPORT`.

**Décision retenue** : les assignations `R_ORG_VIEWER` et `R_SPACE_VIEWER` sont
**supprimées**, sans remplacement automatique. Les administrateurs concernés doivent
réattribuer explicitement un rôle aux personnes touchées. La migration doit produire un
rapport listant les assignations supprimées, par organisation.

C'est le coût assumé du report des rôles `READER` ; ne pas le traiter reviendrait à
convertir des lecteurs en auditeurs.

---

# 13. Doctrine finale

> **TAKIBO possède trois plans d'autorité : Platform, Organization et Space. Chaque rôle
> appartient à un seul plan. Chaque permission nomme explicitement le plan, la ressource
> et l'action. Le rôle explique d'où vient le pouvoir ; les permissions décrivent ce que
> le sujet peut faire ; les claims `org_id` et `space_id` déterminent exactement où il
> peut le faire.**

Cette matrice constitue la base de conception avant toute modification du catalogue
actuel, des migrations et des politiques d'autorisation.

---

# 14. Décisions restant ouvertes

1. ~~**Asymétrie des auditeurs et des politiques**~~ — **tranchée (RBAC-02, PR #46,
   2026-07-26)** : l'asymétrie est assumée. `R_ORG_AUDITOR` détient `P_ORG_POLICY_READ` ;
   `R_SPACE_AUDITOR` n'a pas `P_SPACE_POLICY_READ` — l'auditeur de Space observe des
   événements, pas la configuration. Encodé dans `RolePermissionCatalog` et verrouillé
   par le test `absentPermissionsAndDeprecatedRolesAreDeniedByDefault`.
2. ~~**`R_SPACE_ADMIN` sans export d'audit**~~ — **confirmée (RBAC-02, PR #46,
   2026-07-26)** : séparation des devoirs assumée. L'export de preuves reste une capacité
   d'auditeur ; encodé dans la matrice et verrouillé par le test
   `sensitiveExclusionsAreExplicitlyDenied`.
3. **Rôles `READER`** : reportés. Tant qu'ils n'existent pas, aucun accès en lecture seule
   n'est possible sans passer par un rôle d'auditeur ou d'administrateur — voir le coût de
   migration en §12.
4. ~~**Exposition REST de la création de rôle tenant**~~ — **établie (RBAC-00, PR #44,
   2026-07-26)** : aucune route REST de création n'existe (risque latent, pas exploitable) ;
   l'assignation est gardée aux trois points (création, assignation, évaluation). Voir le
   récit RBAC-00, section « Exposition REST établie ».
