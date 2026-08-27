# Récit ORG-SPACE-01 — Onboarding d’une organisation sans Space et création volontaire du premier Space

## Statut

À faire

## Domaine

Plan de contrôle `ORGANIZATION`

## Dépendances amont

- `ORG-REG-01 — Créer une demande publique d’inscription d’organisation`
- `ORG-REG-02 — Vérifier le courriel et provisionner l’organisation fondatrice`

## Dépendance descendante

- `ORG-SPACE-02 — Sélectionner un Space et obtenir un contexte SPACE`

`ORG-SPACE-02` devra permettre au fondateur d’entrer dans le Space créé sans ressaisir son courriel et son mot de passe.

## Intention

En tant que fondateur connecté à une organisation active ne contenant encore aucun Space,  
je veux être guidé pour créer volontairement mon premier Space,  
afin de commencer à utiliser les capacités situées de TAKIBO sans qu’un Space ait été créé automatiquement pendant le signup.

---

## Doctrine TAKIBO

> Une organisation active avec zéro Space est un état valide.

> Le Space est un contexte de travail, pas une condition d’existence de l’organisation ni de l’identité.

> Le premier Space est créé volontairement après le login par une autorité située au niveau `ORGANIZATION`.

> La création du premier Space inscrit atomiquement l’`Account` fondateur dans ce Space et crée son `User` situé.

> Aucun Space, nommé ou non, explicite ou implicite, n’est créé automatiquement pendant le signup, la vérification, le login ou le chargement du dashboard.

La console peut proposer le nom `Espace principal`, mais la restriction porte uniquement sur la création implicite. Le fondateur peut volontairement confirmer ce nom.

---

## Précondition

Le parcours commence dans l’état suivant :

```text
Organization : ACTIVE
Account      : ACTIVE
Rôle org     : R_ORG_OWNER
Token        : HUMAN / ORGANIZATION
Spaces       : 0
Users        : 0
```

Cet état est nominal.

Il ne doit provoquer :

- ni erreur de navigation ;
- ni `404` sur la liste des Spaces ;
- ni création automatique ;
- ni faux contexte `SPACE` ;
- ni redirection vers un Space inexistant ;
- ni perte de l’autorité organisationnelle du fondateur.

---

# 1. Terminologie des tokens

Les récits TAKIBO utilisent les termes suivants :

```text
registrationAccessToken
→ capacité temporaire limitée au parcours public d’inscription

accessToken avec scope ORGANIZATION
→ token humain normal du fondateur connecté à son organisation

accessToken avec scope SPACE
→ token humain obtenu après sélection explicite d’un Space
```

Dans les exemples HTTP de ce récit, la forme utilisée est :

```http
Authorization: Bearer {accessToken}
```

Les claims attendus pour créer le premier Space sont :

```text
subject_type       = HUMAN
takibo_scope_level = ORGANIZATION
org_id             = organisation ciblée
```

`organizationAccessToken` n’est pas un nouveau type de token TAKIBO.

---

# 2. Parcours fonctionnel

```text
Premier login du fondateur
        ↓
Token HUMAN / ORGANIZATION
        ↓
GET /api/v1/me/spaces
        ↓
200 OK + []
        ↓
Affichage de l’onboarding
        ↓
Création volontaire du premier Space
        ↓
POST /api/v1/orgs/{orgId}/spaces
        ↓
Création atomique :
Space + User situé du fondateur
        ↓
201 Created
        ↓
GET /api/v1/me/spaces
        ↓
Le nouveau Space devient visible et sélectionnable
        ↓
Sélection explicite du Space
        ↓
Changement de contexte ORGANIZATION → SPACE
sans nouvelle saisie des identifiants
```

La création du Space appartient au présent récit.

L’émission ou l’obtention du token `SPACE` appartient à `ORG-SPACE-02`.

---

# 3. État vide des Spaces

## Endpoint

```http
GET /api/v1/me/spaces
Authorization: Bearer {accessToken}
```

