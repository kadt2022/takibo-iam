# TAKIBO — Plan et ordre d’implémentation de la doctrine RBAC v2

## 1. Principe directeur

La doctrine RBAC v2 ne doit pas être introduite par une modification massive de tous les contrôleurs.

L’ordre correct est :

```text
Vocabulaire
→ catalogue
→ matrice rôles-permissions
→ calcul des permissions effectives
→ projection dans les tokens
→ enforcement
→ gouvernance des attributions
→ migrations des données
→ API et interface
→ suppression de l’ancien modèle
```

Le point central est le suivant :

```text
Rôle
= origine du pouvoir

Permission
= capacité effective

org_id / space_id
= frontière d’exercice
```

Une route n’est donc autorisée que lorsque ces trois dimensions sont cohérentes.

---

# 2. Découpage recommandé

La mise en œuvre devrait être divisée en **neuf récits indépendants**.

```text
RBAC-01 — Modèle canonique
RBAC-02 — Catalogue et matrice
RBAC-03 — Permissions effectives
RBAC-04 — Tokens situés
RBAC-05 — Enforcement SPACE
RBAC-06 — Enforcement ORGANIZATION
RBAC-07 — Ownership et gouvernance
RBAC-08 — Audit et intervention plateforme
RBAC-09 — Migration finale et nettoyage
```

Il ne faut pas essayer de livrer les neuf récits dans une seule PR.

---

# 3. RBAC-01 — Introduire le modèle canonique

## Objectif

Créer les objets fondamentaux de la doctrine sans encore modifier le comportement des routes existantes.

## À introduire

### Plans d’autorité

```java
public enum AuthorityPlan {
    PLATFORM,
    ORGANIZATION,
    SPACE
}
```

### Rôles techniques

```text
R_TAKIBO_PLATFORM_ADMIN
R_TAKIBO_PLATFORM_AUDITOR

R_ORG_OWNER
R_ORG_ADMIN
R_ORG_USER_ADMIN
R_ORG_CLIENT_ADMIN
R_ORG_AUDITOR

R_SPACE_ADMIN
R_SPACE_USER_ADMIN
R_SPACE_CLIENT_ADMIN
R_SPACE_AUDITOR
```

### Permissions

Créer un catalogue canonique contenant les permissions :

```text
P_PLATFORM_*
P_ORG_*
P_SPACE_*
```

Chaque permission doit connaître au minimum :

```text
code
plan
ressource
action
description
```

Exemple :

```java
P_SPACE_USERS_MANAGE(
    AuthorityPlan.SPACE,
    Resource.USERS,
    Action.MANAGE
)
```

## Règles à inscrire dans le modèle

Un rôle technique doit déclarer :

```text
son code
son plan
son caractère assignable
son caractère héritable
son caractère administrateur
```

Exemple particulier :

```text
R_ORG_OWNER
assignable = false
inheritable = false
businessRole = false
```

## À ne pas faire dans ce récit

Ne pas encore :

```text
modifier tous les contrôleurs
changer les tokens
migrer les données
supprimer les anciens rôles
modifier l’interface
```

## Critères d’acceptation

- Les 11 rôles existent dans un catalogue unique.
- Toutes les permissions sont rattachées à un plan.
- Aucune permission ne possède un plan ambigu.
- `R_ORG_OWNER` est déclaré non assignable et non héritable.
- `R_SELF` est marqué comme déprécié, sans être encore supprimé brutalement.
- Les tests garantissent l’unicité des codes.

---

# 4. RBAC-02 — Construire la matrice rôles-permissions

## Objectif

Définir explicitement les permissions accordées par chaque rôle.

La matrice ne doit pas être dispersée dans les contrôleurs.

Elle doit vivre dans un composant central, par exemple :

```java
TechnicalRolePermissionCatalog
```

ou :

```java
RolePermissionRegistry
```

## Exemple

```java
R_SPACE_USER_ADMIN -> {
    P_SPACE_READ,
    P_SPACE_USERS_READ,
    P_SPACE_USERS_MANAGE,
    P_SPACE_USERS_LIFECYCLE
}
```

```java
R_ORG_CLIENT_ADMIN -> {
    P_ORG_READ,
    P_ORG_SPACES_READ,
    P_ORG_CLIENTS_READ,
    P_ORG_CLIENTS_MANAGE,
    P_ORG_CLIENTS_ROTATE_SECRET,
    P_ORG_CLIENTS_LIFECYCLE
}
```

## Règles importantes

### Org Owner

