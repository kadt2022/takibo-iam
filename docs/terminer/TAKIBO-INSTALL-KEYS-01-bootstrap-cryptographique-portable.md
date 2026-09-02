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

## Décision actée — restreindre à la création, ou refuser

Les permissions du fichier ne sont pas posées après coup. Un fichier créé lisible puis corrigé
est lisible pendant l'intervalle, et cet intervalle suffit.

- POSIX : attributs `rw-------` **fournis à la création**, jamais un `chmod` ultérieur ;
- Windows : ACL restreinte au propriétaire dès la création, si le fournisseur de fichiers le
  permet ;
- si la restriction ne peut pas être garantie à la création, la CLI **refuse d'écrire** et le
  dit. Java ne sait pas poser une ACL Windows atomiquement dans tous les cas, et il vaut mieux
  un refus explicite qu'un fichier de secrets aux permissions inconnues.

La création elle-même est exclusive (`CREATE_NEW`) : c'est le système de fichiers qui arbitre
l'existence, pas une vérification préalable qui laisserait une fenêtre entre le test et
l'écriture.

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

- [ ] Le cœur TAKIBO (`takibo-authorization-server`, `takibo-iam-boot`) n'importe aucune
      dépendance Docker, Kubernetes, Helm ou OpenShift.
- [ ] L'outil produit une clé de 32 octets et un identifiant satisfaisant les invariants de
      `SecretCipherKey` sans aucune modification de ce type.
- [ ] L'outil produit une troisième valeur, `TAKIBO_TAS_USER_CODE_HMAC_KEY`, de 32 octets,
      satisfaisant les invariants de `HmacSha256UserCodeHmac` sans modification de ce type.
- [ ] `TAKIBO_TAS_USER_CODE_HMAC_KEY` et `TAKIBO_TAS_CIPHER_KEY` ne sont jamais identiques, et
      sont tirées indépendamment plutôt que dérivées l'une de l'autre.
- [ ] Relancer l'outil alors que le fichier de sortie existe est refusé explicitement, jamais
      silencieusement écrasé, et aucune option n'existe pour forcer ce refus.
- [ ] `--out` est obligatoire : aucun chemin de sortie implicite.
- [ ] Le fichier produit contient exactement trois lignes `CLE=valeur`, en LF, avec saut de
      ligne final, sans commentaire ni guillemets, et s'importe tel quel dans `.env` comme dans
      un Secret OpenShift.
- [ ] Les permissions restrictives sont posées à la création, jamais corrigées après coup ; si
      la restriction ne peut pas être garantie, aucun fichier n'est écrit.
- [ ] Aucun secret n'apparaît sur la sortie standard, sur la sortie d'erreur, ni dans aucun
      journal — y compris en cas d'échec.
- [ ] L'outil s'exécute sans démarrer TAS, sans base de données et sans contexte Spring.
- [ ] L'identifiant de clé produit est de la forme `k-<UUID>`, opaque et sans composante
      temporelle.
- [ ] La documentation dit explicitement que la perte du fichier se répare par restauration de
      la sauvegarde, et jamais par une régénération contre une base existante.
- [ ] La documentation d'installation ne mentionne aucune plateforme de déploiement comme
      prérequis ; elle décrit le contrat de sortie et laisse chaque adaptateur choisir sa
      cible.
- [ ] Le mode éphémère et le mode persistant sont documentés comme deux choix explicites,
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
