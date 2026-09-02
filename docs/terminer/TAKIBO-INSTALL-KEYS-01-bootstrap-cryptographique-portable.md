# TAKIBO-INSTALL-KEYS-01 — Bootstrap cryptographique portable

**Statut :** TERMINÉ  
**Branche :** `feat/takibo-install-keys-01`  
**Dépendances :** TAS-GRANTS-02A et TAS-GRANTS-02 (contrats de configuration à consommer), TAS-KEYS-BOOTSTRAP-01 (clé de signature, désormais hors de ce récit)

## Récit

En tant qu'organisation qui installe TAKIBO sur l'infrastructure de son choix, je veux que
l'installation génère elle-même les secrets cryptographiques persistants dont le cœur a besoin,
afin de ne jamais avoir à fabriquer un secret cryptographique à la main.

## Contrat de sortie — trois valeurs, pas deux

| Variable | Forme | Origine du besoin |
| --- | --- | --- |
| `TAKIBO_TAS_CIPHER_KEY` | 32 octets, base64 | TAS-GRANTS-02A — chiffrement au repos |
| `TAKIBO_TAS_CIPHER_KEY_ID` | `[A-Za-z0-9_-]{1,64}` | TAS-GRANTS-02A — rotation de la clé |
| `TAKIBO_TAS_USER_CODE_HMAC_KEY` | 32 octets, base64, **distincte de la clé AES** | TAS-GRANTS-02 — HMAC du `user_code` |

La troisième valeur vient de TAS-GRANTS-02 : un `user_code` (RFC 8628) est de faible entropie, et
un SHA-256 non clé le rendrait énumérable hors ligne à partir de la seule colonne de hash, sans
même casser le chiffrement. Elle doit être **générée indépendamment** de `TAKIBO_TAS_CIPHER_KEY` :
réutiliser la même matière pour chiffrer et pour authentifier annulerait la séparation de rôles
que ce HMAC introduit. Elle ne porte pas d'identifiant, contrairement à la clé AES — un
`user_code` expire en quelques minutes, bien avant qu'une rotation ait à distinguer quelle
génération l'a haché.

## Décision actée — le cœur ne connaît aucune plateforme de déploiement

TAS-GRANTS-02A et TAS-GRANTS-02 posent un contrat générique et fail-closed : *« j'ai besoin de
secrets persistants »*, exprimé par les trois variables d'environnement ci-dessus. Ce contrat est
déjà portable — Docker, Windows, Linux, Kubernetes ou OpenShift peuvent tous le satisfaire — et
ces récits s'arrêtent là : le cœur consomme les secrets, refuse de démarrer s'ils manquent, et
n'a d'opinion sur aucun mécanisme de packaging.

Une première formulation de ce récit couplait le bootstrap à OpenShift/Helm. C'était une
erreur : TAKIBO ne doit importer aucune API OpenShift, Kubernetes, Helm ou Docker dans son
cœur. La séparation retenue :

| Élément | Responsabilité |
| --- | --- |
| Cœur TAKIBO | Consommer les secrets et refuser de démarrer s'ils manquent (TAS-GRANTS-02A, TAS-GRANTS-02) |
| Outil d'installation TAKIBO | Générer des secrets sécurisés une seule fois (ce récit) |
| Adaptateur Docker | Les conserver comme Docker Secrets ou fichiers protégés |
| Adaptateur Kubernetes/OpenShift | Les conserver comme Secrets |
| Adaptateur VM/on-premise | Les conserver dans un coffre ou des fichiers système protégés |

OpenShift devient une **distribution supportée** parmi d'autres, jamais une dépendance du
cœur. Ce récit produit l'outil d'initialisation générique et son contrat de sortie ; les
adaptateurs de packaging par plateforme sont hors périmètre et pourront être des récits
séparés, un par cible.

## Décision actée — CLI portable, et rien d'autre

Le périmètre laissait le choix entre une CLI et une tâche de démarrage. C'est la **CLI** qui est
retenue : une tâche de démarrage ferait dépendre la naissance des secrets d'un démarrage
applicatif, donc d'une base, d'un contexte Spring et d'un ordre d'exécution — trois choses dont
l'installation d'une machine vierge n'a pas à disposer. La CLI n'a besoin que d'une JVM.

**Invocation.** `--out` est obligatoire : aucun chemin implicite, qui créerait un fichier de
secrets au mauvais endroit sans que personne le remarque.

```bash
java -jar takibo-install-keys.jar init --out takibo-secrets.env
```

