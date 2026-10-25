# Rapatriement de Takibo-UI dans Takibo-IAM — audit et plan de migration

> Statut : **PROPOSITION, en attente de l'approbation de Pi.** Aucun code du frontend n'a été déplacé.
> Source (lecture seule) : `kadt2022/takibo-ui` @ `e53d5dd` (main). Destination : `kadt2022/takibo-iam` @ `main`.

## 1. Rapport d'audit (Étape 1)

### 1.1 État des dépôts

| | takibo-ui | takibo-iam |
|---|---|---|
| Commits sur `main` | 39, un seul auteur | historique complet, merges en squash (`(#80)`) |
| Fichiers suivis | 95 (≈ 9,4 Mo de working tree, pack 2,2 Mio) | modules Gradle + `docs/`, `postman/` |
| Branches distantes | 8 branches `feat/ui-0x`, `chore/ui-format-prettier`, `ci/takibo-ui-validation` (déjà fusionnées dans `main` à vérifier) | `main` |
| LFS | non | non |

### 1.2 Frontend
- React 19, TypeScript ~6, Vite 8, Tailwind 4, TanStack Query 5, React Router 7, Zod 4, Radix. Node 22 en CI, `package-lock.json` présent (`npm ci`).
- Commandes : `npm run build` (`tsc -b && vite build`), `lint`, `format:check`, `test` (Vitest/jsdom), `test:e2e` (Playwright/Chromium).
- Pas de Dockerfile, pas de configuration OpenShift/Kubernetes/Helm dans `takibo-ui` **ni** dans `takibo-iam` (recherche `git ls-files`). Le déploiement actuel n'est donc décrit dans aucun des deux dépôts.

### 1.3 Configuration, API, authentification
- `.env.example` : un seul `VITE_BFF_BASE_URL=http://localhost:8080` (non secret, embarqué dans le bundle).
- Dev : `vite.config.ts` relaie `/api` vers `TAKIBO_API_TARGET` (défaut `http://localhost:8081`) = port de `takibo-iam-boot` (`TAKIBO_APP_PORT: 8081` en CI). Le navigateur appelle donc la même origine : pas de CORS en dev.
- Auth : `POST /api/v1/auth/login` (orgCode + email + mot de passe) → token ORGANIZATION conservé **en mémoire React uniquement** (ni localStorage, ni cookie), envoyé en `Authorization: Bearer`. Endpoints consommés : `/api/v1/me/spaces`, `/api/v1/orgs/{id}/spaces`, `/api/v1/orgs/{id}/dashboard/summary`.
- CORS côté IAM : `takibo.cors.allowed-origins` = `TAKIBO_CORS_ALLOWED_ORIGINS`, **vide par défaut** ; le profil `dev` autorise `localhost:[*]`. En production, un frontend servi depuis une autre origine exigera de renseigner cette variable (aucun changement de code).

### 1.4 Secrets
- Aucun secret réel détecté (recherche de motifs clés/tokens/mots de passe sur les 95 fichiers). Les occurrences sont des fixtures de test (`secret123`, faux JWT) et le `README.md` du frontend cite des **identifiants de démonstration** (`postman-client:dev-postman-secret`, `Str0ng!Passw0rd`), qui sont des valeurs de dev. Proposition : les migrer tels quels (aucun changement de contenu) ; à vous de dire si vous préférez les retirer.

### 1.5 Risques identifiés dans `takibo-iam`
1. **`.gitignore` racine silencieusement destructeur** : il ignore `*.png`, `*.svg` et `.env.*`. Vérifié avec `git check-ignore` : `public/takibo-logo.png`, `public/favicon.svg` et `.env.example` seraient **perdus sans erreur**. Correctif : un `takibo-ui/.gitignore` imbriqué (celui du frontend) complété de négations (`!*.png`, `!*.svg`, `!.env.example`) — à valider par `git check-ignore`. Les règles `dist/`, `coverage/`, `node_modules/`, `reports/` couvrent déjà les artefacts générés.
2. **CI backend déclenchée par tout changement non-doc** : la tâche `changes` ne connaît que `docs/` et `*.md` ; une PR purement frontend lancerait toute la chaîne Gradle, PostgreSQL, BVT et Sonar. Inversement, une PR purement backend ne doit pas lancer la chaîne Node.
3. **Statut requis `Takibo CI Status`** : la CI documente déjà le piège (un filtre `paths` au déclencheur laisserait la PR bloquée). Le même schéma — détection par tâche, statut toujours rendu — doit être appliqué au frontend.
4. **Workflow du frontend** : GitHub ne lit que `.github/workflows/` à la racine ; `takibo-ui/.github/workflows/takibo-ui-ci.yml` serait inerte. Il doit être déplacé à la racine avec `working-directory: takibo-ui` et `cache-dependency-path`.
5. **Collision de nom, sans conflit** : un dossier `takibu-ui-test/` existe déjà (page `adp-tester.html`, outil ADP). Il n'est pas touché ; noté pour éviter la confusion.
6. **Gradle / Sonar** : `settings.gradle` liste les modules explicitement ; `takibo-ui/` n'y figure pas et n'est donc ni compilé ni analysé par Sonar. Aucun fichier de build backend n'a besoin de changer.
7. **Aucun conflit de fichiers** : seul le `.gitignore` et le `readme.md` racine sont concernés ; le contenu rapatrié reste sous `takibo-ui/`.
8. **Pas de fichiers générés ni volumineux** à migrer (pas de `dist`, `node_modules`, rapports Playwright suivis).

