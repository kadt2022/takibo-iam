# TAS-KEYS-BOOTSTRAP-01 — Amorçage automatique de la première clé de signature

**Statut :** À FAIRE
**Branche :** `feat/tas-keys-bootstrap-01`
**Dépendances :** TAS-GRANTS-02A (clés persistantes et rotation), TAKIBO-INSTALL-KEYS-01 (secrets externes)

## Récit

En tant qu'organisation cliente qui installe TAKIBO sur son infrastructure, je veux que TAS
fabrique lui-même sa première clé de signature au premier démarrage, afin que l'installation se
limite à fournir les secrets externes et ne comporte aucune étape cryptographique manuelle.

## Le problème, tel qu'il se présente aujourd'hui

`SigningKeyRotationService.initializeFirstIssuer()` existe depuis TAS-GRANTS-02A et fonctionne —
mais **aucun code de production ne l'appelle** : seuls trois tests le font. Une installation
neuve, dûment pourvue de ses trois valeurs de configuration, échoue donc au démarrage :

```
NO_ACTIVE_PLATFORM_SIGNING_KEY: TAS cannot issue tokens
```

En mode persistant, aucune installation ne peut démarrer. Le contournement
`TAKIBO_TAS_EPHEMERAL_KEYS=true` reste **strictement réservé au développement et aux tests** :
il régénère une paire à chaque démarrage et invalide donc tous les JWT en circulation.

## Décision actée — deux natures de matière, deux responsables

| Matière | Nature | Qui la produit |
| --- | --- | --- |
| `TAKIBO_TAS_CIPHER_KEY` + `TAKIBO_TAS_CIPHER_KEY_ID`, `TAKIBO_TAS_USER_CODE_HMAC_KEY` | **Secret externe** : le client doit pouvoir la ranger dans son coffre, la sauvegarder et la faire tourner. Si TAS la fabriquait, elle finirait rangée à côté de ce qu'elle chiffre et le chiffrement au repos ne protégerait plus rien. | L'outil d'installation, une seule fois (TAKIBO-INSTALL-KEYS-01). Absence = démarrage refusé. |
| La clé de signature RSA | **État cryptographique interne persistant** — voir ci-dessous. | TAS lui-même, au premier démarrage (ce récit). |

**Vocabulaire — la clé RSA n'est pas un « état dérivé ».** Elle est tirée aléatoirement et n'est
pas reproductible : on ne peut pas la recalculer à partir des secrets externes. C'est un état
cryptographique interne persistant, ce qui emporte quatre conséquences :

- le client ne la manipule jamais directement ;
- elle est chiffrée en base avec la clé AES externe ;
- elle doit être sauvegardée **avec** PostgreSQL ;
- restaurer la base sans la clé AES, ou la clé AES sans la base, ne suffit pas — les deux sont
  nécessaires, et la documentation d'exploitation doit le dire explicitement.

Exiger du client une commande d'amorçage n'ajouterait aucune sécurité : tout ce que cette clé
protège est déjà protégé par la clé AES qu'il a fournie. Cela n'ajouterait qu'une étape ratable,
dont l'oubli ne se découvre qu'au démarrage refusé.

## Règles à fixer

1. **Amorçage automatique uniquement si aucun historique de clé de plateforme n'existe.** La
   condition n'est pas « aucune émettrice active » mais « aucune ligne de clé de plateforme,
   quel que soit son statut ».
2. **Historique présent sans émettrice active ⇒ échec fermé.** Cette situation dénonce une
   corruption, une restauration partielle ou une rotation interrompue. Générer une clé neuve la
   masquerait, et TAS signerait avec une clé que rien n'a décidée, à côté d'un historique dont
   personne n'aurait examiné l'état.
3. **Insertion concurrente atomique.** `INSERT ... ON CONFLICT DO NOTHING` — l'index unique
   partiel `uk_tas_sk_platform_issuer_active` arbitre — puis relecture de l'émettrice gagnante.
   Ne pas capturer une violation d'unicité pour relire dans la même transaction JPA : elle peut
   déjà être marquée rollback-only, et la relecture échouerait pour une raison sans rapport avec
   le problème réel.
4. **L'amorçage se termine avant la validation de `PersistentJwkSource`**, par une dépendance
   Spring explicite — le bean d'amorçage passé en paramètre de la méthode `@Bean`, donc une
   dépendance de type — et non par un ordre implicite de beans ni par un nom de bean en chaîne
   de caractères.
5. **Le `kid` peut être journalisé ; jamais la matière privée.** L'amorçage doit laisser une
   trace lisible : c'est le seul instant où une clé de plateforme naît sans qu'un opérateur l'ait
   demandée.
6. **Toute autre erreur bloque le démarrage** : secrets absents, base inaccessible, ligne
   incohérente. Le fail-closed de TAS-GRANTS-02A ne s'assouplit nulle part ailleurs.

## Périmètre

- Un point d'entrée d'amorçage, appelé au démarrage, qui décide entre « installation vierge »,
  « émettrice déjà présente » et « historique incohérent », et n'écrit que dans le premier cas.
- Le chemin d'écriture atomique de la règle 3, y compris ce qu'il faut ajouter à
  `SigningKeyWriter` / `JpaSigningKeyRepository` pour l'exprimer sans capture d'exception
  d'unicité.
- Le câblage explicite de la règle 4 dans `SigningKeysConfiguration`.
- La documentation d'exploitation : la sauvegarde de la base et celle de la clé AES sont
  indissociables, et le mode éphémère reste réservé au développement et aux tests.

## Critères d'acceptation

- [ ] Base vide : le premier démarrage crée **exactement une** émettrice de plateforme active.
- [ ] Redémarrage : même `kid`, aucune clé supplémentaire créée.
- [ ] Deux démarrages concurrents : une seule clé créée, et **les deux instances démarrent** avec
      l'émettrice gagnante.
- [ ] Historique de clés présent sans émettrice active : démarrage refusé, message distinct de
      celui d'une installation vierge.
- [ ] Secrets externes absents : démarrage refusé, comportement de TAS-GRANTS-02A inchangé.
- [ ] La clé amorcée est relue et déchiffrée correctement après redémarrage ; TAS signe et
      vérifie avec elle.
- [ ] Le `kid` apparaît dans les journaux de l'amorçage ; aucune trace de matière privée, chiffrée
      ou non.
- [ ] `TAKIBO_TAS_EPHEMERAL_KEYS=true` reste documenté comme réservé au développement et aux
      tests, sans devenir un mode d'installation.

## Hors périmètre

- La rotation (`rotate(Duration)`) et sa politique de chevauchement : TAS-GRANTS-02A.
- L'outil d'installation qui produit les trois valeurs : TAKIBO-INSTALL-KEYS-01.
- Le multi-issuer par organisation : le schéma le permet, la configuration single-issuer le
  ferme, et ce récit ne rouvre pas la question.
- Toute politique de cryptopériode (`expires_at`), aujourd'hui non utilisée.
