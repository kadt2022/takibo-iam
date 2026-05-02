-- ============================================================================
-- TAS (Takibo Authorization Server) - PRODUCTION SCHEMA v1.4 Best Practice
-- - UUID-based multi-tenant (org_id + space_id)
-- - Token hash columns + indexes (performance)
-- - Type safety constraints only (not application logic)
-- - 1 issuer ACTIVE per org (enforced via unique partial)
-- - 1 HMAC ACTIVE per scope (enforced via unique partial)
-- - Principal canonique (account_id)
-- - INET for IP addresses
-- ============================================================================

-- Prereq: set_updated_at() function already exists

-- ════════════════════════════════════════════════════════════════════════
-- 1) OAUTH2 AUTHORIZATION
-- ════════════════════════════════════════════════════════════════════════
-- ============================================================================
-- TAS - oauth2_authorization (v1.4) - HASH-ONLY for codes (non-prod reset)
-- - Drops plaintext code columns: authorization_code_value / device_code_value / user_code_value
-- - Keeps token values for SAS compatibility: access/refresh/id_token
-- - Hash lookups are org+space scoped
-- Prereq: set_updated_at() exists + referenced tables exist
-- ============================================================================

BEGIN;

DROP TABLE IF EXISTS oauth2_authorization CASCADE;

CREATE TABLE oauth2_authorization (
  id                            UUID         NOT NULL,
  org_id                        UUID         NOT NULL,
  space_id                      UUID         NOT NULL,

  registered_client_id          VARCHAR(128) NOT NULL,
  principal_account_id          UUID         NOT NULL,

  authorization_grant_type      VARCHAR(100) NOT NULL,
  authorized_scopes             VARCHAR(2000),
  attributes                    JSONB,
  state                         VARCHAR(500),

  -- Codes (HASH ONLY)
  authorization_code_hash       VARCHAR(64),
  authorization_code_issued_at  TIMESTAMPTZ,
  authorization_code_expires_at TIMESTAMPTZ,
  authorization_code_metadata   JSONB,

  -- Access token (VALUE + HASH)
  access_token_value            VARCHAR(16000),
  access_token_hash             VARCHAR(64),
  access_token_issued_at        TIMESTAMPTZ,
  access_token_expires_at       TIMESTAMPTZ,
  access_token_metadata         JSONB,
  access_token_type             VARCHAR(100),
  access_token_scopes           VARCHAR(2000),

  -- OIDC ID token (VALUE + HASH)
  oidc_id_token_value           VARCHAR(16000),
  oidc_id_token_hash            VARCHAR(64),
  oidc_id_token_issued_at       TIMESTAMPTZ,
  oidc_id_token_expires_at      TIMESTAMPTZ,
  oidc_id_token_metadata        JSONB,

  -- Refresh token (VALUE + HASH)
  refresh_token_value           VARCHAR(4000),
  refresh_token_hash            VARCHAR(64),
  refresh_token_issued_at       TIMESTAMPTZ,
  refresh_token_expires_at      TIMESTAMPTZ,
  refresh_token_metadata        JSONB,

  -- User code (HASH ONLY)
  user_code_hash                VARCHAR(64),
  user_code_issued_at           TIMESTAMPTZ,
  user_code_expires_at          TIMESTAMPTZ,
  user_code_metadata            JSONB,

  -- Device code (HASH ONLY)
  device_code_hash              VARCHAR(64),
  device_code_issued_at         TIMESTAMPTZ,
  device_code_expires_at        TIMESTAMPTZ,
  device_code_metadata          JSONB,

  created_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at                    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT pk_oauth2_authorization PRIMARY KEY (id),

  CONSTRAINT fk_oauth2_authz_org
    FOREIGN KEY (org_id) REFERENCES organizations(id),

  CONSTRAINT fk_oauth2_authz_space_scope
    FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,

  CONSTRAINT fk_oauth2_authz_client_scope
    FOREIGN KEY (org_id, space_id, registered_client_id)
    REFERENCES oauth2_clients(org_id, space_id, client_id) ON DELETE CASCADE,

  CONSTRAINT fk_oauth2_authz_account_scope
    FOREIGN KEY (org_id, principal_account_id)
    REFERENCES accounts(org_id, id) ON DELETE CASCADE,

  -- Hash format guards (hex lowercase, 64 chars)
  CONSTRAINT chk_oauth2_authz_code_hash_fmt
    CHECK (authorization_code_hash IS NULL OR authorization_code_hash ~ '^[a-f0-9]{64}$'),

  CONSTRAINT chk_oauth2_authz_access_hash_fmt
    CHECK (access_token_hash IS NULL OR access_token_hash ~ '^[a-f0-9]{64}$'),

  CONSTRAINT chk_oauth2_authz_refresh_hash_fmt
    CHECK (refresh_token_hash IS NULL OR refresh_token_hash ~ '^[a-f0-9]{64}$'),

  CONSTRAINT chk_oauth2_authz_id_token_hash_fmt
    CHECK (oidc_id_token_hash IS NULL OR oidc_id_token_hash ~ '^[a-f0-9]{64}$'),

  CONSTRAINT chk_oauth2_authz_device_hash_fmt
    CHECK (device_code_hash IS NULL OR device_code_hash ~ '^[a-f0-9]{64}$'),

  CONSTRAINT chk_oauth2_authz_user_hash_fmt
    CHECK (user_code_hash IS NULL OR user_code_hash ~ '^[a-f0-9]{64}$')
);

