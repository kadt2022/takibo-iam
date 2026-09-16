# TAS-GRANTS-03 — Authentification humaine SAS et session navigateur

**Statut :** À faire  
**Branche :** `feat/tas-human-authentication-03`  
**Dépendances :** TAS-GRANTS-02, résolution client/tenant de TAS-GRANTS-01, TIS-Core stable — notamment IAM-31  
**Précède :** TAS-GRANTS-04 — Authorization Code + PKCE  
**Doctrine révisée :** Organization = frontière d'identité forte ; aucun Principal global ni lookup cross-Organization.

---

## Récit

En tant que **TAKIBO Authorization Server (TAS)**,

nous voulons authentifier un utilisateur humain dans une page de connexion hébergée par TAKIBO et établir une session navigateur sécurisée,

afin que les futurs flux OAuth 2.0 interactifs puissent réutiliser un principal humain authentifié sans exposer les identifiants de l'utilisateur aux applications clientes.

---

# 1. Loi du récit

```text
L'Organization est une frontière d'identité forte.
Le contexte Organization est établi avant la vérification des credentials.
TIS-Core authentifie l'Account dans cette Organization uniquement.
TAS porte la session de protocole.
L'application cliente ne voit jamais le mot de passe.
Aucune recherche globale par email n'existe entre Organizations.
```

Ce récit ne crée pas encore d'Authorization Code.

Il établit la fondation humaine et navigateur dont TAS-GRANTS-04 aura besoin.

```text
TAS-GRANTS-03

Contexte Organization fiable
    │
    ▼
Navigateur
    │
    ▼
Page de connexion TAKIBO
    │
    │ email + password
    ▼
TIS-Core
    │
    │ Account vérifié DANS l'Organization
    ▼
Principal humain situé
    │
    ▼
Session navigateur TAS située
```

Puis, dans TAS-GRANTS-04 :

```text
Application
    │
    ▼
/oauth2/authorize
    │
    ├── session humaine existante
    │
    ▼
authorization_code
    │
    ▼
/oauth2/token + PKCE
```

---

# 2. Problème actuel

TAKIBO sait déjà authentifier un humain.

Le flux actuel est conceptuellement :

```text
POST /api/v1/auth/login
        │
        ▼
TIS-Core
  - résout l'organisation
  - recherche l'Account
  - vérifie les credentials
  - applique verrouillage / compteur d'échecs
  - calcule le RBAC effectif
        │
        ▼
HumanAccessTokenIssuer
        │
        ▼
JWT humain
```

Ce mécanisme est utile et doit rester compatible.

Mais il ne constitue pas encore une authentification humaine intégrée à Spring Authorization Server :

```text
pas de session navigateur TAS
pas de page de login TAS
pas de SSO
pas de principal navigateur réutilisable
pas de parcours /oauth2/authorize
```

La vérification de l'identité et l'émission du token sont aujourd'hui trop directement enchaînées pour servir proprement Authorization Code.

TAS-GRANTS-03 doit donc découpler :

```text
AUTHENTIFIER L'HUMAIN
```

de :

```text
ÉMETTRE UNE PREUVE OAUTH
```

---

# 3. Décision architecturale

## 3.1 TAKIBO héberge la page de connexion

La page de connexion appartient à TAKIBO.

Une application telle que Yamba Academy ne collecte jamais elle-même le mot de passe TAKIBO.

Interdit :

```text
Yamba
  │
  │ email/password
  ▼
API TAKIBO
```

Cible :

```text
Yamba
  │
  │ redirection navigateur
  ▼
TAKIBO
  │
  │ page de connexion
  ▼
TIS-Core
```

Cette décision permet :

```text
credentials centralisés
SSO futur
MFA futur
WebAuthn / passkeys futurs
politique d'authentification centralisée
audit centralisé
aucun mot de passe dans les applications clientes
```

---

# 4. Page rendue côté serveur

TAKIBO reste une plateforme principalement API.

Le login humain est une exception volontaire et minimale.

La V1 utilise une page rendue côté serveur.

Une technologie de gabarit légère peut être ajoutée, par exemple Thymeleaf, sans créer une nouvelle application frontend.

Architecture attendue :

```text
TAKIBO
├── API REST
├── endpoints OAuth2
└── surface navigateur d'authentification
      ├── login.html
      └── ressources statiques d'authentification
```

La page ne contient aucune logique métier.

La logique d'authentification reste dans les services et ports applicatifs.

Il ne faut pas créer :