```text
permissions(R_ORG_OWNER)
=
permissions(R_ORG_ADMIN)
+
P_ORG_OWNERSHIP_TRANSFER
+
P_ORG_DEACTIVATE
+
P_ORG_DELETION_REQUEST
```

### Platform Auditor

Il ne reçoit pas automatiquement :

```text
P_PLATFORM_AUDIT_EXPORT
```

### Space Admin

Il ne reçoit jamais :

```text
P_ORG_SPACES_CREATE
P_ORG_SPACES_DELETE
```

Un Space Admin administre un Space existant. Il ne gouverne pas le cycle de vie du conteneur depuis le plan organisation.

## Critères d’acceptation

- Chaque rôle possède une matrice déterministe.
- Aucun rôle `SPACE` ne reçoit une permission `P_ORG_*`.
- Aucun rôle `ORGANIZATION` ne devient artificiellement un rôle `SPACE`.
- Les tests vérifient toute la matrice.
- Une permission absente est refusée par défaut.

---

# 5. RBAC-03 — Calculer les permissions effectives

## Objectif

Introduire un moteur central qui transforme les rôles réels du sujet en permissions effectives dans un contexte donné.

Composant proposé :

```java
EffectivePermissionResolver
```

Entrée :

```java
roles
groups
authorityPlan
orgId
spaceId
subjectNature
actorSource
```

Sortie :

```java
Set<PermissionCode>
```

## Cas fondamental : Org Admin dans un Space

Entrée :

```text
roles = [R_ORG_ADMIN]
org_id = A
space_id = A1
targetPlan = SPACE
```

Sortie :

```text
roles = [R_ORG_ADMIN]

permissions =
P_SPACE_READ
P_SPACE_UPDATE
P_SPACE_USERS_READ
P_SPACE_USERS_MANAGE
P_SPACE_USERS_LIFECYCLE
P_SPACE_CLIENTS_READ
P_SPACE_CLIENTS_MANAGE
P_SPACE_CLIENTS_ROTATE_SECRET
P_SPACE_CLIENTS_LIFECYCLE
P_SPACE_RBAC_READ
P_SPACE_RBAC_ASSIGN
P_SPACE_POLICY_READ
P_SPACE_POLICY_UPDATE
P_SPACE_AUDIT_READ
P_SPACE_AUDIT_EXPORT
```

Le moteur ne doit pas produire :

```text
roles = [R_ORG_ADMIN, R_SPACE_ADMIN]
```

Le rôle réel reste `R_ORG_ADMIN`.

## Règle de projection

Il faut une table explicite de projection :

```text
permission ORGANIZATION
→ permission SPACE équivalente
```

Exemple :

```text
P_ORG_USERS_READ
→ P_SPACE_USERS_READ

P_ORG_USERS_MANAGE
→ P_SPACE_USERS_MANAGE

P_ORG_CLIENTS_MANAGE
→ P_SPACE_CLIENTS_MANAGE
```

Toutes les permissions ne sont pas projetables.

Exemple :

```text
P_ORG_SPACES_CREATE
```

ne devient aucune permission `SPACE`.

## Protections

Le moteur doit refuser :

```text
permission SPACE sans space_id
permission ORGANIZATION sans org_id
space_id appartenant à une autre organisation
rôle SPACE utilisé dans un autre Space
projection PLATFORM vers tenant automatique
```

## Critères d’acceptation

- Le résultat est stable et déterministe.
- L’héritage des groupes est pris en compte.
- L’Org Admin conserve son rôle réel.
- Les permissions effectives dépendent du contexte.
- Aucun rôle ne peut élargir la frontière du token.

---

# 6. RBAC-04 — Projeter les permissions dans les tokens

## Objectif

Faire porter au token la vérité calculée par le moteur RBAC.

## Claims minimaux

```json
{
  "roles": ["R_ORG_ADMIN"],
  "permissions": [
    "P_SPACE_USERS_READ",
    "P_SPACE_USERS_MANAGE"
  ],
  "takibo_scope_level": "SPACE",
  "org_id": "...",
  "space_id": "...",
  "subject_type": "HUMAN",
  "takibo_tenant_source": "human_space_selection"
}
```

## Règles

### Token ORGANIZATION

Doit contenir :

```text
org_id
pas nécessairement space_id
permissions P_ORG_*
```

### Token SPACE

Doit contenir :

```text
org_id
space_id
permissions P_SPACE_*
```

### Token PLATFORM

Doit contenir :

