-- ============================================================
-- Canonical RBAC governance: drop user_roles, add group nature,
-- rename platform-level technical role codes.
-- ============================================================

-- 1. Drop user_roles (legacy path — replaced by role_assignments)
DROP TABLE IF EXISTS user_roles;

-- 2. Add nature column to groups
ALTER TABLE "groups" ADD COLUMN IF NOT EXISTS nature VARCHAR(20);
UPDATE "groups" SET nature = 'GOVERNANCE' WHERE nature IS NULL;
ALTER TABLE "groups" ALTER COLUMN nature SET NOT NULL;
ALTER TABLE "groups" ADD CONSTRAINT ck_groups_nature CHECK (nature IN ('GOVERNANCE', 'BUSINESS'));

-- 3. Rename platform-level role codes in role_assignments
UPDATE role_assignments
SET role_code = 'R_TAKIBO_PLATFORM_ADMIN'
WHERE role_code = 'R_SYSTEM_ADMIN';

UPDATE role_assignments
SET role_code = 'R_TAKIBO_PLATFORM_AUDITOR'
WHERE role_code = 'R_SYSTEM_AUDITOR';

-- 4. Rename platform-level role codes in roles catalog table (if seeded)
UPDATE roles
SET code = 'R_TAKIBO_PLATFORM_ADMIN'
WHERE code = 'R_SYSTEM_ADMIN';

UPDATE roles
SET code = 'R_TAKIBO_PLATFORM_AUDITOR'
WHERE code = 'R_SYSTEM_AUDITOR';
