-- ============================================================================
-- SCOPE: PostgreSQL main migration (classpath:db/migration).
-- This PR modifies the PostgreSQL primary migration path.
-- MySQL support (db/migration/mysql) is explicitly out of scope for this PR.
-- The mysql profile must not be considered supported by this migration.
-- ============================================================================
-- PURPOSE:
--   1. Block legacy org and space codes containing non-ASCII characters before
--      SQL normalization, because this migration intentionally does not depend
--      on PostgreSQL unaccent while Java Slugifier.slug strips diacritics.
--   2. Normalize existing ASCII org and space codes to lowercase-kebab-case.
--   3. Block migration with a clear error if normalization would produce
--      collisions or org codes shorter than 3 characters.
--   4. Space codes shorter than 3 characters are padded with a deterministic suffix.
--   5. Replace case-sensitive UNIQUE constraints with case-insensitive indexes.
--   6. Add a CHECK constraint enforcing org code minimum length of 3.
-- ============================================================================

-- ============================================================================
-- STEP 1 - DIAGNOSTIC: non-ASCII legacy codes
-- Block before SQL normalization, because SQL does not strip diacritics like
-- Java Slugifier.slug and would otherwise produce divergent legacy codes.
-- ============================================================================
DO $$
DECLARE
    invalid_org_count   INTEGER;
    invalid_space_count INTEGER;
BEGIN
    SELECT COUNT(*)
    INTO   invalid_org_count
    FROM   organizations
    WHERE  code !~ '^[A-Za-z0-9 _.-]+$';

    IF invalid_org_count > 0 THEN
        RAISE EXCEPTION
            'MIGRATION BLOCKED: % organization code(s) contain non-ASCII characters. Normalize them manually before re-running.',
            invalid_org_count;
    END IF;

    SELECT COUNT(*)
    INTO   invalid_space_count
    FROM   spaces
    WHERE  code !~ '^[A-Za-z0-9 _.-]+$';

    IF invalid_space_count > 0 THEN
        RAISE EXCEPTION
            'MIGRATION BLOCKED: % space code(s) contain non-ASCII characters. Normalize them manually before re-running.',
            invalid_space_count;
    END IF;
END $$;

-- ============================================================================
-- STEP 2 - DIAGNOSTIC: organizations
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
-- STEP 3 - DIAGNOSTIC: spaces
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
-- STEP 4 - NORMALIZE org codes
-- Rule: lowercase + replace [^a-z0-9]+ with '-' + strip leading/trailing dashes.
-- Only ASCII legacy codes reach this point; non-ASCII codes are blocked above.
-- ============================================================================
UPDATE organizations
SET    code = regexp_replace(
                 regexp_replace(lower(code), '[^a-z0-9]+', '-', 'g'),
                 '^-|-$', '', 'g'
             );

-- ============================================================================
-- STEP 5 - NORMALIZE space codes
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
                 WHEN normalized.nc = ''
                 THEN 'space-' || lpad(
                          ((abs(hashtext(spaces.id::text)) % 9000) + 1000)::text,
                          4, '0'
                      )
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
-- STEP 6 - REPLACE case-sensitive constraints with case-insensitive indexes
-- organizations: drop UNIQUE constraint, add UNIQUE INDEX on LOWER(code).
-- spaces: drop UNIQUE constraint, add UNIQUE INDEX on (org_id, LOWER(code)).
-- ============================================================================
ALTER TABLE organizations DROP CONSTRAINT uk_organizations_code;
CREATE UNIQUE INDEX uk_organizations_code_ci ON organizations (LOWER(code));

ALTER TABLE spaces DROP CONSTRAINT uk_spaces_org_code;
CREATE UNIQUE INDEX uk_spaces_org_code_ci ON spaces (org_id, LOWER(code));

-- ============================================================================
-- STEP 7 - ADD minimum-length CHECK for org codes
-- organization.code must remain >= 3 characters (business rule: org codes are
-- strong tenant boundaries and must be explicitly chosen).
-- ============================================================================
ALTER TABLE organizations
    ADD CONSTRAINT ck_organizations_code_min_length CHECK (length(code) >= 3);