## Réponse nominale avant la création du premier Space

```http
200 OK
Content-Type: application/json
```

```json
[]
```

Une liste vide signifie :

```text
l’Account fondateur est authentifié
l’organisation existe
l’Account possède une autorité organisationnelle
aucun Space ni User situé n’a encore été créé
```

Elle ne signifie pas :

```text
organisation inexistante
session invalide
erreur de provisioning
absence d’autorisation
ressource introuvable
```

La route ne doit donc pas retourner `404 Not Found` simplement parce que la collection est vide.

---

# 4. Onboarding de la console

Lorsque `/api/v1/me/spaces` retourne une liste vide, la console TAKIBO affiche un état d’accueil adapté.

Exemple :

```text
Bienvenue dans votre organisation.

Aucun Space n’a encore été créé.
Créez votre premier Space pour commencer à gérer
les utilisateurs, rôles, groupes et clients situés.

[ Créer le premier Space ]
```

La console peut proposer un nom prérempli :

```text
Espace principal
```

Ce nom reste modifiable.

Aucune requête de création ne doit être exécutée avant une action explicite du fondateur.

Après la création, la console ne demande pas au fondateur de se reconnecter. Elle présente le nouveau Space comme sélectionnable et délègue le changement de contexte à `ORG-SPACE-02`.

---

# 5. Création volontaire du premier Space

## Endpoint

```http
POST /api/v1/orgs/{orgId}/spaces
Authorization: Bearer {accessToken}
Idempotency-Key: 91a610a0-9e78-4bec-96aa-f205e1e364a5
Content-Type: application/json
```

## Requête

```json
{
  "name": "Espace principal"
}
```

Le client ne fournit pas :

```text
spaceCode
accountId
userId
```

`accountId` est extrait du token `HUMAN / ORGANIZATION`.

## Effet atomique

La commande crée exactement une fois :

```text
Space
User situé représentant l’Account fondateur dans ce Space
```

Le `User` est lié au couple :

```text
(accountId, spaceId)
```

## Réponse nominale

```http
201 Created
Location: /api/v1/orgs/{orgId}/spaces/{spaceId}
Cache-Control: no-store
```

```json
{
  "organizationId": "8ca7a98e-8854-4ac4-8143-b30463dd9525",
  "spaceId": "0c801fd0-5592-43f3-b22e-a638230b08a5",
  "accountId": "3dcd571f-e02a-4ac6-aaeb-872c93e23771",
  "userId": "82a61202-99e4-465d-a665-0fe64ebd08c0",
  "name": "Espace principal",
  "spaceCode": "espace-principal-550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE"
}
```

`accountId` identifie l’Account global à l’organisation.

`userId` identifie la représentation située de cet Account dans le Space nouvellement créé.

La base doit garantir conceptuellement :

```text
UNIQUE(account_id, space_id)
```

---

# 6. Génération du `spaceCode`

TAKIBO génère le `spaceCode` selon la forme :

```text
{nom-normalisé}-{uuid}
```

Exemple :

```text
Espace principal
↓
espace-principal-550e8400-e29b-41d4-a716-446655440000
```

## Règles de normalisation

Le générateur doit :

- convertir le nom en minuscules ;
- translittérer ou retirer les accents ;
- remplacer les séparateurs par `-` ;
- retirer les caractères non autorisés ;
- réduire les tirets consécutifs ;
- retirer les tirets en début et en fin ;
- utiliser `space` comme base de repli si le résultat devient vide ;
- ajouter un UUID sans accolades.

## Invariants

- le fondateur choisit le nom métier ;
- TAKIBO génère le `spaceCode` ;
- le nom n’est pas l’identité technique ;
- deux Spaces peuvent porter le même nom dans une organisation ;
- leurs `spaceCode` restent différents ;
- la base impose une contrainte d’unicité sur `space_code` dans la frontière appropriée ;
- une collision technique exceptionnelle est résolue par une nouvelle génération côté serveur.

