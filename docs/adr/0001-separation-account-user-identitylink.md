# ADR 0001 : Séparer Account, User et IdentityLink

Date : 2026-02-14
Statut : Accepté

## Contexte

Takibo doit supporter :
- utilisateurs humains et principals machines
- fédération (Azure AD, Okta, Google, etc.)
- modèles B2B et B2C
- gouvernance multi-spaces et contrôle d’accès

La plupart des modèles IAM échouent au stress test car identité et accès sont fusionnés.

## Décision

Nous adoptons un modèle orthogonal :

- **Account** : principal global, unité stable et minimale authentifiable.
- **User** : projection d’un Account dans un Space ; contient l’appartenance et les affectations d’accès.
- **IdentityLink** : preuve/lien d’identité externe vers un Account (issuer/subject), pas un profil utilisateur.

## Justification

Ce modèle passe le “test de suppression” (suppression indépendante sans corruption d’état) :
- supprimer User → Account survit
- supprimer IdentityLink → Account survit
- supprimer Space → Users en cascade, Accounts survivent
- supprimer Account → tout en cascade (Users, IdentityLinks, MFA/credentials)
- changer le type d’Account → pas de rupture structurelle

Il supporte proprement :
- identités machines (Account=MACHINE)
- fédération (IdentityLink comme preuve, gouvernance locale)
- B2C (Space comme contexte produit)

## Conséquences

- Les tokens sont ancrés sur `account_id` ; `space_id` est un contexte.
- L’autorisation se calcule dans le contexte Space ; l’Account n’est pas un conteneur de droits.
- La fédération est “auth-only” ; elle ne définit pas la gouvernance Takibo.

## Alternatives évaluées

1) Entité unique “User” (identité + accès) :
- rejetée : échec au test de suppression, couplages cachés, fédération et machines compliquées.

2) Identité externe comme Account (miroir uniquement) :
- rejetée : rend l’IdP externe source unique de vérité, affaiblit le cycle de vie/sécurité interne.

## Invariants

- Un Account peut exister sans User.
- Un User ne peut pas exister sans Account + Space.
- IdentityLink référence Account (pas User).
- Supprimer un Space ne supprime pas les Accounts.
- Supprimer un Account cascade Users, IdentityLinks, MFA/credentials.
