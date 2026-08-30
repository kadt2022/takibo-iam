-- ════════════════════════════════════════════════════════════════════════
-- TAS-GRANTS-02 — Frontières et sujets réels de l'autorisation et du consentement OAuth2
-- ════════════════════════════════════════════════════════════════════════
--
-- Décision. Le schéma d'origine (V202601091233) porte quatre défauts que ce récit doit
-- corriger avant qu'un service persistant n'écrive dans ces tables :
--
--   1. org_id et space_id sont NOT NULL sur les deux tables, ce qu'aucun client PLATFORM
--      (postman-client, sans organisation ni space par construction) ne peut jamais
--      satisfaire — la même situation que tas_signing_keys avant V202608270001.
--   2. principal_account_id est NOT NULL sur oauth2_authorization, ce qu'un client_credentials
--      ne peut pas satisfaire : le principal y est le client lui-même
--      (OAuth2ClientCredentialsAuthenticationProvider appelle
--      .principalName(clientPrincipal.getName()), jamais un compte humain), et un device code
--      non encore approuvé n'a pas non plus de compte à ce stade.
--   3. fk_oauth2_authz_client_scope et fk_oauth2_consent_client_scope référencent
--      oauth2_clients(org_id, space_id, client_id) — le client_id PUBLIC — alors que
--      registered_client_id doit porter l'identifiant technique stable de
--      RegisteredClient.id. Cette FK exclurait de plus toute autorisation pour postman-client,
--      qui n'a aucune ligne dans oauth2_clients : sa source est
--      InMemoryPlatformOAuthClientResolver, pas la table TMS.
--   4. Les index d'unicité des hash sont bornés à (org_id, space_id, ...), alors que
--      findByToken(...) n'a aucun paramètre de tenant et doit retrouver un token par son seul
--      hash. Pire : comme pour tas_signing_keys avant V202608270001, PostgreSQL traite deux
--      NULL comme distincts — une fois org_id/space_id nullifiés pour PLATFORM (point 1), cet
--      index scopé n'empêcherait plus deux lignes PLATFORM de partager le même hash de token.
--
-- Ce que ce récit ne fait pas ici : ni les colonnes _value chiffrées (ajoutées par la tranche
-- qui les consomme, avec le port de TAS-GRANTS-02A), ni le bean de service qui écrira dans ces
-- tables. Cette migration ne fait que rendre le schéma capable d'accueillir une ligne PLATFORM,
-- une ligne client_credentials sans compte, et une recherche par hash globale — sans quoi
-- toute tentative d'écriture réelle échouerait avant même d'atteindre le chiffrement.

-- ------------------------------------------------------------------------
-- Garde : aucun service ne persiste encore ici (TAS-GRANTS-00/01). Une ligne existante
-- signalerait un état inattendu que cette migration ne sait pas corriger silencieusement.
-- ------------------------------------------------------------------------
DO $$
DECLARE
    authz_rows  INTEGER;
    consent_rows INTEGER;
BEGIN
    SELECT COUNT(*) INTO authz_rows FROM oauth2_authorization;
    SELECT COUNT(*) INTO consent_rows FROM oauth2_authorization_consent;

    IF authz_rows > 0 OR consent_rows > 0 THEN
        RAISE EXCEPTION
            'MIGRATION BLOCKED: oauth2_authorization has % row(s), oauth2_authorization_consent '
            'has % row(s). Cette migration ajoute des colonnes NOT NULL sans defaut, ce qui '
            'suppose ces tables vides (aucun service ne les alimente avant TAS-GRANTS-02) ; '
            'une ligne existante doit etre comprise avant de continuer.',
            authz_rows, consent_rows;
    END IF;
END $$;

-- ════════════════════════════════════════════════════════════════════════
-- 1) oauth2_authorization
-- ════════════════════════════════════════════════════════════════════════

-- ---- org_id / space_id : NULL = PLATFORM, org sans space = ORGANIZATION, les deux = SPACE ----

ALTER TABLE oauth2_authorization
  ALTER COLUMN org_id DROP NOT NULL,
  ALTER COLUMN space_id DROP NOT NULL;

ALTER TABLE oauth2_authorization
  ADD CONSTRAINT chk_oauth2_authz_space_requires_org
    CHECK (space_id IS NULL OR org_id IS NOT NULL);

COMMENT ON COLUMN oauth2_authorization.org_id IS
  'NULL = autorisation PLATFORM (postman-client). Non NULL sans space_id = ORGANIZATION. '
  'Les deux presents = SPACE. Meme vocabulaire que ResolvedOAuthClient.plan().';
