# TMS-OAUTH-CLIENT-BOUNDARY-01 — Aligner le registre OAuth sur les frontières TAKIBO

**Statut :** À faire  
**Branche :** `feat/tms-oauth-client-boundary-01`  
**Module principal :** TMS — Takibo Management Service  
**Dépendances :** modèle OAuth TMS existant, TAS-GRANTS-01 terminé  
**Précède :** TAS-GRANTS-03 — Authentification humaine SAS, TAS-GRANTS-04 — Authorization Code + PKCE

---

## Récit

En tant que **TAKIBO Management Service (TMS)**,

nous voulons que le registre persistant des clients OAuth représente correctement les trois frontières de sécurité reconnues par TAKIBO,

afin qu'un client OAuth puisse être situé au niveau `PLATFORM`, `ORGANIZATION` ou `SPACE` sans introduire de contexte artificiel ni de dépendance obligatoire à un Space.

---

# 1. Loi du récit

```text
Un client OAuth possède une frontière explicite et valide.

PLATFORM      → org_id absent,  space_id absent
ORGANIZATION  → org_id présent, space_id absent
SPACE         → org_id présent, space_id présent
```

Le registre TMS ne doit jamais imposer un `space_id` à un client dont la frontière réelle est `ORGANIZATION`.

Le schéma persistant doit être cohérent avec le modèle de résolution utilisé par TAS.

---

# 2. Problème actuel

Le domaine TAS sait déjà représenter les frontières suivantes :

```text
PLATFORM
ORGANIZATION
SPACE
```

et `ResolvedOAuthClient` porte conceptuellement :

```text
client_id
plan
org_id
space_id
policy OAuth
```

Mais le registre TMS actuel impose dans `oauth2_clients` :

```text
org_id   NOT NULL
space_id NOT NULL
```

et rattache le client à un Space via une clé étrangère composite.

Conséquence :

```text
client SPACE         → représentable
client ORGANIZATION  → non représentable proprement
client PLATFORM      → non représentable dans le registre persistant actuel
```

Cette asymétrie crée une incohérence entre :

```text
le modèle de frontière OAuth de TAKIBO
```

et :

```text
le modèle de persistance des clients OAuth dans TMS
```

---

# 3. Exemple du défaut

Une organisation vient d'être créée :

```text
Organization
org_id = ORG-A
```

Elle ne possède encore aucun Space.

Nous voulons créer un client OAuth :

```text
client_id = banque-a-web
plan      = ORGANIZATION
org_id    = ORG-A
space_id  = NULL
```

Le modèle actuel refuse cette représentation parce que `space_id` est obligatoire.

Cela crée artificiellement la dépendance :

```text
Organization
    ↓
création obligatoire d'un Space
    ↓
création du client OAuth
```

alors qu'un client de frontière `ORGANIZATION` ne devrait dépendre d'aucun Space.

---

# 4. Décision de conception

Le registre TMS doit pouvoir représenter les trois formes suivantes :

```text
oauth2_clients

plan logique     org_id       space_id
---------------------------------------------
PLATFORM         NULL         NULL
ORGANIZATION     UUID         NULL
SPACE            UUID         UUID
```

Le terme `plan logique` décrit ici la frontière déduite du contexte.

Ce récit **n'impose pas nécessairement l'ajout d'une colonne `plan`** dans `oauth2_clients`.

La représentation recommandée est :

```text
PLATFORM
    org_id IS NULL
    AND space_id IS NULL

ORGANIZATION
    org_id IS NOT NULL
    AND space_id IS NULL

SPACE
    org_id IS NOT NULL
    AND space_id IS NOT NULL
```

Le `ClientPlan` peut être reconstruit à partir de cette forme.

---

# 5. Invariant de forme SQL

Le schéma doit interdire la combinaison impossible :

```text
org_id IS NULL
AND
space_id IS NOT NULL
```

Une contrainte SQL doit donc exprimer au minimum :

```sql
CHECK (
    org_id IS NOT NULL
    OR space_id IS NULL
)
```

