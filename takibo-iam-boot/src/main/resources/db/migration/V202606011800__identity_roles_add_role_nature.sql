ALTER TABLE roles
    ADD COLUMN role_nature VARCHAR(20) NOT NULL DEFAULT 'BUSINESS';

UPDATE roles
SET role_nature = 'GOVERNANCE'
WHERE code LIKE 'R_%';

ALTER TABLE roles
    ADD CONSTRAINT ck_roles_nature CHECK (role_nature IN ('GOVERNANCE', 'BUSINESS'));