```text
takibo-login-react
Node
Vite
SPA dédiée
API CORS dédiée au login
```

pour ce récit.

---

# 5. Branding du client

La page est hébergée par TAKIBO mais peut porter l'identité visuelle de l'application cliente.

Exemple :

```text
client_id = yamba-academy
        ↓
theme = yamba
        ↓
logo Yamba Academy
nom Yamba Academy
couleurs Yamba
```

Le navigateur reste néanmoins sur un domaine TAKIBO.

Le branding V1 est **par client OAuth**, pas par organisation.

L'organisation pourra enrichir le branding dans un récit ultérieur.

## Sécurité du thème

Un thème est une donnée contrôlée.

Il peut exposer au maximum des éléments tels que :

```text
themeKey
displayName
logo TAKIBO-local
primaryColor
secondaryColor
```

Il ne peut jamais fournir :

```text
HTML arbitraire
JavaScript arbitraire
CSS arbitraire
script externe
iframe externe
logo chargé depuis une URL utilisateur non contrôlée
```

La résolution du thème utilise le client OAuth déjà résolu côté serveur.

Un paramètre navigateur :

```text
?theme=yamba
```

ne doit jamais suffire à choisir le thème.

Un thème inconnu retombe sur le thème TAKIBO par défaut.

La mécanique de stockage et d'administration avancée des thèmes n'est pas requise par ce récit. Une abstraction `LoginThemeResolver` ou équivalente permet de remplacer ultérieurement la source de configuration.

---

# 6. L'Organization est connue avant l'authentification

TAKIBO conserve sa doctrine multi-tenant forte :

```text
Organization = frontière d'identité et de credentials
```

Un `Account` appartient à une seule `Organization`.

Deux `Account` appartenant à deux organisations distinctes restent deux identités de sécurité distinctes, même lorsqu'ils portent exactement la même adresse email.

Exemple valide et attendu :

```text
Banque A
└── Account A
    ├── email = pi001@email.com
    └── credentials A

Banque B
└── Account B
    ├── email = pi001@email.com
    └── credentials B
```

TAKIBO ne conclut jamais :

```text
même email
    ↓
même humain global
```

Il n'existe dans ce récit :

```text
aucun Principal global à l'installation
aucune fusion automatique d'Accounts
aucun partage de password entre Organizations
aucune recherche globale d'Account par email
aucune découverte post-login de toutes les Organizations d'un email
```

## 6.1 Précondition du login navigateur

Avant que TIS-Core vérifie `email + password`, TAS doit disposer d'un **contexte Organization fiable**.

Conceptuellement :

```text
TrustedOrganizationContext
├── orgId
├── source du contexte
├── clientId si applicable
└── métadonnées de protocole nécessaires
```

La source exacte du contexte dépend du parcours qui a conduit au login :

```text
client OAuth lié à une Organization
invitation préalablement validée côté serveur
route ou domaine contrôlé par TAKIBO
sélection organisationnelle authentifiée par un mécanisme dédié
autre mécanisme explicitement récité
```

Ce récit ne force pas toutes les intégrations à utiliser le même mécanisme de découverte.

En revanche, il impose l'invariant suivant :

```text
AVANT password verification
        ↓
orgId fiable déjà établi
```

Pour un client PLATFORM ou multi-tenant qui ne permet pas à lui seul de déterminer l'Organization, TAS échoue **fail-closed** tant qu'un mécanisme fiable n'a pas établi cette frontière.

TAS-GRANTS-04 devra préciser comment le contexte OAuth apporte ou référence ce contexte Organization lorsqu'il déclenche le login 03.

## 6.2 `orgCode` n'est pas une doctrine de sécurité

Le récit ne fixe plus la règle « `orgCode` ne doit jamais apparaître dans l'interface ».

Il fixe une règle plus importante :

```text
une valeur saisie par l'utilisateur n'est jamais une frontière de sécurité
tant qu'elle n'a pas été résolue et validée côté serveur
```

Une intégration peut donc présenter un identifiant organisationnel lisible si son parcours l'exige.

Mais avant la vérification des credentials, TAKIBO doit l'avoir transformé en un `orgId` fiable et validé.

Le POST du formulaire de mot de passe ne peut jamais choisir librement :

```text
orgId
spaceId
clientId
redirect_uri
```

Le contexte sensible reste côté serveur.

## 6.3 Même email dans deux Organizations

Le scénario suivant doit rester possible et sûr :