ou une forme équivalente plus explicite.

La base doit elle-même empêcher toute ligne ne correspondant à aucune frontière TAKIBO valide.

---

# 6. Nullabilité

Pour permettre les trois frontières :

```text
org_id   devient nullable
space_id devient nullable
```

Mais cette nullabilité ne signifie jamais absence de doctrine.

La combinaison de valeurs détermine strictement la frontière :

```text
(NULL, NULL) → PLATFORM
(UUID, NULL) → ORGANIZATION
(UUID, UUID) → SPACE
(NULL, UUID) → interdit
```

---

# 7. Relations avec Organization et Space

## 7.1 Client ORGANIZATION

Un client `ORGANIZATION` :

```text
org_id   = UUID
space_id = NULL
```

doit rester lié à une organisation existante.

La suppression ou invalidation d'une organisation doit respecter la politique de cycle de vie définie pour ses clients OAuth.

Aucun Space n'est nécessaire.

## 7.2 Client SPACE

Un client `SPACE` :

```text
org_id   = UUID
space_id = UUID
```

continue d'exiger que :

```text
space.org_id == client.org_id
```

La clé étrangère composite ou une contrainte équivalente doit continuer à empêcher le rattachement d'un client à un Space d'une autre organisation.

## 7.3 Client PLATFORM

Un client `PLATFORM` :

```text
org_id   = NULL
space_id = NULL
```

est sans tenant.

Ce récit rend le modèle persistant capable de représenter cette forme.

Il ne migre cependant pas automatiquement le client PLATFORM de développement actuellement fourni par une source in-memory.

Cette migration reste hors périmètre sauf décision explicite contraire.

### Décision à prendre, pas à différer en silence

Le résolveur in-memory n'est pas un choix de conception : c'est **le contournement qui prouve
la dette que ce récit corrige**. Le client PLATFORM est déclaré en mémoire précisément parce
que le registre ne savait pas représenter une ligne sans organisation ni space.

Si ce récit se termine en laissant ce contournement tel quel, la dette survit à sa propre
correction, et plus rien ne signalera qu'elle existe.

La PR doit donc trancher explicitement, dans un sens ou dans l'autre :

```text
soit  le client PLATFORM devient persistable et le résolveur in-memory est retiré ;

soit  il reste in-memory, et le récit écrit POURQUOI — par exemple parce qu'un client
      d'amorçage ne doit pas dépendre d'une base qui n'a pas encore migré.
```

La seconde branche est défendable. C'est l'absence de décision qui ne l'est pas.

---

# 8. `client_id` reste globalement unique

L'unicité globale de `client_id` est conservée.

Invariant :

```text
un client_id public identifie exactement un client OAuth
dans toute l'installation TAKIBO
```

La résolution :

```text
client_id
    ↓
ResolvedOAuthClient
    ↓
plan + orgId + spaceId + politique OAuth
```

doit rester non ambiguë.

La migration existante portant l'index global unique sur `oauth2_clients(client_id)` ne doit pas être affaiblie.

---

# 9. Résolution du ClientPlan

TMS/TAS doivent produire le plan selon la forme suivante :

```text
if orgId == null && spaceId == null
    → PLATFORM

if orgId != null && spaceId == null
    → ORGANIZATION

if orgId != null && spaceId != null
    → SPACE

if orgId == null && spaceId != null
    → erreur de cohérence
```

Aucun fallback silencieux n'est autorisé.

Une ligne invalide doit échouer en fail-closed.

---

# 10. Adaptation de `OAuth2ClientEntity`

`OAuth2ClientEntity` doit accepter :

```text
orgId nullable
spaceId nullable
```

Les mappings JPA doivent continuer à représenter correctement :

```text
Organization
Space
```

sans supposer qu'un Space existe toujours.

Les associations JPA doivent être compatibles avec :

```text
ORGANIZATION → space = null
PLATFORM     → organization = null, space = null
```

Aucune lecture ne doit provoquer d'accès ou d'exception uniquement parce que `space_id` est nul.

---

