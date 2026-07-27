# ADR 0002 — Doctrine RBAC v2 : plans, rôles et permissions

- **Statut** : ⛔ **SUPERSEDED** par [ADR 0003](0003-doctrine-rbac-v2.md) — ne pas implémenter depuis ce document
- **Date** : 2026-07-21
- **Remplace** : le catalogue implicite porté par `TechnicalRole` / `TechnicalGroup` (13 rôles, 4 scopes)
- **Voir aussi** : [ADR 0001](0001-separation-account-user-identitylink.md), [PHILOSOPHY](../PHILOSOPHY.md)

> **Pourquoi ce document est remplacé**
>
> La §3 de cet ADR pose que « une permission décrit une action, jamais la frontière »
> (permissions non préfixées : `P_USER_CREATE`). L'[ADR 0003](0003-doctrine-rbac-v2.md)
> **écarte cette position** : chaque permission y nomme explicitement son plan
> (`P_ORG_USERS_MANAGE`, `P_SPACE_USERS_MANAGE`). Le motif décisif est le mécanisme
> d'héritage Organisation → Space : il permet à un Org Admin d'agir dans un Space en
> conservant son rôle réel (`R_ORG_ADMIN`) tout en recevant des permissions de plan
> SPACE, sans qu'on lui fabrique un `R_SPACE_ADMIN` qu'il n'a pas.
>
> Restent valides et repris dans l'ADR 0003 : les trois plans, l'audit comme capacité de
> chaque plan, la disparition de `R_SELF`, l'ownership non délégable, l'impersonation
> plateforme tracée, et le correctif de sécurité (§9 ici, §11 dans l'ADR 0003).
>
> Ce document est conservé pour la traçabilité de la décision et pour son analyse des
> six défauts du catalogue actuel (§1), toujours d'actualité.

---

## 1. Le problème

Le catalogue actuel a été construit par accumulation. Six défauts structurels ont été
constatés dans le code :

1. **`TechnicalPermission.scope` porte deux sémantiques contradictoires.**
   Pour `CREATE_ORG`/`DELETE_ORG`, `SYSTEM` signifie « autorité plateforme ». Pour
   `MANAGE_USERS`, `ORGANIZATION` signifie « visible par un tenant » — alors que la
   permission s'applique aussi au niveau Space, comme sa propre description l'admet
   (« at organization or space level »). Un champ, deux sens, et ce champ pilote de
   vrais filtres (catalogue et RBAC effectif).

2. **`R_ORG_OWNER` n'a aucun pouvoir de propriétaire.** Il ne détient pas `DELETE_ORG`.
   Son seul écart avec `R_ORG_ADMIN` est `EXPORT_AUDIT_LOGS`. La propriété n'est donc
   pas représentée.

3. **`CREATE_ORG` et `DELETE_ORG` sont inatteignables.** Déclarées `SYSTEM`, elles sont
   exclues du calcul effectif (`permission.scope() == ORGANIZATION` sur le chemin ORG)
   et du catalogue tenant. Aucun token ne peut les porter.

4. **`R_SELF` est un rôle fantôme.** Scope `USER`, exclu du catalogue et du RBAC
   effectif : jamais listable, jamais dans un token, jamais assignable.

5. **Quatre groupes techniques sont vides** (`G_ORG_USERS`, `G_ORG_CLIENTS`,
   `G_SPACE_USERS`, `G_SPACE_CLIENTS` → `Set.of()`), sans intention documentée.

6. **`R_SPACE_VIEWER` confond observateur et auditeur** (il lit l'audit *et* les
   politiques), alors que le plan ORGANIZATION distingue bien `R_ORG_AUDITOR` de
   `R_ORG_VIEWER`.

À quoi s'ajoute un **défaut de sécurité** traité en §9.

---

## 2. Les trois plans

> **TAKIBO possède trois frontières administratives : `PLATFORM`, `ORGANIZATION`, `SPACE`.
> L'audit n'est pas une quatrième frontière : c'est une capacité présente dans chacune.**

```java
public enum TechnicalScope {
    PLATFORM,
    ORGANIZATION,
    SPACE
}
```

| Plan | Frontière | Gouverne | Ne gouverne jamais directement |
|---|---|---|---|
| `PLATFORM` | toute l'installation | configuration plateforme, catalogue des organisations, exploitation et audit globaux | les opérations quotidiennes d'une organisation sans entrée explicite (§7) |
| `ORGANIZATION` | une organisation | l'organisation, ses membres, ses clients, ses politiques et **tous ses Spaces** | une autre organisation, la plateforme |
| `SPACE` | un Space | utilisateurs, clients, rôles, groupes et politiques **de ce Space** | les autres Spaces, les paramètres de l'organisation |

`USER` est supprimé : ce n'est pas une frontière administrative (§6).

La frontière est portée par l'**attribution**, pas par le rôle seul :

```
PLATFORM        org_id = NULL   space_id = NULL
ORGANIZATION    org_id = UUID   space_id = NULL
SPACE           org_id = UUID   space_id = UUID
```

**Visibilité tenant** : le catalogue exposé à une organisation ne raconte que
`ORGANIZATION` et `SPACE`. Un code de plan `PLATFORM` **n'existe pas** pour un tenant
(404, anti-énumération). Le champ `scope` n'a plus qu'un seul sens.

---

## 3. Permissions : l'action, jamais la frontière

> **Une permission décrit une capacité sur une ressource. Elle ne dit jamais où elle
> s'applique. La frontière vient du rôle, de son attribution et du token actif.**

C'est le renversement central. `P_USER_CREATE` est la même permission pour un
`R_ORG_USER_ADMIN` et un `R_SPACE_USER_ADMIN` — leur pouvoir diffère par la frontière,
pas par la permission.

**Ressource ≠ plan.** `P_SPACE_CREATE` nomme la *ressource* (un Space) mais est détenue
par un rôle du plan `ORGANIZATION`, car créer un Space est un acte organisationnel.
De même `P_ORG_READ` est détenue au plan `ORGANIZATION` (lire son org) comme au plan
`PLATFORM` (lire n'importe quelle org) : c'est le plan du rôle qui fixe le rayon.

### Catalogue des permissions

| Ressource | Permissions |
|---|---|
| Organisation | `P_ORG_READ`, `P_ORG_UPDATE`, `P_ORG_CREATE`, `P_ORG_DELETE`, `P_ORG_SUSPEND`, `P_ORG_TRANSFER_OWNERSHIP` |
| Space | `P_SPACE_READ`, `P_SPACE_CREATE`, `P_SPACE_UPDATE`, `P_SPACE_DELETE` |
| Utilisateur | `P_USER_READ`, `P_USER_CREATE`, `P_USER_UPDATE`, `P_USER_MANAGE_LIFECYCLE` |
| Client OAuth2 | `P_CLIENT_READ`, `P_CLIENT_CREATE`, `P_CLIENT_UPDATE`, `P_CLIENT_ROTATE_SECRET`, `P_CLIENT_MANAGE_LIFECYCLE` |
| Rôle / Groupe | `P_ROLE_READ`, `P_ROLE_ASSIGN`, `P_GROUP_READ`, `P_GROUP_ASSIGN` |
| Politique | `P_POLICY_READ`, `P_POLICY_UPDATE` |
| Audit | `P_AUDIT_READ`, `P_AUDIT_EXPORT` |

Réservées à plus tard, et **jamais** pour un auditeur :
`P_AUDIT_MANAGE_RETENTION`, `P_AUDIT_CONFIGURE_DESTINATIONS`.

`TechnicalPermission.scope` est **supprimé**.

---

## 4. Catalogue des rôles

```
PLATFORM                ORGANIZATION            SPACE
├── ADMIN               ├── OWNER               ├── ADMIN
└── AUDITOR             ├── ADMIN               ├── USER_ADMIN
                        ├── USER_ADMIN          ├── CLIENT_ADMIN
                        ├── CLIENT_ADMIN        ├── AUDITOR
                        ├── AUDITOR             └── READER
                        └── READER
```

**2 + 6 + 5 = 13 rôles techniques.**

| Code | Plan | Permissions |
|---|---|---|
| `R_TAKIBO_PLATFORM_ADMIN` | PLATFORM | toutes |
| `R_TAKIBO_PLATFORM_AUDITOR` | PLATFORM | `P_ORG_READ`, `P_AUDIT_READ`, `P_POLICY_READ` |
| `R_ORG_OWNER` | ORGANIZATION | toutes celles de `R_ORG_ADMIN` **+ `P_ORG_DELETE` + `P_ORG_TRANSFER_OWNERSHIP`** |
| `R_ORG_ADMIN` | ORGANIZATION | `P_ORG_READ/UPDATE`, `P_SPACE_*`, `P_USER_*`, `P_CLIENT_*`, `P_ROLE_*`, `P_GROUP_*`, `P_POLICY_*`, `P_AUDIT_READ/EXPORT` |
| `R_ORG_USER_ADMIN` | ORGANIZATION | `P_ORG_READ`, `P_USER_*`, `P_ROLE_READ`, `P_GROUP_READ` |
| `R_ORG_CLIENT_ADMIN` | ORGANIZATION | `P_ORG_READ`, `P_CLIENT_*` |
| `R_ORG_AUDITOR` | ORGANIZATION | `P_ORG_READ`, `P_AUDIT_READ`, `P_AUDIT_EXPORT`, `P_POLICY_READ` |
| `R_ORG_READER` | ORGANIZATION | `P_ORG_READ`, `P_SPACE_READ`, `P_USER_READ`, `P_CLIENT_READ`, `P_ROLE_READ`, `P_GROUP_READ`, `P_POLICY_READ` |
| `R_SPACE_ADMIN` | SPACE | `P_SPACE_READ/UPDATE`, `P_USER_*`, `P_CLIENT_*`, `P_ROLE_*`, `P_GROUP_*`, `P_POLICY_*`, `P_AUDIT_READ` |
| `R_SPACE_USER_ADMIN` | SPACE | `P_SPACE_READ`, `P_USER_*` |
| `R_SPACE_CLIENT_ADMIN` | SPACE | `P_SPACE_READ`, `P_CLIENT_*` |
| `R_SPACE_AUDITOR` | SPACE | `P_SPACE_READ`, `P_AUDIT_READ`, `P_AUDIT_EXPORT` |
| `R_SPACE_READER` | SPACE | `P_SPACE_READ`, `P_USER_READ`, `P_CLIENT_READ`, `P_ROLE_READ`, `P_GROUP_READ`, `P_POLICY_READ` |

Points notables :

- **`R_SPACE_ADMIN` n'a ni `P_SPACE_CREATE` ni `P_SPACE_DELETE`** : créer ou supprimer
  un Space est un acte organisationnel.
- **`R_ORG_USER_ADMIN` n'a pas `P_ROLE_ASSIGN`** : gérer des identités n'est pas
  déléguer du pouvoir. Il lit le catalogue, il n'attribue pas.
- **`R_*_READER` remplace `R_*_VIEWER`** : ressources lisibles énumérées, **jamais**
  l'audit ni les secrets. Le mot « viewer » était trop imprécis ; le mot « auditeur »
  a une mission claire. Sans un rôle lecteur, le seul accès en lecture passerait par
  `R_*_AUDITOR`, ce qui donnerait l'export des preuves d'audit à qui veut seulement
  consulter une console.
- Il n'existe **ni `R_SPACE_OWNER`** (un Space appartient à l'organisation, pas à un
  individu) **ni `R_PLATFORM_OWNER`** (le propriétaire du déploiement n'est pas une
  identité RBAC manipulable).

---

## 5. L'audit dans les trois plans

| Rôle | Audit plateforme | Audit de son org | Audit des Spaces de son org | Audit d'un autre tenant |
|---|:---:|:---:|:---:|:---:|
| `R_TAKIBO_PLATFORM_AUDITOR` | oui | oui | oui | oui (console plateforme) |
| `R_ORG_AUDITOR` | non | oui | oui | non |
| `R_SPACE_AUDITOR` | non | non | son Space uniquement | non |

**Le plan de la permission détermine le rayon de lecture.** Un auditeur n'obtient jamais
implicitement : gestion des utilisateurs, gestion des clients, attribution de rôles,
modification de politiques, rotation de secrets, suspension de ressources.

`P_AUDIT_EXPORT` **n'est pas accordé** à `R_TAKIBO_PLATFORM_AUDITOR` : un export
transverse toutes-organisations est la donnée la plus sensible de la plateforme et
reste réservé à l'administrateur.

---

## 6. Le libre-service n'est pas du RBAC

`R_SELF` est **supprimé**. Tout principal authentifié peut, **sans rôle ni permission** :

```
lire son profil            gérer son MFA
changer son mot de passe   consulter et révoquer ses sessions
                           consulter ses propres événements
```

Ce sont des droits intrinsèques de l'identité sur elle-même, pas des pouvoirs
attribuables. Il serait absurde qu'un administrateur puisse retirer à quelqu'un le
droit de changer son propre mot de passe.

---

## 7. Héritage : le rôle donne le pouvoir, le token donne la frontière

La hiérarchie n'est **pas** « PLATFORM > ORGANIZATION > SPACE, donc le supérieur agit
partout automatiquement ».

> **Un rôle donne un pouvoir maximal. Le token actif fixe la frontière dans laquelle ce
> pouvoir s'exerce.**

**Platform Admin** — un token `PLATFORM` ne s'utilise pas sur une route interne de
tenant. Pour intervenir dans une organisation :

```
Platform Admin
    ↓ action explicite « Entrer dans l'organisation » (motif obligatoire)
token situé sur l'organisation
    ↓
trace : ActorSource = PLATFORM_IMPERSONATION, reason = <motif>
```

Cela impose d'ajouter `PLATFORM_IMPERSONATION` à `ActorSource`, dans la continuité de
la doctrine de provenance (`HUMAN` / `SERVICE_ACCOUNT` / `SYSTEM`).

**Org Admin** — gouverne tous les Spaces de son organisation, jamais ceux d'une autre.
Quand il ouvre un Space, le token situé restreint son action à ce Space :

```
R_ORG_ADMIN + org_id=A   →  Space A1 : autorisé
                         →  Space A2 : autorisé
                         →  Space B1 : refusé
```

La cascade s'applique **au calcul du token**, jamais en dupliquant des permissions dans
les rôles :

```
Token ORGANIZATION  →  permissions des rôles de plan ORGANIZATION
Token SPACE         →  permissions des rôles Space ∪ autorité ORGANIZATION héritée
```

**Space Admin** — ne peut jamais créer un autre Space, administrer un autre Space,
modifier l'organisation, ni devenir Org Admin.

---

## 8. Les onze lois

1. Les seules frontières administratives sont `PLATFORM`, `ORGANIZATION` et `SPACE`.
2. L'audit est une capacité disponible dans chaque frontière, pas un quatrième scope.
3. Les rôles techniques ne sont jamais modifiables par les tenants.
4. Les rôles techniques ne sont assignables que dans une frontière compatible.
5. `R_ORG_OWNER` n'est attribuable que par création d'organisation ou transfert
   d'ownership — jamais par `POST /users/{id}/roles`, jamais par une invitation.
6. Il n'existe ni `R_SPACE_OWNER` ni `R_SELF` dans le catalogue administratif.
7. Une permission décrit une action ; le rôle et son attribution décrivent la frontière.
8. Un rôle supérieur ne contourne jamais la frontière du token actif.
9. Les rôles d'audit sont strictement en lecture et export.
10. Chaque rôle doit être prouvé par au moins un scénario autorisé et un scénario refusé.
11. **Une permission n'est jamais évaluée hors de la frontière de son token.** Aucun
    contrôle ne doit reposer sur la seule présence d'un code de permission
    (`hasAuthority('P_USER_CREATE')` seul est interdit) : la décision lit toujours le
    couple *permission + frontière du token*.

La loi 11 est la contrepartie obligatoire de la §3. Sans elle, le jour où un contrôle
lit une permission décontextualisée, le modèle casse silencieusement.

---

## 9. Correctif de sécurité préalable

**À traiter avant toute application de cette doctrine.**

Quatre codes d'administrateur plateforme circulent, dont un seul existe au catalogue :

| Code | Emplacement | Au catalogue ? |
|---|---|---|
| `R_TAKIBO_PLATFORM_ADMIN` | `TechnicalRole` | **oui** |
| `R_PLATFORM_ADMIN` | `PolicyEvaluator:112`, `SecurityConfig:27` | non |
| `PLATFORM_ADMIN` | `PolicyEvaluator:112`, `SecurityConfig:28`, `DefaultThresholdPolicy:37` | non |
| `ROLE_PLATFORM_ADMIN` | `SecurityConfig:76`, `BoundaryMembershipService:286` | non |

`PolicyEvaluator.isTenantAdmin()` accepte les codes fantômes `R_PLATFORM_ADMIN` et
`PLATFORM_ADMIN`, **mais pas** `R_TAKIBO_PLATFORM_ADMIN`.

**Chaîne d'escalade par nommage** (établie par lecture du code) :

```
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
tenant. `RoleApplicationService` expose bien un chemin de création
(`roleRepository.save`), et aucune réservation de préfixe n'a été trouvée dans
`takibo-identity-core`.

**Reste à vérifier** : si ce chemin de création est exposé aux tenants via REST et avec
quelle autorisation. Si oui, la faille est exploitable aujourd'hui.

**Correctifs** :

1. `isTenantAdmin()` ne reconnaît que les codes réels du catalogue technique —
   suppression des alias fantômes.
2. Un code de rôle tenant (`GOVERNANCE` / `BUSINESS`) ne peut jamais emprunter un
   préfixe réservé (`R_TAKIBO_`, `R_ORG_`, `R_SPACE_`, `PLATFORM`) ni collisionner avec
   un code technique.
3. Un rôle `GOVERNANCE` n'entre au token que sous un préfixe non ambigu.

---

## 10. Groupes techniques

Les groupes suivent les mêmes plans. Les quatre groupes actuellement vides
(`G_ORG_USERS`, `G_ORG_CLIENTS`, `G_SPACE_USERS`, `G_SPACE_CLIENTS`) sont conservés
comme **marqueurs d'appartenance purs, sans aucun rôle** — et cette intention est
désormais explicite : appartenir à une organisation ou à un Space ne confère aucun
pouvoir de lecture automatique. Le pouvoir passe toujours par un rôle nommé.

`G_ORG_ADMINS` (→ `R_ORG_OWNER`, `R_ORG_ADMIN`) et `G_SPACE_ADMINS` (→ `R_SPACE_ADMIN`)
restent inchangés.

---

## 11. Table de migration

Les rôles sont persistés en base (`V202606011800__identity_roles_add_role_nature.sql`,
`V202607111500__rbac_org_authority_reclassification.sql`). Le changement de catalogue
exige donc une migration de données, pas un simple retrait d'enum.

| Ancien | Nouveau | Action |
|---|---|---|
| `SYSTEM` (scope) | `PLATFORM` | renommage |
| `USER` (scope) | — | suppression, aucun rôle restant |
| `SYSTEM_ADMIN` (enum) | `PLATFORM_ADMIN` (enum) | renommage — le code `R_TAKIBO_PLATFORM_ADMIN` ne change pas |
| `SYSTEM_AUDITOR` (enum) | `PLATFORM_AUDITOR` (enum) | idem |
| `R_ORG_VIEWER` | `R_ORG_READER` | renommage + reclassement des assignations |
| `R_SPACE_VIEWER` | `R_SPACE_READER` | renommage ; **perd** la lecture d'audit → `R_SPACE_AUDITOR` si l'audit était l'intention |
| `R_SELF` | — | suppression des assignations ; remplacé par le libre-service |
| — | `R_SPACE_AUDITOR` | création |
| `P_MANAGE_USERS` | `P_USER_*` | éclatement |
| `P_MANAGE_CLIENTS` | `P_CLIENT_*` | éclatement |
| `P_ASSIGN_ROLES` | `P_ROLE_ASSIGN`, `P_GROUP_ASSIGN` | éclatement |
| `P_READ_AUDIT_LOGS` | `P_AUDIT_READ` | renommage |
| `P_EXPORT_AUDIT_LOGS` | `P_AUDIT_EXPORT` | renommage |
| `P_READ_ORG` / `P_UPDATE_ORG_SETTINGS` | `P_ORG_READ` / `P_ORG_UPDATE` | renommage |
| `P_CREATE_SPACE` / `P_DELETE_SPACE` | `P_SPACE_CREATE` / `P_SPACE_DELETE` | renommage |
| `P_CREATE_ORG` | `P_ORG_CREATE` | renommage ; plan PLATFORM. Le signup self-service crée une organisation **sans permission** (route publique) |
| `P_DELETE_ORG` | `P_ORG_DELETE` | **une seule permission.** Le rayon vient du plan du rôle qui la détient : un rôle PLATFORM supprime n'importe quelle organisation, `R_ORG_OWNER` supprime la sienne. Nommer `P_PLATFORM_ORG_DELETE` réintroduirait la frontière dans la permission et violerait la loi 7 |
| `P_READ_POLICY` / `P_UPDATE_POLICY` | `P_POLICY_READ` / `P_POLICY_UPDATE` | renommage |

---

## 12. Conséquences

**Positives** — un champ, un sens ; la propriété d'organisation devient réelle ; l'audit
est symétrique sur les trois plans ; le libre-service sort du RBAC ; l'intervention
plateforme devient explicite et tracée ; le catalogue passe de « accumulé » à « dérivé
d'une matrice ».

**Coûts** — migration de données obligatoire (rôles persistés) ; éclatement des
permissions à répercuter dans `PolicyEvaluator` et les politiques ; `ActorSource` à
étendre ; les tests RBAC existants à reprendre.

**Non traité ici** — le flux d'entrée « Platform Admin dans une organisation » (UI +
audit) et la politique de libre-service font l'objet de récits distincts.

---

## 13. Décisions restant ouvertes

1. `R_ORG_USER_ADMIN` doit-il pouvoir attribuer des rôles (`P_ROLE_ASSIGN`) ? La
   doctrine dit non ; à confirmer.
2. `R_SPACE_AUDITOR` doit-il avoir `P_AUDIT_EXPORT` ? Retenu ici par symétrie avec
   `R_ORG_AUDITOR`, mais l'export est la capacité la plus sensible d'un auditeur.
3. Les rôles `R_*_READER` sont un amendement à la proposition initiale, qui supprimait
   purement les viewers. À valider ou rejeter explicitement.