```text
Organization A
└── Account A : pi001@email.com

Organization B
└── Account B : pi001@email.com
```

Les deux comptes sont indépendants.

Un succès d'authentification dans A ne prouve rien dans B.

Les credentials de A ne peuvent jamais être utilisés pour authentifier l'Account B.

Une session TAS établie pour A ne satisfait jamais automatiquement une demande exigeant B.

---

# 7. Refactor TIS-Core : authentifier sans forcément émettre un token

TIS-Core reste l'unique propriétaire de la vérification d'identité.

Le récit doit extraire du flux actuel un cas d'usage réutilisable conceptuellement équivalent à :

```java
VerifiedHuman authenticate(TrustedOrganizationContext context,
                           HumanCredentials credentials);
```

Le nom exact reste libre.

Ce cas d'usage porte notamment :

```text
validation du contexte Organization reçu
recherche Account exclusivement par (orgId, email)
validation Account
recherche credentials exclusivement dans cette Organization
vérification du mot de passe
compteur d'échecs org-scopé
verrouillage org-scopé
égalisation temporelle
réponse d'échec humain uniforme
```

Il est interdit à ce cas d'usage de rechercher des candidats dans plusieurs Organizations à partir du seul email.

Il ne signe rien.

Il ne crée aucune session HTTP.

Il ne connaît pas Spring Authorization Server.

Résultat conceptuel :

```text
VerifiedHuman
├── orgId
├── accountId
├── subjectType = HUMAN
├── authenticationMethod = PASSWORD
└── faits d'authentification nécessaires
```

Le `HumanLoginService` REST existant doit pouvoir réutiliser ce nouveau cœur puis continuer à appeler `HumanAccessTokenIssuer` afin de préserver le comportement existant.

On obtient donc :

```text
                 TIS-Core
                    │
             authenticateHuman()
                    │
          ┌─────────┴─────────┐
          │                   │
          ▼                   ▼
REST login actuel       Browser login TAS
          │                   │
HumanAccessTokenIssuer        │
          │                   │
          ▼                   ▼
        JWT              Principal/session
```

Il ne doit exister qu'une seule implémentation de vérification des credentials.

---

# 8. Principal humain SAS

Après authentification réussie, TAS construit un principal humain **situé dans l'Organization authentifiée**, destiné à Spring Security / Spring Authorization Server.

Ce principal est un principal de session/protocole. Il ne constitue pas une nouvelle identité globale TAKIBO et ne relie jamais des Accounts de plusieurs Organizations.

Nom conceptuel :

```text
TakiboHumanPrincipal
```

Il contient au minimum :

```text
subjectType = HUMAN
orgId
accountId
authenticationMethod
authenticatedAt
```

Il peut également porter un identifiant de session et les métadonnées techniques nécessaires à l'audit.

Il ne contient jamais :

```text
password
password hash
client secret
authorization code
access token
refresh token
```

## Pas de snapshot RBAC durable dans la session

La session navigateur ne devient pas une nouvelle source de vérité RBAC.

Les rôles, groupes et permissions restent la propriété de TIS-Core.

Le principal de session identifie l'humain authentifié.

Les autorités métier utilisées lors d'une future émission OAuth doivent être relues ou recalculées à partir de TIS-Core selon la politique définie par le récit qui émet la preuve.

Ainsi :

```text
Session = quel Account a été authentifié dans quelle Organization ?
TIS-Core = que peut cet Account faire maintenant dans cette frontière ?
```

et non :

```text
Session vieille de 8 heures = vérité RBAC permanente
```

---

# 9. Session navigateur TAS

Après un login réussi, TAS établit une session serveur.

La session doit survivre à plusieurs requêtes navigateur et permettre au futur `/oauth2/authorize` de reconnaître un humain déjà authentifié.

## Fixation de session

L'identifiant de session est renouvelé immédiatement après authentification réussie.

Une session anonyme existante ne conserve jamais le même identifiant après le login.

## Cookie

En production, le cookie de session doit être au minimum :

```text
HttpOnly
Secure
SameSite=Lax
Path=/
```

Aucun identifiant sensible ne doit être placé dans un cookie lisible par JavaScript.

Le cookie contient une référence de session, pas le principal complet et pas un JWT métier.

## Durée

La durée de session est configurable.

Deux limites distinctes doivent être prévues :

```text
idle timeout
absolute maximum lifetime
```

Les valeurs exactes sont configurables par l'installation.

Des valeurs raisonnables par défaut doivent être documentées.

## Multi-instance