Le contrat doit refuser un `spaceCode` fourni par le client :

```http
400 Bad Request
```

```json
{
  "error": "unknown_property",
  "property": "spaceCode"
}
```

---

# 7. Idempotence de la création

`Idempotency-Key` protège contre :

- le double-clic ;
- la répétition automatique d’une requête réseau ;
- le rejeu après un délai côté client ;
- deux soumissions identiques pendant un chargement lent.

## Premier appel réussi

```http
201 Created
```

TAKIBO crée un seul Space.

## Rejeu avec la même clé et le même corps

```http
200 OK
Content-Location: /api/v1/orgs/{orgId}/spaces/{spaceId}
Cache-Control: no-store
```

TAKIBO retourne le même Space et le même User situé déjà créés.

La réponse rejouée contient les mêmes :

```text
organizationId
spaceId
accountId
userId
```

Aucun deuxième Space et aucun deuxième User ne sont créés.

## Même clé avec un corps différent

```http
409 Conflict
```

```json
{
  "error": "idempotency_key_reused",
  "message": "The idempotency key was already used with a different request."
}
```

## Clé absente ou mal formée

```http
400 Bad Request
```

```json
{
  "error": "invalid_idempotency_key"
}
```

Le rate limiting ne remplace pas l’idempotence. Il protège contre les abus ; l’idempotence protège contre les doubles soumissions accidentelles.

## Portée de la clé d’idempotence

La clé n’est jamais évaluée seule. TAKIBO la combine systématiquement avec :

```text
org_id (issu du path)
account_id (issu du token)
route (POST /api/v1/orgs/{orgId}/spaces)
```

Deux requêtes portant la même `Idempotency-Key` mais des `org_id` ou des `account_id` différents sont deux clés effectives distinctes.

Elles ne doivent jamais :

- entrer en conflit `409` l’une avec l’autre ;
- exposer le `spaceId` d’une organisation à l’acteur d’une autre organisation ;
- produire un rejeu `200 OK` croisé entre deux frontières différentes.

Une collision de valeur brute entre deux organisations est un événement normal et sans conséquence, pas une violation de frontière.

## Durée de vie de la clé

Une `Idempotency-Key` reste active pendant une fenêtre de **24 heures** après sa première utilisation.

Passé ce délai, la même valeur de clé peut être réutilisée par le même acteur pour une nouvelle requête, traitée comme un nouvel appel.

## Clé et échec de validation

Une requête refusée pour une raison imputable à la requête elle-même (nom invalide, permission absente, mauvaise frontière) ne verrouille pas la clé sur cet échec.

Le fondateur peut corriger sa requête et la rejouer avec la **même** `Idempotency-Key` sans recevoir `409 idempotency_key_reused`.

Seule une création terminée avec succès (`201 Created`) verrouille la clé sur cette réponse.

---

# 8. Autorisation

La création du premier Space est autorisée au niveau `ORGANIZATION`.

La décision de sécurité doit vérifier au minimum :

```text
subject_type = HUMAN
scope_level  = ORGANIZATION
org_id       = organisation ciblée
account_id   = Account fondateur
permission   = permission de créer un Space dans l’organisation
```

La route ne doit pas exiger :

```text
space_id
```

puisque le premier Space n’existe pas encore.

Elle ne doit pas utiliser un fallback vers `SYSTEM_ACTOR_ID`.

## Rôle et permission

Le récit ne couple pas directement la route à un simple test de nom de rôle.

La règle métier est :

```text
R_ORG_OWNER
→ possède obligatoirement la permission située de créer un Space
```

Si `R_ORG_ADMIN` existe dans le catalogue TAKIBO, son accès à cette permission dépend de la doctrine RBAC officielle et reste hors périmètre de ce récit.

La politique doit évaluer la permission située, pas uniquement rechercher une chaîne de rôle.

---

# 9. Frontière organisationnelle

