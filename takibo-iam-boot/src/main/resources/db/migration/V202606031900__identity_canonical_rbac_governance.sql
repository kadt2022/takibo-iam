-- ============================================================
-- Canonical RBAC governance: migrate user_roles data, drop the
-- table, add group nature, rename platform-level role codes.
-- ============================================================

-- 1. Migrate user_roles grants into role_assignments before dropping the table.
--    user_id (users.id) is resolved to identity_id via users.account_id → takibo_identities.
--    Only rows not already present in role_assignments are inserted (idempotent).
INSERT INTO role_assignments (
    id,
    org_id,
    space_id,
    identity_type,
    identity_id,
    role_code,
    business_role_id,
    role_source,
    created_at,
    updated_at
)
SELECT
    gen_random_uuid(),
    ur.org_id,
    ur.space_id,
    'HUMAN',
    ti.identity_id,
    r.code,
    NULL,
    'TECHNICAL',
    COALESCE(ur.assigned_at, NOW()),
    NOW()
FROM user_roles ur
JOIN users u
    ON u.org_id   = ur.org_id
   AND u.space_id = ur.space_id
   AND u.id       = ur.user_id
JOIN takibo_identities ti
    ON ti.org_id      = u.org_id
   AND ti.account_id  = u.account_id
JOIN roles r
    ON r.id = ur.role_id
WHERE NOT EXISTS (
    SELECT 1
    FROM role_assignments ra
    WHERE ra.org_id        = ur.org_id
      AND ra.space_id      = ur.space_id
      AND ra.identity_type = 'HUMAN'
      AND ra.identity_id   = ti.identity_id
      AND ra.role_code     = r.code
      AND ra.role_source   = 'TECHNICAL'
);

-- 2. Drop user_roles — all grants are now in role_assignments.
DROP TABLE IF EXISTS user_roles;

-- 3. Add nature column to groups.
ALTER TABLE "groups" ADD COLUMN IF NOT EXISTS nature VARCHAR(20);
UPDATE "groups" SET nature = 'GOVERNANCE' WHERE nature IS NULL;
ALTER TABLE "groups" ALTER COLUMN nature SET NOT NULL;
ALTER TABLE "groups" ADD CONSTRAINT ck_groups_nature CHECK (nature IN ('GOVERNANCE', 'BUSINESS'));

-- 4. Rename platform-level role codes in role_assignments.
UPDATE role_assignments
SET role_code = 'R_TAKIBO_PLATFORM_ADMIN'
WHERE role_code = 'R_SYSTEM_ADMIN';

UPDATE role_assignments
SET role_code = 'R_TAKIBO_PLATFORM_AUDITOR'
WHERE role_code = 'R_SYSTEM_AUDITOR';

-- 5. Rename platform-level role codes in roles catalog table (if seeded).
UPDATE roles
SET code = 'R_TAKIBO_PLATFORM_ADMIN'
WHERE code = 'R_SYSTEM_ADMIN';

UPDATE roles
SET code = 'R_TAKIBO_PLATFORM_AUDITOR'
WHERE code = 'R_SYSTEM_AUDITOR';