La session de production ne doit pas dépendre uniquement de la mémoire d'une JVM.

TAKIBO étant déployable avec plusieurs instances, le stockage de session doit disposer d'une implémentation partagée.

Doctrine :

```text
pas de Redis obligatoire
PostgreSQL autorisé et privilégié pour l'implémentation par défaut
```

Spring Session JDBC ou une solution équivalente est acceptable.

Un stockage en mémoire peut exister uniquement pour développement/tests explicitement identifiés.

Aucun mot de passe, secret OAuth ou token brut ne doit être persisté dans la session partagée.

## Conséquence explicite de schéma et de dépendances

Le choix d'une session partagée JDBC est une décision d'architecture de ce récit, pas un détail caché d'implémentation.

Si Spring Session JDBC est retenu comme implémentation de référence, la PR doit inclure explicitement :

```text
dépendance Spring Session JDBC
migration Flyway des tables/index nécessaires
politique d'expiration et de nettoyage
configuration production
tests PostgreSQL réels
preuve de lecture d'une session depuis une autre instance logique
```

Les tables de session restent des tables techniques TAS/Boot. Elles ne deviennent jamais une source de vérité d'identité, de RBAC ou de tenant.

Leur cycle de vie et leur nettoyage doivent être documentés afin d'éviter une croissance silencieuse de la base.

---

# 10. SSO de base dans une frontière compatible

Le récit introduit le premier invariant SSO de TAKIBO sans affaiblir l'isolation des Organizations.

Si une session humaine valide existe déjà pour :

```text
orgId = ORG-A
accountId = ACCOUNT-A
```

TAKIBO peut reconnaître ce principal et éviter un nouveau mot de passe **uniquement lorsqu'une nouvelle demande est compatible avec cette même frontière Organization**.

Exemple :

```text
Client A1 ─────┐
               │
Client A2 ─────┼──► session Account A / ORG-A
               │
Client A3 ─────┘
```

Une demande visant une autre Organization ne peut jamais réutiliser cette preuve comme authentification suffisante :

```text
session ORG-A
    +
demande ORG-B
    ↓
pas de SSO cross-Organization
```

TAS doit alors exiger le parcours d'authentification approprié pour ORG-B ou échouer selon le protocole concerné.

Ce récit ne définit pas encore toutes les règles OAuth de réutilisation de la session.

TAS-GRANTS-04 décidera comment `/oauth2/authorize` exploite une authentification existante lorsque le client, le tenant résolu et la session sont compatibles.

Le SSO ne signifie jamais qu'un client hérite des permissions ou scopes d'un autre client.

La session prouve l'authentification d'un `Account` dans une `Organization`.

Chaque demande OAuth reste autorisée séparément.

---

# 11. Séparer erreurs de protocole et erreurs d'identité

Deux familles d'échecs ne doivent pas être confondues.

## 11.1 Erreurs de protocole ou de contexte

Les erreurs telles que :

```text
client OAuth inconnu ou désactivé
contexte tenant manquant
contexte client/Organization incohérent
état de protocole absent ou corrompu
demande incompatible avec la session Organization existante
```

ne sont pas des « mauvais mots de passe ».

Elles doivent échouer avant la vérification des credentials lorsque cela est possible.

Elles sont exposées selon le contrat du protocole concerné de façon suffisamment explicite pour permettre à l'intégrateur de diagnostiquer une mauvaise configuration, sans révéler de donnée tenant non autorisée.

Elles sont toujours détaillées dans les logs/audits techniques.

## 11.2 Erreurs d'authentification humaine

Une fois un contexte Organization valide établi, le comportement existant de non-énumération doit être conservé.

Les causes suivantes ne doivent pas être distinguables dans la page de login :

```text
Organization non admissible au login
Account inconnu dans l'Organization
credentials absents
mauvais mot de passe
Account verrouillé
Account non admissible
```

La réponse utilisateur reste générique, par exemple :

```text
Impossible de valider cette connexion.
```

La cause réelle est disponible uniquement dans les logs et/ou l'audit approprié.

L'égalisation temporelle existante de TIS-Core ne doit pas être contournée par le nouveau flux navigateur.

Aucune erreur humaine ne doit révéler qu'un même email existe dans une autre Organization.

---

# 12. CSRF et sécurité navigateur

Le POST de login est protégé contre CSRF.

Le token CSRF est généré par TAKIBO et validé côté serveur.

La page de login ne doit jamais accepter comme autorité des champs cachés tels que :

