# SEC-TMS-06 — Fiabiliser le contrôle d’appartenance à l’organisation d’un Space

**Statut** : À FAIRE  
**Lot** : Durcissement TMS et sécurité  
**Priorité proposée** : haute — refus erronés et diagnostic d’incident faussé  
**Risque** : élevé — contrôle de frontière multi-tenant  
**Doctrine** : ADR 0001, IAM-31 ; articulation avec RBAC-05/06  
**Dépend de** : contrat des identifiants explicites du récit IAM-JWT-01 (lot 0)  
**Révision vérifiée** : `main@6d9b2144f36749e5664c5139415154ea332159a7`  
**Date de vérification** : 19 octobre 2026 (America/Toronto)  
**Nature de cette livraison** : récit uniquement ; aucune correction implémentée.

## 1. Récit et résultat attendu

En tant qu’acteur authentifié, je veux que le contrôle d’appartenance emploie mon identité explicite et distingue une absence réelle d’une impossibilité technique de vérifier, afin qu’une identité valide ne soit pas refusée pour une mauvaise interprétation d’identifiant et que l’exploitation voie la cause réelle.

Ce service vérifie l’appartenance à **l’organisation du Space cible**, via un User dans un Space de cette organisation. Il ne prouve pas, à lui seul, l’appartenance au Space cible ni la permission d’effectuer l’action. Réussir ce contrôle ne vaut jamais autorisation finale.

## 2. Méthode et limites de la vérification

Les sources et les tests ci-dessous ont été lus directement dans le dépôt GitHub à la révision indiquée, indépendamment des affirmations de la conversation de référence. L’arbre récursif complet ne contient pas de `AGENTS.md`. Les règles de présentation de [docs/backlog/README.md](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/docs/backlog/README.md) sont prises en compte.

Les tests ont été **inspectés, pas exécutés** : la copie locale du dépôt a échoué lors de la résolution réseau. Aucun résultat vert, incident de production, jeton réel, schéma déployé ni trace HTTP n’est revendiqué. Les reproductions ci-dessous sont des protocoles à automatiser, déduits du code.

## 3. Faits confirmés par les sources

