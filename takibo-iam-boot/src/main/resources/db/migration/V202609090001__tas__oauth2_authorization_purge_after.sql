-- TAS-GRANTS-02B — Consolidation de l'echeance de purge des autorisations OAuth 2.0.
--
-- Le purgeur travaille globalement : « donne-moi N autorisations dont plus aucun artefact
-- n'est actif ». Les index d'expiration existants commencent tous par (org_id, space_id),
-- ils servent des lectures situees dans un tenant et ne peuvent pas soutenir cette question.
-- Six conditions dispersees a chaque execution imposeraient de plus un balayage complet.
--
-- Cette migration porte l'invariant dans la base plutot que dans Java : chaque ecriture
-- d'une expiration recalcule l'echeance, et le purgeur n'a plus qu'une seule comparaison
-- a faire.

-- ---------------------------------------------------------------------------
-- 1. Un artefact present doit porter son expiration
-- ---------------------------------------------------------------------------
-- Sans cet invariant, GREATEST ignorerait le NULL d'un artefact reellement present et
-- rendrait l'autorisation purgeable a tort. Exemple : un jeton d'acces present sans
-- expiration, et un jeton de rafraichissement expire hier — l'echeance consolidee vaudrait
-- celle du rafraichissement, et la ligne partirait alors qu'un jeton d'acces d'expiration
-- inconnue existe encore.
--
-- Les contraintes existantes ne verifiaient que le format des empreintes, jamais la
-- presence de l'expiration correspondante.
--
-- Consequence assumee : un artefact sans expiration devient un refus d'ecriture. Spring
-- Authorization Server fixe toujours une expiration en pratique, mais son modele autorise
-- un jeton de rafraichissement sans date. TAKIBO refuse desormais ce cas plutot que de le
-- persister : un jeton qui n'expire jamais ne pourrait jamais etre purge, et cette posture
-- doit etre un choix explicite du produit, pas une consequence silencieuse d'un reglage.

ALTER TABLE oauth2_authorization
    ADD CONSTRAINT ck_oauth2_authz_code_expiry_present
    CHECK (authorization_code_hash IS NULL OR authorization_code_expires_at IS NOT NULL);

ALTER TABLE oauth2_authorization
    ADD CONSTRAINT ck_oauth2_authz_access_expiry_present
    CHECK (access_token_hash IS NULL OR access_token_expires_at IS NOT NULL);

ALTER TABLE oauth2_authorization
    ADD CONSTRAINT ck_oauth2_authz_id_token_expiry_present
    CHECK (oidc_id_token_hash IS NULL OR oidc_id_token_expires_at IS NOT NULL);

ALTER TABLE oauth2_authorization
    ADD CONSTRAINT ck_oauth2_authz_refresh_expiry_present
    CHECK (refresh_token_hash IS NULL OR refresh_token_expires_at IS NOT NULL);

ALTER TABLE oauth2_authorization
    ADD CONSTRAINT ck_oauth2_authz_user_code_expiry_present
    CHECK (user_code_hash IS NULL OR user_code_expires_at IS NOT NULL);

ALTER TABLE oauth2_authorization
    ADD CONSTRAINT ck_oauth2_authz_device_code_expiry_present
    CHECK (device_code_hash IS NULL OR device_code_expires_at IS NOT NULL);

-- ---------------------------------------------------------------------------
-- 2. Echeance consolidee
-- ---------------------------------------------------------------------------
-- GREATEST ignore les valeurs nulles sous PostgreSQL : l'echeance vaut donc la derniere
-- expiration reellement presente. Tant qu'un artefact repousse cette date, la ligne reste.
--
-- Le CASE fail-closed n'est pas redondant avec les contraintes ci-dessus, il est
-- deliberement independant d'elles. La purge est une operation destructrice : sa surete ne
-- doit pas reposer sur la survie d'une contrainte declaree ailleurs dans le schema. Si une
-- migration future affaiblissait l'une d'elles, l'echeance deviendrait NULL plutot que
-- fausse, et la ligne cesserait d'etre purgeable au lieu de disparaitre a tort.
--
-- STORED plutot que calcule en Java : l'invariant appartient a la donnee, et toute ecriture
-- d'une expiration le recalcule sans qu'aucun appelant ait a y penser.

ALTER TABLE oauth2_authorization
    ADD COLUMN purge_after TIMESTAMPTZ
    GENERATED ALWAYS AS (
        CASE
            WHEN (authorization_code_hash IS NOT NULL AND authorization_code_expires_at IS NULL)
              OR (access_token_hash       IS NOT NULL AND access_token_expires_at       IS NULL)
              OR (oidc_id_token_hash      IS NOT NULL AND oidc_id_token_expires_at      IS NULL)
              OR (refresh_token_hash      IS NOT NULL AND refresh_token_expires_at      IS NULL)
              OR (user_code_hash          IS NOT NULL AND user_code_expires_at          IS NULL)
              OR (device_code_hash        IS NOT NULL AND device_code_expires_at        IS NULL)
            THEN NULL
            ELSE GREATEST(
                authorization_code_expires_at,
                access_token_expires_at,
                oidc_id_token_expires_at,
                refresh_token_expires_at,
                user_code_expires_at,
                device_code_expires_at
            )
        END
    ) STORED;

COMMENT ON COLUMN oauth2_authorization.purge_after IS
    'TAS-GRANTS-02B : derniere expiration parmi les artefacts presents. NULL signifie '
    'jamais purgeable automatiquement (aucun artefact, ou artefact sans expiration) — '
    'anomalie a signaler, jamais a supprimer.';

-- ---------------------------------------------------------------------------
-- 3. Index de la purge
-- ---------------------------------------------------------------------------
-- Partiel : les lignes d'echeance nulle ne sont jamais candidates, les exclure garde
-- l'index petit et evite qu'une anomalie ralentisse la purge.
-- L'identifiant en seconde colonne donne un ordre total, donc des lots deterministes et
-- un parcours stable entre deux executions concurrentes.

CREATE INDEX idx_oauth2_authz_purge_after
    ON oauth2_authorization (purge_after, id)
    WHERE purge_after IS NOT NULL;

-- Index miroir des anomalies. Compter les lignes non purgeables imposerait sinon un
-- balayage complet, puisque l'index ci-dessus les exclut. Celui-ci reste minuscule tant
-- que les anomalies sont rares — et si un jour il grossit, c'est precisement le signal
-- qu'on cherchait a produire.
CREATE INDEX idx_oauth2_authz_unpurgeable
    ON oauth2_authorization (id)
    WHERE purge_after IS NULL;
