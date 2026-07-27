# RBAC-00 — Fermer l'escalade par nommage de rôle

**Statut** : TERMINÉ
**Doctrine** : [ADR 0003 §11](../adr/0003-doctrine-rbac-v2.md)
**Dépend de** : —
**Risque** : élevé

## Contexte

Quatre codes d'administrateur plateforme circulent dans le code, dont un seul existe au
catalogue technique :

| Code | Emplacement | Au catalogue ? |
| --- | --- | --- |
| `R_TAKIBO_PLATFORM_ADMIN` | `TechnicalRole` | **oui** |
| `R_PLATFORM_ADMIN` | `PolicyEvaluator:112`, `SecurityConfig:27` | non |
| `PLATFORM_ADMIN` | `PolicyEvaluator:112`, `SecurityConfig:28`, `DefaultThresholdPolicy:37` | non |
| `ROLE_PLATFORM_ADMIN` | `SecurityConfig:76`, `BoundaryMembershipService:286` | non |

`PolicyEvaluator.isTenantAdmin()` accepte les deux codes fantômes `R_PLATFORM_ADMIN` et
`PLATFORM_ADMIN`, **mais pas** `R_TAKIBO_PLATFORM_ADMIN` — le seul que l'enum produise.

Chaîne d'escalade établie par lecture du code :

```text
UserRoleGovernanceService.resolveGovernableRole()
  « PLATFORM_ADMIN » n'est pas un TechnicalRole  →  bascule sur la recherche DB
  rôle GOVERNANCE trouvé  →  ResolvedRole(scope = SPACE, forcé)
  assertNoScopeEscalation() ne s'applique qu'au scope ORGANIZATION  →  aucun contrôle
  assignation enregistrée avec le code « PLATFORM_ADMIN »
        ↓
EffectiveRbacQueryService : un code non technique n'est jamais « caché »  →  entre au token
        ↓
PolicyEvaluator.isTenantAdmin()  →  vrai
```

`RoleApplicationService:33` expose un chemin de création (`roleRepository.save`), et
aucune réservation de préfixe n'existe dans `takibo-identity-core`.

## Loi du récit

Un code de rôle tenant ne peut jamais usurper une autorité de plan supérieur. Seuls les
codes réels du catalogue technique confèrent une autorité ; tout autre code est un rôle
de tenant, sans pouvoir implicite.

## Périmètre

- Purger les alias fantômes de `PolicyEvaluator.isTenantAdmin()` : ne reconnaître que les
  codes réels du catalogue technique.
- Aligner `SecurityConfig`, `BoundaryMembershipService` et `DefaultThresholdPolicy` sur
  ces mêmes codes.
- Réserver les préfixes `R_TAKIBO_`, `R_ORG_`, `R_SPACE_` et le littéral `PLATFORM` :
  interdits pour tout code de rôle tenant (`GOVERNANCE` / `BUSINESS`), à la création
  comme à l'assignation.
- Refuser toute collision entre un code tenant et un code technique existant.
- **Établir** si le chemin de création de rôle est exposé aux tenants via REST et sous
  quelle autorisation — la réponse détermine si la faille est exploitable aujourd'hui ou
  seulement latente.

## Exposition REST établie

- `RoleApplicationService` n'est injecté dans aucun contrôleur et n'a aucun appelant de
  production. Le catalogue REST (`ReadableRbacCatalogController`) n'expose que des
  lectures `GET` : il n'existe donc actuellement aucune route REST de création de rôle
  tenant. Le risque de création est **latent** sur cette version.
- L'assignation est exposée par
  `POST /api/v1/orgs/{orgCode}/spaces/{spaceCode}/users/{userId}/roles`. Elle exige un
  sujet humain situé dans la frontière du space et un code d'administrateur technique
  canonique selon `PolicyEvaluator`; les gardes applicatives vérifient ensuite la
  frontière et le compte acteur.
- Avant ce correctif, une ligne de rôle injectée par migration, accès base ou futur
  appelant de `RoleApplicationService` rendait donc la chaîne exploitable par la route
  d'assignation. Les contrôles à la création, à l'assignation et à l'évaluation ferment
  désormais ces trois points.

## Hors périmètre

- Renommer `TechnicalScope.SYSTEM` en `PLATFORM` (RBAC-01).
- Introduire le nouveau catalogue de permissions (RBAC-01).
- Modifier la matrice rôles-permissions (RBAC-02).

Ce récit est un correctif de sécurité : il doit rester petit et déployable seul, sans
attendre la doctrine v2.

## Critères d'acceptation

### AC-01 — Codes fantômes refusés

Un sujet portant `PLATFORM_ADMIN`, `R_PLATFORM_ADMIN` ou `ROLE_PLATFORM_ADMIN` sans
détenir un code réel du catalogue n'est pas reconnu comme administrateur.

### AC-02 — Code réel reconnu

Un sujet portant `R_TAKIBO_PLATFORM_ADMIN` est reconnu comme administrateur plateforme
partout où ce statut est évalué.

### AC-03 — Préfixe réservé refusé à la création

La création d'un rôle tenant dont le code commence par `R_TAKIBO_`, `R_ORG_`, `R_SPACE_`
ou contient `PLATFORM` échoue explicitement.

### AC-04 — Préfixe réservé refusé à l'assignation

Si un tel rôle existe déjà en base (donnée héritée), son assignation échoue.

### AC-05 — Chaîne d'escalade fermée

Un rôle `GOVERNANCE` nommé `PLATFORM_ADMIN`, assigné puis porté dans un token, ne confère
aucun statut d'administrateur.

## Vérifications

- `:takibo-security-management:test`
- `:takibo-identity-core:test`
- Test de non-régression dédié reproduisant la chaîne d'escalade complète.

## Branche

`security/rbac-close-role-naming-escalation`

## Commit proposé

`fix(security): close privilege escalation through tenant role naming`