```text
aucun faux org_id
aucun faux space_id
permissions P_PLATFORM_*
```

### Changement de Space

Le changement de Space doit produire un nouveau contexte ou un nouveau token situé.

Il ne faut jamais modifier seulement le `space_id` côté interface tout en conservant les permissions d’un ancien Space.

## Critères d’acceptation

- Le token ne contient aucune permission incompatible avec son plan.
- Un token `SPACE` porte toujours `org_id` et `space_id`.
- Un token `ORGANIZATION` ne donne pas accès automatiquement à un Space arbitraire.
- Les permissions sont recalculées à chaque émission ou changement de contexte.
- Les anciens tokens restent valides seulement pendant leur TTL normal.

---

# 7. RBAC-05 — Migrer d’abord l’enforcement SPACE

## Pourquoi commencer par SPACE

Le plan `SPACE` est la frontière la plus précise et la plus facile à tester.

Il permet de valider immédiatement la loi essentielle :

```text
permission correcte
+
mauvais space_id
=
403
```

## Ordre des ressources

Migrer les routes SPACE dans cet ordre :

```text
1. Users
2. OAuth2/OIDC Clients
3. Roles et Groups
4. Space configuration
5. Policies
6. Audit
```

## Exemple de politique

Pour créer un utilisateur :

```text
permission requise :
P_SPACE_USERS_MANAGE

frontière requise :
token.org_id == route.orgId
token.space_id == route.spaceId

état requis :
organisation active
space actif
acteur authentifié
```

## PolicyEvaluator

Le `PolicyEvaluator` doit recevoir une demande structurée :

```java
AuthorizationRequest.builder()
    .requiredPermission(P_SPACE_USERS_MANAGE)
    .targetOrganizationId(orgId)
    .targetSpaceId(spaceId)
    .build();
```

Il ne doit plus déduire le pouvoir seulement à partir d’un nom de rôle.

## Migration progressive

Pendant la transition :

```text
ancien rôle connu
→ conversion interne vers permissions v2
→ décision avec le moteur v2
```

Il faut éviter un mélange durable du type :

```java
hasRole("R_SPACE_ADMIN")
|| hasPermission("P_SPACE_USERS_MANAGE")
```

Le double système doit être temporaire et centralisé, jamais copié dans tous les contrôleurs.

## Critères d’acceptation

Tester au minimum :

```text
permission correcte + bonne frontière = autorisé
permission correcte + mauvais Space = refusé
bonne frontière + permission absente = refusé
rôle Org Admin + projection valide = autorisé
rôle Space Admin d’un autre Space = refusé
token sans space_id = refusé
Space suspendu = refusé
```

---

# 8. RBAC-06 — Migrer l’enforcement ORGANIZATION

## Objectif

Après validation du plan SPACE, migrer les opérations organisationnelles.

## Ordre des ressources

```text
1. Lecture et modification de l’organisation
2. Liste et cycle de vie des Spaces
3. Utilisateurs de l’organisation
4. Clients de l’organisation
5. Catalogue et attributions RBAC
6. Politiques
7. Audit
```

## Règle de frontière

Pour toute route :

```text
/api/v1/orgs/{orgId}/...
```

il faut garantir :

```text
token.org_id == orgId
```

sauf intervention plateforme explicitement située.

## Attention particulière

Un rôle `R_SPACE_ADMIN` ne doit jamais obtenir une permission `P_ORG_*`, même si son Space appartient à l’organisation ciblée.

## Critères d’acceptation

- Un Org Admin voit seulement son organisation.
- Un Org User Admin ne gère pas les clients.
- Un Org Client Admin ne gère pas les utilisateurs.
- Un Org Auditor ne modifie aucune ressource.
- Un Space Admin ne peut ni créer ni supprimer un Space.
- Les permissions d’ownership sont absentes de l’Org Admin.

---

# 9. RBAC-07 — Implémenter l’ownership et la gouvernance des attributions

## Objectif

Traiter séparément les opérations dangereuses qui ne doivent jamais passer par les endpoints génériques d’attribution.

## TransferOwnership

Créer une commande dédiée :

```java
TransferOrganizationOwnershipCommand
```

L’opération doit être atomique :

```text
vérifier l’Owner actuel
vérifier le nouveau propriétaire
retirer R_ORG_OWNER à l’ancien propriétaire
accorder R_ORG_OWNER au nouveau propriétaire
garantir qu’il reste exactement un Owner
auditer l’opération
valider la transaction
```

## Interdictions génériques

