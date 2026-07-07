-- PR #26 : les rôles/groupes tenant de nature GOVERNANCE s'assignent par code.
-- GOVERNANCE rejoint TECHNICAL comme source « par code » (role_code porté, jamais
-- de business_role_id) ; la forme BUSINESS reste inchangée.

-- ────────────────────────────────────────────────────────────────
-- 1) role_assignments : autoriser la source GOVERNANCE
-- ────────────────────────────────────────────────────────────────
ALTER TABLE role_assignments
    DROP CONSTRAINT ck_ra_role_source_shape;

ALTER TABLE role_assignments
    ADD CONSTRAINT ck_ra_role_source_shape CHECK (
        (
            role_source IN ('TECHNICAL', 'GOVERNANCE')
            AND role_code IS NOT NULL
            AND business_role_id IS NULL
        )
        OR
        (
            role_source = 'BUSINESS'
            AND role_code IS NULL
            AND business_role_id IS NOT NULL
            AND space_id IS NOT NULL
        )
    );

-- Un rôle GOVERNANCE est une ligne tenant d'un space : l'assignation est toujours
-- située. Même unicité que les assignations TECHNICAL de space.
CREATE UNIQUE INDEX uq_ra_space_governance_role
    ON role_assignments(org_id, space_id, identity_type, identity_id, role_code)
    WHERE space_id IS NOT NULL
      AND role_source = 'GOVERNANCE';

-- ────────────────────────────────────────────────────────────────
-- 2) group_assignments : normaliser le legacy 'SYSTEM', contraindre la forme,
--    garantir l'unicité des memberships par code (aucune ne l'était jusqu'ici)
-- ────────────────────────────────────────────────────────────────
UPDATE group_assignments
SET group_source = 'TECHNICAL'
WHERE group_source = 'SYSTEM'
  AND group_code IS NOT NULL
  AND business_group_id IS NULL;

ALTER TABLE group_assignments
    ALTER COLUMN group_source SET DEFAULT 'TECHNICAL';

-- Dédoublonnage avant l'index unique : des memberships identiques ont pu être
-- insérés tant qu'aucune contrainte n'existait.
DELETE FROM group_assignments a
    USING group_assignments b
WHERE a.id > b.id
  AND a.org_id = b.org_id
  AND a.space_id IS NOT DISTINCT FROM b.space_id
  AND a.identity_type = b.identity_type
  AND a.identity_id = b.identity_id
  AND a.group_code IS NOT DISTINCT FROM b.group_code
  AND a.group_source = b.group_source
  AND a.group_code IS NOT NULL;

ALTER TABLE group_assignments
    ADD CONSTRAINT ck_ga_group_source_shape CHECK (
        (
            group_source IN ('TECHNICAL', 'GOVERNANCE')
            AND group_code IS NOT NULL
            AND business_group_id IS NULL
        )
        OR
        (
            group_source = 'BUSINESS'
            AND group_code IS NULL
            AND business_group_id IS NOT NULL
            AND space_id IS NOT NULL
        )
    );

CREATE UNIQUE INDEX uq_ga_space_code_membership
    ON group_assignments(org_id, space_id, identity_type, identity_id, group_code)
    WHERE space_id IS NOT NULL
      AND group_source IN ('TECHNICAL', 'GOVERNANCE');

CREATE UNIQUE INDEX uq_ga_org_code_membership
    ON group_assignments(org_id, identity_type, identity_id, group_code)
    WHERE space_id IS NULL
      AND group_source IN ('TECHNICAL', 'GOVERNANCE');
