# TMS-VAL-01 — Validation imbriquée et contrats REST

## Contexte

Le payload d'inscription d'une organisation contient quatre objets imbriqués. Ils
sont obligatoires, mais leurs propres contraintes ne sont pas parcourues. Des
valeurs vides ou invalides peuvent donc atteindre la couche applicative.

Deux réponses de création ne respectent pas non plus leur contrat REST : l'URL de
l'organisation créée contient l'identifiant du space et la création d'un client
OAuth répond `200 OK` sans URL vers la ressource créée.

## Loi du récit

Toute entrée invalide est refusée à la frontière HTTP avec un statut `400`, avant
l'appel du service applicatif. Toute création réussie répond `201 Created` et fournit
une en-tête `Location` construite avec l'identifiant de la ressource créée.

## Périmètre

- Activer la validation en cascade des blocs `organization`, `space`, `account` et
  `profile` du signup.
- Rendre obligatoires les champs nécessaires à la création et aligner la longueur
  du nom du space sur le stockage.
- Corriger l'en-tête `Location` du signup avec l'identifiant de l'organisation.
- Retourner `201 Created` et `Location` lors de la création d'un client OAuth.
- Préserver les en-têtes anti-cache protégeant le secret OAuth à usage unique.
- Ajouter des tests HTTP de non-régression.

## Critères d'acceptation

### AC-01 — Validation imbriquée

Un signup contenant un champ imbriqué vide, trop court ou mal formé répond `400` et
le service d'inscription n'est pas appelé.

### AC-02 — Création de l'organisation

Un signup valide répond `201` avec
`Location: /api/v1/orgs/{organizationId}`.

### AC-03 — Création du client OAuth

Une création valide répond `201` avec
`Location: /api/v1/orgs/{orgId}/spaces/{spaceId}/clients/{clientId}` et conserve les
en-têtes `Cache-Control`, `Pragma` et `X-Content-Type-Options`.

## Branche

`fix/tms-validation-rest-contracts`

## Commit proposé

`fix(tms): enforce validation and REST creation contracts`
