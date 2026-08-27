-- ════════════════════════════════════════════════════════════════════════
-- TAS-GRANTS-02A — Portée des clés de signature : mono-tenant assumé
-- ════════════════════════════════════════════════════════════════════════
--
-- Décision. TAS signe avec UNE clé de plateforme, pas une par organisation.
--
--   * TakiboAuthorizationServerConfiguration appelle .issuer(...), ce qui force
--     explicitement une configuration mono-tenant côté Spring Authorization Server ;
--   * /oauth2/jwks est un endpoint global unique ;
--   * les tokens humains et machine partagent le même JwtEncoder.
--
-- Passer en multi-issuer changerait le claim `iss` de tous les JWT en circulation et
-- obligerait chaque resource server à résoudre un JWKS par organisation. Hors de portée
-- de ce récit, et contraire au besoin immédiat.
--
-- Le schéma d'origine était pourtant org-scopé de bout en bout. Plutôt que de loger la
-- clé de plateforme dans une organisation fictive — ce que le récit interdit — la portée
-- devient explicite : org_id NULL signifie « clé de plateforme ». Des clés org-scopées
-- pourront coexister le jour où le multi-issuer sera décidé, sans migration de données.
--
-- Trois conséquences sur les contraintes, toutes nécessaires :
--
--   1. org_id devient nullable ;
--   2. `kid` devient globalement unique — le JWKS étant unique, deux clés de même kid
--      seraient indistinguables à la vérification ;
--   3. l'unicité de l'émetteur actif est scindée en deux index partiels, parce que
--      PostgreSQL considère les NULL comme distincts : sans cela, rien n'empêcherait
--      deux clés de plateforme actives simultanément.

-- ------------------------------------------------------------------------
-- Garde : la table doit être vide ou cohérente avant de relâcher la contrainte.
-- ------------------------------------------------------------------------
DO $$
DECLARE
    duplicate_kids INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO   duplicate_kids
    FROM (
        SELECT kid
        FROM   tas_signing_keys
        GROUP  BY kid
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_kids > 0 THEN
        RAISE EXCEPTION
            'MIGRATION BLOCKED: % signing key kid(s) are duplicated across organizations. '
            'A single JWKS cannot expose two keys with the same kid; resolve before migrating.',
            duplicate_kids;
    END IF;
END $$;

-- ------------------------------------------------------------------------
-- 1) org_id nullable — NULL = clé de plateforme
-- ------------------------------------------------------------------------
ALTER TABLE tas_signing_keys
  ALTER COLUMN org_id DROP NOT NULL;

COMMENT ON COLUMN tas_signing_keys.org_id IS
  'Organisation propriétaire de la clé. NULL = clé de plateforme, seule portée utilisée '
  'tant que TAS est mono-tenant (TAS-GRANTS-02A).';

-- ------------------------------------------------------------------------
-- 2) kid globalement unique
-- ------------------------------------------------------------------------
ALTER TABLE tas_signing_keys
  DROP CONSTRAINT IF EXISTS uk_tas_sk_org_kid;

CREATE UNIQUE INDEX IF NOT EXISTS uk_tas_sk_kid_global
  ON tas_signing_keys (kid);

-- ------------------------------------------------------------------------
-- 3) Un seul émetteur actif — de plateforme, et par organisation
-- ------------------------------------------------------------------------
DROP INDEX IF EXISTS uk_tas_sk_org_issuer_active;

-- Au plus une clé de plateforme émettrice active. L'expression (org_id IS NULL) vaut
-- TRUE pour toutes les lignes retenues par le filtre : elles partagent donc la même
-- clé d'index, et une seule peut exister.
CREATE UNIQUE INDEX uk_tas_sk_platform_issuer_active
  ON tas_signing_keys ((org_id IS NULL))
  WHERE is_issuer = TRUE AND status = 'ACTIVE' AND org_id IS NULL;

-- Au plus une clé émettrice active par organisation, si le multi-issuer arrive un jour.
CREATE UNIQUE INDEX uk_tas_sk_org_issuer_active
  ON tas_signing_keys (org_id)
  WHERE is_issuer = TRUE AND status = 'ACTIVE' AND org_id IS NOT NULL;
