# IAM-JWT-01 — Aligner l’identité JWT, le contexte TAKIBO et les contrôles d’appartenance

**Statut** : À FAIRE  
**Lot** : Identité et cohérence des jetons  
**Priorité proposée** : haute — ambiguïté entre principal et projection locale  
**Risque** : élevé — identité, autorisation, audit et profils ADP  
**Doctrine** : ADR 0001 ; continuité d’IAM-31 ; articulation RBAC-04/05/06  
**Dépend de** : aucune implémentation préalable ; lot de contrat partagé avec SEC-TMS-06  
**Révision vérifiée** : `main@6d9b2144f36749e5664c5139415154ea332159a7`  
**Date de vérification** : 19 octobre 2026 (America/Toronto)  
**Nature de cette livraison** : récit uniquement ; aucune correction implémentée.

## 1. Récit et loi d’identité

En tant que responsable de l’identité TAKIBO, je veux qu’un humain conserve la même identité de compte entre ses jetons ORGANIZATION et SPACE, tout en portant séparément sa projection User locale, afin que ni le contrôle d’appartenance ni les consommateurs du contexte ne confondent deux types d’identifiants.

**Account identifie le principal ; User situe sa projection dans un Space.** Aucun des deux n’est abandonné dans les sources vérifiées. Le retrait du user_id du token ORGANIZATION ne constitue pas le retrait du concept User ni de son claim dans un token SPACE.

## 2. Sources vérifiées et limites

Les fichiers source et tests ont été lus directement via GitHub, à la révision ci-dessus. La conversation de référence sert de piste, sans constituer une preuve. L’arbre récursif complet a été inspecté ; aucun AGENTS.md n’y figure.

Les tests ont été **lus, pas exécutés** : le clonage local a échoué sur la résolution réseau. Aucun résultat de CI ni comportement de production n’est affirmé. Les scénarios décrits sont des déductions et protocoles de validation ; les hypothèses restent nommées comme telles.

## 3. Doctrine réellement écrite

[0001-separation-account-user-identitylink.md](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/docs/adr/0001-separation-account-user-identitylink.md) définit Account comme principal stable et authentifiable, User comme projection d’un Account dans un Space, IdentityLink comme lien d’identité externe vers Account. Il précise que les tokens sont ancrés sur account_id, que space_id est un contexte et qu’Account n’est pas un conteneur de droits. Il permet un Account sans User.

L’ADR ne spécifie pas à lui seul toutes les règles de `SubjectIdentity.subjectId`, la grammaire des claims, ni leur validation. La cible de contexte ci-dessous est donc **une formalisation proposée**, cohérente avec l’ancrage Account, à consigner lors du lot 0.

[IAM-31-authentification-humaine-organisationnelle.md](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/docs/terminer/IAM-31-authentification-humaine-organisationnelle.md) confirme explicitement pour l’humain ORGANIZATION : sub=accountId, account_id et org_id présents, space_id et user_id absents. Il maintient les tokens SPACE. Le schéma initial et IAM-31 décrivent par ailleurs des Accounts org-scopés, alors que l’ADR emploie « principal global » : ce récit ne tranche pas ce décalage par une migration du modèle. « Stable » signifie ici stable à travers les contextes d’un même compte, sans promettre une identité partagée entre organisations.

## 4. Trajet réel des identifiants

| Étape | Comportement confirmé | Source |
| --- | --- | --- |
| TAS humain | HumanTokenSigner signe sub=accountId et account_id=accountId dans les deux formes. SPACE exige org/account/space/user ; ORGANIZATION exige org/account et interdit user sans space. | [HumanTokenSigner.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-authorization-server/src/main/java/com/takibo/authorizationserver/infrastructure/springauthserver/token/HumanTokenSigner.java) |
| Vocabulaire signé | account_id, user_id, org_id, space_id, subject_type, auth_method, takibo_scope_level sont les noms canoniques. | [TakiboTokenClaims.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-authorization-server/src/main/java/com/takibo/authorizationserver/infrastructure/springauthserver/token/TakiboTokenClaims.java) |
| Validation / normalisation | TasJwtTokenValidatorAdapter décode, conserve sub et traduit les claims canoniques en accountId, userId, orgId, spaceId, subjectType, authMethod, scopeLevel pour le consommateur interne. | [TasJwtTokenValidatorAdapter.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/token/TasJwtTokenValidatorAdapter.java) |
| Factory humain | Lit les clés internes ; subjectId = premier non vide de userId, sub, accountId. Les attributs ACCOUNT_ID et USER_ID sont aussi conservés séparément. | [TakiboSecurityContextFactory.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/TakiboSecurityContextFactory.java) |
| Nature du sujet | subjectType explicite décide HUMAN ; à défaut, présence d’accountId sert de compatibilité. Non humain : sub obligatoire, nature SERVICE. | [TakiboSecurityContextFactory.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/TakiboSecurityContextFactory.java) |
| Authentication | JwtAuthenticationFilter valide, appelle la factory, calcule le principal et construit le JwtAuthenticationToken TAKIBO, qui implémente le carrier. | [JwtAuthenticationFilter.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/JwtAuthenticationFilter.java), [JwtAuthenticationToken.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/JwtAuthenticationToken.java) |
| Nom du principal | PrincipalNameFactory préfère preferred_username, username, upn, puis subjectId, puis accountId. Le nom n’est donc pas un contrat User ID. | [PrincipalNameFactory.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/PrincipalNameFactory.java) |
| Boundary | La branche carrier interprète subjectId comme UUID User et renvoie toujours accountId=null ; elle ignore les attributs d’identité pourtant présents. | [BoundaryMembershipService.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipService.java) |