Le token ne doit jamais permettre de créer un Space dans une autre organisation.

Cas interdit :

```text
token.org_id != path.orgId
```

Réponse attendue :

```http
403 Forbidden
```

```json
{
  "error": "organization_boundary_violation"
}
```

La possession d’un rôle administratif ne peut jamais élargir la frontière portée par le token.

> Un administrateur tenant exerce son pouvoir dans sa frontière ; il ne déplace pas cette frontière.

---

# 10. Erreurs de création

## Nom absent, vide ou invalide

```http
400 Bad Request
```

```json
{
  "error": "invalid_space_name"
}
```

## `spaceCode`, `accountId` ou `userId` fourni par le client

```http
400 Bad Request
```

```json
{
  "error": "unknown_property",
  "property": "userId"
}
```

Ces identifiants sont déterminés exclusivement par TAKIBO.

## Permission absente

```http
403 Forbidden
```

```json
{
  "error": "space_creation_forbidden"
}
```

## Mauvaise frontière organisationnelle

```http
403 Forbidden
```

```json
{
  "error": "organization_boundary_violation"
}
```

## Clé d’idempotence réutilisée avec un autre corps

```http
409 Conflict
```

```json
{
  "error": "idempotency_key_reused"
}
```

## Limitation de fréquence

```http
429 Too Many Requests
Retry-After: 60
```

```json
{
  "error": "space_creation_rate_limited"
}
```

Les noms dupliqués ne produisent pas de conflit : le `spaceCode` généré demeure unique.

---

# 11. Découverte après création

Après la création réussie, le fondateur appelle de nouveau :

```http
GET /api/v1/me/spaces
Authorization: Bearer {accessToken}
```

La réponse contient le nouveau contexte :

```http
200 OK
```

```json
[
  {
    "organizationId": "8ca7a98e-8854-4ac4-8143-b30463dd9525",
    "spaceId": "0c801fd0-5592-43f3-b22e-a638230b08a5",
    "accountId": "3dcd571f-e02a-4ac6-aaeb-872c93e23771",
    "userId": "82a61202-99e4-465d-a665-0fe64ebd08c0",
    "spaceCode": "espace-principal-550e8400-e29b-41d4-a716-446655440000",
    "name": "Espace principal",
    "spaceStatus": "ACTIVE",
    "userStatus": "ACTIVE",
    "selectable": true
  }
]
```

Le nouveau Space devient sélectionnable sans nouveau signup ni nouvelle saisie du mot de passe.

Le champ `spaceCode` porte le même nom en écriture (`POST /api/v1/orgs/{orgId}/spaces`) et en lecture (`GET /api/v1/me/spaces`). Aucun endpoint du contrat ne doit exposer cette donnée sous un nom différent (par exemple `code`).

Le token `ORGANIZATION` initial ne doit pas être modifié artificiellement pour lui ajouter un `space_id`.

L’obtention d’un token `SPACE` se fait par une opération explicite de changement de contexte décrite dans `ORG-SPACE-02`.

---

# 12. Aucun effet implicite

Les opérations suivantes ne créent jamais un Space :

```text
vérification du courriel
provisioning de l’organisation
premier login
GET /api/v1/me/spaces
chargement du dashboard
ouverture du sélecteur de contexte
rafraîchissement de la page
expiration ou renouvellement de session
```

La seule création autorisée passe par une commande métier explicite de création de Space.

---

# 13. Compatibilité transverse

TAKIBO doit supporter une organisation active avec zéro Space dans toutes les zones chargées avant la création du premier Space.

À vérifier au minimum :

```text
navigation principale
dashboard organisation
compteurs
menus
notifications
audit
quotas
facturation éventuelle
sélecteur de contexte
GET /api/v1/me/spaces
politiques d’autorisation
journalisation
pages d’erreur
```

Aucun composant ne doit supposer :

```text
organization.spaces.size() >= 1
```

Aucune lecture ne doit accéder directement au premier élément d’une collection vide sans garde.