**Format de sortie.** Trois lignes `CLE=valeur`, LF, saut de ligne final, sans commentaire ni
guillemets. C'est le seul format que `source`, `docker --env-file`,
`oc create secret --from-env-file` et un coffre acceptent tous sans retouche ; un en-tête de
commentaire le rendrait plus lisible et moins importable.

**Pas de `--stdout` dans cette version.** Un secret sur la sortie standard finit dans les
journaux d'un job CI, dans l'historique d'un terminal ou dans une capture d'écran. L'intégration
directe à un coffre fera l'objet d'un adaptateur explicite, jamais d'un tuyau ouvert par défaut.

**Identifiant de clé : `k-<UUID>`.** Opaque, et volontairement sans date : l'ordre temporel des
clés doit venir des métadonnées de la base, jamais de la lecture d'un nom. Une date dans
l'identifiant inviterait à raisonner sur l'ancienneté à partir d'une chaîne que rien ne
garantit.

## Décision actée — la seule garde est le fichier, et cette limite est assumée

La CLI ne parle ni à la base ni à TAS. Elle ne peut donc constater qu'une seule chose :
**son fichier de sortie existe déjà**. C'est sa seule garde, et elle refuse alors d'écrire.
Il n'y a **aucun `--force`** : l'option n'existe pas, pour qu'elle ne puisse pas être utilisée
sous pression.

Ce que cette garde ne couvre pas doit être dit sans détour : si le fichier a été perdu alors
que la base contient déjà des données chiffrées, la CLI n'a aucun moyen de le savoir et
produirait des clés neuves qui rendraient tout indéchiffrable. **La conduite à tenir est de
restaurer la sauvegarde, jamais de régénérer contre une base existante.** La documentation
d'installation le formule ainsi, en toutes lettres.

## Décision actée — la clé RSA ne concerne plus ce récit

Au moment où ce récit a été écrit, la clé de signature était une question ouverte renvoyée à
TAS-GRANTS-02A. Elle est close depuis TAS-KEYS-BOOTSTRAP-01 : **TAS amorce lui-même sa première
clé de signature** au premier démarrage, la conserve chiffrée en base avec la clé AES, et refuse
de démarrer par-dessus un historique incohérent.

La séparation est donc devenue nette. Ce récit produit les **secrets externes** — ceux que le
client range dans son coffre, sauvegarde et fait tourner. La clé RSA est un **état
cryptographique interne persistant**, que le client ne manipule jamais. La CLI produit trois
valeurs, jamais quatre.

Conséquence d'exploitation à documenter : la sauvegarde de PostgreSQL et celle de la clé AES
sont indissociables — restaurer l'une sans l'autre ne rend pas la clé de signature.

## Décision actée — aucun secret dans un fichier qu'un autre peut lire

L'invariant n'est pas « poser les permissions à la création » : c'est **qu'aucun octet secret
n'existe jamais dans un fichier accessible à un autre principal**. Formulé ainsi, il se tient
sur les deux familles de systèmes, alors que la première formulation n'était vraie que sur
l'une.

- **POSIX** : les attributs `rw-------` sont fournis à la **création**. Le fichier n'existe
  jamais autrement, et aucun `chmod` ultérieur n'intervient.
- **Systèmes à ACL** : l'API standard n'offre aucun attribut de création équivalent. Le fichier
  est donc créé **vide**, son ACL est restreinte au propriétaire, puis **relue pour
  vérification**, et ce n'est qu'ensuite que le contenu est écrit. Ce qui existe brièvement
  sous les permissions héritées est un fichier vide — jamais un secret. Si la relecture montre
  une entrée pour un autre principal, ou une entrée de refus, le fichier vide est effacé et
  rien n'est publié.
- **Ni l'un ni l'autre** : la CLI refuse d'écrire plutôt que de produire un fichier de secrets
  aux permissions inconnues.

La relecture n'est pas une précaution de style : `setAcl` peut être partiellement honoré selon
le volume, sans lever la moindre exception, et une entrée héritée laissée en place —
`Administrators`, `SYSTEM`, `Users` — rendrait le secret lisible par d'autres en silence.

La création elle-même est exclusive (`CREATE_NEW`) : c'est le système de fichiers qui arbitre
l'existence, pas une vérification préalable qui laisserait une fenêtre entre le test et
l'écriture.

## Décision actée — la durabilité est déclarée, jamais supposée