COMMENT ON COLUMN oauth2_authorization.space_id IS
  'NULL pour PLATFORM et ORGANIZATION ; requis pour SPACE. Ne peut jamais etre renseigne '
  'sans org_id (chk_oauth2_authz_space_requires_org).';

-- ---- Sujet explicite : CLIENT_APP (client_credentials) ou HUMAN ----

ALTER TABLE oauth2_authorization
  ADD COLUMN subject_type VARCHAR(20) NOT NULL DEFAULT 'HUMAN',
  ADD COLUMN principal_name VARCHAR(255) NOT NULL DEFAULT '';

-- Le DEFAULT ci-dessus n'existe que pour satisfaire la syntaxe ADD COLUMN sur une table que
-- la garde plus haut a deja verifiee vide : aucune ligne ne portera jamais cette valeur par
-- defaut en pratique. On la retire immediatement pour qu'un futur INSERT omettant ces colonnes
-- echoue au lieu de mentir silencieusement sur le sujet.
ALTER TABLE oauth2_authorization
  ALTER COLUMN subject_type DROP DEFAULT,
  ALTER COLUMN principal_name DROP DEFAULT;

ALTER TABLE oauth2_authorization
  ADD CONSTRAINT chk_oauth2_authz_subject_type
    CHECK (subject_type IN ('CLIENT_APP', 'HUMAN'));

-- principal_account_id nullable : un client_credentials n'a pas de compte humain (le principal
-- EST le client), et un device code pas encore approuve n'en a pas non plus a ce stade.
ALTER TABLE oauth2_authorization
  ALTER COLUMN principal_account_id DROP NOT NULL;

ALTER TABLE oauth2_authorization
  ADD CONSTRAINT chk_oauth2_authz_client_app_has_no_account
    CHECK (subject_type <> 'CLIENT_APP' OR principal_account_id IS NULL);

COMMENT ON COLUMN oauth2_authorization.subject_type IS
  'CLIENT_APP (client_credentials : le principal est le client lui-meme, jamais de compte) '
  'ou HUMAN (authorization_code, device, refresh issus d''un login).';
COMMENT ON COLUMN oauth2_authorization.principal_name IS
  'OAuth2Authorization.getPrincipalName() tel quel : le client_id pour CLIENT_APP, '
  'l''identifiant du compte authentifie pour HUMAN.';
COMMENT ON COLUMN oauth2_authorization.principal_account_id IS
  'NULL pour CLIENT_APP (aucun compte) et pour un device code non encore approuve. Requis '
  'des qu''un principal HUMAN est etabli.';

-- ---- registered_client_id : identifiant technique, plus jamais lie au client_id public ----

ALTER TABLE oauth2_authorization
  DROP CONSTRAINT IF EXISTS fk_oauth2_authz_client_scope;

COMMENT ON COLUMN oauth2_authorization.registered_client_id IS
  'RegisteredClient.id (identifiant technique stable), jamais le client_id public. Aucune FK '
  'vers oauth2_clients : postman-client (source PLATFORM in-memory, TAS-GRANTS-01) n''y a '
  'aucune ligne. La resolvabilite est verifiee a la lecture par ResolvedOAuthClientResolver, '
  'pas par une contrainte SQL. Remplace fk_oauth2_authz_client_scope, qui referencait a tort '
  'le client_id public.';

-- ---- Unicite des hash : globale, jamais bornee au tenant ----
--
-- findByToken(...) n'a aucun parametre de tenant ; et comme pour tas_signing_keys avant
-- V202608270001, un index scope par (org_id, space_id, hash) laisserait deux lignes PLATFORM
-- (org_id NULL, space_id NULL) partager le meme hash sans qu'aucune contrainte ne le remarque.

DROP INDEX IF EXISTS uk_oauth2_authz_code_hash;
DROP INDEX IF EXISTS uk_oauth2_authz_access_hash;
DROP INDEX IF EXISTS uk_oauth2_authz_refresh_hash;
DROP INDEX IF EXISTS uk_oauth2_authz_id_token_hash;
DROP INDEX IF EXISTS uk_oauth2_authz_device_hash;
DROP INDEX IF EXISTS uk_oauth2_authz_user_hash;