La différence snake_case/camelCase n’est **pas**, dans le trajet TAS configuré par défaut, une perte prouvée : l’adaptateur réalise bien la traduction. En revanche, les branches alternatives Spring JwtAuthenticationToken et Jwt direct du service lisent les clés camelCase sans normalisation locale. Sur un Jwt TAS brut, account_id/user_id peuvent donc être ignorés et sub être essayé comme User. Il faut tester et préciser les formes d’Authentication effectivement supportées.

### Contradiction visible avec des identifiants distincts

Pour un même compte A et deux projections U1/S1 et U2/S2 :

| Forme humaine | sub signé | account_id signé | user_id signé | subjectId actuel |
| --- | --- | --- | --- | --- |
| ORGANIZATION | A | A | absent | A |
| SPACE S1 | A | A | U1 | U1 |
| SPACE S2 | A | A | U2 | U2 |

Le signeur reste ancré sur Account ; le sujet interne change à la présence du User. Ce changement est établi par le code et par les assertions actuelles de la factory. L’existence d’un incident de production imputable à ce changement reste à démontrer.

## 5. Validation actuelle et angles morts confirmés

- ClaimReader accepte UUID ou chaîne convertible ; une chaîne UUID mal formée lève une exception. Les branches legacy du service utilisent au contraire un parsing tolérant qui peut revenir à sub. Ces comportements doivent avoir un contrat commun.
- La factory ne compare pas sub à accountId et ne vérifie pas les relations User–Account–Space–Org. Un token de test ayant userId=U et sub/accountId divergents peut être transformé en contexte sans rejet par cette méthode. Cela ne prouve pas qu’un attaquant puisse obtenir un tel jeton signé.
- Le signeur vérifie la forme de sa commande et les préfixes de permissions ; il ne consulte aucune identité en base. La validité des relations est du ressort du producteur TIS-CORE et des gardes concernées.
- Les noms de connexion ne sont pas recopiés par TasJwtTokenValidatorAdapter. Dans le trajet humain canonique du signeur, ils ne sont de toute façon pas émis. Le secours par username du boundary ne constitue donc pas une solution au contrat Account/User.

Preuves : [ClaimReader.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/ClaimReader.java), [TakiboSecurityContextFactory.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/TakiboSecurityContextFactory.java), [HumanTokenSigner.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-authorization-server/src/main/java/com/takibo/authorizationserver/infrastructure/springauthserver/token/HumanTokenSigner.java), [TasJwtTokenValidatorAdapter.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/token/TasJwtTokenValidatorAdapter.java). Les conséquences éventuelles sur tokens externes et anciens tokens restent à inventorier avant suppression de compatibilité.

## 6. Contrat cible proposé et invariants

Le lot 0 doit publier ce contrat et son plan de compatibilité avant tout changement du sens de subjectId.

| Élément | Humain ORGANIZATION | Humain SPACE | Machine client_credentials |
| --- | --- | --- | --- |
| sub | A non vide | même A | identité du client selon son contrat |
| account_id | A, égal à sub | même A, égal à sub | pas d’Account humain fabriqué |
| user_id | absent | U obligatoire, lié à A/O/S | pas de User humain inféré |
| org_id | O obligatoire | O obligatoire | selon le plan client déclaré |
| space_id | absent | S obligatoire | selon le plan client déclaré |
| subjectId du contexte, cible proposée | A | A | sujet machine explicite |
| Attributs d’identité | ACCOUNT_ID=A ; USER_ID absent | ACCOUNT_ID=A ; USER_ID=U | uniquement les attributs effectivement garantis par ce profil |