```text
redirect_uri
orgId
spaceId
clientId
returnUrl
```

provenant directement du navigateur.

Le contexte sensible du parcours reste côté serveur.

Les réponses de la surface d'authentification doivent utiliser des en-têtes défensifs appropriés, notamment :

```text
Cache-Control: no-store
Content-Security-Policy restrictive
frame-ancestors 'none'
X-Content-Type-Options: nosniff
Referrer-Policy restrictive
```

La production exige HTTPS.

Aucun mot de passe ne doit être inscrit dans :

```text
logs
audit metadata
traces
metrics
exceptions
URL
query string
```

---

# 13. Logout

TAKIBO fournit une sortie de session humaine.

Le logout :

```text
invalide la session serveur
supprime le contexte Spring Security
expire le cookie de session
```

Après logout, un nouvel accès nécessitant un humain authentifié ne peut pas réutiliser l'ancienne session.

Ce récit couvre le **logout de la session navigateur TAKIBO**.

La révocation de tous les access tokens et refresh tokens OAuth appartient aux récits OAuth appropriés et n'est pas implicite ici.

---

# 14. Compatibilité avec `/api/v1/auth/login`

Le récit ne casse pas le login REST existant.

```http
POST /api/v1/auth/login
```

continue de fonctionner selon son contrat actuel.

La refactorisation doit au contraire faire converger les deux flux vers le même cœur TIS-Core.

Aucun client existant ne doit subir une modification de claims ou de statut HTTP uniquement parce que la session navigateur est introduite.

La dépréciation éventuelle du login REST direct sera décidée séparément, après la disponibilité réelle d'Authorization Code + PKCE.

---

# 15. Ce que TAS-GRANTS-03 ne fait PAS

Ce récit ne doit produire aucun :

```text
authorization_code
refresh_token
device_code
user_code
consent OAuth
échange PKCE
redirect OAuth final
nouveau grant_type
```

Une authentification via le formulaire ne retourne jamais directement :

```json
{
  "access_token": "...",
  "refresh_token": "..."
}
```

Une réussite signifie :

```text
session humaine établie
```

et rien de plus.

---

# 16. Relation avec TAS-GRANTS-04

TAS-GRANTS-04 pourra ensuite utiliser cette fondation :

```text
GET /oauth2/authorize
        │
        ▼
Résolution + validation OAuth
        │
        ▼
Session humaine ?
   │            │
   non          oui
   │            │
   ▼            │
Login 03        │
   │            │
   └────────────┘
        │
        ▼
Authorization Code
        │
        ▼
redirect_uri
        │
        ▼
POST /oauth2/token
        │
        ▼
PKCE S256
        │
        ▼
tokens
```

Le récit 04 ne doit donc réimplémenter ni le formulaire ni la vérification du mot de passe.

---

# 17. Invariants

```text
I1. Une application cliente ne reçoit jamais le password de l'utilisateur.

I2. Organization reste une frontière d'identité forte.

I3. TIS-Core authentifie toujours un Account à l'intérieur d'un orgId déjà fiable.

I4. Aucune recherche globale d'Account par email n'existe entre Organizations.

I5. Deux Accounts portant le même email dans deux Organizations restent indépendants.

I6. Les credentials d'une Organization ne permettent jamais d'authentifier un Account d'une autre Organization.

I7. TAS reste propriétaire de la session navigateur utilisée par les protocoles OAuth.

I8. Une authentification navigateur réussie ne produit aucun token OAuth dans ce récit.

I9. Une authentification navigateur réussie ne produit aucun authorization_code.

I10. Le navigateur ne choisit jamais librement orgId, spaceId, clientId ou redirect_uri au moment du POST login.

I11. Une session anonyme change d'identifiant après authentification réussie.

I12. Aucun mot de passe ou secret OAuth n'est stocké dans la session.

I13. Le principal de session ne devient ni un Principal global TAKIBO ni une source de vérité RBAC.

I14. Une cause d'échec d'authentification humaine ne permet pas d'énumérer les organisations ou comptes.

I15. Une erreur de protocole/client reste distincte d'un échec de credentials.

I16. Le branding ne permet aucune injection HTML, JavaScript ou CSS arbitraire.

I17. Une installation multi-instance ne dépend pas d'une session JVM locale en production.

I18. Une session ORG-A ne satisfait jamais automatiquement une demande ORG-B.

I19. Le login REST actuel continue de fonctionner.

I20. Une session humaine située peut être reconnue ultérieurement par Spring Authorization Server.
```

