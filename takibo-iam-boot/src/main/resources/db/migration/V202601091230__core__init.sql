-- ============================================================================
-- Takibo IAM v1.0 - PostgreSQL PRODUCTION SCHEMA
-- UUID native types (generated in application DDD layer)
-- Org-scoped multi-tenant with composite FKs
-- Minimal CHECK constraints - Business logic in application layer
-- ============================================================================


CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ============================================================================
-- ORGANIZATIONS (root tenant)
-- ============================================================================
CREATE TABLE organizations (
  id            UUID         NOT NULL,
  code          VARCHAR(80)  NOT NULL,
  name          VARCHAR(160) NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  status_reason VARCHAR(512),
  created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version       BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_organizations PRIMARY KEY (id),
  CONSTRAINT uk_organizations_code UNIQUE (code),
  CONSTRAINT ck_organizations_status CHECK (status IN ('ACTIVE','SUSPENDED','DISABLED'))
);

CREATE TRIGGER trg_organizations_updated_at
BEFORE UPDATE ON organizations
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- ACCOUNTS (org-scoped)
-- ============================================================================
CREATE TABLE accounts (
  id           UUID         NOT NULL,
  org_id       UUID         NOT NULL,
  email        VARCHAR(254) NOT NULL,
  display_name VARCHAR(160),
  avatar_url   VARCHAR(255),
  metadata     JSONB,
  created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version      BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_accounts PRIMARY KEY (id),
  CONSTRAINT fk_accounts_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT uk_accounts_org_id UNIQUE (org_id, id)
);

CREATE UNIQUE INDEX uk_accounts_org_email_ci ON accounts (org_id, LOWER(email));
CREATE INDEX idx_accounts_org ON accounts (org_id);