1. La stabilité de sub et du sujet humain n’efface pas User. Le User local peut changer lors du changement de Space ; Account reste identique.
2. La possession de A n’accorde aucun droit. Une route SPACE exige la bonne frontière et la permission située ; une route ORG n’exige pas artificiellement un User.
3. Un UUID ne révèle pas son type. Ne jamais convertir un sub client en User ni utiliser A comme U pour remplir un champ manquant.
4. Les relations User–Account–Space–Org sont contrôlées à une frontière explicite et testable. Une incohérence doit être rejetée avant l’action ; aucun fallback vers username, sub ou acteur système ne l’efface.
5. Les noms de claims signés sont canoniques ; la normalisation interne est unique et documentée. Des alias contradictoires sont rejetés selon une règle figée, pas départagés silencieusement.
6. Une identité manquante/malformée est traitée sans accès ; le contrat HTTP distingue jeton invalide, refus de frontière et panne technique sans divulguer de détails.
7. La validation cryptographique, l’émetteur et l’expiration restent exigés. Les fixtures signées incohérentes appartiennent aux tests, sans affaiblir les contrôles du décodeur.
8. Le profil CLIENT_APP reste distinct. Le customizer n’ajoute subject_type=CLIENT_APP que pour client_credentials ; ne pas étendre aveuglément l’égalité sub=account_id à tous les grants OAuth2. Voir [TakiboOAuth2TokenCustomizer.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-authorization-server/src/main/java/com/takibo/authorizationserver/infrastructure/springauthserver/token/TakiboOAuth2TokenCustomizer.java).
9. Les anciens jetons ne sont ni acceptés indéfiniment par heuristique ni invalidés sans plan : fixer les profils autorisés, la durée de transition, le renouvellement et l’observabilité. Tout token ambigu hors profil explicite est refusé.

## 7. Consommateurs à traiter ensemble

Changer seulement la priorité dans la factory serait incomplet. La lecture des consommateurs suivants confirme des dépendances au sujet :

| Consommateur inspecté | Dépendance réelle | Travail requis avant bascule |
| --- | --- | --- |
| BoundaryMembershipService | subjectId essayé comme User ; Account ignoré | Lire A/U explicitement ; lot de correction porté par SEC-TMS-06. |
| CurrentActorProviderImpl | currentUserId lit USER_ID puis retombe sur subjectId ; currentAccountId délègue au port Account | Interdire qu’un token ORG sans User retourne A comme User ; tests sans fallback ambigu. |
| SpringAuditActorProvider | Lit A/U séparément mais utilise subjectId comme Account si A absent | Fixer l’identité de l’audit et la compatibilité ; ne pas enregistrer un User comme Account. |
| TakibuAuditUserContextFilter | Déprécié, mais encore composant ; getUserId retourne notamment subjectId | Documenter/remplacer la sémantique de ce champ ; ne pas renommer silencieusement les identifiants historiques. |
| AdpContextEnricher | Utilise subjectId pour la requête ADP et la vélocité | Évaluer les clés de profil et de compteur lors du passage U→A ; documenter partitionnement, transition et retour arrière. |
| PrincipalNameFactory | Peut retourner subjectId comme principal affiché | Éviter de traiter ce nom comme preuve d’identité locale. |

Sources : [CurrentActorProviderImpl.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-management-service/src/main/java/com/takibo/managementservice/infrastructure/security/CurrentActorProviderImpl.java), [SpringAuditActorProvider.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/audit/SpringAuditActorProvider.java), [TakibuAuditUserContextFilter.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/audit/TakibuAuditUserContextFilter.java), [AdpContextEnricher.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-adp-spring/src/main/java/com/takibo/adp/spring/adapter/AdpContextEnricher.java), [PrincipalNameFactory.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/main/java/com/takibo/securitymanagement/infrastructure/security/PrincipalNameFactory.java).

Cet inventaire est un point de départ fondé sur les consommateurs lus, pas une preuve d’exhaustivité de tous les modules. Les conséquences sur les données ADP/audit existantes ne sont pas établies ; leur analyse est une condition de bascule.

## 8. Reproductions et matrice de tests

Fixtures isolées : O, X ; A et B distincts ; U1 lié à A/O/S1, U2 lié à A/O/S2, UB lié à B/X/SX. A, B, U1, U2 et UB ont des UUID distincts. Éviter un jeu où userId=accountId, qui masquerait le défaut.

