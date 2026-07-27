# Takibo (TAKIBO) — Philosophie d’identité

Ce document n’est pas du marketing. Il existe pour empêcher l’érosion du modèle.

## Axiome central

**Un Account n’est pas une personne. Un Account est un principal.**

Un **Account** est l’unité minimale, stable, qui peut être authentifiée.
Un **User** est la projection d’un Account dans un contexte (Space), où vivent l’accès et la gouvernance.
Un **IdentityLink** est une preuve/lien vers une identité externe (fédération), pas l’Account lui-même.

Ces trois concepts sont conçus pour être orthogonaux et composables.

## Définitions

### Account (Principal global)
Un **Account** représente un sujet qui peut être authentifié (humain ou machine).
Il est global et stable à travers les Spaces.

Exemples de propriétés :
- `account_id` (identifiant stable)
- `account_type` (HUMAN, MACHINE, etc.)
- posture de sécurité globale (mot de passe/MFA, récupération, signaux de risque)
- cycle de vie global (actif/verrouillé/désactivé)

Un Account peut exister avec :
- zéro User (aucun accès à aucun Space pour l’instant)
- plusieurs Users (membre de plusieurs Spaces)

### User (Projection contextuelle)
Un **User** est un Account “vu” depuis un **Space**.
C’est ici que vivent l’appartenance, les rôles/groupes, et les attributs spécifiques au Space.

Exemples de propriétés :
- `space_id`
- `account_id`
- attributs locaux (alias, tags, flags internes)
- affectations rôles/groupes dans le Space

Si un User est supprimé, l’Account reste : l’identité survit, l’accès à ce Space disparaît.

### IdentityLink (Preuve externe / lien de fédération)
Un **IdentityLink** relie une identité externe à un Account :
- provider (azuread, okta, google, etc.)
- issuer
- subject
- attributs éventuellement “miroir” (optionnel)

Les IdentityLinks ne définissent pas les droits. Ils définissent seulement des routes d’authentification.

Si un IdentityLink est supprimé, l’Account reste et d’autres méthodes de connexion peuvent subsister.

## Stress tests (Cas limites)

### 1) Identités machines (service accounts, bots, clients API)
Une identité machine est un **Account(type=MACHINE)**.
Elle obtient l’accès à un Space via un User dans ce Space (même gouvernance que les humains).

Aucune exception ou logique spéciale n’est nécessaire.

### 2) Fédération (Azure AD, Okta, etc.)
La preuve d’authentification vient de l’IdP externe via IdentityLink (issuer/subject).
L’autorisation reste locale à Takibo dans les Spaces.

Cela évite “deux sources de vérité” :
- l’IdP externe prouve l’identité
- Takibo gouverne l’accès

### 3) B2C (pas d’organisation entreprise, login social)
Un consommateur correspond aussi à un **Account**.
L’accès se fait via un Space représentant le contexte produit/application (ex : Space “public”).

Pas de “skip org” : la multi-tenance est un choix de contexte, pas une exception du modèle.

## Invariants de suppression (Test de suppression)

Le modèle doit rester cohérent sous des suppressions indépendantes :

- Supprimer un **User** → l’Account survit ; le principal existe encore mais perd l’accès à ce Space.
- Supprimer un **IdentityLink** → l’Account survit ; d’autres méthodes de login peuvent rester.
- Supprimer un **Space** → les Users de ce Space disparaissent (cascade) ; les Accounts survivent.
- Supprimer un **Account** → tout cascade proprement (Users, IdentityLinks, facteurs MFA, credentials).
- Changer AccountType HUMAN ↔ MACHINE → rien ne casse structurellement ; l’Account reste un principal.

Toute évolution future qui viole ces invariants est une régression de design.

## Conséquences pratiques

- Les tokens identifient un principal (**account_id**) et éventuellement un contexte (**space_id**).
- L’autorisation se calcule dans un contexte (Space / scope), jamais dans l’Account.
- La fédération ne “gouverne” pas Takibo : elle prouve seulement une identité externe.

## Non-objectifs

- Nous n’assimilons pas Account à “fiche RH” ou “profil client”.
- Nous ne stockons pas la gouvernance d’accès dans les providers externes.
- Nous ne fusionnons pas identité et accès dans une seule entité.
