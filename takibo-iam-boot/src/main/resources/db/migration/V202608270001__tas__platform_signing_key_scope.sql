-- ════════════════════════════════════════════════════════════════════════
-- TAS-GRANTS-02A — Portée des clés de signature : single-issuer assumé
-- ════════════════════════════════════════════════════════════════════════
--
-- Décision. TAS signe avec UNE clé de plateforme, pas une par organisation.
--
-- Le mot compte : TAKIBO reste MULTI-TENANT au sens métier — organisations, spaces,
-- frontières situées dans chaque token. Ce qui est unique, c'est l'ÉMETTEUR : une clé de
-- signature, un `iss`, un JWKS. « single-issuer », donc, et non « mono-tenant ».
--
--   * TakiboAuthorizationServerConfiguration appelle .issuer(...), ce qui force
--     explicitement une configuration single-issuer côté Spring Authorization Server ;
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
  'tant que TAS reste single-issuer (TAS-GRANTS-02A).';

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

-- ------------------------------------------------------------------------
-- 4) Rétablir la vérité dans la base
-- ------------------------------------------------------------------------
-- La migration d'origine V202601091233 porte en commentaires de source
-- « 1 issuer ACTIVE per org » et une contrainte uk_tas_sk_org_kid qui n'existent plus.
-- Ces lignes ne peuvent pas être corrigées : modifier une migration déjà appliquée
-- changerait son empreinte et ferait échouer validate-on-migrate. La description
-- exacte est donc portée par la base elle-même, où elle reste consultable.

COMMENT ON TABLE tas_signing_keys IS
  'Cles de signature des JWT emis par TAS. org_id NULL = cle de plateforme, seule portee '
  'utilisee tant que TAS reste single-issuer (TAS-GRANTS-02A). Les commentaires de source de la '
  'migration V202601091233 decrivent l''etat anterieur et ne font plus foi.';

COMMENT ON INDEX uk_tas_sk_kid_global IS
  'kid unique a l''echelle de l''installation : /oauth2/jwks est un endpoint unique, deux '
  'cles homonymes y seraient indistinguables a la verification. Remplace uk_tas_sk_org_kid.';

COMMENT ON INDEX uk_tas_sk_platform_issuer_active IS
  'Au plus une cle de plateforme emettrice active. Indexe (org_id IS NULL), vrai pour toutes '
  'les lignes retenues par le filtre : PostgreSQL tenant chaque NULL pour distinct, un index '
  'sur org_id seul n''aurait rien empeche.';

COMMENT ON INDEX uk_tas_sk_org_issuer_active IS
  'Au plus une cle emettrice active par organisation, si le multi-issuer est decide un jour. '
  'Sans objet tant que seules des cles de plateforme existent.';

-- Le commentaire historique de is_issuer annonce « only 1 ACTIVE per org », ce qui n'est plus
-- vrai depuis que la clé de plateforme existe : la contrainte est désormais scindée entre
-- uk_tas_sk_platform_issuer_active et uk_tas_sk_org_issuer_active.
COMMENT ON COLUMN tas_signing_keys.is_issuer IS
  'TRUE = cette cle signe les nouveaux tokens ; FALSE = elle ne sert plus qu''a verifier. '
  'Au plus une emettrice ACTIVE de plateforme (org_id NULL), et au plus une par organisation '
  'si le multi-issuer est decide un jour. Remplace le commentaire d''origine, qui annoncait '
  'une seule emettrice active par organisation.';
