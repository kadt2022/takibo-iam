# TAKIBO-INSTALL-KEYS-01 — Bootstrap cryptographique portable

**Statut :** À FAIRE  
**Branche :** `feat/takibo-install-keys-01`  
**Dépendances :** TAS-GRANTS-02A et TAS-GRANTS-02 (contrats de configuration à consommer)

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

## Périmètre

- Un outil d'initialisation TAKIBO (CLI ou tâche de démarrage dédiée, à trancher dans ce
  récit) qui génère les trois valeurs du contrat de sortie : deux matières de 32 octets
  cryptographiquement sûres et indépendantes l'une de l'autre, et un identifiant de clé
  conforme au contrat de `SecretCipherKey`.
- Sortie du bootstrap sous une forme neutre — flux standard, fichier, ou les deux — que
  n'importe quel adaptateur de packaging peut rediriger vers son propre mécanisme de secret.
- Détection et refus explicite d'un bootstrap relancé sur une installation déjà initialisée :
  régénérer la clé de chiffrement sans plan de rotation rendrait indéchiffrable tout ce qui a
  été chiffré avec l'ancienne, et régénérer la clé HMAC rendrait irretrouvable tout `user_code`
  déjà haché.
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
- [ ] Relancer l'outil sur une installation déjà initialisée est refusé explicitement,
      jamais silencieusement écrasé.
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
- Génération ou rotation des clés de signature — objet propre de TAS-GRANTS-02A.
