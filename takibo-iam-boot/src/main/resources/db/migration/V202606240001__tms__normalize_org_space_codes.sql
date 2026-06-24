-- ============================================================================
-- SCOPE: PostgreSQL main migration (classpath:db/migration).
-- This PR modifies the PostgreSQL primary migration path.
-- MySQL support (db/migration/mysql) is explicitly out of scope for this PR.
-- The mysql profile must not be considered supported by this migration.
-- ============================================================================
-- PURPOSE:
--   1. Normalize existing org and space codes to lowercase-kebab-case,
--      mirroring the TakiboCodeNormalizer Java logic (Slugifier.slug).
--   2. Block migration with a clear error if normalization would produce
--      collisions or org codes shorter than 3 characters.
--   3. Space codes shorter than 3 characters are padded with a deterministic suffix.
--   4. Replace case-sensitive UNIQUE constraints with case-insensitive indexes.
--   5. Add a CHECK constraint enforcing org code minimum length of 3.
-- ============================================================================

-- ============================================================================
-- STEP 1 — DIAGNOSTIC: organizations
-- Block if any org code would collide or become < 3 chars after normalization.
-- ============================================================================
DO $$
DECLARE
    collision_count INTEGER;
    short_count     INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO   collision_count
    FROM (
        SELECT COUNT(*)
        FROM   organizations
        GROUP  BY regexp_replace(
                      regexp_replace(lower(code), '[^a-z0-9]+', '-', 'g'),
                      '^-|-$', '', 'g'
                  )
        HAVING COUNT(*) > 1
    ) sub;

    IF collision_count > 0 THEN
        RAISE EXCEPTION
            'MIGRATION BLOCKED: % organization code group(s) would collide after normalization. '
            'Resolve duplicates manually before re-running.',
            collision_count;
    END IF;

    SELECT COUNT(*)
    INTO   short_count
    FROM   organizations
    WHERE  length(
               regexp_replace(
                   regexp_replace(lower(code), '[^a-z0-9]+', '-', 'g'),
                   '^-|-$', '', 'g'
               )
           ) < 3;

    IF short_count > 0 THEN
        RAISE EXCEPTION
            'MIGRATION BLOCKED: % organization code(s) would be shorter than 3 characters after '
            'normalization. Fix them manually (organization.code must be >= 3 chars) before re-running.',
            short_count;
    END IF;
END $$;

-- ============================================================================
-- STEP 2 — DIAGNOSTIC: spaces
-- Block if any space code would collide within the same org after normalization.
-- (Short space codes are padded, not rejected — no short-code block here.)
-- ============================================================================
DO $$
DECLARE
    collision_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO   collision_count
    FROM (
        SELECT COUNT(*)
        FROM   spaces
        GROUP  BY org_id,
                  regexp_replace(
                      regexp_replace(lower(code), '[^a-z0-9]+', '-', 'g'),
                      '^-|-$', '', 'g'
                  )
        HAVING COUNT(*) > 1
    ) sub;

    IF collision_count > 0 THEN
        RAISE EXCEPTION
            'MIGRATION BLOCKED: % space code group(s) would collide within the same org after '
            'normalization. Resolve duplicates manually before re-running.',
            collision_count;
    END IF;
END $$;

-- ============================================================================
-- STEP 3 — NORMALIZE org codes
-- Rule: lowercase + replace [^a-z0-9]+ with '-' + strip leading/trailing dashes.
-- Mirrors TakiboCodeNormalizer (Slugifier.slug) in Java.
-- ============================================================================
UPDATE organizations
SET    code = regexp_replace(
                 regexp_replace(lower(code), '[^a-z0-9]+', '-', 'g'),
                 '^-|-$', '', 'g'
             );

-- ============================================================================
-- STEP 4 — NORMALIZE space codes
-- Same normalization rule as orgs.
-- If normalized code < 3 chars: pad with a deterministic 4-digit suffix
-- derived from hashtext(id) so the result is reproducible and unique.
-- ============================================================================
WITH normalized AS (
    SELECT id,
           regexp_replace(
               regexp_replace(lower(code), '[^a-z0-9]+', '-', 'g'),
               '^-|-$', '', 'g'
           ) AS nc
    FROM   spaces
)
UPDATE spaces
SET    code = CASE
                 WHEN length(normalized.nc) < 3
                 THEN normalized.nc || '-' || lpad(
                          ((abs(hashtext(spaces.id::text)) % 9000) + 1000)::text,
                          4, '0'
                      )
                 ELSE normalized.nc
             END
FROM   normalized
WHERE  spaces.id = normalized.id;

-- ============================================================================
-- STEP 5 — REPLACE case-sensitive constraints with case-insensitive indexes
-- organizations: drop UNIQUE constraint, add UNIQUE INDEX on LOWER(code).
-- spaces: drop UNIQUE constraint, add UNIQUE INDEX on (org_id, LOWER(code)).
-- ============================================================================
ALTER TABLE organizations DROP CONSTRAINT uk_organizations_code;
CREATE UNIQUE INDEX uk_organizations_code_ci ON organizations (LOWER(code));

ALTER TABLE spaces DROP CONSTRAINT uk_spaces_org_code;
CREATE UNIQUE INDEX uk_spaces_org_code_ci ON spaces (org_id, LOWER(code));

-- ============================================================================
-- STEP 6 — ADD minimum-length CHECK for org codes
-- organization.code must remain >= 3 characters (business rule: org codes are
-- strong tenant boundaries and must be explicitly chosen).
-- ============================================================================
ALTER TABLE organizations
    ADD CONSTRAINT ck_organizations_code_min_length CHECK (length(code) >= 3);