L’endpoint générique d’attribution doit refuser :

```text
R_ORG_OWNER
```

Il doit aussi empêcher un administrateur spécialisé d’attribuer un rôle plus puissant que le sien.

## Groupes

```text
G_ORG_ADMINS
→ R_ORG_ADMIN
```

Jamais :

```text
G_ORG_ADMINS
→ R_ORG_OWNER
```

## Suppression d’un propriétaire

Il faut refuser :

```text
désactivation
suppression
retrait d’appartenance
```

du dernier Owner tant qu’un transfert n’a pas eu lieu.

## Critères d’acceptation

- Une organisation possède exactement un Owner.
- `R_ORG_OWNER` ne passe par aucun endpoint générique.
- `R_ORG_OWNER` ne peut pas venir d’un groupe.
- Le transfert est transactionnel.
- Toute tentative invalide est auditée.

---

# 10. RBAC-08 — Audit situé et intervention plateforme

## Audit

L’audit doit être traité comme une capacité de chaque plan :

```text
P_PLATFORM_AUDIT_*
P_ORG_AUDIT_*
P_SPACE_AUDIT_*
```

Il ne faut pas créer :

```text
AUDIT comme quatrième plan
```

## Filtres obligatoires

### Audit PLATFORM

```text
événements produits par le plan plateforme
```

### Audit ORGANIZATION

```text
organization_id = token.org_id
```

Avec agrégation autorisée des Spaces de cette organisation.

### Audit SPACE

```text
organization_id = token.org_id
space_id = token.space_id
```

## Intervention plateforme

Le Platform Admin ne doit pas utiliser son token plateforme directement dans un tenant.

Créer un workflow explicite :

```text
demande d’intervention
motif obligatoire
organisation cible
Space cible éventuel
durée limitée
émission d’un nouveau token
audit spécial
```

Le nouveau contexte doit porter :

```text
ActorSource = PLATFORM_IMPERSONATION
```

Il faut conserver séparément :

```text
acteur réel
acteur effectif
motif
tenant cible
date de début
date d’expiration
```

## Critères d’acceptation

- Un Platform Admin ne voit aucun tenant par défaut.
- Une intervention nécessite une action explicite.
- Le motif est obligatoire.
- Le token d’intervention est limité au tenant ciblé.
- Toutes les actions sont identifiables comme impersonation plateforme.

---

# 11. RBAC-09 — Migrations, interface et suppression de l’ancien modèle

Cette phase arrive seulement lorsque les moteurs et l’enforcement sont stabilisés.

## Étape 1 — Migration du catalogue

Mettre à jour :

```text
TechnicalRole
catalogue des permissions
rôles techniques persistés
relations rôle-permission
groupes techniques
```

Les migrations doivent être :

```text
idempotentes
rejouables
compatibles avec les environnements existants
```

## Étape 2 — Migration des anciennes attributions

Créer une table de correspondance explicite.

Exemple :

```text
ancien R_ADMIN
→ décision manuelle ou mapping selon sa frontière réelle

ancien R_SELF
→ aucune attribution
```

Il ne faut pas convertir automatiquement un ancien rôle ambigu en rôle très puissant.

## Étape 3 — Disparition de R_SELF

Retirer `R_SELF` des :

```text
rôles techniques
migrations
groupes
tokens
tests
interfaces
```

Les opérations personnelles doivent passer par une politique dédiée :

```java
SelfRelationshipPolicy
```

Exemples :

```text
subject.accountId == target.accountId
subject.userId == target.userId
```

## Étape 4 — Interface

L’interface doit utiliser les permissions effectives.

Elle peut masquer ou désactiver une action selon :

```text
permissions du token
plan actuel
frontière actuelle
```

Mais l’interface ne constitue jamais l’enforcement de sécurité.

## Étape 5 — Nettoyage

Supprimer :

```text
anciens codes de rôles
compatibilité transitoire
branches historiques du PolicyEvaluator
mappings temporaires
tests de l’ancien modèle
```

Le nettoyage doit faire l’objet d’une PR séparée.

---

# 12. Ordre d’implémentation technique par module

## 1. Module de modèle partagé

Introduire :

```text
AuthorityPlan
PermissionCode
TechnicalRole v2
RoleCharacteristics
RolePermissionCatalog
```

## 2. TIS-CORE

Implémenter :

```text
résolution des rôles
héritage par groupes
calcul des permissions effectives
gouvernance des attributions
ownership
self-service
```

## 3. TAS

TAS ne doit pas inventer les permissions.

