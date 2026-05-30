ALTER TABLE role_assignments
    RENAME COLUMN assignment_source TO role_source;

UPDATE role_assignments
SET role_source = 'TECHNICAL'
WHERE role_source = 'SYSTEM'
  AND role_code IS NOT NULL
  AND business_role_id IS NULL;

ALTER TABLE role_assignments
    ALTER COLUMN role_source SET DEFAULT 'TECHNICAL';

ALTER TABLE role_assignments
    ADD CONSTRAINT ck_ra_role_source_shape CHECK (
        (
            role_source = 'TECHNICAL'
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

CREATE UNIQUE INDEX uq_ra_space_technical_role
    ON role_assignments(org_id, space_id, identity_type, identity_id, role_code)
    WHERE space_id IS NOT NULL
      AND role_source = 'TECHNICAL';

CREATE UNIQUE INDEX uq_ra_org_technical_role
    ON role_assignments(org_id, identity_type, identity_id, role_code)
    WHERE space_id IS NULL
      AND role_source = 'TECHNICAL';

CREATE UNIQUE INDEX uq_ra_space_business_role
    ON role_assignments(org_id, space_id, identity_type, identity_id, business_role_id)
    WHERE space_id IS NOT NULL
      AND role_source = 'BUSINESS';