Publier le fichier ne suffit pas : après une coupure de courant, un fichier parfaitement écrit
peut n'être rattaché à aucun nom de répertoire. La CLI force donc les entrées du répertoire —
après la création du `.pending`, puis après la publication, avant d'annoncer le succès.

Tous les systèmes ne le permettent pas : l'API standard n'ouvre pas un répertoire en canal sous
Windows. Deux conduites étaient possibles, et le choix n'est pas neutre pour un outil qui
manipule la clé rendant une base lisible ou définitivement illisible :

| Conduite | Conséquence |
| --- | --- |
| Fail-closed | la CLI deviendrait inutilisable sur Windows, plateforme d'installation légitime |
| Dégradation silencieuse | l'outil promettrait une garantie qu'il ne tient pas |

Ni l'une ni l'autre. La garantie est **déclarée** : l'écriture rend le niveau atteint, et la CLI
l'annonce sur la sortie d'erreur quand il est moindre. Le succès reste un succès — le fichier
est écrit, publié et protégé — mais l'opérateur sait sur quel point précis la garantie
appartient au système de fichiers plutôt qu'à TAKIBO. Dans les deux cas, la conduite reste la
même : sauvegarder le fichier avant de mettre les clés en service.

## Périmètre

- Une CLI d'initialisation TAKIBO qui génère les trois valeurs du contrat de sortie : deux
  matières de 32 octets cryptographiquement sûres et indépendantes l'une de l'autre, et un
  identifiant de clé conforme au contrat de `SecretCipherKey`.
- Sortie dans un fichier neutre — trois lignes `CLE=valeur` — que n'importe quel adaptateur de
  packaging redirige vers son propre mécanisme de secret : `.env`, Secret OpenShift, coffre.
- Création exclusive du fichier, permissions restreintes dès la création, et refus d'écrire si
  cette restriction ne peut pas être garantie.
- Refus explicite si le fichier de sortie existe déjà : régénérer la clé de chiffrement sans
  plan de rotation rendrait indéchiffrable tout ce qui a été chiffré avec l'ancienne, et
  régénérer la clé HMAC rendrait irretrouvable tout `user_code` déjà haché. Aucune option ne
  permet de passer outre.
- Documentation des deux modes d'exploitation, sans en préférer un dans le code :
  - les trois variables persistantes fournies par l'installation ;
  - `TAKIBO_TAS_EPHEMERAL_KEYS=true`, réservé au développement et aux tests.

## Critères d'acceptation

Tous validés. La preuve est nommée pour ceux qui ne se lisent pas dans le code seul — un
comportement de concurrence, une garantie propre à une plateforme, une décision d'exploitation.

- [x] Le cœur TAKIBO (`takibo-authorization-server`, `takibo-iam-boot`) n'importe aucune
      dépendance Docker, Kubernetes, Helm ou OpenShift. — la CLI est un module séparé, sans
      dépendance de production, pas même vers le cœur.
- [x] L'outil produit une clé de 32 octets et un identifiant satisfaisant les invariants de
      `SecretCipherKey` sans aucune modification de ce type. — `InstallKeysContractTest`
      construit le vrai `SecretCipherKey` et fait un aller-retour de chiffrement.
- [x] L'outil produit une troisième valeur, `TAKIBO_TAS_USER_CODE_HMAC_KEY`, de 32 octets,
      satisfaisant les invariants de `HmacSha256UserCodeHmac` sans modification de ce type. —
      même test, avec un HMAC réellement calculé.
- [x] `TAKIBO_TAS_USER_CODE_HMAC_KEY` et `TAKIBO_TAS_CIPHER_KEY` ne sont jamais identiques, et
      sont tirées indépendamment plutôt que dérivées l'une de l'autre. — deux tirages distincts,
      refus à la construction si la matière est partagée.
- [x] Relancer l'outil alors que le fichier de sortie existe est refusé explicitement, jamais
      silencieusement écrasé, et aucune option n'existe pour forcer ce refus. — code 3 ;
      `--force` tombe dans « option inconnue » et n'est jamais ignoré en silence.
- [x] `--out` est obligatoire : aucun chemin de sortie implicite.
- [x] Le fichier produit contient exactement trois lignes `CLE=valeur`, en LF, avec saut de
      ligne final, sans commentaire ni guillemets, et s'importe tel quel dans `.env` comme dans
      un Secret OpenShift. — vérifié aussi hors tests, sur le fichier produit par le jar :
      trois lignes, aucun octet `\r`, dernier octet `\n`.
