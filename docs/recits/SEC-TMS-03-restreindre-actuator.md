# SEC-TMS-03 — Restreindre Actuator

## Contexte

La chaîne Spring Security autorise publiquement `/actuator/**`. La configuration web
expose `health`, `info`, `beans`, `conditions`, `metrics`, `env` et `loggers`, tandis
que `management.endpoint.health.show-details=always` publie les composants internes
de santé à tout appelant.

Cette surface révèle la topologie applicative et la configuration opérationnelle. Elle
permet également d'atteindre des endpoints mutables comme `loggers` sans rôle.

## Loi du récit

Actuator est une surface d'exploitation plateforme. Seules les sondes de santé
strictement nécessaires sont publiques et ne révèlent aucun détail interne. Tous les
autres endpoints exigent une autorité d'administration plateforme.

## Matrice d'accès

| Route | Méthode | Accès |
|---|---|---|
| `/actuator/health` | GET | public, statut agrégé uniquement |
| `/actuator/health/liveness` | GET | public, sans détails internes |
| `/actuator/health/readiness` | GET | public, sans détails internes |
| `/actuator/**` | toute autre paire route/méthode | administration plateforme |

La seule autorité acceptée est le rôle canonique `R_TAKIBO_PLATFORM_ADMIN`. Les alias
historiques `R_PLATFORM_ADMIN` et `PLATFORM_ADMIN` ont été retirés par RBAC-00. Les
diagnostics restent exposés pour l'exploitation, mais ne sont plus publics.

## Périmètre

- Retirer `/actuator/**` de la liste publique globale.
- Autoriser explicitement les trois sondes GET.
- Réserver le reste d'Actuator aux autorités plateforme.
- Remplacer les détails de santé permanents par `when-authorized`.
- Limiter l'autorisation des détails au rôle plateforme.
- Retirer les endpoints Actuator de la documentation OpenAPI publique.
- Ajouter des tests HTTP traversant la vraie chaîne Spring Security.

## Hors périmètre

- Déplacer Actuator sur un port de management séparé.
- Ajouter une authentification réseau ou mTLS dédiée à l'exploitation.
- Modifier la liste des diagnostics disponibles pour les administrateurs plateforme.
- Changer les indicateurs de santé métier.

## Critères d'acceptation

### AC-01 — Santé publique minimale

Un appel anonyme à `/actuator/health` reçoit le statut de santé agrégé sans champ
`components`.

### AC-02 — Diagnostics privés

Un appel anonyme à `/actuator`, `/actuator/info` ou `/actuator/env` reçoit 401.

### AC-03 — Rôles tenant insuffisants

Un `R_ORG_ADMIN` authentifié reçoit 403 sur les diagnostics Actuator.

### AC-04 — Administration plateforme préservée

Un `R_TAKIBO_PLATFORM_ADMIN` authentifié accède aux diagnostics exposés et aux détails
de santé.

### AC-05 — Documentation publique réduite

Springdoc n'inclut plus les endpoints Actuator dans l'OpenAPI public.

## Vérifications

- Test HTTP `ActuatorSecurityIntegrationTest`.
- Suite complète `:takibo-security-management:test`.
- Suite complète `:takibo-iam-boot:test`.
- Compilation des modules concernés.

## Branche

`security/restrict-actuator`

## Commit proposé

`fix(security): restrict actuator endpoints`