-- Basic indexes
CREATE INDEX idx_oauth2_authz_org_space
  ON oauth2_authorization(org_id, space_id);

CREATE INDEX idx_oauth2_authz_client
  ON oauth2_authorization(org_id, space_id, registered_client_id);

CREATE INDEX idx_oauth2_authz_account
  ON oauth2_authorization(org_id, principal_account_id);

-- Hash-based unique indexes (critical)
CREATE UNIQUE INDEX uk_oauth2_authz_code_hash
  ON oauth2_authorization(org_id, space_id, authorization_code_hash)
  WHERE authorization_code_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_access_hash
  ON oauth2_authorization(org_id, space_id, access_token_hash)
  WHERE access_token_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_refresh_hash
  ON oauth2_authorization(org_id, space_id, refresh_token_hash)
  WHERE refresh_token_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_id_token_hash
  ON oauth2_authorization(org_id, space_id, oidc_id_token_hash)
  WHERE oidc_id_token_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_device_hash
  ON oauth2_authorization(org_id, space_id, device_code_hash)
  WHERE device_code_hash IS NOT NULL;

CREATE UNIQUE INDEX uk_oauth2_authz_user_hash
  ON oauth2_authorization(org_id, space_id, user_code_hash)
  WHERE user_code_hash IS NOT NULL;

-- Expiration indexes
CREATE INDEX idx_oauth2_authz_code_expires
  ON oauth2_authorization(org_id, space_id, authorization_code_expires_at)
  WHERE authorization_code_hash IS NOT NULL;

CREATE INDEX idx_oauth2_authz_access_expires
  ON oauth2_authorization(org_id, space_id, access_token_expires_at)
  WHERE access_token_hash IS NOT NULL;

CREATE INDEX idx_oauth2_authz_refresh_expires
  ON oauth2_authorization(org_id, space_id, refresh_token_expires_at)
  WHERE refresh_token_hash IS NOT NULL;

CREATE INDEX idx_oauth2_authz_device_expires
  ON oauth2_authorization(org_id, space_id, device_code_expires_at)
  WHERE device_code_hash IS NOT NULL;

CREATE INDEX idx_oauth2_authz_user_expires
  ON oauth2_authorization(org_id, space_id, user_code_expires_at)
  WHERE user_code_hash IS NOT NULL;

