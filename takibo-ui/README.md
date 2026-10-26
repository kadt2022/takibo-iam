# TAKIBO UI

**TAKIBO Administration Console** — Identity, Access and Context.

TAKIBO UI est l'interface web officielle d'administration de la plateforme TAKIBO.
Ce dépôt contient le socle frontend et la page de connexion (récit TAKIBO UI 01).

> Une identité souveraine. Un accès situé. Une confiance vérifiable.

## Stack

| Technologie     | Responsabilité                        |
| --------------- | ------------------------------------- |
| React 19        | Construction des pages et composants  |
| TypeScript      | Typage strict des modèles et contrats |
| Vite            | Développement et build                |
| React Router    | Navigation entre les pages            |
| TanStack Query  | Gestion des données serveur           |
| React Hook Form | Gestion des formulaires               |
| Zod             | Validation des données frontend       |
| Tailwind CSS 4  | Langage visuel piloté par tokens      |
| Radix UI        | Primitives accessibles                |
| Vitest + RTL    | Tests unitaires et de composants      |
| Playwright      | Tests fonctionnels dans le navigateur |

## Démarrage

```bash
npm install
npm run dev
```

L'application démarre sur `http://localhost:5173` et redirige vers `/login`.

## Commandes

```bash
npm run dev          # serveur de développement
npm run lint         # ESLint
npm run format       # Prettier (écriture)
npm run test         # tests unitaires Vitest
npm run test:e2e     # tests fonctionnels Playwright (npx playwright install chromium au premier lancement)
npm run build        # vérification TypeScript stricte + build de production
npm run preview      # prévisualisation du build
```

## Architecture

```text
src/
├── app/              # démarrage, routeur, providers globaux
├── design-system/    # composants visuels génériques TAKIBO (sans logique métier)
├── features/         # capacités fonctionnelles par domaine (authentication…)
├── layouts/          # structures visuelles de pages
├── shared/           # éléments techniques partagés
└── styles/           # tokens de design et styles globaux
```

Les couleurs, espacements, rayons, ombres et typographies sont définis comme
tokens dans [src/styles/tokens.css](src/styles/tokens.css) : aucun composant
n'introduit de valeur brute.

## Sécurité

Le frontend n'est jamais la frontière finale de sécurité.

- Aucun secret dans le dépôt ni dans les variables `VITE_*` (embarquées dans le bundle).
- Le mot de passe n'apparaît jamais dans les logs, l'URL, le stockage navigateur ou un message d'erreur.
- Les erreurs affichées ne révèlent ni l'existence d'un compte ni un détail technique du backend.
- Architecture cible : le navigateur ne conserve aucun token ; la session web sera portée
  par un cookie sécurisé émis par le BFF Spring Boot (récit TAKIBO UI 02), qui dialoguera
  en OAuth2/OIDC avec le TAKIBO Authorization Server.

## Connexion organisationnelle (récit UI 01.6 — aligné IAM 31)

> L'organisation identifie le compte. Le space situe l'action.

Le formulaire appelle réellement TIS-CORE (`POST /api/v1/auth/login`) via le
proxy Vite (`/api` → `http://localhost:8081`) avec **trois champs** :
organisation + courriel + mot de passe. En cas de succès, `/org` ouvre la
**Console Organisation** : contexte de session, pouvoir organisationnel du
token (rôles, groupes, permissions de scope ORGANIZATION uniquement) et la
liste des spaces de l'organisation (surface TMS, autorité d'org requise —
la liste personnelle d'un compte sans autorité arrivera avec IAM 32).
L'entrée dans un space (échange de contexte) arrivera avec IAM 33.

Tout échec de connexion affiche le même message — « Impossible de valider
cette connexion. » — à l'image du 401 uniforme du backend (anti-énumération).

Ce mode direct est **provisoire et assumé** : la session vit uniquement en
mémoire React (un rafraîchissement la termine), aucun token n'est écrit dans
le stockage navigateur. Le BFF Spring Boot (récit UI 02) apportera la session
durable par cookie sécurisé.

### Tester en local

1. Démarrer le backend Takibo-IAM : `./gradlew :takibo-iam-boot:bootRun` (port 8081).
2. Provisionner une organisation et son fondateur (une seule fois) :

```powershell
# Token PLATFORM (client dev postman)
$pair = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes('postman-client:dev-postman-secret'))
$tok = Invoke-RestMethod -Method Post -Uri http://localhost:8081/oauth2/token `
  -Headers @{ Authorization = "Basic $pair" } `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body 'grant_type=client_credentials&scope=api.read api.write'

# Signup org + space + fondateur
Invoke-RestMethod -Method Post -Uri http://localhost:8081/api/v1/orgs/signup `
  -Headers @{ Authorization = "Bearer $($tok.access_token)" } `
  -ContentType 'application/json' -Body '{
    "organization": { "code": "takibo-demo", "name": "Org TAKIBO Demo" },
    "space":        { "code": "finance", "name": "Finance", "description": "Espace finance" },
    "account":      { "email": "founder@takibo.io", "password": "Str0ng!Passw0rd" },
    "profile":      { "username": "founder", "firstName": "Tresor", "lastName": "Kadima" }
  }'
```

3. `npm run dev` puis se connecter sur `http://localhost:5173/login` avec
   `takibo-demo` / `founder@takibo.io` / `Str0ng!Passw0rd`.

## Hors périmètre

Le MFA, la fédération, la récupération du mot de passe et la console
d'administration arrivent dans les récits suivants.