- [x] **Aucun secret n'existe jamais dans un fichier accessible à un autre principal.** Sur
      POSIX, les permissions restrictives sont un attribut de **création** : le fichier n'existe
      jamais autrement. Sur un système à ACL, l'API standard n'offre aucun attribut de création
      équivalent — le fichier est donc créé **vide**, son ACL restreinte au propriétaire, relue
      pour vérification, et **aucun octet secret n'est écrit avant** que cette vérification
      n'ait abouti. Si elle échoue, le fichier vide est effacé et rien n'est publié. Vérifié
      hors tests avec `icacls` : une seule entrée, le propriétaire, aucune ACE héritée.
- [x] Aucun secret n'apparaît sur la sortie standard, sur la sortie d'erreur, ni dans aucun
      journal — y compris en cas d'échec. — un test compare chaque valeur écrite aux deux flux ;
      `InstallKeys.toString()` tait sa matière, qu'un record publierait par défaut.
- [x] L'outil s'exécute sans démarrer TAS, sans base de données et sans contexte Spring. — le
      module n'a aucune dépendance de production ; le lien avec le cœur est une dépendance de
      test.
- [x] L'identifiant de clé produit est de la forme `k-<UUID>`, opaque et sans composante
      temporelle.
- [x] La documentation dit explicitement que la perte du fichier se répare par restauration de
      la sauvegarde, et jamais par une régénération contre une base existante.
- [x] Le contenu est écrit intégralement, quel que soit le nombre d'écritures que le canal
      consent : un fichier tronqué n'est jamais publié ni annoncé comme un succès. — prouvé par
      un canal simulant des écritures de 7 octets, qu'un fichier local ne produit
      pratiquement jamais.
- [x] L'état laissé par une exécution précédente est examiné avant les capacités du volume :
      un `.pending` ou une cible existante ne doivent jamais être masqués par un diagnostic de
      système de fichiers.
- [x] Le niveau de durabilité atteint est déclaré à l'opérateur, jamais silencieusement
      dégradé. — vérifié sous Windows, où la note apparaît sur la sortie d'erreur et le code
      reste 0.
- [x] Un échec du tirage ne laisse rien derrière lui et ne se déguise pas en amorçage
      interrompu : code 7, aucun fichier de travail, et une relance suffit.
- [x] Deux initialisations simultanées ne produisent jamais deux fichiers ni un fichier écrasé.
      — huit écritures concurrentes et quatre CLI concurrentes : une seule gagnante, contenu
      jamais mélangé, les perdantes en 3 ou 6 et jamais un autre code.
- [x] Les deux instants d'un arrêt brutal sont distingués : `.pending` seul ⇒ refus sans rien
      effacer ; `.pending` lié à la cible ⇒ nettoyage et code 3. Vérifié hors tests avec le jar.
- [x] La documentation d'installation ne mentionne aucune plateforme de déploiement comme
      prérequis ; elle décrit le contrat de sortie et laisse chaque adaptateur choisir sa
      cible.
- [x] Le mode éphémère et le mode persistant sont documentés comme deux choix explicites,
      sans dépréciation implicite de l'un par l'autre.

## Hors périmètre

- Tout adaptateur de packaging par plateforme (Helm/OpenShift, Kubernetes brut, Docker
  Compose, VM) — récits séparés, un par cible, si et quand une cible est retenue.
- Rotation de la clé de chiffrement elle-même — portée par TAS-GRANTS-02A si le besoin
  apparaît, distincte du bootstrap initial que ce récit couvre.
- Génération ou rotation des clés de signature : TAS amorce lui-même sa première clé
  (TAS-KEYS-BOOTSTRAP-01, livré) et la rotation appartient à TAS-GRANTS-02A. La CLI ne produit
  jamais de matière de signature.
- Intégration directe à un coffre (écriture par API, sortie standard) — adaptateur explicite,
  récit séparé.

## Documentation d'exploitation

Elle vit avec l'outil, dans [`takibo-install-keys/README.md`](../../takibo-install-keys/README.md) :
invocation, import dans `.env` / Docker / OpenShift, codes de sortie, conduite à tenir après une
initialisation interrompue, et la règle de sauvegarde conjointe base + clé AES.

## Clôture documentaire

Lorsque ce récit est terminé et validé :

1. passer son statut à `TERMINÉ` ;
2. déplacer ce fichier de `docs/backlog` vers `docs/terminer` avec `git mv` ;
3. mettre à jour les index et liens concernés ;
4. inclure ce déplacement dans la PR de clôture du récit.