---

# 14. Audit

Événements attendus :

```text
ORG_WITHOUT_SPACE_ONBOARDING_VIEWED
FIRST_SPACE_CREATION_REQUESTED
SPACE_CREATED
FIRST_SPACE_CREATED
FIRST_SPACE_CREATION_REPLAYED
FIRST_SPACE_CREATION_FAILED
```

Les noms définitifs doivent rester cohérents avec les conventions d’audit TAKIBO.

Les événements portent :

```text
organizationId
actorAccountId
spaceId lorsque disponible
createdUserId lorsque disponible
idempotencyKeyHash
traceId
```

Ils ne contiennent aucun token complet, secret ou clé d’idempotence en clair.

`FIRST_SPACE_CREATED` est produit uniquement lorsque l’organisation ne possédait aucun Space avant la commande.

La création réussie doit corréler le `spaceId` et le `createdUserId`.

La création d’un deuxième Space produit l’événement général `SPACE_CREATED` et crée également le `User` situé de l’Account demandeur si celui-ci n’existe pas encore dans ce Space.

---

# 15. Critères d’acceptation

## AC1 — Organisation sans Space valide

Étant donné une organisation `ACTIVE` et un fondateur `ACTIVE` avec zéro Space,  
quand le fondateur se connecte,  
alors TAKIBO ne considère pas l’organisation comme incomplète ou invalide.

## AC2 — Liste vide

Étant donné une organisation sans Space,  
quand le fondateur appelle `GET /api/v1/me/spaces`,  
alors TAKIBO retourne `200 OK` avec `[]`.

## AC3 — Onboarding affiché

Étant donné que `/api/v1/me/spaces` retourne `[]`,  
quand la console charge le contexte,  
alors elle affiche une action explicite de création du premier Space.

## AC4 — Aucune création automatique

Étant donné une organisation sans Space,  
quand le fondateur se connecte, recharge la page ou consulte le dashboard,  
alors aucun Space n’est créé.

## AC5 — Création avec autorité organisationnelle

Étant donné un token `HUMAN / ORGANIZATION` du fondateur,  
quand il crée un Space dans sa propre organisation,  
alors la requête ne nécessite aucun `space_id`.

## AC6 — Permission du Owner

Étant donné le rôle `R_ORG_OWNER`,  
alors le fondateur possède la permission située de créer un Space dans son organisation.

## AC7 — Frontière réelle

Étant donné un token situé dans l’organisation A,  
quand l’acteur tente de créer un Space dans l’organisation B,  
alors TAKIBO retourne `403 Forbidden`.

## AC8 — Création nominale

Étant donné une requête valide et une nouvelle `Idempotency-Key`,  
quand le fondateur appelle `POST /api/v1/orgs/{orgId}/spaces`,  
alors TAKIBO crée atomiquement le Space et le User situé du fondateur,  
et retourne `201 Created` avec un en-tête `Location`.

La réponse contient obligatoirement :

```text
organizationId
spaceId
accountId
userId
```

## AC9 — Code généré par TAKIBO

Étant donné le nom `Espace principal`,  
quand le Space est créé,  
alors le `spaceCode` commence par `espace-principal-`  
et se termine par un UUID valide.

## AC10 — Rejeu idempotent

Étant donné la même `Idempotency-Key` et le même corps,  
quand la requête est rejouée,  
alors TAKIBO retourne `200 OK` avec les mêmes `spaceId` et `userId`,  
sans créer un deuxième Space ni un deuxième User.

## AC11 — Clé réutilisée avec un autre corps

Étant donné une clé déjà consommée,  
quand elle est réutilisée avec un autre nom,  
alors TAKIBO retourne `409 Conflict`.

## AC12 — Découverte du nouveau Space et du User

Étant donné une création réussie,  
quand le fondateur rappelle `/api/v1/me/spaces`,  
alors le nouveau Space est présent avec les mêmes `accountId` et `userId`,  
et `selectable: true`.

