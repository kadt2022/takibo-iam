-- Fix CHAR(64) to VARCHAR(64) for token hash columns
-- Hibernate expects VARCHAR not bpchar (CHAR)

ALTER TABLE oauth2_authorization
  ALTER COLUMN authorization_code_hash TYPE VARCHAR(64),
  ALTER COLUMN access_token_hash TYPE VARCHAR(64),
  ALTER COLUMN oidc_id_token_hash TYPE VARCHAR(64),
  ALTER COLUMN refresh_token_hash TYPE VARCHAR(64),
  ALTER COLUMN user_code_hash TYPE VARCHAR(64),
  ALTER COLUMN device_code_hash TYPE VARCHAR(64);

COMMENT ON COLUMN oauth2_authorization.authorization_code_hash IS 'SHA-256 hex lowercase (64 chars VARCHAR for Hibernate compatibility)';
COMMENT ON COLUMN oauth2_authorization.access_token_hash IS 'SHA-256 hex lowercase (64 chars VARCHAR for Hibernate compatibility)';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_hash IS 'SHA-256 hex lowercase (64 chars VARCHAR for Hibernate compatibility)';
COMMENT ON COLUMN oauth2_authorization.refresh_token_hash IS 'SHA-256 hex lowercase (64 chars VARCHAR for Hibernate compatibility)';
COMMENT ON COLUMN oauth2_authorization.user_code_hash IS 'SHA-256 hex lowercase (64 chars VARCHAR for Hibernate compatibility)';
COMMENT ON COLUMN oauth2_authorization.device_code_hash IS 'SHA-256 hex lowercase (64 chars VARCHAR for Hibernate compatibility)';