---

# 18. Critères d'acceptation

- [ ] **AC-01 — Page hébergée.** TAKIBO expose une page de connexion humaine rendue côté serveur ; aucune SPA supplémentaire n'est nécessaire.

- [ ] **AC-02 — Organization préalable.** Avant toute vérification de `email + password`, TAS/TIS dispose d'un `orgId` issu d'un contexte serveur fiable et validé.

- [ ] **AC-03 — Aucun lookup global.** Le login navigateur ne contient aucun chemin qui recherche un Account dans plusieurs Organizations à partir du seul email.

- [ ] **AC-04 — Même email, comptes indépendants.** Deux Accounts de deux Organizations distinctes peuvent porter le même email ; authentifier l'un ne crée, ne lie ni n'authentifie l'autre.

- [ ] **AC-05 — Credentials org-scopés.** Un mot de passe valide pour `Account A / ORG-A` ne permet jamais d'authentifier `Account B / ORG-B`, même si les deux Accounts portent le même email.

- [ ] **AC-06 — Contexte non falsifiable.** `orgId`, `spaceId`, `clientId` ou `redirect_uri` modifiés dans le POST de login ne deviennent jamais une source de vérité.

- [ ] **AC-07 — Réutilisation TIS.** Le login navigateur et `POST /api/v1/auth/login` utilisent le même cœur de vérification des credentials TIS-Core.

- [ ] **AC-08 — Aucun token.** Un login navigateur réussi n'appelle pas directement le chemin d'émission du JWT utilisé par le login REST.

- [ ] **AC-09 — Principal humain situé.** Une authentification réussie établit dans Spring Security un principal HUMAN contenant au minimum `orgId`, `accountId`, méthode d'authentification et instant d'authentification ; ce principal n'est pas global à l'installation.

- [ ] **AC-10 — Pas de RBAC figé.** Le stockage durable de session ne contient pas un snapshot métier de rôles/groupes/permissions considéré comme source de vérité.

- [ ] **AC-11 — Session fixation.** L'identifiant de session observé avant login diffère de celui observé après un login réussi.

- [ ] **AC-12 — Cookie sécurisé.** En profil production/TLS, le cookie est `HttpOnly`, `Secure`, `SameSite=Lax` et n'expose aucun token métier.

- [ ] **AC-13 — CSRF.** Un POST de login sans preuve CSRF valide est refusé ; un POST valide peut être authentifié.

- [ ] **AC-14 — Erreur humaine uniforme.** Account inconnu dans l'Organization, mauvais mot de passe, credentials absents et Account verrouillé produisent la même information externe.

- [ ] **AC-15 — Erreur protocolaire distincte.** Un client invalide, un contexte tenant absent ou une incohérence client/Organization échoue comme erreur de protocole/contexte et n'est pas présenté comme un simple mauvais mot de passe ; le diagnostic technique est disponible dans l'audit/log.

- [ ] **AC-16 — Anti-énumération.** Le nouveau chemin navigateur conserve l'égalisation temporelle et ne crée aucun oracle permettant de découvrir qu'un email existe dans la même ou dans une autre Organization.

- [ ] **AC-17 — Branding client.** Deux contextes clients configurés avec des thèmes différents rendent l'identité visuelle attendue ; un thème inconnu utilise le thème TAKIBO par défaut.

- [ ] **AC-18 — Branding sûr.** Une valeur de thème malveillante ne permet pas d'injecter de HTML, JavaScript, CSS arbitraire ou ressource distante non autorisée.

- [ ] **AC-19 — Session partagée.** La configuration de production dispose d'un stockage partagé compatible multi-instance sans Redis obligatoire ; PostgreSQL est supporté comme implémentation de référence.

- [ ] **AC-20 — Conséquence de schéma explicite.** Si JDBC est l'implémentation de référence, la PR contient la dépendance nécessaire, les migrations Flyway des tables/index de session, leur politique d'expiration/nettoyage et des tests PostgreSQL réels.

- [ ] **AC-21 — SSO dans la même frontière.** Une requête suivante visant la même Organization et compatible avec la session retrouve le principal sans nouvelle vérification du mot de passe.

- [ ] **AC-22 — Pas de SSO cross-org.** Une session établie pour ORG-A ne satisfait pas une demande visant ORG-B.

- [ ] **AC-23 — Logout.** Après logout, l'ancienne session n'authentifie plus aucune requête.

- [ ] **AC-24 — Headers.** La page et le traitement d'authentification appliquent `no-store` et les protections navigateur définies par ce récit.

