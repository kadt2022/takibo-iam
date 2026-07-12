-- IAM 31 : une autorité d'organisation ne doit jamais dépendre de l'existence d'un space.
--
-- Jusqu'ici, la provision écrivait les autorités de scope ORGANIZATION
-- (R_ORG_OWNER du fondateur, G_ORG_ADMINS…) rattachées au space initial :
-- l'autorité organisationnelle était accidentellement située, et la
-- suppression du space l'aurait emportée (ON DELETE CASCADE).
--
-- Cette migration reclasse les attributions TECHNICAL de scope ORGANIZATION
-- au niveau org (space_id NULL). Les codes sont listés en dur : le SQL ne
-- connaît pas le catalogue enum (TechnicalRole / TechnicalGroup), qui reste
-- la source de vérité côté application.
--
-- Les index d'unicité org-level existent déjà (uq_ra_org_technical_role,
-- uq_ga_org_code_membership) : le dédoublonnage DOIT précéder le reclassement,
-- sinon un compte portant le même code dans plusieurs spaces produirait une
-- collision. On conserve la ligne la plus ancienne (puis id min pour départager).

-- ────────────────────────────────────────────────────────────────
-- 1) role_assignments : dédoublonnage des codes ORGANIZATION multi-lignes
-- ────────────────────────────────────────────────────────────────
DELETE FROM role_assignments a
    USING role_assignments b
WHERE a.role_source = 'TECHNICAL'
  AND b.role_source = 'TECHNICAL'
  AND a.role_code = b.role_code
  AND a.role_code IN (
      'R_ORG_OWNER', 'R_ORG_ADMIN', 'R_ORG_USER_ADMIN',
      'R_ORG_CLIENT_ADMIN', 'R_ORG_AUDITOR', 'R_ORG_VIEWER'
  )
  AND a.org_id = b.org_id
  AND a.identity_type = b.identity_type
  AND a.identity_id = b.identity_id
  AND (a.created_at > b.created_at
       OR (a.created_at = b.created_at AND a.id > b.id));

-- ────────────────────────────────────────────────────────────────
-- 2) role_assignments : reclassement org-level
-- ────────────────────────────────────────────────────────────────
UPDATE role_assignments
SET space_id = NULL
WHERE role_source = 'TECHNICAL'
  AND space_id IS NOT NULL
  AND role_code IN (
      'R_ORG_OWNER', 'R_ORG_ADMIN', 'R_ORG_USER_ADMIN',
      'R_ORG_CLIENT_ADMIN', 'R_ORG_AUDITOR', 'R_ORG_VIEWER'
  );

-- ────────────────────────────────────────────────────────────────
-- 3) group_assignments : dédoublonnage des groupes ORGANIZATION multi-lignes
-- ────────────────────────────────────────────────────────────────
DELETE FROM group_assignments a
    USING group_assignments b
WHERE a.group_source = 'TECHNICAL'
  AND b.group_source = 'TECHNICAL'
  AND a.group_code = b.group_code
  AND a.group_code IN ('G_ORG_ADMINS', 'G_ORG_USERS', 'G_ORG_CLIENTS')
  AND a.org_id = b.org_id
  AND a.identity_type = b.identity_type
  AND a.identity_id = b.identity_id
  AND (a.created_at > b.created_at
       OR (a.created_at = b.created_at AND a.id > b.id));

-- ────────────────────────────────────────────────────────────────
-- 4) group_assignments : reclassement org-level
-- ────────────────────────────────────────────────────────────────
UPDATE group_assignments
SET space_id = NULL
WHERE group_source = 'TECHNICAL'
  AND space_id IS NOT NULL
  AND group_code IN ('G_ORG_ADMINS', 'G_ORG_USERS', 'G_ORG_CLIENTS');