CREATE UNIQUE INDEX uk_oauth2_authz_code_hash_global
  ON oauth2_authorization(authorization_code_hash)
  WHERE authorization_code_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_access_hash_global
  ON oauth2_authorization(access_token_hash)
  WHERE access_token_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_refresh_hash_global
  ON oauth2_authorization(refresh_token_hash)
  WHERE refresh_token_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_id_token_hash_global
  ON oauth2_authorization(oidc_id_token_hash)
  WHERE oidc_id_token_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_device_hash_global
  ON oauth2_authorization(device_code_hash)
  WHERE device_code_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_user_hash_global
  ON oauth2_authorization(user_code_hash)
  WHERE user_code_hash IS NOT NULL;

COMMENT ON TABLE oauth2_authorization IS
  'Etat OAuth2/OIDC (TAS-GRANTS-02). org_id/space_id NULL selon le plan (voir leurs '
  'commentaires). Unicite des hash globale, jamais bornee au tenant : findByToken n''a aucun '
  'parametre de tenant. Les colonnes de valeur chiffree (*_value pour les codes et le device/ '
  'user code) sont ajoutees par la tranche qui les consomme, avec le port de TAS-GRANTS-02A.';

-- ════════════════════════════════════════════════════════════════════════
-- 2) oauth2_authorization_consent
-- ════════════════════════════════════════════════════════════════════════

ALTER TABLE oauth2_authorization_consent
  ALTER COLUMN space_id DROP NOT NULL;

ALTER TABLE oauth2_authorization_consent
  ADD CONSTRAINT chk_oauth2_consent_space_requires_org
    CHECK (space_id IS NULL OR org_id IS NOT NULL);

ALTER TABLE oauth2_authorization_consent
  ADD COLUMN subject_type VARCHAR(20) NOT NULL DEFAULT 'HUMAN',
  ADD COLUMN principal_name VARCHAR(255) NOT NULL DEFAULT '';

ALTER TABLE oauth2_authorization_consent
  ALTER COLUMN subject_type DROP DEFAULT,
  ALTER COLUMN principal_name DROP DEFAULT;

ALTER TABLE oauth2_authorization_consent
  ADD CONSTRAINT chk_oauth2_consent_subject_type
    CHECK (subject_type IN ('CLIENT_APP', 'HUMAN'));

-- OAuth2AuthorizationConsentService.findById(registeredClientId, principalName) ne prend ni
-- tenant ni compte : c'est desormais principal_name, pas principal_account_id, qui porte la
-- cle de lecture. La colonne et sa FK restent pour la tracabilite du compte ayant consenti.
-- uk_oauth2_consent_client_principal est une contrainte UNIQUE nommee (V202601091233), pas un
-- simple index : la retirer via DROP CONSTRAINT emporte son index de secours du meme geste.
ALTER TABLE oauth2_authorization_consent
  DROP CONSTRAINT IF EXISTS uk_oauth2_consent_client_principal;

ALTER TABLE oauth2_authorization_consent
  DROP CONSTRAINT IF EXISTS fk_oauth2_consent_client_scope;

CREATE UNIQUE INDEX uk_oauth2_consent_client_principal_global
  ON oauth2_authorization_consent(registered_client_id, principal_name);

COMMENT ON COLUMN oauth2_authorization_consent.space_id IS
  'NULL pour PLATFORM et ORGANIZATION ; requis pour SPACE. Memes regles que '
  'oauth2_authorization.space_id.';
COMMENT ON COLUMN oauth2_authorization_consent.subject_type IS
  'HUMAN dans tous les cas observes aujourd''hui : un ecran de consentement ne s''affiche '
  'jamais pour client_credentials. Portee pour la meme raison de symetrie et de lecture par '
  'principal_name que oauth2_authorization.subject_type.';
COMMENT ON COLUMN oauth2_authorization_consent.principal_name IS
  'Cle de lecture de OAuth2AuthorizationConsentService.findById(registeredClientId, '
  'principalName) : aucun des deux parametres n''est un tenant ni un identifiant technique.';
COMMENT ON COLUMN oauth2_authorization_consent.registered_client_id IS
  'RegisteredClient.id (identifiant technique stable), jamais le client_id public. Aucune FK '
  'vers oauth2_clients, pour la meme raison que oauth2_authorization.registered_client_id.';

COMMENT ON TABLE oauth2_authorization_consent IS
  'Consentement OAuth2 (TAS-GRANTS-02). Cle de lecture globale (registered_client_id, '
  'principal_name), jamais tenant-scopee : la signature de findById n''a pas de parametre de '
  'tenant. space_id nullable selon le meme plan que oauth2_authorization.';