| Cas | Protocole | Actuel établi ou déduit | Attendu cible |
| --- | --- | --- | --- |
| J1 | Signer humain SPACE A/U1 ; décoder, normaliser puis créer contexte. | sub=A ; attribut A ; sujet=U1. | sujet=A ; attribut User=U1 ; preuve du trajet complet. |
| J2 | Signer ORGANIZATION A sans User ; même trajet. | sujet=A ; USER_ID absent. | Identité stable ; currentUserId ne retourne jamais A comme User. |
| J3 | Signer pour A/U1/S1 puis A/U2/S2. | Sujet interne passe de U1 à U2. | Sujet humain stable A ; contexte et User passent correctement de S1/U1 à S2/U2. |
| J4 | Claims normalisés humains sub=B, accountId=A, userId=U1. | La factory ne vérifie pas l’égalité et choisit U1. | Rejet explicite avant action, sans secours. |
| J5 | Jeton de test signé avec U=UB mais A/O/S1, puis avec U1 et S2. | La factory ne vérifie pas ces relations ; le boundary vérifie seulement l’org via son identifiant choisi. | Rejet pour relation incohérente au point contractuellement désigné. |
| J6 | Account/User UUID malformés, champs manquants, alias contradictoires. | Parsing strict factory ou tolérant legacy, selon chemin. | Politique uniforme par profil ; aucune identité de substitution. |
| J7 | Client sub textuel puis sub UUID égal par hasard à U1, sans identité humaine. | Nature SERVICE ; le boundary peut quand même essayer le sujet comme User UUID. | Aucune appartenance humaine déduite d’une collision ; politique machine explicitement testée. |
| J8 | Même token TAS fourni via carrier, JwtAuthenticationToken Spring et Jwt direct, si ces trois formes sont conservées. | Lectures de clés différentes ; normalisation non garantie dans les branches alternatives. | Même sens d’identité et mêmes refus, ou forme non supportée explicitement rejetée. |
| J9 | Token ORG A sur route SPACE ; token S1 sur S2 ; permission absente avec bonne frontière. | Résultat HTTP à rejouer. | Refus dans les trois cas, sans élargissement par la réparation de membership. |

J1–J3 doivent traverser **signeur réel → JWT signé → décodeur configuré → adaptateur → filtre/factory → Authentication → boundary et garde de route**. Une Map seule ne suffit pas à prouver le contrat de bout en bout. Les tests malformés peuvent utiliser un signeur de fixture de confiance ; ils ne doivent pas exiger du signeur de production qu’il émette des formes qu’il refuse déjà.

## 9. Tests existants réellement inspectés

| Test | Ce qu’il affirme aujourd’hui | Limite pertinente |
| --- | --- | --- |
| [HumanTokenSignerTest.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-authorization-server/src/test/java/com/takibo/authorizationserver/infrastructure/springauthserver/token/HumanTokenSignerTest.java) | sub=Account dans les deux formes ; SPACE porte User ; ORG ne porte pas Space/User ; refus des commandes incomplètes et des permissions/sources incompatibles. | Ne teste pas la consommation par la factory/boundary. |
| [TasJwtTokenValidatorAdapterTest.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/test/java/com/takibo/securitymanagement/infrastructure/token/TasJwtTokenValidatorAdapterTest.java) | Normalisation des claims humains, préservation des pouvoirs, profil plateforme sans identité humaine. | Décodeur simulé ; pas de relation A/U ni parcours signé complet. |
| [TakiboSecurityContextFactoryTest.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/test/java/com/takibo/securitymanagement/infrastructure/security/TakiboSecurityContextFactoryTest.java) | Humain avec User → subjectId=User ; fallback Account ; attributs A/U ; SERVICE et sujet manquant. | Verrouille le sens actuel à faire évoluer ; plusieurs fixtures n’ont ni sub ni contexte complet. |
| [BoundaryMembershipServiceTest.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-security-management/src/test/java/com/takibo/securitymanagement/infrastructure/security/boundary/BoundaryMembershipServiceTest.java) | Bypass et refus des alias de rôle. | Pas de parcours réel de claims ; pas d’Account explicite consommé. |
| [HumanLoginBaselineIntegrationTest.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-iam-boot/src/test/java/com/takibo/iamboot/tas/HumanLoginBaselineIntegrationTest.java) | Login HTTP ORG, token décodé sub=Account, absence de User/Space et refus uniformes. | Conditionné à Docker ; ne traverse pas ensuite le contrôle de membership. |
| [SituatedHumanTokenPipelineIntegrationTest.java](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/takibo-iam-boot/src/test/java/com/takibo/iamboot/security/SituatedHumanTokenPipelineIntegrationTest.java) | Deux Spaces, permissions recalculées puis claims capturés par l’encodeur. | Encodeur de capture ; pas de validation JWT ni filtres boundary. |

