-- ============================================================================
-- SCOPE: PostgreSQL main migration (classpath:db/migration).
-- MySQL support is explicitly out of scope for this PR.
-- ============================================================================
-- PURPOSE (TAS v1 - scope-bound tokens):
--   /oauth2/token ne reçoit que (client_id + client_secret), sans orgCode/spaceCode/tenant_hint.
--   TAS doit donc pouvoir résoudre un client par son client_id SEUL -> client_id globalement unique.
--   L'unicité scopée existante (org_id, space_id, client_id) n'est PAS supprimée ; on ajoute
--   un index unique global en complément.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- STEP 1 — DIAGNOSTIC: bloque si des client_id sont dupliqués globalement.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    duplicate_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO   duplicate_count
    FROM (
        SELECT client_id
        FROM   oauth2_clients
        GROUP  BY client_id
        HAVING COUNT(*) > 1
    ) duplicates;

    IF duplicate_count > 0 THEN
        RAISE EXCEPTION
            'MIGRATION BLOCKED: % OAuth2 client_id value(s) are duplicated globally. '
            'Resolve duplicates before enabling global client_id uniqueness.',
            duplicate_count;
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- STEP 2 — Index unique global sur client_id.
-- ----------------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uq_oauth2_clients_client_id_global
ON oauth2_clients (client_id);
