# IAM-REVOKE-01 — Une identité révoquée n'obtient plus de preuve d'authentification

**Statut** : EN COURS — lots 1 et 2 implémentés sur `fix/iam-revoke-01`  
**Lot** : Identité et cohérence des jetons  
**Risque** : élevé — authentification humaine, tous parcours  
**Doctrine** : PHILOSOPHY.md (« une identité n'est pas son autorité ») ; ADR 0001 ; TAS-GRANTS-03-A  
**Dépend de** : TAS-GRANTS-03-A (cœur unique `HumanAuthenticationCase`, PR #123)  
**Révision vérifiée** : `main@0f4bc86`

## 1. Récit

En tant que responsable de l'identité TAKIBO, je veux qu'une identité absente, suspendue ou
désactivée n'obtienne plus aucune nouvelle preuve d'authentification, afin que la révocation
d'une identité source ne laisse survivre aucune projection active par un nouveau login.

## 2. Invariants

**Invariant A — Émission (ce récit).** Une identité absente, suspendue ou désactivée ne peut
obtenir une nouvelle preuve d'authentification, quel que soit le parcours : login REST
ORGANIZATION, login REST SPACE transitoire, et la session navigateur de TAS-GRANTS-03-B.

**Invariant B — Utilisation (hors de ce récit).** Après une révocation effective, aucune preuve
émise antérieurement ne continue d'exercer une autorité. L'invariant A ne le garantit pas : voir §6.

## 3. Preuves (lecture du code et sonde PostgreSQL)

| # | Constat | Preuve |
|---|---|---|
| P1 | Le statut d'identité existe : `takibo_identities.identity_status` ∈ {ACTIVE, SUSPENDED, DISABLED} | `V202601091230__core__init.sql` |
| P2 | Le domaine sait suspendre, désactiver et réactiver, mais **aucun code n'appelle** ces transitions et aucune API n'écrit le statut | `TakiboIdentity.suspend/disable/activate` ; aucun appelant dans `src/main` |
| P3 | **Aucun parcours d'authentification ne lit** le statut | `HumanAuthenticationService`, `HumanLoginService` |
| P4 | Le login ORGANIZATION (IAM-31) ne consulte aucun User : désactiver tous les Users d'un humain ne l'empêche pas d'obtenir un token d'organisation | `HumanLoginService.loginOrganizationScoped` |
| P5 | Deux chemins créent un Account : l'inscription d'organisation (`AccountApplicationService.createAccountInOrg`, sans identité) et `UserRegistrationOrchestrator` → `AccountDomainService` (avec identité) | lecture du code |
| P6 | **Le fondateur reçoit pourtant son identité** : l'inscription enregistre ensuite son User avec ce compte, et `resolveExistingAccount` appelle `ensureTakiboIdentityExists` | sonde PostgreSQL temporaire (non versionnée) : fondateur et employé ont chacun 1 ligne |
| P7 | L'invariant « tout Account a une identité » ne tient donc que par l'ordre des appels de l'inscription, pas par construction | P5 + P6 |

L'hypothèse initiale « les fondateurs n'ont pas d'identité » est **réfutée** par P6. Des Accounts
sans identité restent possibles en base (données antérieures, insertion directe) : leur nombre
réel ne peut être établi que par le diagnostic en lecture seule (§7).

## 4. Décision doctrinale : l'absence d'identité est une anomalie

Le modèle traite l'identité comme la contrepartie obligatoire de tout Account humain
authentifiable : `ensureTakiboIdentityExists` répare son absence, et la clé
`fk_ti_account_scope` en fait une dépendance de l'Account. Une identité absente n'est donc pas un
état du cycle de vie : c'est une anomalie de persistance. Conséquences :

- toute création d'Account humain authentifiable garantit son identité **par construction**,
  dans la même transaction (pas seulement par l'ordre des appels de l'inscription) ;
- le login refuse une identité absente (défense en profondeur) ; aucun contournement ne
  l'accepte, même provisoirement.

## 5. Découpage proposé

**Lot 1 — Contrôle d'émission** (cœur `HumanAuthenticationService`)
- Lire l'identité de l'Account dans l'Organization du contexte.
- Refuser une identité absente ou non ACTIVE par l'échec uniforme existant.
- Placer le contrôle **après** la vérification du mot de passe : le coût du hash est déjà payé,
  aucun oracle temporel ; un mot de passe faux reste compté comme aujourd'hui ; un refus pour
  identité non active ne modifie pas le compteur d'échecs.
- Cause réelle en journal uniquement.

**Lot 2 — Cohérence des identités**
- `AccountApplicationService.createAccountInOrg` provisionne l'identité dans sa transaction.
- Migration Flyway de rattrapage **seulement si le diagnostic §7 montre des Accounts sans
  identité** : `INSERT … SELECT … WHERE NOT EXISTS … ON CONFLICT DO NOTHING`, statut ACTIVE,
  type HUMAN, `identity_id = account_id` (convention de `provisionTakiboIdentity`). Elle
  n'exécute aucun `UPDATE` : une identité existante, quel que soit son statut, n'est jamais
  modifiée ni réactivée. Transactionnelle, idempotente, bornée par `(org_id, account_id)`.
- Ordre de déploiement : Flyway s'exécute au démarrage, avant que l'application ne serve ; le
  lot 1 et le lot 2 doivent partir dans la même version, ou le lot 2 avant.
- Le couple lot 1 + lot 2 ne fait que restreindre : il n'accorde à aucun Account un accès qu'il
  n'a pas aujourd'hui (le statut n'est lu nulle part avant ce récit).

**Récit séparé — Gouvernance et révocation** : API de suspension, désactivation et
réactivation ; permission ; protection du dernier propriétaire ; audit des transitions ; étude de
l'invariant B.

**Hors périmètre** : identités SERVICE et MACHINE (audit distinct ; les machines sont des clients
OAuth) ; vérification d'une Organization ACTIVE sur le chemin SPACE (traitement séparé).

## 6. Risque résiduel documenté — invariant B

- Un token humain déjà émis reste valide jusqu'à son expiration : 300 s par défaut
  (`takibo.tas.human-token.ttl-seconds`, `takibo.tas.human-org-token.ttl-seconds`), sans
  révocation ni refresh token humain aujourd'hui.
- La session navigateur de TAS-GRANTS-03-B devra relire le statut, ou être invalidée lors d'une
  révocation : à décider dans 03-B.
- La décision sur l'époque de sécurité (TAS-GRANTS-04) doit couvrir l'invariant B.

## 7. Diagnostic en lecture seule (non exécuté sur une base réelle)

Requête préparée hors dépôt, en `BEGIN TRANSACTION READ ONLY … ROLLBACK` : Accounts sans identité
par Organization ; répartition des statuts d'identité ; identités non ACTIVE disposant de
credentials. Son exécution sur une base réelle demande une autorisation spécifique.

## 8. Tests attendus (rouges avant implémentation)

**Unitaires — `HumanAuthenticationServiceTest`**
- identité SUSPENDED + bon mot de passe → échec uniforme, compteur inchangé ;
- identité DISABLED + bon mot de passe → échec uniforme ;
- identité absente + bon mot de passe → échec uniforme ;
- identité non active + mauvais mot de passe → échec uniforme, échec compté comme aujourd'hui ;
- le hash réel est vérifié dans tous ces cas (pas d'oracle temporel) ;
- identité ACTIVE → succès inchangé ;
- l'identité est lue uniquement dans l'Organization du contexte.

**Unitaires — création d'Account**
- `createAccountInOrg` provisionne exactement une identité ACTIVE HUMAN ;
- un Account existant n'est pas dupliqué.

**PostgreSQL de bout en bout**
- login ORGANIZATION et login SPACE refusés (401 uniforme) pour une identité SUSPENDED,
  DISABLED, puis absente ; acceptés pour ACTIVE ;
- un humain dont tous les Users sont désactivés et dont l'identité est DISABLED n'obtient plus
  de token d'organisation ;
- inscription : le fondateur a exactement une identité sans dépendre de l'enregistrement de son
  User ;
- migration (si retenue) : crée les identités manquantes, ne modifie aucune identité existante
  (SUSPENDED et DISABLED restent tels quels), rejouée sans effet.

**Régression** : `HumanLoginBaselineIntegrationTest`, `client_credentials`, RBAC, frontières ;
les fixtures de test qui insèrent des Accounts directement devront insérer aussi leur identité.

## 9. Critères d'acceptation

- [ ] AC-01 — Aucune identité absente, SUSPENDED ou DISABLED n'obtient de token, sur les deux parcours REST.
- [ ] AC-02 — La réponse publique est identique à tout autre échec ; aucun oracle temporel.
- [ ] AC-03 — Le contrôle vit dans le cœur unique ; aucun autre parcours ne le duplique.
- [ ] AC-04 — Toute création d'Account humain garantit son identité dans la même transaction.
- [ ] AC-05 — Aucune migration ne réactive ni ne modifie une identité existante.
- [ ] AC-06 — Le risque résiduel de l'invariant B est documenté et rattaché à 03-B et 04.
- [ ] AC-07 — Aucune régression des suites existantes.