- [ ] **AC-25 — Aucun artefact OAuth.** Après un login navigateur seul, aucune nouvelle ligne d'autorisation OAuth ne doit exister à cause de ce login et aucun authorization code n'est créé.

- [ ] **AC-26 — Compatibilité.** Les tests existants de login humain REST, `client_credentials`, résolution tenant, persistance OAuth et signature restent verts.

---

---

# 19. Tests attendus

## TIS-Core

Tester séparément le nouveau cas d'usage d'authentification humaine :

```text
credentials corrects dans ORG-A
mauvais password
account inconnu dans ORG-A
organisation inactive
account verrouillé
compteur d'échecs
reset après succès
égalisation temporelle
même email dans ORG-A et ORG-B
password A refusé pour Account B
absence totale de lookup cross-Organization
```

Prouver également que le cas d'usage ne dépend ni de Spring MVC ni de Spring Authorization Server.

## TAS / Boot

Tests d'intégration navigateur :

```text
GET login avec contexte Organization valide
refus avant login si contexte tenant/client invalide
POST login nominal
POST sans CSRF
tentative de modification orgId/clientId dans le formulaire
session fixation
cookie sécurisé
principal situé sauvegardé
session relue sur requête suivante dans la même Organization
refus de réutilisation de session vers une autre Organization
logout
headers de sécurité
thème client
fallback thème TAKIBO
```

## PostgreSQL réel

Si le stockage de session de production est JDBC/PostgreSQL :

```text
migration Flyway des tables/index de session
création session
lecture depuis une seconde instance logique
expiration
invalidation
nettoyage
absence de données RBAC/credentials sensibles dans les lignes de session
```

Les comportements multi-instance ne doivent pas être prouvés uniquement avec une Map Java.

## Régression

Les suites existantes doivent continuer à prouver :

```text
client_credentials
login REST ORGANIZATION
login REST SPACE transitoire
claims humains actuels
RBAC
tenant boundaries
```

---

# 20. Observabilité

Les événements suivants doivent être observables sans exposer de secret :

```text
HUMAN_BROWSER_LOGIN_SUCCEEDED
HUMAN_BROWSER_LOGIN_FAILED
HUMAN_SESSION_CREATED
HUMAN_SESSION_REUSED
HUMAN_SESSION_LOGGED_OUT
```

Les métriques peuvent porter des compteurs agrégés.

Les logs peuvent contenir les identifiants techniques appropriés après résolution sécurisée :

```text
clientId
orgId
accountId sur succès
sessionId corrélé sous forme sûre si nécessaire
cause interne d'échec
```

Ils ne contiennent jamais :

```text
email brut si non nécessaire
password
password hash
CSRF token
cookie de session
authorization code
access token
refresh token
```

L'audit de sécurité doit distinguer la cause réelle lorsque cela est nécessaire à l'exploitation, tout en conservant une réponse publique uniforme.

---

# 21. Doctrine de session et révocation future

Une session humaine n'est pas un access token.

```text
session TAS
    ≠
JWT OAuth
```

Un récit ultérieur pourra invalider les sessions lors de :

```text
changement de mot de passe
désactivation du compte
logout global
incident de sécurité
changement d'époque de sécurité
```

Le modèle du principal introduit par 03 doit donc pouvoir accueillir une notion d'époque/version de sécurité sans casser son contrat.

Si une `security_epoch` est introduite dans ce récit, elle appartient à TIS-Core et TAS ne la fabrique pas.

TAS-GRANTS-04 devra décider explicitement si cette époque est également projetée dans les tokens humains.

Cette décision ne doit pas être repoussée jusqu'au récit de révocation.

---

# 22. Hors périmètre

```text
Authorization Code              → TAS-GRANTS-04
PKCE                            → TAS-GRANTS-04
Refresh Token                   → TAS-GRANTS-05
Device Authorization Grant      → TAS-GRANTS-06
Révocation OAuth                → TAS-GRANTS-07
Consentement OAuth avancé       → récit dédié / 04 si strictement nécessaire
MFA                             → récit dédié
WebAuthn / passkeys             → récit dédié
fédération externe              → récit dédié
Google/Microsoft login          → récit dédié
branding complet par tenant     → récit dédié
éditeur de thèmes               → hors 03
administration publique thèmes  → hors 03
```

Le récit prépare ces évolutions sans les implémenter prématurément.

---

# 23. Décisions irréversibles ou importantes