Ces assertions ont été vérifiées dans les sources ; leur succès d’exécution à cette révision n’a pas été vérifié pendant cette rédaction.

## 10. Critères d’acceptation

- [ ] AC-01 : contrat signé/interne et tableau des profils approuvés au lot 0 ; distinction Account/User et compatibilité documentées, sans annoncer user_id abandonné.
- [ ] AC-02 : J1–J3 passent ; sub et sujet humain restent A entre ORG et deux Spaces, User local change explicitement.
- [ ] AC-03 : forme ORG sans User/Space et forme SPACE complète préservées ; pas de User inventé pour un Account sans projection.
- [ ] AC-04 : J4–J6 refusés avec contrat d’erreur public stable ; les relations A/U/O/S sont vérifiées à un point identifié et couvert.
- [ ] AC-05 : J7 prouve la séparation humain/machine, y compris avec sub UUID ; aucun gain de droits par collision.
- [ ] AC-06 : J8 tranche les formes supportées et les noms de claims ; aliases contradictoires et vieux profils couverts par des tests de compatibilité ou de refus.
- [ ] AC-07 : J9 et les gardes strictes restent verts ; la réussite du membership ne vaut ni permission ni accès à un autre Space.
- [ ] AC-08 : currentUserId sur ORG échoue explicitement ; currentAccountId, audit et consommateurs ADP ont une sémantique stable et des tests adaptés.
- [ ] AC-09 : effets sur profils, compteurs et audit existants documentés ; transition et retour arrière sans mélange Account/User.
- [ ] AC-10 : au moins un test d’intégration signé couvre toute la chaîne ; les tests du signeur, du mapping, de la factory et du boundary restent cohérents.
- [ ] AC-11 : aucune régression de validation JWT, d’isolation tenant, de révocation existante ni du login uniforme ; rapport d’exécution joint à l’implémentation future.

## 11. Périmètre, exclusions et lots futurs

**Inclus :** contrat d’identité, normalisation, factory, typage des attributs, consommateurs directement impactés, tests de cohérence et compatibilité. **SEC-TMS-06** possède la correction du service et la classification des erreurs SQL ; ce récit possède le sens de A/U/sub et la cohérence des producteurs/consommateurs. Ne pas réaliser deux corrections concurrentes du même service.

**Exclus :** suppression d’Account, User ou IdentityLink ; fusion d’identités inter-organisations ; réécriture de l’historique d’audit ; migration massive de profils ; nouveaux grants OAuth2 ; MFA, BFF et UI ; refonte RBAC ; changement de la clé ou de l’algorithme de signature ; déploiement ou fusion.

| Lot | Contenu et preuve de sortie |
| --- | --- |
| 0 — Contrat commun | Formaliser sujet humain stable, profils canoniques/legacy, relations à vérifier, responsabilités TIS/TAS/TSM ; consigner l’interprétation de l’ADR et les impacts ; partager avec SEC-TMS-06. |
| 1 — Caractérisation | Automatiser J1–J8 et inventorier les lecteurs de subjectId, User et Account ; produire une matrice avant/après et les décisions de compatibilité. |
| 2 — Cohérence interne | Adapter factory et consommateurs, coordonner avec le lot identité de SEC-TMS-06 ; préserver les claims TAS déjà cohérents, sans modification cosmétique du signeur. |
| 3 — Preuve et transition | Test signé de bout en bout, PostgreSQL et chaîne HTTP ; J9 ; analyse audit/ADP, renouvellement des anciens jetons et stratégie de retour arrière. |

Vérifications prévues pour les deux récits : tests Gradle de takibo-security-management, takibo-authorization-server, takibo-identity-core, takibo-management-service et takibo-iam-boot ; élargir aux modules ADP/audit directement modifiés. Java 21 est exigé par le build ; les tests PostgreSQL d’intégration requièrent leur environnement Docker. Un test ignoré faute de Docker ne constitue pas une preuve de réussite.

Aucun lot n’est implémenté par cette livraison documentaire. Clôturer uniquement après validation des critères, puis appliquer [docs/backlog/README.md](https://github.com/kadt2022/takibo-iam/blob/6d9b2144f36749e5664c5139415154ea332159a7/docs/backlog/README.md).