### 1.6 Historique Git : le conserver ?
Oui : 39 commits d'un auteur, 2 Mio, aucun secret, et les messages de commit portent la traçabilité des récits UI-01 à UI-06. Méthode recommandée : **`git subtree add --prefix=takibo-ui <source> main`** (sans `--squash`), ou `git filter-repo --to-subdirectory-filter takibo-ui` sur un clone jetable puis fusion `--allow-unrelated-histories`. Dans les deux cas le dépôt source n'est jamais modifié. **Point d'attention** : `takibo-iam` fusionne en squash ; avec un squash, les 39 commits seraient perdus. La PR de rapatriement devrait donc être fusionnée **en merge commit** (décision à prendre par vous au moment de la fusion). Si vous préférez un squash, je consigne le SHA source `e53d5dd` dans le message.

## 2. Plan de migration (Étape 2)

### 2.1 Objectif et périmètre
Un seul dépôt, deux applications indépendantes (builds, tests, déploiements séparés). Aucune nouvelle fonctionnalité, aucun refactoring, backend inchangé.

### 2.2 Structure cible
```
takibo-iam/
├── takibo-ui/            # package.json, package-lock.json, src/, public/, tests/, configs, README.md, .gitignore, .env.example
├── takibo-*/             # modules Gradle inchangés
├── .github/workflows/    # takibo-ci.yml (adapté) + takibo-ui-ci.yml (déplacé et adapté)
├── docs/migration/rapatriement-takibo-ui.md
└── readme.md             # section « Structure du dépôt » ajoutée
```
Inventaire : les 95 fichiers suivis de `takibo-ui@e53d5dd`, sans exclusion, y compris `package-lock.json`.

### 2.3 Étapes (un commit chacun, sur `chore/integrate-takibo-ui`)
1. Import de l'historique sous `takibo-ui/` (subtree sans squash).
2. `takibo-ui/.gitignore` : négations pour `*.png`, `*.svg`, `.env.example` ; contrôle `git ls-files takibo-ui | wc -l` = 95.
3. Déplacement de `takibo-ui-ci.yml` vers `.github/workflows/` : `working-directory: takibo-ui`, `cache-dependency-path: takibo-ui/package-lock.json`, filtre de changements `takibo-ui/**` + le workflow lui-même.
4. `takibo-ci.yml` : la tâche `changes` expose `backend` et `ui` ; la chaîne Gradle ne tourne que si `backend` ; « Takibo CI Status » intègre les deux chaînes, ignore ce qui est écarté, et rend toujours son verdict.
5. Documentation : `readme.md` racine (structure, démarrage des deux applications, port 8081/5173, `TAKIBO_CORS_ALLOWED_ORIGINS`), `takibo-ui/README.md` inchangé sauf chemins devenus faux.

### 2.4 Impacts CI/CD et déploiement
- Une PR frontend ne lance plus Gradle/Sonar ; une PR backend ne lance plus Node. Une PR mixte lance les deux.
- Les checks requis sur `main` (« Takibo CI Status ») sont conservés ; les noms de jobs UI (`Quality`, `Playwright E2E`) seront préfixés pour éviter les collisions. **À confirmer par Pi** : liste exacte des checks requis dans la protection de branche.
- OpenShift : rien dans les dépôts ; si les builds OpenShift sont configurés ailleurs, ils devront pointer vers le contexte `takibo-ui/` (ou `.` pour le backend). **Question à Pi** : où est défini le déploiement du frontend ?

### 2.5 Tests de non-régression (Étape 4)
Frontend (dans `takibo-ui/`) : `npm ci`, `format:check`, `lint`, `test`, `build`, `test:e2e`. Backend : `./gradlew assemble` et tests des modules, y compris `iam-boot` sous PostgreSQL, Database Check, BVT, Sonar (0 nouveau problème, ≥ 95 % de couverture sur le nouveau code — inchangé car aucun code Java modifié). Intégration : démarrage de `takibo-iam-boot` (profil dev), `npm run dev`, connexion via `/api/v1/auth/login`, navigation Spaces/Dashboard. Contrôle de l'absence de secrets et de fichiers générés dans le diff. Les échecs éventuels seront documentés tels quels.

### 2.6 Retour arrière
La PR n'est pas fusionnée sans accord de Pi. Avant fusion : fermer la PR et supprimer la branche. Après fusion en merge commit : `git revert -m 1 <merge>`. `kadt2022/takibo-ui` reste intact et reste la référence jusqu'à la bascule validée.

### 2.7 Décisions demandées à Pi
1. Conserver l'historique (recommandé) avec fusion de la PR en merge commit ?
2. Laisser les identifiants de démonstration du README UI tels quels ?
3. Où est configuré le déploiement OpenShift du frontend, et quels checks sont requis sur `main` ?