## D1 — Organization reste la frontière d'identité

Un Account et ses credentials sont org-scopés.

Le même email dans deux Organizations ne crée aucun lien implicite.

## D2 — Le password reste chez TAKIBO

Une application OAuth ne collecte pas les credentials TAKIBO.

## D3 — La page est server-rendered

Pas de frontend React supplémentaire pour la V1 de la page d'identité.

## D4 — TIS authentifie, TAS sessionne

La séparation de modules reste explicite.

TIS reçoit un contexte Organization déjà fiable puis authentifie l'Account dans cette frontière.

## D5 — Aucun Principal global

`TakiboHumanPrincipal` est un objet de sécurité/session situé, pas une identité globale reliant plusieurs Organizations.

## D6 — Le login REST actuel n'est pas supprimé

Il reste une compatibilité transitoire tant que le flux Authorization Code complet n'est pas disponible.

## D7 — `orgCode` n'est pas la frontière

Le récit ne promet plus sa disparition universelle de l'UX.

La frontière est l'`orgId` validé côté serveur avant l'authentification.

## D8 — La session est serveur

Le navigateur porte une référence opaque de session, pas une identité métier autosuffisante.

## D9 — PostgreSQL plutôt que Redis obligatoire

TAKIBO reste exploitable on-prem sans infrastructure de cache distribuée imposée.

Si JDBC est retenu, ses tables, migrations et règles de nettoyage font explicitement partie de la livraison.

## D10 — Pas de SSO cross-Organization

Une session d'Account dans ORG-A ne prouve rien dans ORG-B.

## D11 — Le branding est fermé

Personnalisation visuelle oui ; exécution de code fourni par un tenant non.

## D12 — Erreur protocolaire ≠ erreur de credentials

La configuration OAuth/tenant doit rester diagnostiquable sans affaiblir l'uniformité des échecs d'identité humaine.

---

# 24. Définition de terminé

TAS-GRANTS-03 est terminé lorsque le scénario suivant est prouvé de bout en bout :

```text
1. TAKIBO possède un contexte Organization fiable avant toute vérification
   du mot de passe.

2. Le navigateur reçoit la page de login correspondant au client/contexte.

3. L'utilisateur fournit ses credentials humains.

4. TIS-Core recherche l'Account uniquement dans l'Organization déterminée.

5. Un même email présent dans une autre Organization n'entre jamais
   dans cette décision.

6. TIS-Core vérifie les credentials avec les mêmes règles
   que le login humain existant.

7. TAS reçoit un Account humain vérifié et situé dans son orgId.

8. Spring Security établit un principal de session situé,
   non global à l'installation.

9. L'identifiant de session est renouvelé.

10. La session est sauvegardée dans le stockage partagé prévu.

11. Une seconde requête compatible avec la même Organization retrouve
    le principal sans redemander le password.

12. Une demande visant une autre Organization ne réutilise pas
    cette session comme preuve suffisante.

13. Les erreurs client/tenant/protocole sont distinguées des erreurs
    de credentials dans le diagnostic technique.

14. Les erreurs humaines restent uniformes pour l'utilisateur.

15. Aucun authorization_code n'est créé.

16. Aucun access_token ou refresh_token n'est émis.

17. Le logout invalide réellement la session.

18. Si JDBC est retenu, les migrations Flyway, l'expiration et le
    nettoyage des tables de session sont prouvés sur PostgreSQL réel.

19. Le login REST actuel et client_credentials n'ont subi
    aucune régression.
```

À ce moment seulement, TAS possède la notion nécessaire :

```text
« ce navigateur porte une session TAKIBO dans laquelle
cet Account a été authentifié dans cette Organization »
```

TAS-GRANTS-04 peut alors commencer.

---

# 25. Suite immédiate

Après fusion de TAS-GRANTS-03 :

```text
TAS-GRANTS-04 — Authorization Code + PKCE
```

devra définir notamment :

```text
/oauth2/authorize
validation client
établissement/référence du contexte Organization AVANT login 03
règles des clients PLATFORM ou multi-tenant
compatibilité entre client, orgId et session 03
redirect_uri exacte
state
nonce si OIDC
session humaine 03
authorization code court
usage unique
hash/chiffrement du code
PKCE S256 obligatoire pour PUBLIC
/token
protection contre rejeu
claims humains situés
frontières ORG / SPACE
audit
tests cross-tenant
```

Aucun de ces comportements ne doit être anticipé dans 03 au point de mélanger les responsabilités des deux récits.