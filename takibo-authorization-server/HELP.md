# Read Me First
The following was discovered as part of building this project:

* The original package name 'com.takibu.authorization-server' is invalid and this project uses 'com.takibu.authorizationserver' instead.

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Gradle documentation](https://docs.gradle.org)
* [Spring Boot Gradle Plugin Reference Guide](https://docs.spring.io/spring-boot/3.5.5/gradle-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/3.5.5/gradle-plugin/packaging-oci-image.html)

### Additional Links
These additional references should also help you:

* [Gradle Build Scans – insights for your project's build](https://scans.gradle.com#gradle)
****

La table **`context_hmac_keys`** sert à stocker (et surtout **faire tourner**) les **secrets HMAC** utilisés par **STEA** pour “attacher” un **jeton** à une **preuve de contexte** sans mettre le contexte brut dans le JWT.

### 1) Le problème que STEA résout

Tu veux éviter deux extrêmes :

* **Mettre le contexte complet dans le JWT** (IP, device, geo, etc.)
  → ça fuite des infos, ça grossit le token, et ça augmente la surface d’attaque.
* **Stocker le contexte en DB** et faire des requêtes à chaque call
  → tu voulais précisément éviter ça.

STEA fait un compromis :
Tu ne stockes pas le contexte, tu stockes **une empreinte/signature HMAC** du contexte (ou d’un “snapshot” canonique), et tu peux vérifier cette empreinte plus tard.

### 2) À quoi sert une “clé HMAC de contextualisation”

Une **clé HMAC** (secret symétrique) permet de calculer une valeur du type :

`ctx_hmac = HMAC_SHA256(key, canonical_context_string)`

* `canonical_context_string` = représentation **stable** (triée, normalisée) d’un sous-ensemble du contexte choisi : ex. deviceId, ipPrefix, userAgentHash, tenant, client_id, etc.
* Résultat : une **preuve** que “ce token a été émis avec ce contexte-là”, sans révéler le contexte.

Dans le JWT (ou dans le trust token), tu mets par exemple :

* `ctx_kv` : version de clé utilisée (ex. 7)
* `ctx_h` : la signature HMAC (base64url)
* éventuellement `ctx_alg` : `HS256_CTX` ou juste `HMAC-SHA256`

### 3) Pourquoi une table dédiée `context_hmac_keys`

Parce que ces secrets doivent être **rotés** et gérés comme des clés cryptographiques :

* quelle clé est **active** pour signer maintenant
* quelles clés sont **retired** (on ne signe plus avec, mais on vérifie encore les tokens existants)
* quelles clés sont **revoked** (compromises → on refuse de vérifier avec)

Donc la table contient typiquement :

* `key_version` (INT) : l’identifiant stable de la clé
* `key_value` : le secret (idéalement **chiffré** ou stocké via KMS/HSM et ici tu stockes une référence)
* `status` : `ACTIVE | RETIRED | REVOKED`
* `created_at`

### 4) Cycle de vie (rotation) — très concret

1. TAS lit la clé `ACTIVE` (version N)
2. À l’émission d’un token : calcule `ctx_h = HMAC(keyN, contextCanonique)` et met `ctx_kv=N`
3. À la validation : TAS (ou le composant qui valide) lit `ctx_kv`, récupère la clé correspondante et recalcule le HMAC
4. Rotation : tu crées une nouvelle clé `ACTIVE` (N+1) et tu passes l’ancienne en `RETIRED` pendant une fenêtre (jusqu’à expiration des tokens)
5. Incident : si compromission → status `REVOKED`, et tu peux “bump epoch” en parallèle pour forcer logout global.

### 5) Point d’architecture important (symétrique)

Comme c’est **HMAC**, ceux qui vérifient doivent connaître le secret.

* Si **seul TAS valide** (gateway / introspection / filtre central), parfait : secret reste dans TAS.
* Si **les resource servers valident eux-mêmes** sans introspection, alors tu dois **distribuer le secret** (moins idéal) ou basculer vers une preuve **asymétrique** (signature avec clé privée) ou un claim signé par TAS suffisant (sans HMAC partagé).

### 6) Reco Takibo (sécurité)

* `key_value` ne doit jamais être en clair en DB : chiffre-le (enveloppe KMS) ou stocke une référence KMS.
* Ajoute éventuellement `org_id` si tu veux des clés **par org** (isolation forte multi-tenant). Sinon clé globale = plus simple.

En une phrase : **`context_hmac_keys` est le “keyring” de STEA** : il permet de signer et vérifier une empreinte de contexte, avec rotation et révocation propres, sans mettre de contexte sensible dans les tokens.
