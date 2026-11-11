# SEC-HUNT-02 — Le seuil de verrouillage tient sous des échecs concurrents

**Statut** : EN REVUE — branche `fix/sec-hunt-02-login-lockout`  
**Lot** : SECURITY FIX 01–05  
**Risque** : moyen — exploitable sans authentification (email et code d'Organization suffisent)  
**Origine** : SECURITY HUNT 01, découverte HUNT-02  
**Révision vérifiée** : `main@19998c4`

## 1. Récit

En tant que responsable de l'authentification, je veux que le verrouillage après
`max-failed-attempts` échecs tienne quel que soit le parallélisme des tentatives, afin qu'une
rafale de requêtes ne multiplie pas le nombre de mots de passe essayés.

## 2. Défaut constaté

`HumanAuthenticationService.authenticate` lisait les credentials, comparait le mot de passe, puis
écrivait `registerFailure()` (lecture-modification-écriture, verrou optimiste sur
`account_credentials.version`). Des requêtes concurrentes lisaient le même compteur : une seule
écriture passait, les autres étaient perdues ou levaient `StaleStateException`, traduite en 500.

Mesure sur instance locale jetable (`main@19998c4`), seuil 5 :

| Scénario | Compteur | Verrou | Mots de passe essayés |
|---|---|---|---|
| 5 échecs séquentiels | 5 | posé | 5 |
| 50 échecs concurrents | 2 | non | 50 |
| 150 échecs concurrents (3 lots) | 5 | posé | **150** |

Plusieurs réponses 500 au lieu de 401.

## 3. Correctif

La tentative est **réservée avant** la comparaison du mot de passe, par une seule instruction SQL
atomique sérialisée par le verrou de ligne de PostgreSQL :

```sql
UPDATE account_credentials
   SET failed_attempts = failed_attempts + 1,
       locked_until = CASE WHEN :lockEnabled AND failed_attempts + 1 >= :max THEN :lockUntil ELSE locked_until END, …
 WHERE org_id = :orgId AND account_id = :accountId
   AND (locked_until IS NULL OR locked_until <= :now)
RETURNING failed_attempts
```

- Aucune ligne rendue : le compte est verrouillé, la tentative n'est ni comptée ni vérifiée.
- Mauvais mot de passe : la réservation **est** l'échec compté.
- Bon mot de passe, identité ACTIVE : compteur remis à zéro et verrou levé (`clearFailedAttempts`).
- Bon mot de passe, identité absente ou révoquée (IAM-REVOKE-01) : la réservation est **annulée**,
  le compteur revient exactement à sa valeur antérieure (`cancelFailedAttempt`).

Sous une rafale, au plus `max-failed-attempts` comparaisons du vrai hash ont lieu avant le
verrou. Les écritures ne passent plus par l'entité versionnée : aucun conflit de version, aucune
erreur 500.

Comportements inchangés : seuil exact en séquentiel, durée du verrou (`lock-seconds`), remise à
zéro par un succès, refus uniforme, égalisation temporelle des comptes inconnus, règle
IAM-REVOKE-01.

Fichiers de production : `HumanAuthenticationService`, `AccountCredentialsRepository` (+3
méthodes), `AccountCredentialsRepositoryAdapter`, `FailedAttemptReservation` (nouveau).

Hors périmètre : limitation de débit, verrouillage par adresse, CAPTCHA.

## 4. Tests

| Niveau | Test | Ce qu'il prouve |
|---|---|---|
| Unitaire | `HumanAuthenticationServiceTest` (20) | réservation avant comparaison, annulation sur identité révoquée, remise à zéro au succès, compte verrouillé par une tentative concurrente, durée nulle |
| Unitaire | `HumanLoginServiceTest` (14) | contrat du login REST inchangé |
| Bout en bout PostgreSQL | `LoginLockoutConcurrencyPostgresIntegrationTest` (6) | seuil exact séquentiel ; 4 échecs concurrents tous comptés ; rafale de 40 : **exactement 5** comparaisons du vrai hash, aucun 500 ; expiration du verrou ; IAM-REVOKE-01 préservé |
| Bout en bout PostgreSQL | `IdentityRevocationLoginPostgresIntegrationTest`, `HumanLoginBaselineIntegrationTest` | non-régression |
| Newman | dossier « 02 » (+5 requêtes) | verrou au 5e échec séquentiel ; bon mot de passe refusé après 30 échecs concurrents ; aucun 500 |

Rouge avant correctif : 2 des 6 tests de bout en bout échouent sur `main` (réponses 500,
mises à jour perdues). Contre le jar de `main`, la collection modifiée échoue : le bon mot de
passe est accepté après la rafale.

## 5. Limites et risques résiduels

- **Succès concurrent** : un bon mot de passe remet le compteur à zéro, y compris les échecs
  concurrents réservés juste avant lui (comportement antérieur conservé).
- **Verrouillage ciblé** : 5 échecs verrouillent n'importe quel compte connu pendant
  `lock-seconds` (constat de conception de HUNT 01, inchangé).
- **Après expiration**, le compteur reste au seuil : un seul nouvel échec reverrouille
  (comportement antérieur conservé).
- `AccountCredentials.registerFailure` et `registerSuccess` ne sont plus appelés par le service ;
  ils ne sont pas supprimés dans ce récit.