Il doit :

```text
recevoir un sujet déjà résolu
projeter les claims
signer le token
```

La vérité RBAC doit être calculée en amont.

## 4. Security Context

Exposer clairement :

```text
current roles
current permissions
current authority plan
current organization
current space
actor source
```

## 5. PolicyEvaluator

Faire évoluer les décisions de :

```text
rôle seul
```

vers :

```text
permission
+
frontière
+
état de la ressource
+
nature du sujet
+
source de l’acteur
```

## 6. TMS

Appliquer les permissions aux ressources :

```text
organisations
Spaces
clients OAuth2/OIDC
ownership
lifecycle
```

## 7. Security Management et Audit

Implémenter :

```text
lecture située
export situé
traçage des décisions
intervention plateforme
```

## 8. Interface

Adapter :

```text
menus
actions
boutons
écrans d’attribution
affichage du rôle réel
affichage du contexte courant
```

---

# 13. Ordre des PR recommandé

| PR | Contenu | Risque |
|---|---|---:|
| PR 1 | Types, codes et catalogue RBAC v2 | Faible |
| PR 2 | Matrice rôles-permissions et tests | Faible |
| PR 3 | EffectivePermissionResolver | Moyen |
| PR 4 | Claims de permissions dans les tokens | Élevé |
| PR 5 | Enforcement des routes SPACE | Élevé |
| PR 6 | Enforcement des routes ORGANIZATION | Élevé |
| PR 7 | Ownership et restrictions d’attribution | Élevé |
| PR 8 | Audit situé et intervention plateforme | Élevé |
| PR 9 | Migrations des données existantes | Élevé |
| PR 10 | UI fondée sur les permissions | Moyen |
| PR 11 | Suppression de l’ancien modèle | Moyen |

---

# 14. Ce qu’il ne faut surtout pas faire

## Ne pas renommer seulement les rôles

Changer :

```text
R_ADMIN
```

en :

```text
R_ORG_ADMIN
```

sans modifier la frontière et les permissions ne constitue pas une migration RBAC.

## Ne pas attribuer R_SPACE_ADMIN à l’Org Admin

L’Org Admin doit conserver :

```text
R_ORG_ADMIN
```

et recevoir des permissions `P_SPACE_*` effectives lorsqu’il travaille dans un Space.

## Ne pas mettre tout le pouvoir dans le token

Le token décrit une autorité située, mais le serveur doit toujours vérifier :

```text
la frontière de la route
l’existence de la ressource
l’appartenance du Space à l’organisation
l’état actif des frontières
```

## Ne pas commencer par l’interface

Masquer un bouton ne sécurise pas un endpoint.

## Ne pas commencer par Platform Impersonation

L’intervention plateforme est une capacité sensible. Elle ne doit être implémentée qu’après stabilisation de l’autorisation ORGANIZATION et SPACE.

## Ne pas supprimer R_SELF trop tôt

Il faut d’abord remplacer ses usages par une politique explicite de relation à soi.

---

# 15. Premier récit à exécuter

Le premier récit doit être :

## RBAC-01 — Catalogue canonique des rôles et permissions situés

### Résultat attendu

À la fin du récit :

```text
les 11 rôles existent
les permissions P_PLATFORM_*, P_ORG_* et P_SPACE_* existent
chaque code connaît son plan
R_ORG_OWNER est non assignable et non héritable
R_SELF est déprécié
aucune route existante n’est encore cassée
```

### Pourquoi commencer ici

Sans ce vocabulaire canonique :

```text
le token ne sait pas quoi transporter
le PolicyEvaluator ne sait pas quoi vérifier
les migrations ne savent pas quoi créer
l’interface ne sait pas quoi afficher
les tests ne disposent pas d’une vérité commune
```

Le catalogue constitue donc la première pierre obligatoire.

---

# 16. Résumé exécutif

```text
1. Figer les codes et les trois plans
2. Définir la matrice rôles-permissions
3. Calculer les permissions effectives
4. Les projeter dans les tokens
5. Sécuriser d’abord les routes SPACE
6. Sécuriser ensuite les routes ORGANIZATION
7. Isoler l’ownership des attributions génériques
8. Situer l’audit et l’intervention plateforme
9. Migrer les données existantes
10. Adapter l’interface
11. Supprimer l’ancien modèle
```

La règle de livraison doit rester :

> Une PR introduit une capacité cohérente, testable et réversible. Aucune PR ne doit mélanger catalogue, migration massive, tokens, contrôleurs et interface.