# 11. Adaptation des ports, repositories et mappers

Les couches suivantes doivent être vérifiées et adaptées si nécessaire :

```text
OAuth2ClientEntity
OAuthClientRepository
OAuthClientRepositoryAdapter
repositories Spring Data
mappers TMS
DTO / commands de création
JpaResolvedOAuthClientResolver
ResolvedOAuthClient
RegisteredClient mapping TAS
```

Le contrat doit conserver la séparation :

```text
TMS
    possède et administre les clients OAuth

TAS
    consomme leur configuration en lecture
```

TAS ne devient pas propriétaire du registre.

---

# 12. Création d'un client OAuth

Le cas d'usage de création doit accepter explicitement une frontière.

Forme conceptuelle :

```text
CreateOAuthClientCommand
├── clientId
├── boundary / plan
├── orgId nullable selon plan
├── spaceId nullable selon plan
├── clientType
├── grantTypes
├── redirectUris
├── requirePkce
└── autres politiques OAuth
```

Les règles sont vérifiées avant persistance.

## PLATFORM

```text
orgId   doit être absent
spaceId doit être absent
```

## ORGANIZATION

```text
orgId   obligatoire
spaceId interdit
```

## SPACE

```text
orgId   obligatoire
spaceId obligatoire
```

---

# 13. Compatibilité avec les clients existants

Les clients actuels `SPACE` doivent continuer à fonctionner sans changement fonctionnel.

Une migration ne doit pas transformer silencieusement leur frontière.

Avant :

```text
org_id = A
space_id = S1
```

Après :

```text
org_id = A
space_id = S1
plan logique = SPACE
```

Leurs grant types, redirect URIs, secrets, scopes, TTL et politiques restent inchangés.

---

# 14. Aucun changement de politique OAuth dans ce récit

Ce récit porte la **frontière du client**, pas le comportement des grants.

Il ne décide pas :

```text
quels clients utilisent authorization_code
quels clients utilisent client_credentials
comment fonctionne PKCE
comment fonctionne /oauth2/authorize
comment sont créés les authorization codes
comment sont émis les refresh tokens
```

Ces sujets restent dans les récits TAS correspondants.

Un client `ORGANIZATION` peut exister indépendamment de l'activation future de `authorization_code`.

## 14.1 Cycle de vie et volume — hors périmètre, mais nommé

Ce récit ne traite pas non plus le **cycle de vie** d'un client : ce qu'il advient quand son
organisation ou son space disparaît, comment un client est révoqué, et qui nettoie les lignes
devenues orphelines.

Ce n'est pas un oubli. C'est une conséquence prévisible de la frontière que ce récit ouvre :

```text
un client ORGANIZATION par organisation
        ↓
autant de clients que d'organisations
```

Pour une installation à quelques dizaines d'organisations institutionnelles, la question ne se
pose pas. Pour une installation grand public où chaque foyer est une organisation, elle
deviendra réelle — non par le volume, que PostgreSQL absorbe sans difficulté avec un
`client_id` unique et indexé, mais par le **provisionnement et le nettoyage**.

Ce sujet appartient à un récit dédié, à ouvrir avant qu'une intégration grand public ne crée
des clients en masse. Il est écrit ici pour qu'il ne soit pas découvert en production.

---

# 15. Exemple générique — application organisationnelle

```text
Organization
  id = ORG-A

OAuth Client
  client_id    = org-a-web
  boundary     = ORGANIZATION
  org_id       = ORG-A
  space_id     = NULL
  client_type  = PUBLIC
```

La résolution :

```text
org-a-web
    ↓
ResolvedOAuthClient
    ↓
plan    = ORGANIZATION
orgId   = ORG-A
spaceId = null
```

permet à TAS de disposer immédiatement d'une frontière organisationnelle fiable.

---

# 16. Exemple générique — application de Space

```text
Organization
  id = ORG-A

Space
  id = SPACE-1

OAuth Client
  client_id    = org-a-finance
  boundary     = SPACE
  org_id       = ORG-A
  space_id     = SPACE-1
```