-- updated_at trigger
CREATE TRIGGER trg_oauth2_authorization_updated_at
BEFORE UPDATE ON oauth2_authorization
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Comments
COMMENT ON TABLE oauth2_authorization IS 'OAuth2/OIDC authorization state (org+space scoped, hash-based lookups)';
COMMENT ON COLUMN oauth2_authorization.authorization_code_hash IS 'SHA-256 hex lowercase of authorization code (hash-only, no plaintext)';
COMMENT ON COLUMN oauth2_authorization.device_code_hash IS 'SHA-256 hex lowercase of device code (hash-only, no plaintext)';
COMMENT ON COLUMN oauth2_authorization.user_code_hash IS 'SHA-256 hex lowercase of user code (hash-only, no plaintext)';
COMMENT ON COLUMN oauth2_authorization.access_token_hash IS 'SHA-256 hex lowercase of access token for fast lookup';
COMMENT ON COLUMN oauth2_authorization.refresh_token_hash IS 'SHA-256 hex lowercase of refresh token for fast lookup';
COMMENT ON COLUMN oauth2_authorization.oidc_id_token_hash IS 'SHA-256 hex lowercase of OIDC ID token for fast lookup';

COMMIT;

-- ════════════════════════════════════════════════════════════════════════
-- 2) OAUTH2 AUTHORIZATION CONSENT
-- ════════════════════════════════════════════════════════════════════════

CREATE TABLE oauth2_authorization_consent (
  id                    UUID         NOT NULL,
  org_id                UUID         NOT NULL,
  space_id              UUID         NOT NULL,

  registered_client_id  VARCHAR(128) NOT NULL,
  principal_account_id  UUID         NOT NULL,

  authorities           VARCHAR(2000) NOT NULL,

  created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT pk_oauth2_authorization_consent PRIMARY KEY (id),

  CONSTRAINT uk_oauth2_consent_client_principal
    UNIQUE (org_id, space_id, registered_client_id, principal_account_id),

  CONSTRAINT fk_oauth2_consent_org
    FOREIGN KEY (org_id) REFERENCES organizations(id),

  CONSTRAINT fk_oauth2_consent_space_scope
    FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,

  CONSTRAINT fk_oauth2_consent_client_scope
    FOREIGN KEY (org_id, space_id, registered_client_id)
    REFERENCES oauth2_clients(org_id, space_id, client_id) ON DELETE CASCADE,

  CONSTRAINT fk_oauth2_consent_account_scope
    FOREIGN KEY (org_id, principal_account_id)
    REFERENCES accounts(org_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_oauth2_consent_org_space
  ON oauth2_authorization_consent(org_id, space_id);

CREATE INDEX idx_oauth2_consent_client
  ON oauth2_authorization_consent(org_id, space_id, registered_client_id);

CREATE INDEX idx_oauth2_consent_account
  ON oauth2_authorization_consent(org_id, principal_account_id);

CREATE TRIGGER trg_oauth2_authorization_consent_updated_at
BEFORE UPDATE ON oauth2_authorization_consent
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE oauth2_authorization_consent IS 'OAuth2 consent records (org+space scoped, principal = account_id)';


-- ════════════════════════════════════════════════════════════════════════
-- 3) ACCOUNT SECURITY STATE (EPOCH revocation)
-- ════════════════════════════════════════════════════════════════════════

CREATE TABLE account_security_state (
  org_id            UUID        NOT NULL,
  account_id        UUID        NOT NULL,

  current_epoch     INT         NOT NULL DEFAULT 1,
  last_bump_reason  VARCHAR(255),
  last_bump_at      TIMESTAMPTZ,

  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT pk_account_security_state PRIMARY KEY (org_id, account_id),

  CONSTRAINT fk_ass_org
    FOREIGN KEY (org_id) REFERENCES organizations(id),

  CONSTRAINT fk_ass_account_scope
    FOREIGN KEY (org_id, account_id)
    REFERENCES accounts(org_id, id) ON DELETE CASCADE,

  -- Type safety: epoch must be positive
  CONSTRAINT ck_ass_epoch_positive
    CHECK (current_epoch >= 1)
);

CREATE INDEX idx_ass_org
  ON account_security_state(org_id);

CREATE INDEX idx_ass_org_epoch
  ON account_security_state(org_id, current_epoch);

CREATE TRIGGER trg_account_security_state_updated_at
BEFORE UPDATE ON account_security_state
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE account_security_state IS 'Epoch-based revocation state per account (org-scoped, epoch >= 1)';
COMMENT ON COLUMN account_security_state.current_epoch IS 'Current epoch number (starts at 1, always positive)';


-- ════════════════════════════════════════════════════════════════════════
-- 4) TAS SIGNING KEYS (rotation + JWKS)
-- ════════════════════════════════════════════════════════════════════════

