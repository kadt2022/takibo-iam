# TAKIBO-INSTALL-KEYS-01 — Bootstrap cryptographique portable

**Statut :** À FAIRE  
**Branche :** `feat/takibo-install-keys-01`  
**Dépendances :** TAS-GRANTS-02A (contrat de configuration à consommer)

## Récit

En tant qu'organisation qui installe TAKIBO sur l'infrastructure de son choix, je veux que
l'installation génère elle-même la clé de chiffrement au repos et son identifiant, afin de ne
jamais avoir à fabriquer un secret cryptographique à la main.

## Décision actée — le cœur ne connaît aucune plateforme de déploiement

TAS-GRANTS-02A pose un contrat générique et fail-closed : *« j'ai besoin d'une clé de
chiffrement persistante et de son identifiant »*, exprimé par deux variables
d'environnement, `TAKIBO_TAS_CIPHER_KEY` et `TAKIBO_TAS_CIPHER_KEY_ID`. Ce contrat est déjà
portable — Docker, Windows, Linux, Kubernetes ou OpenShift peuvent tous le satisfaire — et
02A s'arrête là : le cœur consomme la clé, refuse de démarrer si elle manque, et n'a
d'opinion sur aucun mécanisme de packaging.

Une première formulation de ce récit couplait le bootstrap à OpenShift/Helm. C'était une
erreur : TAKIBO ne doit importer aucune API OpenShift, Kubernetes, Helm ou Docker dans son
cœur. La séparation retenue :

| Élément | Responsabilité |
| --- | --- |
| Cœur TAKIBO | Consommer la clé et refuser de démarrer si elle manque (TAS-GRANTS-02A) |
| Outil d'installation TAKIBO | Générer une clé sécurisée une seule fois (ce récit) |
| Adaptateur Docker | La conserver comme Docker Secret ou fichier protégé |
| Adaptateur Kubernetes/OpenShift | La conserver comme Secret |
| Adaptateur VM/on-premise | La conserver dans un coffre ou fichier système protégé |

OpenShift devient une **distribution supportée** parmi d'autres, jamais une dépendance du
cœur. Ce récit produit l'outil d'initialisation générique et son contrat de sortie ; les
adaptateurs de packaging par plateforme sont hors périmètre et pourront être des récits
séparés, un par cible.

## Périmètre

- Un outil d'initialisation TAKIBO (CLI ou tâche de démarrage dédiée, à trancher dans ce
  récit) qui génère une matière de 32 octets cryptographiquement sûre et un identifiant de
  clé conforme au contrat de `SecretCipherKey`.
- Sortie du bootstrap sous une forme neutre — flux standard, fichier, ou les deux — que
  n'importe quel adaptateur de packaging peut rediriger vers son propre mécanisme de secret.
- Détection et refus explicite d'un bootstrap relancé sur une installation déjà initialisée :
  régénérer la clé sans plan de rotation rendrait indéchiffrable tout ce qui a été chiffré
  avec l'ancienne.
- Documentation des deux modes d'exploitation, sans en préférer un dans le code :
  - les deux variables persistantes fournies par l'installation ;
  - `TAKIBO_TAS_EPHEMERAL_KEYS=true`, réservé au développement et aux tests.

## Critères d'acceptation

- [ ] Le cœur TAKIBO (`takibo-authorization-server`, `takibo-iam-boot`) n'importe aucune
      dépendance Docker, Kubernetes, Helm ou OpenShift.
- [ ] L'outil produit une clé de 32 octets et un identifiant satisfaisant les invariants de
      `SecretCipherKey` sans aucune modification de ce type.
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