## AC13 — Aucun nouveau login

Après la création,  
alors la console ne demande pas au fondateur de ressaisir son courriel et son mot de passe.

## AC14 — Changement de contexte explicite

Le Space créé n’est pas injecté artificiellement dans le token `ORGANIZATION`.

Son utilisation comme contexte `SPACE` passe par le parcours défini dans `ORG-SPACE-02`.

## AC15 — État vide distinct de l’erreur

Une collection vide ne provoque ni `404`, ni erreur générique, ni redirection vers un Space inexistant.

## AC16 — Compatibilité transverse

Les pages et services chargés avant la création du premier Space fonctionnent avec une collection vide.

## AC17 — Échec sans création partielle

Étant donné un échec durant la création,  
alors aucun Space actif sans User fondateur et aucun User orphelin ne sont exposés.

L’organisation demeure valide avec zéro Space et zéro User situé.

## AC17A — Unicité du User dans le Space

Étant donné le même `accountId` et le même `spaceId`,  
alors TAKIBO ne peut créer qu’un seul `userId`.

La contrainte `UNIQUE(account_id, space_id)` est garantie en persistance.

## AC18 — Isolation de la clé d’idempotence entre organisations

Étant donné deux fondateurs de deux organisations différentes utilisant par coïncidence la même valeur d’`Idempotency-Key`,  
quand chacun crée un Space,  
alors les deux créations réussissent indépendamment,  
et aucune des deux réponses n’expose le `spaceId` de l’autre organisation.

## AC19 — Retry après échec de validation

Étant donné une requête refusée pour nom invalide,  
quand le fondateur soumet une requête corrigée avec la même `Idempotency-Key`,  
alors TAKIBO traite la nouvelle requête normalement plutôt que de retourner `409 idempotency_key_reused`.

## AC20 — Expiration de la clé

Étant donné une `Idempotency-Key` utilisée avec succès il y a plus de 24 heures,  
quand la même valeur est réutilisée par le même acteur,  
alors TAKIBO traite la requête comme un nouvel appel indépendant.

---

# 16. Tests obligatoires

## Tests de read-side

- `/api/v1/me/spaces` retourne `200` avec `[]` ;
- aucune exception sur collection vide ;
- absence de faux Space par défaut ;
- nouvelle entrée visible après création ;
- présence de `accountId` et `userId` ;
- mêmes identifiants que dans la réponse de création ;
- `selectable: true` pour le nouveau Space actif.

## Tests de génération

- normalisation du nom ;
- accents et caractères spéciaux ;
- base vide après normalisation ;
- génération `{slug}-{uuid}` ;
- deux noms identiques produisent deux codes différents ;
- refus d’un `spaceCode` fourni par le client.

## Tests d’idempotence

- premier appel en `201` ;
- rejeu identique en `200` ;
- mêmes `spaceId` et `userId` retournés ;
- aucun doublon de Space en base ;
- aucun doublon de User en base ;
- même clé avec autre corps en `409` ;
- double soumission concurrente ;
- clé absente ou mal formée ;
- même valeur de clé entre deux organisations : aucun conflit croisé, aucune fuite de `spaceId` ;
- clé rejouable après un échec de validation (nom invalide) ;
- clé réutilisable par le même acteur après expiration de la fenêtre de 24 heures.

## Tests de création

- création atomique du premier Space et du User fondateur ;
- réponse contenant `organizationId`, `spaceId`, `accountId` et `userId` ;
- `accountId` extrait du token et absent du corps de requête ;
- `userId` généré par TAKIBO et absent du corps de requête ;
- contrainte `UNIQUE(account_id, space_id)` ;
- rollback du Space si la création du User échoue ;
- rollback du User si la création du Space échoue ;
- création d’un deuxième Space avec une nouvelle clé ;
- validation du nom ;
- en-tête `Location` ;
- rollback en cas d’échec ;
- limitation de fréquence.

## Tests de sécurité