CREATE TABLE tas_signing_keys (
  id                    UUID        NOT NULL,
  org_id                UUID        NOT NULL,

  kid                   VARCHAR(64)  NOT NULL,
  alg                   VARCHAR(32)  NOT NULL,
  kty                   VARCHAR(16)  NOT NULL,
  key_use               VARCHAR(16)  NOT NULL DEFAULT 'sig',
  is_issuer             BOOLEAN      NOT NULL DEFAULT FALSE,
  status                VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',

  public_jwk_json       JSONB        NOT NULL,
  private_key_encrypted VARCHAR(8000),

  not_before            TIMESTAMPTZ,
  expires_at            TIMESTAMPTZ,

  created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

  CONSTRAINT pk_tas_signing_keys PRIMARY KEY (id),

  CONSTRAINT fk_tas_sk_org
    FOREIGN KEY (org_id) REFERENCES organizations(id),

  CONSTRAINT uk_tas_sk_org_kid
    UNIQUE (org_id, kid),

  -- Type safety: status enum
  CONSTRAINT ck_tas_sk_status
    CHECK (status IN ('ACTIVE','RETIRED','REVOKED')),

  -- Type safety: temporal coherence
  CONSTRAINT ck_tas_sk_dates
    CHECK (not_before IS NULL OR expires_at IS NULL OR not_before < expires_at)
);

CREATE INDEX idx_tas_sk_org
  ON tas_signing_keys(org_id);

CREATE INDEX idx_tas_sk_org_status
  ON tas_signing_keys(org_id, status);

CREATE INDEX idx_tas_sk_org_expires
  ON tas_signing_keys(org_id, expires_at);

-- Application logic: only 1 issuer ACTIVE per org (enforced via unique partial)
CREATE UNIQUE INDEX uk_tas_sk_org_issuer_active
  ON tas_signing_keys(org_id)
  WHERE is_issuer = TRUE AND status = 'ACTIVE';

CREATE TRIGGER trg_tas_signing_keys_updated_at
BEFORE UPDATE ON tas_signing_keys
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE tas_signing_keys IS 'Signing keys used by TAS for JWT/JWS issuance (org-scoped)';
COMMENT ON COLUMN tas_signing_keys.is_issuer IS 'TRUE = this key signs new tokens (only 1 ACTIVE per org enforced via unique index); FALSE = verifies only';
COMMENT ON COLUMN tas_signing_keys.status IS 'ACTIVE: can sign (if issuer) + verify; RETIRED: verify only; REVOKED: cannot verify';


-- ════════════════════════════════════════════════════════════════════════
-- 5) TAS AUDIT EVENTS (OAuth2 flows audit)
-- ════════════════════════════════════════════════════════════════════════

CREATE TABLE tas_audit_events (
  event_id      UUID        NOT NULL,
  org_id        UUID        NOT NULL,
  space_id      UUID,

  event_type    VARCHAR(64) NOT NULL,
  status        VARCHAR(32),

  account_id    UUID,

  client_id     VARCHAR(128),
  occurred_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  ip_address    INET,
  user_agent    VARCHAR(4000),

  metadata_json JSONB,

  CONSTRAINT pk_tas_audit_events PRIMARY KEY (event_id),

  CONSTRAINT fk_tae_org
    FOREIGN KEY (org_id) REFERENCES organizations(id),

  CONSTRAINT fk_tae_space_scope
    FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE SET NULL
);