CREATE TRIGGER trg_accounts_updated_at
BEFORE UPDATE ON accounts
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE account_credentials (
  org_id                 UUID         NOT NULL,
  account_id             UUID         NOT NULL,
  password_hash          VARCHAR(255) NOT NULL,
  password_algo          VARCHAR(40),
  password_version       INT,
  password_updated_at    TIMESTAMPTZ,
  must_change_next_login BOOLEAN      NOT NULL DEFAULT FALSE,
  failed_attempts        INT          NOT NULL DEFAULT 0,
  locked_until           TIMESTAMPTZ,
  created_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at             TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version                BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_account_credentials PRIMARY KEY (org_id, account_id),
  CONSTRAINT fk_acctcred_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_acctcred_account_scope FOREIGN KEY (org_id, account_id)
    REFERENCES accounts(org_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_acctcred_org ON account_credentials (org_id);
CREATE INDEX idx_acctcred_org_locked_until ON account_credentials (org_id, locked_until);

CREATE TRIGGER trg_account_credentials_updated_at
BEFORE UPDATE ON account_credentials
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- SPACES (org-scoped)
-- ============================================================================
CREATE TABLE spaces (
  id                UUID         NOT NULL,
  org_id            UUID         NOT NULL,
  code              VARCHAR(80)  NOT NULL,
  name              VARCHAR(80)  NOT NULL,
  description       VARCHAR(255),
  status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  status_reason     VARCHAR(512),
  status_updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  owner_account_id  UUID,
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version           BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_spaces PRIMARY KEY (id),
  CONSTRAINT fk_spaces_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_spaces_owner_account_scope FOREIGN KEY (org_id, owner_account_id)
    REFERENCES accounts(org_id, id) ON DELETE SET NULL,
  CONSTRAINT uk_spaces_org_code UNIQUE (org_id, code),
  CONSTRAINT uk_spaces_org_id UNIQUE (org_id, id),
  CONSTRAINT ck_spaces_status CHECK (status IN ('ACTIVE','SUSPENDED','DISABLED'))
);

CREATE INDEX idx_spaces_org ON spaces (org_id);
CREATE INDEX idx_spaces_status ON spaces (org_id, status);

CREATE TRIGGER trg_spaces_updated_at
BEFORE UPDATE ON spaces
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE space_domains (
  id                 UUID         NOT NULL,
  org_id             UUID         NOT NULL,
  space_id           UUID         NOT NULL,
  domain             VARCHAR(255) NOT NULL,
  verified           BOOLEAN      NOT NULL DEFAULT FALSE,
  verification_token VARCHAR(255),
  created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version            BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_space_domains PRIMARY KEY (id),
  CONSTRAINT fk_space_domains_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_space_domains_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX uq_space_domain_ci ON space_domains(org_id, space_id, LOWER(domain));
CREATE INDEX idx_space_domains_org_space ON space_domains (org_id, space_id);

CREATE TRIGGER trg_space_domains_updated_at
BEFORE UPDATE ON space_domains
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================================
-- TAKIBO IDENTITIES (org-scoped)
-- - identity_id = identifiant technique stable (PK)
-- - account_id reste la "racine d'auth" (Phase A), unique par org
-- - conserve UNIQUE(org_id, account_id) pour compatibilité avec les FKs existantes
-- ============================================================================

CREATE TABLE takibo_identities (
  identity_id    UUID        NOT NULL,
  org_id         UUID        NOT NULL,
  account_id     UUID        NOT NULL,

  identity_type   VARCHAR(32) NOT NULL,
  identity_status VARCHAR(32) NOT NULL,

  trust_level     INT         NOT NULL DEFAULT 0,
  risk_score      INT         NOT NULL DEFAULT 0,
  last_active_at  TIMESTAMPTZ,

  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by      VARCHAR(255),
  version         BIGINT      NOT NULL DEFAULT 0,

  CONSTRAINT pk_takibo_identities PRIMARY KEY (identity_id),

  CONSTRAINT fk_ti_org
    FOREIGN KEY (org_id) REFERENCES organizations(id),

  CONSTRAINT fk_ti_account_scope
    FOREIGN KEY (org_id, account_id)
    REFERENCES accounts(org_id, id)
    ON DELETE CASCADE,

  CONSTRAINT uk_ti_org_account UNIQUE (org_id, account_id),
  CONSTRAINT uk_ti_org_identity UNIQUE (org_id, identity_id),

  CONSTRAINT ck_ti_identity_type
    CHECK (identity_type IN ('HUMAN','SERVICE','MACHINE')),

  CONSTRAINT ck_ti_identity_status
    CHECK (identity_status IN ('ACTIVE','SUSPENDED','DISABLED'))
);

CREATE INDEX idx_ti_org ON takibo_identities (org_id);
CREATE INDEX idx_ti_org_account ON takibo_identities (org_id, account_id);

CREATE TRIGGER trg_takibo_identities_updated_at
BEFORE UPDATE ON takibo_identities
FOR EACH ROW EXECUTE FUNCTION set_updated_at();



CREATE TABLE linked_identities (
  linked_identity_id UUID         NOT NULL,
  org_id             UUID         NOT NULL,
  account_id         UUID         NOT NULL,
  source             VARCHAR(32)  NOT NULL,
  issuer             VARCHAR(512) NOT NULL,
  subject            VARCHAR(512) NOT NULL,
  link_status        VARCHAR(32)  NOT NULL,
  linked_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  last_seen_at       TIMESTAMPTZ,
  display_name       VARCHAR(256),
  email              VARCHAR(320),
  metadata_json      JSONB,

  CONSTRAINT pk_linked_identities PRIMARY KEY (linked_identity_id),
  CONSTRAINT fk_linked_identities_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_linked_identities_account_scope FOREIGN KEY (org_id, account_id)
    REFERENCES takibo_identities(org_id, account_id) ON DELETE RESTRICT,
  CONSTRAINT uk_linked_identities_org_provider UNIQUE (org_id, source, issuer, subject),
  CONSTRAINT ck_li_link_status CHECK (link_status IN ('ACTIVE','SUSPENDED','REVOKED'))
);

CREATE INDEX idx_linked_identities_org_account ON linked_identities (org_id, account_id);

CREATE TABLE identity_keys (
  key_id                UUID        NOT NULL,
  org_id                UUID        NOT NULL,
  account_id            UUID        NOT NULL,
  key_type              VARCHAR(32) NOT NULL,
  key_status            VARCHAR(32) NOT NULL,
  rotation_required     BOOLEAN     NOT NULL DEFAULT FALSE,
  failed_attempts       INT         NOT NULL DEFAULT 0,
  locked_until          TIMESTAMPTZ,
  last_used_at          TIMESTAMPTZ,
  credential_account_id UUID,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version               BIGINT      NOT NULL DEFAULT 0,

  CONSTRAINT pk_identity_keys PRIMARY KEY (key_id),
  CONSTRAINT fk_identity_keys_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_identity_keys_account_scope FOREIGN KEY (org_id, account_id)
    REFERENCES takibo_identities(org_id, account_id) ON DELETE RESTRICT,
  CONSTRAINT fk_identity_keys_cred_account_scope FOREIGN KEY (org_id, credential_account_id)
    REFERENCES accounts(org_id, id) ON DELETE SET NULL,
  CONSTRAINT ck_ik_key_type CHECK (key_type IN ('PASSWORD','TOTP','WEBAUTHN','API_KEY')),
  CONSTRAINT ck_ik_key_status CHECK (key_status IN ('ACTIVE','LOCKED','REVOKED'))
);

CREATE INDEX idx_identity_keys_org_account ON identity_keys (org_id, account_id);
CREATE INDEX idx_identity_keys_org_status ON identity_keys (org_id, key_status);

CREATE TRIGGER trg_identity_keys_updated_at
BEFORE UPDATE ON identity_keys
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE identity_observations (
  observation_id   UUID         NOT NULL,
  org_id           UUID         NOT NULL,
  account_id       UUID,
  observation_type VARCHAR(32)  NOT NULL,
  severity         VARCHAR(16)  NOT NULL DEFAULT 'INFO',
  occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  source_service   VARCHAR(64),
  request_id       VARCHAR(128),
  ip_address       VARCHAR(128),
  user_agent       VARCHAR(2048),
  space_id         UUID,
  action           VARCHAR(128),
  data_json        JSONB,

  CONSTRAINT pk_identity_observations PRIMARY KEY (observation_id),
  CONSTRAINT fk_identity_observations_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_identity_observations_account_scope FOREIGN KEY (org_id, account_id)
    REFERENCES takibo_identities(org_id, account_id) ON DELETE SET NULL,
  CONSTRAINT fk_identity_observations_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE SET NULL
);

CREATE INDEX idx_identity_observations_org_time ON identity_observations (org_id, occurred_at);
CREATE INDEX idx_identity_observations_org_account_time ON identity_observations (org_id, account_id, occurred_at DESC);

-- ============================================================================
-- USERS (org + space scope)
-- ============================================================================
CREATE TABLE users (
  id               UUID         NOT NULL,
  org_id           UUID         NOT NULL,
  space_id         UUID         NOT NULL,
  account_id       UUID         NOT NULL,
  username         VARCHAR(150) NOT NULL,
  first_name       VARCHAR(160),
  last_name        VARCHAR(160),
  status           VARCHAR(32)  NOT NULL,
  user_type        VARCHAR(32)  NOT NULL,
  mfa_enabled      BOOLEAN      NOT NULL DEFAULT FALSE,
  password_expired BOOLEAN      NOT NULL DEFAULT FALSE,
  last_login_at    TIMESTAMPTZ,
  metadata         JSONB,
  created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version          BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_users PRIMARY KEY (id),

  CONSTRAINT fk_users_org
    FOREIGN KEY (org_id)
    REFERENCES organizations(id),

  CONSTRAINT fk_users_space_scope
    FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id),

  CONSTRAINT fk_users_account_scope
    FOREIGN KEY (org_id, account_id)
    REFERENCES accounts (org_id, id)
    ON UPDATE RESTRICT
    ON DELETE RESTRICT,

  CONSTRAINT uk_users_org_space_account
    UNIQUE (org_id, space_id, account_id),

  CONSTRAINT uk_users_scope_id
    UNIQUE (org_id, space_id, id)
);

CREATE UNIQUE INDEX uq_users_org_space_username_ci ON users(org_id, space_id, LOWER(username));
CREATE INDEX idx_users_org_space ON users (org_id, space_id);
CREATE INDEX idx_users_org_space_statu  ON users (org_id, space_id, status);
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ============================================================================
-- RBAC: ROLES, PERMISSIONS, GROUPS (org + space scope)
-- ============================================================================
CREATE TABLE roles (
  id          UUID         NOT NULL,
  org_id      UUID         NOT NULL,
  space_id    UUID         NOT NULL,
  code        VARCHAR(120) NOT NULL,
  name        VARCHAR(160) NOT NULL,
  description VARCHAR(255),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version     BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_roles PRIMARY KEY (id),
  CONSTRAINT fk_roles_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_roles_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_roles_org_space_code UNIQUE (org_id, space_id, code),
  CONSTRAINT uk_roles_scope_id UNIQUE (org_id, space_id, id)
);

CREATE INDEX idx_roles_org_space ON roles (org_id, space_id);

CREATE TRIGGER trg_roles_updated_at
BEFORE UPDATE ON roles
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE permissions (
  id          UUID         NOT NULL,
  org_id      UUID         NOT NULL,
  space_id    UUID         NOT NULL,
  code        VARCHAR(160) NOT NULL,
  name        VARCHAR(160) NOT NULL,
  description VARCHAR(255),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version     BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_permissions PRIMARY KEY (id),
  CONSTRAINT fk_permissions_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_permissions_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_permissions_org_space_code UNIQUE (org_id, space_id, code),
  CONSTRAINT uk_permissions_scope_id UNIQUE (org_id, space_id, id)
);

CREATE INDEX idx_permissions_org_space ON permissions (org_id, space_id);

CREATE TRIGGER trg_permissions_updated_at
BEFORE UPDATE ON permissions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE "groups" (
  id          UUID         NOT NULL,
  org_id      UUID         NOT NULL,
  space_id    UUID         NOT NULL,
  code        VARCHAR(120) NOT NULL,
  name        VARCHAR(160) NOT NULL,
  description VARCHAR(255),
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version     BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_groups PRIMARY KEY (id),
  CONSTRAINT fk_groups_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_groups_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_groups_org_space_code UNIQUE (org_id, space_id, code),
  CONSTRAINT uk_groups_scope_id UNIQUE (org_id, space_id, id)
);

CREATE INDEX idx_groups_org_space ON "groups" (org_id, space_id);

CREATE TRIGGER trg_groups_updated_at
BEFORE UPDATE ON "groups"
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE role_permissions (
  id            UUID        NOT NULL,
  org_id        UUID        NOT NULL,
  space_id      UUID        NOT NULL,
  role_id       UUID        NOT NULL,
  permission_id UUID        NOT NULL,
  effect        VARCHAR(8)  NOT NULL DEFAULT 'ALLOW',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version       BIGINT      NOT NULL DEFAULT 0,

  CONSTRAINT pk_role_permissions PRIMARY KEY (id),
  CONSTRAINT fk_rp_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_rp_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id),
  CONSTRAINT fk_rp_role_scope FOREIGN KEY (org_id, space_id, role_id)
    REFERENCES roles(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_rp_perm_scope FOREIGN KEY (org_id, space_id, permission_id)
    REFERENCES permissions(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT uk_rp_org_space_role_perm UNIQUE (org_id, space_id, role_id, permission_id)
);

CREATE INDEX idx_rp_org_space_role ON role_permissions (org_id, space_id, role_id);

CREATE TRIGGER trg_role_permissions_updated_at
BEFORE UPDATE ON role_permissions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE group_roles (
  id          UUID        NOT NULL,
  org_id      UUID        NOT NULL,
  space_id    UUID        NOT NULL,
  group_id    UUID        NOT NULL,
  role_id     UUID        NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  assigned_by UUID,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version     BIGINT      NOT NULL DEFAULT 0,

  CONSTRAINT pk_group_roles PRIMARY KEY (id),
  CONSTRAINT fk_gr_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_gr_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id),
  CONSTRAINT fk_gr_group_scope FOREIGN KEY (org_id, space_id, group_id)
    REFERENCES "groups"(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_gr_role_scope FOREIGN KEY (org_id, space_id, role_id)
    REFERENCES roles(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT uk_gr_org_space_group_role UNIQUE (org_id, space_id, group_id, role_id)
);

CREATE INDEX idx_gr_org_space_group ON group_roles (org_id, space_id, group_id);

CREATE TRIGGER trg_group_roles_updated_at
BEFORE UPDATE ON group_roles
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE group_members (
  id          UUID        NOT NULL,
  org_id      UUID        NOT NULL,
  space_id    UUID        NOT NULL,
  group_id    UUID        NOT NULL,
  user_id     UUID        NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  assigned_by UUID,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version     BIGINT      NOT NULL DEFAULT 0,

  CONSTRAINT pk_group_members PRIMARY KEY (id),
  CONSTRAINT fk_gm_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_gm_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id),
  CONSTRAINT fk_gm_group_scope FOREIGN KEY (org_id, space_id, group_id)
    REFERENCES "groups"(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_gm_user_scope FOREIGN KEY (org_id, space_id, user_id)
    REFERENCES users(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT uk_gm_org_space_group_user UNIQUE (org_id, space_id, group_id, user_id)
);

CREATE INDEX idx_gm_org_space_user ON group_members (org_id, space_id, user_id);

CREATE TRIGGER trg_group_members_updated_at
BEFORE UPDATE ON group_members
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_roles (
  id          UUID        NOT NULL,
  org_id      UUID        NOT NULL,
  space_id    UUID        NOT NULL,
  user_id     UUID        NOT NULL,
  role_id     UUID        NOT NULL,
  assigned_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  assigned_by UUID,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version     BIGINT      NOT NULL DEFAULT 0,

  CONSTRAINT pk_user_roles PRIMARY KEY (id),
  CONSTRAINT fk_ur_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_ur_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id),
  CONSTRAINT fk_ur_user_scope FOREIGN KEY (org_id, space_id, user_id)
    REFERENCES users(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_ur_role_scope FOREIGN KEY (org_id, space_id, role_id)
    REFERENCES roles(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT uk_ur_org_space_user_role UNIQUE (org_id, space_id, user_id, role_id)
);

CREATE INDEX idx_ur_org_space_user ON user_roles (org_id, space_id, user_id);

CREATE TRIGGER trg_user_roles_updated_at
BEFORE UPDATE ON user_roles
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_permissions (
  id            UUID        NOT NULL,
  org_id        UUID        NOT NULL,
  space_id      UUID        NOT NULL,
  user_id       UUID        NOT NULL,
  permission_id UUID        NOT NULL,
  effect        VARCHAR(8)  NOT NULL DEFAULT 'ALLOW',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version       BIGINT      NOT NULL DEFAULT 0,

  CONSTRAINT pk_user_permissions PRIMARY KEY (id),
  CONSTRAINT fk_up_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_up_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id),
  CONSTRAINT fk_up_user_scope FOREIGN KEY (org_id, space_id, user_id)
    REFERENCES users(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_up_perm_scope FOREIGN KEY (org_id, space_id, permission_id)
    REFERENCES permissions(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT uk_up_org_space_user_perm UNIQUE (org_id, space_id, user_id, permission_id)
);

CREATE INDEX idx_up_org_space_user ON user_permissions (org_id, space_id, user_id);

CREATE TRIGGER trg_user_permissions_updated_at
BEFORE UPDATE ON user_permissions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- MFA (org + space scope)
-- ============================================================================
CREATE TABLE user_mfa_factors (
  id           UUID        NOT NULL,
  org_id       UUID        NOT NULL,
  space_id     UUID        NOT NULL,
  user_id      UUID        NOT NULL,
  factor_type  VARCHAR(20) NOT NULL,
  label        VARCHAR(160),
  payload_json JSONB,
  enabled      BOOLEAN     NOT NULL DEFAULT FALSE,
  last_used_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version      BIGINT      NOT NULL DEFAULT 0,

  CONSTRAINT pk_user_mfa_factors PRIMARY KEY (id),
  CONSTRAINT fk_umf_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_umf_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id),
  CONSTRAINT fk_umf_user_scope FOREIGN KEY (org_id, space_id, user_id)
    REFERENCES users(org_id, space_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_umf_org_space_user_label UNIQUE (org_id, space_id, user_id, label)
);

CREATE INDEX idx_umf_org_space_user ON user_mfa_factors (org_id, space_id, user_id);

CREATE TRIGGER trg_user_mfa_factors_updated_at
BEFORE UPDATE ON user_mfa_factors
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_recovery_codes (
  id         UUID         NOT NULL,
  org_id     UUID         NOT NULL,
  space_id   UUID         NOT NULL,
  user_id    UUID         NOT NULL,
  code_hash  VARCHAR(255) NOT NULL,
  used       BOOLEAN      NOT NULL DEFAULT FALSE,
  used_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version    BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_user_recovery_codes PRIMARY KEY (id),
  CONSTRAINT fk_urc_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_urc_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id),
  CONSTRAINT fk_urc_user_scope FOREIGN KEY (org_id, space_id, user_id)
    REFERENCES users(org_id, space_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_urc_org_space_user_code UNIQUE (org_id, space_id, user_id, code_hash)
);

CREATE INDEX idx_urc_org_space_user ON user_recovery_codes (org_id, space_id, user_id);

CREATE TRIGGER trg_user_recovery_codes_updated_at
BEFORE UPDATE ON user_recovery_codes
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- OAUTH2 CLIENTS (org + space scope)
-- ============================================================================
CREATE TABLE oauth2_clients (
  id                         UUID         NOT NULL,
  org_id                     UUID         NOT NULL,
  space_id                   UUID         NOT NULL,
  client_id                  VARCHAR(128) NOT NULL,
  client_id_issued_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  client_name                VARCHAR(160) NOT NULL,
  client_type                VARCHAR(20)  NOT NULL DEFAULT 'CONFIDENTIAL',
  require_client_secret      BOOLEAN      NOT NULL DEFAULT TRUE,
  client_secret_hash         VARCHAR(255),
  client_secret_expires_at   TIMESTAMPTZ,
  token_endpoint_auth_method VARCHAR(64)  NOT NULL DEFAULT 'client_secret_basic',
  require_pkce               BOOLEAN      NOT NULL DEFAULT FALSE,
  require_consent            BOOLEAN      NOT NULL DEFAULT FALSE,
  jwks_uri                   VARCHAR(255),
  jwks_json                  TEXT,
  id_token_signed_alg        VARCHAR(32),
  access_token_ttl_seconds   INT,
  refresh_token_ttl_seconds  INT,
  id_token_ttl_seconds       INT,
  additional_settings        JSONB,
  created_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  version                    BIGINT       NOT NULL DEFAULT 0,

  CONSTRAINT pk_oauth2_clients PRIMARY KEY (id),
  CONSTRAINT fk_oauth2_clients_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_oauth2_clients_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_oauth2_clients_scope_id UNIQUE (org_id, space_id, id),
  CONSTRAINT ck_oauth2_clients_type CHECK (client_type IN ('CONFIDENTIAL','PUBLIC')),
  CONSTRAINT ck_oauth2_public_no_secret CHECK (
    client_type != 'PUBLIC' OR require_client_secret = FALSE
  ),
  CONSTRAINT ck_oauth2_secret_presence CHECK (
    require_client_secret = FALSE OR client_secret_hash IS NOT NULL
  )
);

CREATE UNIQUE INDEX uq_oauth2_clients_scope_client_id ON oauth2_clients(org_id, space_id, client_id);
CREATE INDEX idx_oauth2_clients_org_space ON oauth2_clients (org_id, space_id);

CREATE TRIGGER trg_oauth2_clients_updated_at
BEFORE UPDATE ON oauth2_clients
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE oauth2_client_scopes (
  id        UUID         NOT NULL,
  org_id    UUID         NOT NULL,
  space_id  UUID         NOT NULL,
  client_id UUID         NOT NULL,
  scope     VARCHAR(128) NOT NULL,

  CONSTRAINT pk_oauth2_client_scopes PRIMARY KEY (id),
  CONSTRAINT fk_ocs_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_ocs_client_scope FOREIGN KEY (org_id, space_id, client_id)
    REFERENCES oauth2_clients(org_id, space_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_ocs_client_scope UNIQUE (org_id, space_id, client_id, scope)
);

CREATE INDEX idx_ocs_client ON oauth2_client_scopes(org_id, space_id, client_id);

CREATE TABLE oauth2_client_grant_types (
  id         UUID        NOT NULL,
  org_id     UUID        NOT NULL,
  space_id   UUID        NOT NULL,
  client_id  UUID        NOT NULL,
  grant_type VARCHAR(64) NOT NULL,

  CONSTRAINT pk_oauth2_client_grant_types PRIMARY KEY (id),
  CONSTRAINT fk_ocg_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_ocg_client_scope FOREIGN KEY (org_id, space_id, client_id)
    REFERENCES oauth2_clients(org_id, space_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_ocg_client_grant UNIQUE (org_id, space_id, client_id, grant_type)
);

CREATE INDEX idx_ocg_client ON oauth2_client_grant_types(org_id, space_id, client_id);

CREATE TABLE oauth2_client_redirect_uris (
  id        UUID         NOT NULL,
  org_id    UUID         NOT NULL,
  space_id  UUID         NOT NULL,
  client_id UUID         NOT NULL,
  uri       VARCHAR(255) NOT NULL,

  CONSTRAINT pk_oauth2_client_redirect_uris PRIMARY KEY (id),
  CONSTRAINT fk_ocr_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_ocr_client_scope FOREIGN KEY (org_id, space_id, client_id)
    REFERENCES oauth2_clients(org_id, space_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_ocr_client_redirect UNIQUE (org_id, space_id, client_id, uri)
);

CREATE INDEX idx_ocr_client ON oauth2_client_redirect_uris(org_id, space_id, client_id);

CREATE TABLE oauth2_client_post_logout_redirect_uris (
  id        UUID         NOT NULL,
  org_id    UUID         NOT NULL,
  space_id  UUID         NOT NULL,
  client_id UUID         NOT NULL,
  uri       VARCHAR(255) NOT NULL,

  CONSTRAINT pk_oauth2_client_plr_uris PRIMARY KEY (id),
  CONSTRAINT fk_ocplr_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_ocplr_client_scope FOREIGN KEY (org_id, space_id, client_id)
    REFERENCES oauth2_clients(org_id, space_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_ocplr_client_post_logout UNIQUE (org_id, space_id, client_id, uri)
);

CREATE INDEX idx_ocplr_client ON oauth2_client_post_logout_redirect_uris(org_id, space_id, client_id);

CREATE TABLE oauth2_client_cors_origins (
  id        UUID         NOT NULL,
  org_id    UUID         NOT NULL,
  space_id  UUID         NOT NULL,
  client_id UUID         NOT NULL,
  origin    VARCHAR(255) NOT NULL,

  CONSTRAINT pk_oauth2_client_cors_origins PRIMARY KEY (id),
  CONSTRAINT fk_occo_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_occo_client_scope FOREIGN KEY (org_id, space_id, client_id)
    REFERENCES oauth2_clients(org_id, space_id, id) ON DELETE CASCADE,
  CONSTRAINT uk_occo_client_origin UNIQUE (org_id, space_id, client_id, origin)
);

CREATE INDEX idx_occo_client ON oauth2_client_cors_origins(org_id, space_id, client_id);

CREATE TABLE oauth2_client_secret_history (
  id          UUID         NOT NULL,
  org_id      UUID         NOT NULL,
  space_id    UUID         NOT NULL,
  client_id   UUID         NOT NULL,
  secret_hash VARCHAR(255) NOT NULL,
  created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  expires_at  TIMESTAMPTZ,
  revoked_at  TIMESTAMPTZ,

  CONSTRAINT pk_oauth2_client_secret_history PRIMARY KEY (id),
  CONSTRAINT fk_ocsh_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_ocsh_client_scope FOREIGN KEY (org_id, space_id, client_id)
    REFERENCES oauth2_clients(org_id, space_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_ocsh_client ON oauth2_client_secret_history(org_id, space_id, client_id);

-- ============================================================================
-- USER ↔ CLIENT ASSOCIATIONS (org + space scope)
-- ============================================================================
CREATE TABLE user_client_associations (
  org_id     UUID        NOT NULL,
  space_id   UUID        NOT NULL,
  client_id  UUID        NOT NULL,
  user_id    UUID        NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_by VARCHAR(255),

  CONSTRAINT pk_user_client_associations PRIMARY KEY (org_id, space_id, client_id, user_id),
  CONSTRAINT fk_uca_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_uca_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,
  CONSTRAINT fk_uca_client_scope FOREIGN KEY (org_id, space_id, client_id)
    REFERENCES oauth2_clients(org_id, space_id, id) ON DELETE CASCADE,
  CONSTRAINT fk_uca_user_scope FOREIGN KEY (org_id, space_id, user_id)
    REFERENCES users(org_id, space_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_uca_user ON user_client_associations (org_id, space_id, user_id);

-- ============================================================================
-- CLIENT ROLE PERMISSIONS (org + space scope)
-- ============================================================================
CREATE TABLE client_role_permissions (
  id            UUID        NOT NULL,
  org_id        UUID        NOT NULL,
  space_id      UUID        NOT NULL,
  client_id     UUID        NOT NULL,
  role_id       UUID        NOT NULL,
  permission_id UUID        NOT NULL,
  effect        VARCHAR(8)  NOT NULL DEFAULT 'ALLOW',
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  version       BIGINT      NOT NULL DEFAULT 0,

  CONSTRAINT pk_client_role_permissions PRIMARY KEY (id),
  CONSTRAINT fk_crp_org FOREIGN KEY (org_id) REFERENCES organizations(id),
  CONSTRAINT fk_crp_space_scope FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,
  CONSTRAINT fk_crp_client_scope FOREIGN KEY (org_id, space_id, client_id)
    REFERENCES oauth2_clients(org_id, space_id, id) ON DELETE CASCADE,
  CONSTRAINT fk_crp_role_scope FOREIGN KEY (org_id, space_id, role_id)
    REFERENCES roles(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT fk_crp_perm_scope FOREIGN KEY (org_id, space_id, permission_id)
    REFERENCES permissions(org_id, space_id, id) ON DELETE RESTRICT,
  CONSTRAINT uk_crp_org_space_client_role_perm UNIQUE (org_id, space_id, client_id, role_id, permission_id)
);

CREATE INDEX idx_crp_org_space_client ON client_role_permissions (org_id, space_id, client_id);

CREATE TRIGGER trg_client_role_permissions_updated_at
BEFORE UPDATE ON client_role_permissions
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================================
-- ASSIGNMENTS (org scope with optional space scope)
-- - identity_id référence takibo_identities(identity_id) avec scope org
-- ============================================================================

CREATE TABLE role_assignments (
  id                UUID         NOT NULL,
  org_id            UUID         NOT NULL,
  space_id          UUID,
  identity_type     VARCHAR(30)  NOT NULL,
  identity_id       UUID         NOT NULL,

  role_code         VARCHAR(120),
  business_role_id  UUID,

  assignment_source VARCHAR(120)  NOT NULL DEFAULT 'SYSTEM',
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  created_by        UUID,
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_by        UUID,

  CONSTRAINT pk_role_assignments PRIMARY KEY (id),

  CONSTRAINT fk_ra_org
    FOREIGN KEY (org_id) REFERENCES organizations(id),

  CONSTRAINT fk_ra_space_scope
    FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,

  CONSTRAINT fk_ra_identity_scope
    FOREIGN KEY (org_id, identity_id)
    REFERENCES takibo_identities(org_id, identity_id) ON DELETE CASCADE,

  CONSTRAINT fk_ra_business_role_scope
    FOREIGN KEY (org_id, space_id, business_role_id)
    REFERENCES roles(org_id, space_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_ra_space_scope
  ON role_assignments(org_id, space_id, identity_type, identity_id, role_code, business_role_id)
  WHERE space_id IS NOT NULL;

CREATE UNIQUE INDEX uq_ra_org_scope
  ON role_assignments(org_id, identity_type, identity_id, role_code, business_role_id)
  WHERE space_id IS NULL;

CREATE INDEX idx_ra_identity ON role_assignments(org_id, identity_type, identity_id);



CREATE TABLE group_assignments (
  id                UUID         NOT NULL,
  org_id            UUID         NOT NULL,
  space_id          UUID,
  identity_type     VARCHAR(30)  NOT NULL,
  identity_id       UUID         NOT NULL,

  group_code        VARCHAR(120),
  business_group_id UUID,

  group_source      VARCHAR(30)  NOT NULL DEFAULT 'SYSTEM',
  created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  created_by        UUID,
  updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
  updated_by        UUID,

  CONSTRAINT pk_group_assignments PRIMARY KEY (id),

  CONSTRAINT fk_ga_org
    FOREIGN KEY (org_id) REFERENCES organizations(id),

  CONSTRAINT fk_ga_space_scope
    FOREIGN KEY (org_id, space_id)
    REFERENCES spaces(org_id, id) ON DELETE CASCADE,

  CONSTRAINT fk_ga_identity_scope
    FOREIGN KEY (org_id, identity_id)
    REFERENCES takibo_identities(org_id, identity_id) ON DELETE CASCADE,

  CONSTRAINT fk_ga_business_group_scope
    FOREIGN KEY (org_id, space_id, business_group_id)
    REFERENCES "groups"(org_id, space_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX uq_ga_space_scope
  ON group_assignments(org_id, space_id, identity_type, identity_id, group_code, business_group_id)
  WHERE space_id IS NOT NULL;

CREATE UNIQUE INDEX uq_ga_org_scope
  ON group_assignments(org_id, identity_type, identity_id, group_code, business_group_id)
  WHERE space_id IS NULL;

CREATE INDEX idx_ga_identity ON group_assignments(org_id, identity_type, identity_id);

-- ============================================================================
-- SCHEMA DOCUMENTATION
-- ============================================================================

COMMENT ON SCHEMA public IS 'Takibo IAM v1.0 - Multi-tenant identity and access management';

COMMENT ON TABLE organizations IS 'Root tenant entity';
COMMENT ON TABLE spaces IS 'Workspaces within organizations';
COMMENT ON TABLE accounts IS 'User accounts (org-scoped, email-based)';
COMMENT ON TABLE takibo_identities IS 'Identity layer (Phase A: identity_id = account_id)';
COMMENT ON TABLE users IS 'Space-scoped user profiles';
COMMENT ON TABLE roles IS 'RBAC roles (space-scoped)';
COMMENT ON TABLE permissions IS 'RBAC permissions (space-scoped)';
COMMENT ON TABLE oauth2_clients IS 'OAuth2/OIDC client applications';

-- ============================================================================
-- RBAC POLICY DOCUMENTATION
-- ============================================================================
-- Permission Evaluation Rule: DENY takes precedence over ALLOW
-- Prevents privilege escalation through conflicting permissions
-- ============================================================================