La résolution donne :

```text
plan    = SPACE
orgId   = ORG-A
spaceId = SPACE-1
```

La frontière composite reste protégée.

---

# 17. Exemple générique — plateforme

```text
OAuth Client
  client_id = platform-client
  boundary  = PLATFORM
  org_id    = NULL
  space_id  = NULL
```

La résolution donne :

```text
plan    = PLATFORM
orgId   = null
spaceId = null
```

La présence de cette représentation dans le schéma ne signifie pas que le client PLATFORM de développement existant doit être migré dans ce récit.

---

# 18. Invariants

```text
I1. Chaque client OAuth correspond à exactement une frontière TAKIBO valide.

I2. PLATFORM implique org_id NULL et space_id NULL.

I3. ORGANIZATION implique org_id présent et space_id NULL.

I4. SPACE implique org_id présent et space_id présent.

I5. space_id ne peut jamais être présent sans org_id.

I6. Un client SPACE ne peut jamais référencer un Space d'une autre Organization.

I7. Un client ORGANIZATION ne dépend de l'existence d'aucun Space.

I8. client_id reste globalement unique.

I9. La résolution par client_id reste suffisante pour retrouver la frontière du client.

I10. Aucun fallback ne convertit une combinaison invalide en PLATFORM, ORGANIZATION ou SPACE.

I11. Les clients SPACE existants conservent leur comportement.

I12. TMS reste propriétaire de la création et de l'administration des clients OAuth.

I13. TAS consomme le client résolu et ne recrée pas une seconde source de vérité.

I14. Le client PLATFORM in-memory de développement n'est pas migré implicitement par ce récit.
```

---

# 19. Critères d'acceptation

- [ ] **AC-01 — Nullabilité maîtrisée.** `oauth2_clients.org_id` et `oauth2_clients.space_id` permettent les formes nécessaires aux trois frontières.

- [ ] **AC-02 — Combinaison impossible refusée.** Une ligne avec `org_id = NULL` et `space_id != NULL` est rejetée par la base.

- [ ] **AC-03 — Client PLATFORM.** Une représentation `(NULL, NULL)` peut être interprétée comme `ClientPlan.PLATFORM`.

- [ ] **AC-04 — Client ORGANIZATION.** Une ligne `(org_id, NULL)` peut être persistée et résolue comme `ClientPlan.ORGANIZATION`.

- [ ] **AC-05 — Client sans Space.** Un client `ORGANIZATION` peut être créé pour une organisation ne possédant encore aucun Space.

- [ ] **AC-06 — Client SPACE.** Une ligne `(org_id, space_id)` continue d'être résolue comme `ClientPlan.SPACE`.

- [ ] **AC-07 — Isolation composite.** La base refuse un client `SPACE` dont `space_id` appartient à une autre organisation.

- [ ] **AC-08 — Unicité globale.** Deux clients ne peuvent pas partager le même `client_id`, même dans deux organisations différentes.

- [ ] **AC-09 — Entity JPA.** `OAuth2ClientEntity` supporte correctement les trois formes sans supposer un `spaceId` obligatoire.

- [ ] **AC-10 — Resolver.** `JpaResolvedOAuthClientResolver` produit le `ClientPlan` correspondant aux valeurs `orgId/spaceId`.

- [ ] **AC-11 — Fail-closed.** Une combinaison incohérente ne produit jamais de client résolu.

- [ ] **AC-12 — Pas de régression SPACE.** Les tests existants de création, lecture et résolution des clients SPACE restent verts.

- [ ] **AC-13 — Pas de régression `client_credentials`.** Le flux machine existant reste inchangé.

- [ ] **AC-14 — Sort du PLATFORM in-memory tranché par écrit.** La PR dit explicitement si le client PLATFORM de développement devient persistable — et alors le résolveur in-memory est retiré — ou s'il reste en mémoire, avec la raison écrite. Aucune des deux branches n'est imposée ; c'est l'absence de décision qui est refusée, parce que ce contournement est la trace même de la dette corrigée ici.

