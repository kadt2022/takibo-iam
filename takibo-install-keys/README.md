# takibo-install-keys — initialisation cryptographique de TAKIBO

Génère, **une seule fois par installation**, les secrets externes dont TAKIBO a besoin pour
démarrer en mode persistant.

```bash
java -jar takibo-install-keys.jar init --out takibo-secrets.env
```

Aucune base de données, aucun serveur, aucune plateforme de déploiement : une JVM suffit.

## Ce que l'outil produit

Trois lignes, dans un fichier lisible par son seul propriétaire :

| Variable | Rôle |
| --- | --- |
| `TAKIBO_TAS_CIPHER_KEY_ID` | identifiant de la clé de chiffrement, inscrit dans chaque chiffre — c'est lui qui rendra une rotation possible |
| `TAKIBO_TAS_CIPHER_KEY` | 32 octets, base64 — chiffrement au repos |
| `TAKIBO_TAS_USER_CODE_HMAC_KEY` | 32 octets, base64, tirés indépendamment — HMAC du `user_code` (RFC 8628) |

**La clé de signature RSA n'est pas là, et c'est voulu.** TAS l'amorce lui-même au premier
démarrage et la conserve chiffrée en base avec la clé AES ci-dessus. Le client ne la manipule
jamais.

## Où mettre ce fichier

Le format est du `CLE=valeur` nu, sans commentaire ni guillemet : il s'importe tel quel.

```bash
# Fichier d'environnement
set -a && . ./takibo-secrets.env && set +a

# Docker
docker run --env-file takibo-secrets.env ...

# Kubernetes / OpenShift
oc create secret generic takibo-tas-keys --from-env-file=takibo-secrets.env
```

Une fois les valeurs rangées dans le mécanisme de secret de votre plateforme, **le fichier n'a
plus à rester sur le disque de la machine d'installation** — mais il doit être sauvegardé
ailleurs. Voir ci-dessous.

## La règle de sauvegarde, la plus importante de cette page

> **La base PostgreSQL et la clé AES se sauvegardent ensemble. Restaurer l'une sans l'autre ne
> restaure rien.**

La clé de signature de TAKIBO vit chiffrée dans la base, scellée par `TAKIBO_TAS_CIPHER_KEY`.
Une sauvegarde de base sans sa clé est indéchiffrable ; une clé sans sa base ne désigne plus
rien. Les codes et jetons OAuth 2.0 persistés obéissent à la même règle.

**Si le fichier de secrets est perdu alors que la base contient des données : restaurez la
sauvegarde. Ne régénérez jamais.** Des clés neuves rendraient définitivement illisible tout ce
que les anciennes ont scellé. L'outil n'a aucun moyen de détecter cette situation — il ne parle
pas à la base — et c'est pourquoi il n'offre aucune option pour passer outre.

## Deux modes d'exploitation, deux choix explicites

| Mode | Comment | Pour qui |
| --- | --- | --- |
| **Persistant** | les trois variables ci-dessus | toute installation durable |
| **Éphémère** | `TAKIBO_TAS_EPHEMERAL_KEYS=true` | développement et tests uniquement |

Le mode éphémère régénère les clés à chaque démarrage : tous les JWT en circulation sont
invalidés à chaque redémarrage, et tout ce qui a été chiffré devient illisible. C'est sans
conséquence sur un poste de développement, et c'est ce que fait la CI. Ce n'est jamais un mode
d'installation.

## Durabilité : ce qui est garanti, et où

L'outil écrit dans `<cible>.pending`, force son contenu sur le disque, puis le publie sous son
nom final par un lien — jamais par un remplacement. Restent les **entrées de répertoire** : un
fichier parfaitement écrit peut, après une coupure de courant, n'être rattaché à aucun nom.

L'outil force donc aussi le répertoire, deux fois. Mais tous les systèmes ne le permettent pas :
l'API Java n'ouvre pas un répertoire en canal sous Windows. La garantie est alors moindre, et
**l'outil le dit** plutôt que de le taire :

```
Note: this filesystem does not allow flushing directory entries; if power is lost immediately
after this run, confirm that takibo-secrets.env still exists before using these keys.
```

| Niveau | Quand | Ce que ça signifie |
| --- | --- | --- |
| forcé sur le disque | volumes qui exposent leurs répertoires (Linux, la plupart des montages POSIX) | après le code `0`, une coupure ne peut plus faire disparaître le fichier |
| au mieux | volumes qui ne l'exposent pas (Windows) | le fichier est écrit et publié ; sa survie à une coupure **immédiate** dépend du système de fichiers seul |

Dans les deux cas, la conduite est la même et elle ne coûte rien : **sauvegardez le fichier avant
de mettre ces clés en service.** C'est de toute façon ce qu'exige la règle ci-dessus.

## Codes de sortie

| Code | Situation | Conduite |
| --- | --- | --- |
| `0` | les trois valeurs sont écrites | ranger le fichier dans le mécanisme de secret, et le sauvegarder |
| `2` | commande ou argument invalide | rien n'a été tenté |
| `3` | le fichier de sortie existe déjà | l'installation a déjà eu lieu — ne pas régénérer |
| `4` | le système de fichiers ne peut pas protéger le fichier | écrire sur un volume qui porte des permissions POSIX ou des ACL |
| `5` | échec d'entrée-sortie | répertoire absent, disque plein, droits insuffisants |
| `6` | un `.pending` est présent | voir ci-dessous |

Le code `0` accompagné de la note de durabilité reste un succès : le fichier est écrit, publié
et protégé. La note ne porte que sur la survie à une coupure survenant dans la seconde qui suit.

## Quand une initialisation a été interrompue

Pendant son travail, l'outil écrit dans `<cible>.pending`, puis publie ce fichier sous son nom
final. Un arrêt brutal peut donc laisser deux situations, et l'outil les distingue :

- **`.pending` seul, sans fichier final** — l'initialisation a été interrompue avant
  publication. L'outil sort en `6` et **n'efface rien** : ce fichier contient des clés qui ont
  peut-être déjà servi. À vous de décider — soit le renommer en fichier final si ces clés sont
  les bonnes, soit le supprimer **en connaissance de cause** si aucune donnée n'a encore été
  chiffrée avec elles.
- **`.pending` et fichier final identiques** — la publication avait réussi, seul le nettoyage
  manquait. L'outil retire le `.pending` et sort en `3` : l'installation est faite.
- **`.pending` et fichier final différents** — deux jeux de clés coexistent. L'outil sort en
  `6` sans rien toucher : lui ne peut pas savoir lequel est en service, vous le pouvez.

## Ce que l'outil ne fait pas

- Il n'écrase jamais un fichier existant, et il n'a **pas** d'option `--force`.
- Il n'écrit jamais un secret sur la sortie standard, ni dans un journal, ni en cas d'échec.
- Il ne contacte ni la base, ni TAS, ni aucune API.
- Il ne fait pas tourner les clés : la rotation de la clé de signature appartient à TAS
  (TAS-GRANTS-02A).