CREATE INDEX idx_tae_org
  ON tas_audit_events(org_id);

CREATE INDEX idx_tae_org_space
  ON tas_audit_events(org_id, space_id);

CREATE INDEX idx_tae_org_time
  ON tas_audit_events(org_id, occurred_at DESC);

CREATE INDEX idx_tae_org_account_time
  ON tas_audit_events(org_id, account_id, occurred_at DESC);

CREATE INDEX idx_tae_event_type_time
  ON tas_audit_events(event_type, occurred_at DESC);

-- Audit API indexes
CREATE INDEX idx_tae_org_client_time
  ON tas_audit_events(org_id, client_id, occurred_at DESC);

CREATE INDEX idx_tae_org_status_time
  ON tas_audit_events(org_id, status, occurred_at DESC);

COMMENT ON TABLE tas_audit_events IS 'Audit trail for OAuth2/OIDC flows (org+space scoped)';
COMMENT ON COLUMN tas_audit_events.ip_address IS 'Client IP address (INET type for network operations)';


-- ════════════════════════════════════════════════════════════════════════
-- 6) CONTEXT HMAC KEYS (STEA keyring)
-- ════════════════════════════════════════════════════════════════════════

CREATE TABLE context_hmac_keys (
  key_id        UUID         NOT NULL,
  org_id        UUID         NOT NULL,
  space_id      UUID         NOT NULL,

  key_version   INT          NOT NULL,
  key_value     VARCHAR(1024) NOT NULL,
  status        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',

  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  retired_at    TIMESTAMPTZ,
  revoked_at    TIMESTAMPTZ,
  revoke_reason VARCHAR(255),

  CONSTRAINT pk_context_hmac_keys PRIMARY KEY (key_id),

  CONSTRAINT uk_context_hmac_scope_version
    UNIQUE (org_id, space_id, key_version),

  CONSTRAINT fk_context_hmac_org
    FOREIGN KEY (org_id) REFERENCES organizations(id),

  CONSTRAINT fk_context_hmac_space_scope
    FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,

  -- Type safety: status enum
  CONSTRAINT ck_context_hmac_status
    CHECK (status IN ('ACTIVE','RETIRED','REVOKED')),

  CONSTRAINT ck_context_hmac_retired_at
    CHECK (retired_at IS NULL OR status IN ('RETIRED','REVOKED')),

  CONSTRAINT ck_context_hmac_revoked_at
    CHECK (revoked_at IS NULL OR status = 'REVOKED')
);

CREATE INDEX idx_context_hmac_org_space
  ON context_hmac_keys(org_id, space_id);

CREATE INDEX idx_context_hmac_scope_status
  ON context_hmac_keys(org_id, space_id, status);

CREATE UNIQUE INDEX uk_context_hmac_scope_active
  ON context_hmac_keys(org_id, space_id)
  WHERE status = 'ACTIVE';

CREATE TRIGGER trg_context_hmac_keys_updated_at
BEFORE UPDATE ON context_hmac_keys
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

COMMENT ON TABLE context_hmac_keys IS 'STEA keyring: HMAC keys for context proof signing/verification (org+space scoped, 1 ACTIVE per scope enforced via unique index)';
COMMENT ON COLUMN context_hmac_keys.key_version IS 'Embedded in tokens as ctx_kv (unique per org+space)';
COMMENT ON COLUMN context_hmac_keys.key_value IS 'Encrypted HMAC key material (hex lowercase if hash, base64 if encrypted); NEVER plaintext';


-- ════════════════════════════════════════════════════════════════════════
-- SCHEMA DOCUMENTATION
-- ════════════════════════════════════════════════════════════════════════

COMMENT ON SCHEMA public IS 'Takibo IAM v1.0 - Multi-tenant IAM with TAS (Authorization Server) - Production v1.4';