- [ ] **AC-15 — PostgreSQL réel.** Les contraintes de frontière et les migrations sont prouvées avec Testcontainers/PostgreSQL.

- [ ] **AC-16 — Documentation.** La doctrine `PLATFORM / ORGANIZATION / SPACE` du registre TMS est documentée dans le backlog et dans les commentaires de modèle pertinents.

- [ ] **AC-17 — Mode de la clé étrangère documenté.** La migration porte en commentaire le fait que la FK composite vers `spaces` repose sur le comportement `MATCH SIMPLE` — non vérifiée dès qu'une colonne est nulle — et que la passer en `MATCH FULL` rendrait tout client `ORGANIZATION` impossible à écrire. AC-04 et AC-05 servent de test de non-régression à cette contrainte.

---

# 20. Migration SQL attendue

La migration doit être écrite de manière explicite et fail-closed.

Elle doit notamment :

```text
1. rendre org_id nullable si la représentation PLATFORM est retenue dans le registre ;
2. rendre space_id nullable ;
3. adapter ou remplacer la FK composite vers spaces ;
4. préserver la FK vers organizations lorsque org_id est présent ;
5. ajouter une contrainte CHECK sur les combinaisons valides ;
6. conserver l'index global unique de client_id ;
7. conserver les données existantes sans transformation de frontière ;
8. être testée sur PostgreSQL réel.
```

La migration ne doit pas dépendre de MySQL ou d'un mode H2.

PostgreSQL reste la base cible de ce lot.

---

# 21. Point d'attention — clé étrangère vers Space

La FK actuelle :

```text
(org_id, space_id) → spaces(org_id, id)
```

ne peut plus être appliquée naïvement comme si `space_id` était toujours présent.

La solution retenue doit conserver l'invariant :

```text
si space_id est présent,
alors ce Space appartient à org_id
```

sans imposer un Space aux clients `ORGANIZATION`.

Le choix exact peut utiliser :

```text
FK composite nullable
+
CHECK de forme
```

## 21.1 Pourquoi la FK composite nullable suffit — et ce qu'il ne faut jamais « durcir »

Ce point doit être écrit noir sur blanc dans la migration, parce que la correction peut se
défaire toute seule si quelqu'un l'ignore.

Une clé étrangère composite PostgreSQL est vérifiée en `MATCH SIMPLE` par défaut. Dans ce
mode, **la contrainte est ignorée dès qu'une de ses colonnes est nulle** :

```text
(org_id = A, space_id = S1)  → FK vérifiée, S1 doit appartenir à A
(org_id = A, space_id = NULL) → FK non vérifiée : c'est exactement ce qu'on veut
(org_id = NULL, space_id = NULL) → FK non vérifiée
```

C'est donc le comportement par défaut qui rend le client `ORGANIZATION` possible, sans clause
supplémentaire. L'organisation, elle, reste vérifiée par sa propre FK simple vers
`organizations`, qui n'est pas concernée par ce mécanisme.

**Piège à interdire explicitement :** passer cette contrainte en `MATCH FULL` paraîtrait un
durcissement légitime lors d'une revue future. Ce serait une régression totale — `MATCH FULL`
exige que toutes les colonnes soient nulles ou toutes non nulles, donc **tout client
`ORGANIZATION` deviendrait impossible à écrire**.

La migration doit porter ce commentaire, et un test doit prouver qu'un client `ORGANIZATION`
s'écrit et se relit. Sans ce test, la régression passerait inaperçue.

ou une solution SQL équivalente démontrée sur PostgreSQL.

---

# 22. Tests attendus

## Domaine / application

```text
PLATFORM accepté avec aucun tenant
ORGANIZATION accepté avec org seul
SPACE accepté avec org + space
space sans org refusé
org inconnu refusé
space inconnu refusé
space d'une autre org refusé
```

## Repository / JPA

```text
save client ORGANIZATION
read client ORGANIZATION
save client SPACE
read client SPACE
client PLATFORM si persistance activée
duplicate client_id global refusé
```