- `R_ORG_OWNER` peut créer dans sa propre organisation ;
- aucun `space_id` requis ;
- refus d’une autre organisation ;
- refus d’un token `SPACE` d’une autre frontière ;
- refus d’un utilisateur sans permission ;
- aucun fallback `SYSTEM_ACTOR_ID`.

## Tests UI

- affichage de l’état vide ;
- bouton visible pour un acteur autorisé ;
- absence de création au chargement ;
- formulaire prérempli mais modifiable ;
- une seule requête logique en cas de double-clic ;
- affichage du nouveau Space après succès ;
- aucune demande de nouveau login ;
- gestion lisible d’un échec.

## Tests transverses

- dashboard avec zéro Space ;
- menus avec zéro Space ;
- sélecteur de contexte avec zéro Space ;
- audit avec zéro Space ;
- compteurs et quotas avec zéro Space ;
- absence de `IndexOutOfBoundsException`, `NoSuchElementException` ou équivalent.

## BVT

```text
200 - list zero spaces after founder login
400 - reject client supplied spaceCode
400 - reject missing idempotency key
403 - reject first space creation in another organization
201 - create first space and founder user as organization owner
200 - replay first space and founder user creation idempotently
409 - reject idempotency key reused with another body
429 - rate limit repeated space creation
200 - list newly created space with accountId and userId
201 - create second space with a new idempotency key
201 - create spaces in two organizations with the same idempotency key value
```

---

# 17. Hors périmètre

Ce récit ne couvre pas :

- le signup public ;
- la vérification du courriel ;
- la création de l’Organization ou de l’Account fondateur ;
- l’attribution initiale de `R_ORG_OWNER` ;
- la création automatique d’un Space ;
- la personnalisation avancée du Space ;
- la gestion des rôles et groupes du Space ;
- la création des clients OAuth2 du Space ;
- l’émission du token `SPACE` ;
- le changement de contexte `ORGANIZATION → SPACE` ;
- la facturation par Space ;
- la suppression ou suspension du Space.

Le changement de contexte appartient à `ORG-SPACE-02`.

---

# 18. Definition of Done

Le récit est terminé lorsque :

- une organisation active avec zéro Space est supportée dans tout le périmètre post-login ;
- `/api/v1/me/spaces` retourne `200 OK` avec `[]` ;
- la console affiche un onboarding explicite ;
- aucun Space n’est créé sans action utilisateur ;
- `R_ORG_OWNER` peut créer le premier Space dans sa propre organisation ;
- la commande crée atomiquement le Space et le User situé du fondateur ;
- la réponse contient `organizationId`, `spaceId`, `accountId` et `userId` ;
- le rejeu retourne les mêmes `spaceId` et `userId` ;
- la contrainte `UNIQUE(account_id, space_id)` est garantie ;
- aucun Space actif sans User fondateur ni User orphelin n’est exposé ;
- la création n’exige aucun `space_id` ;
- `spaceCode` est généré exclusivement par TAKIBO ;
- la création est protégée par `Idempotency-Key` ;
- un rejeu identique ne crée aucun doublon ;
- la clé d’idempotence est évaluée dans la frontière `(org_id, account_id, route)`, sans conflit ni fuite possible entre deux organisations ;
- la clé expire après 24 heures et redevient réutilisable par le même acteur ;
- un échec de validation ne verrouille pas la clé ;
- le champ `spaceCode` porte le même nom en écriture et en lecture ;
- la frontière `org_id` est vérifiée en fail-closed ;
- le nouveau Space apparaît dans `/api/v1/me/spaces` ;
- la console ne demande pas une nouvelle authentification après la création ;
- `ORG-SPACE-02` est référencé pour le passage au contexte `SPACE` ;
- les tests unitaires, d’intégration, de sécurité, UI et BVT passent ;
- les logs et événements d’audit ne contiennent aucun secret ;
- OpenAPI décrit les réponses nominales, les erreurs et l’idempotence.