| Réf. | Constat confirmé | Preuve |
| --- | --- | --- |
| F1 | Sans Space : `SPACE_ID_REQUIRED` ; sans authentification : `UNAUTHENTICATED`. Le rôle plateforme canonique sort avant toute recherche du Space. | [BoundaryMembershipService.java L39–47](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L39-L47) |
| F2 | Les rôles ORG_ADMIN/ORG_OWNER canoniques, avec ou sans préfixe Spring `ROLE_`, contournent ce seul contrôle si l’organisation du contexte égale l’organisation cible. | [BoundaryMembershipService.java L49–60](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L49-L60), [BoundaryMembershipService.java L286–289](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L286-L289) |
| F3 | Le service essaie successivement User, Account, puis username ; il lève `AccessDeniedException("ACTOR_NOT_IN_SPACE_ORG")` si aucun essai ne réussit. | [BoundaryMembershipService.java L62–75](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L62-L75) |
| F4 | Pour `TakiboSecurityContextCarrier`, le sujet est parsé comme User UUID ; l’extraction Account renvoie toujours null. Les attributs explicites ACCOUNT_ID et USER_ID ne sont pas lus. | [BoundaryMembershipService.java L173–201](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L173-L201), [BoundaryMembershipService.java L275–284](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L275-L284) |
| F5 | Le jeton d’authentification TAKIBO implémente effectivement ce carrier ; ce chemin est utilisé après validation et construction du contexte dans le filtre JWT. | [JwtAuthenticationToken.java L11–25](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/JwtAuthenticationToken.java#L11-L25), [JwtAuthenticationFilter.java L44–60](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/JwtAuthenticationFilter.java#L44-L60) |
| F6 | Les trois requêtes d’appartenance font un COUNT dans users joint à spaces, filtré sur l’organisation. Tout `Exception` devient false. Une erreur n’est donc pas un résultat négatif fiable ; un fallback suivant peut même réussir après cette erreur. | [BoundaryMembershipService.java L109–169](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L109-L169) |
| F7 | La résolution Space → organisation retourne vide sur absence de ligne, org_id nul **et toute autre exception**, y compris de conversion UUID. Le code appelant produit alors une ResponseStatusException 404 avec `SPACE_NOT_FOUND`. | [BoundaryMembershipService.java L80–107](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L80-L107), [BoundaryMembershipService.java L296–321](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L296-L321) |
| F8 | Le cache positif Space → organisation a un TTL de 10 secondes. Un cache chaud peut masquer une panne de résolution ; il n’y a pas de mise en cache négative dans ce code. | [BoundaryMembershipService.java L36–37](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L36-L37), [BoundaryMembershipService.java L80–105](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L80-L105) |
| F9 | La factory conserve pourtant ACCOUNT_ID et USER_ID dans les attributs. Pour un humain, elle préfère userId, puis sub, puis accountId comme subjectId. | [TakiboSecurityContextFactory.java L19–56](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/TakiboSecurityContextFactory.java#L19-L56), [TakiboSecurityContextFactory.java L118–135](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/TakiboSecurityContextFactory.java#L118-L135) |

Les branches Spring resource-server et Jwt direct lisent `userId/sub`, `accountId` et plusieurs noms de connexion, mais ne sont pas équivalentes au carrier : [BoundaryMembershipService.java L204–234](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java#L204-L234). Elles ne lisent pas les noms canoniques snake_case à cet endroit. Ce défaut de compatibilité relève du contrat IAM-JWT-01 ; il ne justifie pas d’introduire un nouveau fallback ici.

## 4. Conséquences établies et hypothèses à ne pas confondre

**Déduction déterministe du code, avec les données indiquées :** un humain ORGANIZATION sans user_id, sans rôle de bypass et dont le compte A possède une projection User U dans l’organisation O sera présenté comme userId=A. Si aucun User n’a id=A et aucun username ne vaut A, le service refuse, bien qu’une recherche par account_id=A aurait trouvé U. Le `account_id` est conservé en amont puis ignoré ici.

Ce refus au niveau du service ne signifie pas qu’un token ORGANIZATION devrait accéder à une route SPACE. Une telle route doit rester interdite sans contexte SPACE, même après réparation du contrôle.

**Confirmé structurellement :** une erreur SQL de membership peut produire le même code que la non-appartenance ; une erreur de résolution peut produire `SPACE_NOT_FOUND`. **Non établi :** que ces erreurs se sont produites en production, que le driver UUID ou les migrations sont en cause, ou que le client reçoit actuellement exactement ces codes HTTP. Le gestionnaire Sentinel existe, mais le filtre boundary n’intercepte pas ses exceptions ; sa traduction dans la chaîne réelle doit être testée.

Le fallback username peut cacher le défaut sur certains jeux de données. Aucun exploit de collision n’a été reproduit. Un UUID de client machine ne constitue pas une preuve d’identité User.

## 5. Scénarios de reproduction à automatiser

Jeu isolé : deux organisations O et X ; Spaces S1 et S2 dans O, SX dans X ; compte A, User U lié à A/S1 ; A ≠ U ; username de U = `alice` ; aucun User id=A ni username=A. Aucune autorité de bypass sauf mention. Utiliser une instance neuve du service pour les essais de résolution.

| Cas | Montage / action | Résultat actuel déduit | Attendu après correction |
| --- | --- | --- | --- |
| R1 — Account ignoré | Contexte humain ORG : subjectId=A, attribut ACCOUNT_ID=A, pas de USER_ID ; principal=A ; cibler S2. | Recherche u.id=A puis username=A ; account_id jamais interrogé ; ACTOR_NOT_IN_SPACE_ORG. | Le contrôle d’organisation reconnaît A via sa projection dans O ; la politique de la route reste indépendante. |
| R2 — Contrôle positif SPACE | Humain SPACE S1 : attributs A/U, sujet actuel U ; cibler S1. | Recherche u.id=U positive. | Pas de régression ; lier U à A et au contexte conformément au contrat IAM-JWT-01. |
| R3 — Vraie non-appartenance | Compte de X, aucune projection dans O ; cibler S1. | Refus. | Refus d’autorisation, jamais accès par identité de secours ambiguë. |
| R4 — SQL membership | Résoudre S1→O ; faire lever une DataAccessException au COUNT ; aucun fallback positif. | Exception absorbée puis ACTOR_NOT_IN_SPACE_ORG. | Échec technique distinct ; chaîne métier non poursuivie. |
| R5 — Fallback après panne | Même panne, puis simuler username positif. | Le code peut poursuivre et accepter le contrôle. | Ne pas transformer une vérification échouée en réussite via un autre nom. |
| R6 — SQL résolution | Cache froid ; faire lever une DataAccessException lors de SELECT org_id. | ResponseStatusException 404 SPACE_NOT_FOUND. | Erreur technique 5xx selon politique ci-dessous. |
| R7 — Space absent | Cache froid ; aucune ligne pour S1. | SPACE_NOT_FOUND. | Absence réelle distinguée de la panne ; 404 pour la ressource absente. |
| R8 — Cache | Résoudre S1, provoquer panne, rejouer avant puis après TTL avec horloge maîtrisée. | Cache positif utilisé avant expiration ; panne masquée en 404 après. | Sémantique du TTL documentée ; après expiration, panne technique distincte ; aucune mémorisation d’une panne comme absence. |

Compléter au niveau HTTP avec les quatre formes de chemins du filtre, dont `/api/v1/spaces/{spaceId}/...`, puis avec `spaceId` en paramètre. Vérifier le chemin effectivement sélectionné et l’absence de marqueur `TAKIBO_BOUNDARY_OK` lors d’un échec. Les règles d’exclusion et le parsing d’URL se trouvent dans [OrgBoundaryFilter.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/OrgBoundaryFilter.java). Les URL/UUID invalides et les ambiguïtés de routes sont à caractériser, sans étendre discrètement le chantier au routage complet.

## 6. Comportement cible et invariants de sécurité

1. Les identifiants sont typés et nommés : Account n’est pas User, même s’ils sont tous deux des UUID. Lire les attributs explicites validés ; ne pas deviner le type depuis subjectId.
2. Une preuve de membership reste bornée à l’organisation cible résolue. Une projection dans X ne donne rien dans O. Les incohérences A/U/O/S sont rejetées, sans essai d’un username pour les effacer.
3. Une appartenance à O via S1 peut satisfaire **ce contrôle organisationnel** pour S2 ; elle ne donne ni permission ni contexte S2. `SpaceBoundaryGuard` et les contrôles de permission restent exigés.
4. L’autorité organisationnelle sans User reste possible selon IAM-31. Ne pas exiger un User pour authentifier ou exercer une autorité ORG ; ne pas inventer un User pour une identité machine.
5. Une erreur technique arrête la décision. Aucune conversion d’exception en false, en absence, en réussite, en acteur système ou en bypass.
6. Les exceptions de données, notamment org_id nul ou UUID illisible, ne signifient pas « Space absent ». Les causes internes restent dans la télémétrie avec traceId ; pas de SQL, token brut ni stacktrace dans la réponse.
7. Les bypass canoniques actuels ne sont ni élargis ni renommés dans ce récit. Préserver les tests contre les alias fantômes. Leur évolution vers les permissions situées relève de RBAC-05/06/08.
8. Le cache ne contient que des relations résolues avec succès. Le contrôle de membership n’est pas mis en cache par ce récit ; le TTL positif existant doit être caractérisé sans promettre une révocation immédiate.

**Contrat d’erreur proposé pour l’implémentation future :** non-appartenance prouvée → refus 403 ; Space réellement absent → 404 ; indisponibilité temporaire de la base → 503 générique ; erreur SQL structurelle, mapping ou donnée incohérente → 500 générique. Employer les conventions Sentinel existantes et figer le code public exact au lot 0. Conserver la cause technique en interne. Il s’agit d’une cible de backlog, pas d’un comportement HTTP déjà observé.

## 7. Tests existants et manque de couverture

[BoundaryMembershipServiceTest.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/test/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipServiceTest.java) teste les bypass ORG canoniques, le mauvais org pour ORG_OWNER, les bypass plateforme et le refus des alias non canoniques. Le contexte simulé porte directement USER_ID comme sujet ; ces tests ne traversent ni signeur, ni normalisation, ni factory.

Ils ne couvrent pas le compte conservé dans les attributs, les erreurs SQL, l’absence réelle de Space, la différence cache froid/chaud, ni le contrat HTTP du filtre. [TakiboSecurityContextFactoryTest.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/test/java/com/takibo/securitymanagement/infrastructure/security/TakiboSecurityContextFactoryTest.java) vérifie la conservation des attributs, mais ne les fait pas consommer par le service. Aucun succès d’exécution de ces tests n’est attesté ici.

## 8. Critères d’acceptation

- [ ] AC-01 : R1 ne produit plus de faux refus d’organisation ; un test prouve que l’Account explicite sert à la recherche, avec A différent de U.
- [ ] AC-02 : R2 et R3 couvrent succès et refus ; un A/U incohérent est refusé sans secours par username.
- [ ] AC-03 : chaque requête de membership est testée avec panne injectée ; aucune panne ne devient ACTOR_NOT_IN_SPACE_ORG ni autorisation via fallback.
- [ ] AC-04 : absence réelle de Space, panne, org_id nul et conversion UUID invalide sont distingués ; seuls les vrais absents reçoivent SPACE_NOT_FOUND.
- [ ] AC-05 : réponses HTTP conformes au contrat d’erreur approuvé, sans détails internes ; traceId et cause distincte exploitables côté serveur.
- [ ] AC-06 : chaîne interrompue et marqueur boundary absent lors d’un échec ; test à travers la chaîne de filtres réellement configurée.
- [ ] AC-07 : tests des bypass canoniques et des alias refusés maintenus ; mauvais org, mauvais Space et permission absente restent refusés par les gardes correspondantes.
- [ ] AC-08 : token ORG toujours refusé sur route SPACE ; compte avec User dans un autre Space du même org n’obtient pas automatiquement accès au Space cible.
- [ ] AC-09 : cache testé avant/après TTL et sur erreur ; aucune panne stockée comme absence ; limites de fraîcheur documentées.
- [ ] AC-10 : test PostgreSQL avec migrations réelles pour les UUID/jointures, plus tests unitaires et HTTP ; preuves d’exécution attachées lors de l’implémentation.

## 9. Périmètre, exclusions et lots futurs

**Inclus :** extraction d’identité dans le service, requêtes et classification des résultats, propagation/traduction des erreurs nécessaires au filtre, cache existant, tests ciblés. Le contrat d’identité et la compatibilité des autres formes d’Authentication sont définis par [IAM-JWT-01](IAM-JWT-01-aligner-identite-jwt-account-user.md).

**Exclus :** changement du JWT émis par TAS, suppression de user_id, migration Account/User, refonte RBAC, nouveau bypass, ouverture d’une route ORG avec token SPACE ou inversement, modification du login, déploiement et correction de données de production.

| Lot | Contenu et preuve de sortie |
| --- | --- |
| 0 — Caractériser | Figer le contrat avec IAM-JWT-01 ; tests R1–R8 reproduisant les différences, décision sur codes publics. |
| 1 — Identité explicite | Consommer A/U séparément et vérifier leur cohérence ; aucune déduction User depuis un Account ; tests positifs/négatifs. |
| 2 — Erreurs fiables | Distinguer absence et erreur, arrêter les fallbacks sur panne, assurer traduction Sentinel dans la chaîne effective. |
| 3 — Intégration | PostgreSQL, chaîne HTTP, cache, garde SPACE et non-régression des rôles ; rapport de preuves et limites restantes. |

Ces lots décrivent des travaux à venir. Ils ne sont ni implémentés ni autorisés à être fusionnés par cette fiche. Clôturer uniquement après preuves des AC, puis appliquer la règle de statut/déplacement du backlog.