## Resolver TAS

```text
client PLATFORM      → plan PLATFORM
client ORGANIZATION  → plan ORGANIZATION
client SPACE         → plan SPACE
forme invalide       → fail-closed
```

## PostgreSQL réel

```text
CHECK des frontières
FK organization
FK composite space
unicité globale client_id
migration depuis un jeu de clients SPACE existants
```

## Régression

```text
client_credentials
RegisteredClient mapping
PKCE policy mapping existant
TTL mapping
redirect URIs
scopes
secrets
tenant resolution
```

---

# 23. Observabilité

Les erreurs de configuration doivent permettre de distinguer en interne :

```text
INVALID_CLIENT_BOUNDARY
ORGANIZATION_REQUIRED
SPACE_REQUIRES_ORGANIZATION
SPACE_NOT_IN_ORGANIZATION
CLIENT_ID_ALREADY_EXISTS
```

Les réponses publiques suivent les contrats REST TMS existants.

Aucune information d'une autre organisation ne doit être révélée pour expliquer une erreur de frontière.

---

# 24. Hors périmètre

```text
Authorization Code                    → TAS-GRANTS-04
page de login humaine                 → TAS-GRANTS-03
session navigateur                    → TAS-GRANTS-03
PKCE runtime                          → TAS-GRANTS-04
Refresh Token                         → TAS-GRANTS-05
Device Authorization Grant            → TAS-GRANTS-06
révocation                            → TAS-GRANTS-07
création automatique de clients
  pour un produit particulier         → hors TMS-OAUTH-CLIENT-BOUNDARY-01
logique métier Yamba                   → hors TAKIBO
découverte du tenant par email         → hors périmètre
Principal global                       → hors doctrine TAKIBO
fusion d'Accounts inter-Organization   → interdite
migration du client PLATFORM dev
  in-memory vers TMS                   → récit dédié
```

---

# 25. Décisions importantes

## D1 — `Organization` reste une frontière forte

Ce récit n'affaiblit pas l'isolation tenant.

## D2 — Un client ORGANIZATION n'a pas besoin de Space

`space_id = NULL` est une représentation valide et intentionnelle.

## D3 — `client_id` reste global

La résolution par `client_id` reste non ambiguë à l'échelle de l'installation.

## D4 — Pas de colonne `plan` obligatoire

Le plan peut être déduit de `org_id/space_id`.

Une colonne supplémentaire n'est introduite que si une justification indépendante apparaît.

## D5 — Le schéma porte l'invariant

La validité de la frontière ne repose pas uniquement sur Java.

PostgreSQL doit refuser les combinaisons impossibles.

## D6 — TMS reste propriétaire du registre

TAS ne persiste pas un second catalogue de clients.

---

# 26. Définition de terminé

Le récit est terminé lorsque les scénarios suivants sont prouvés :

```text
A. Client ORGANIZATION

1. Une Organization existe.
2. Aucun Space n'existe dans cette Organization.
3. TMS crée un client OAuth avec org_id et space_id NULL.
4. Le client est persisté.
5. La résolution par client_id retourne :
      plan = ORGANIZATION
      orgId = l'organisation
      spaceId = null.

B. Client SPACE

1. Une Organization et un Space existent.
2. TMS crée un client avec org_id + space_id.
3. La résolution retourne SPACE.
4. Un space_id d'une autre Organization est refusé.

C. Client PLATFORM

1. La forme sans org et sans space est reconnue comme PLATFORM
   si elle est utilisée dans le registre.
2. Aucun space sans org n'est accepté.

D. Unicité

1. Deux organisations tentent de créer le même client_id.
2. La seconde création est refusée.

E. Régression

1. Les clients SPACE existants restent résolvables.
2. client_credentials reste vert.
3. Les mappings OAuth existants restent verts.
```

À ce stade, le registre TMS est cohérent avec les frontières OAuth reconnues par TAKIBO et peut être utilisé par TAS-GRANTS-03 et TAS-GRANTS-04 sans inventer de Space artificiel.
