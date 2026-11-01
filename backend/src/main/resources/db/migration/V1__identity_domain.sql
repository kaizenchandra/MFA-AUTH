CREATE TABLE app_user (
  id uuid PRIMARY KEY,
  issuer varchar(512) NOT NULL,
  subject varchar(255) NOT NULL,
  status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  UNIQUE (issuer, subject)
);
CREATE TABLE organization (
  id uuid PRIMARY KEY,
  slug varchar(63) NOT NULL UNIQUE,
  name varchar(160) NOT NULL,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE organization_security_policy (
  organization_id uuid PRIMARY KEY REFERENCES organization(id),
  sso_only boolean NOT NULL DEFAULT false,
  mfa_mode varchar(24) NOT NULL DEFAULT 'MANDATORY' CHECK (mfa_mode IN ('OPTIONAL','MANDATORY','TRUSTED_EXTERNAL')),
  jit_enabled boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0
);
CREATE TABLE organization_identity_provider (
  organization_id uuid PRIMARY KEY REFERENCES organization(id),
  alias varchar(100) NOT NULL UNIQUE,
  protocol varchar(8) NOT NULL CHECK (protocol IN ('OIDC','SAML')),
  issuer varchar(512) NOT NULL,
  tenant_id varchar(100),
  enabled boolean NOT NULL DEFAULT false,
  -- Configuration is approved by an operator; arbitrary metadata URLs are never fetched by this API.
  approved boolean NOT NULL DEFAULT false,
  version bigint NOT NULL DEFAULT 0
);
CREATE TABLE organization_domain (
  domain varchar(253) PRIMARY KEY,
  organization_id uuid NOT NULL REFERENCES organization(id),
  verified_at timestamptz NOT NULL
);
CREATE TABLE organization_membership (
  organization_id uuid NOT NULL REFERENCES organization(id),
  user_id uuid NOT NULL REFERENCES app_user(id),
  role varchar(16) NOT NULL CHECK (role IN ('OWNER','ADMIN','MEMBER')),
  status varchar(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED')),
  created_at timestamptz NOT NULL DEFAULT now(),
  version bigint NOT NULL DEFAULT 0,
  PRIMARY KEY (organization_id,user_id)
);
CREATE INDEX membership_principal ON organization_membership(user_id,status);
CREATE TABLE invitation (
  id uuid PRIMARY KEY,
  organization_id uuid NOT NULL REFERENCES organization(id),
  token_hash char(64) NOT NULL UNIQUE,
  email varchar(254) NOT NULL,
  role varchar(16) NOT NULL CHECK (role IN ('ADMIN','MEMBER')),
  expires_at timestamptz NOT NULL,
  accepted_by uuid REFERENCES app_user(id),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE security_audit_event (
  id uuid PRIMARY KEY,
  actor_id uuid REFERENCES app_user(id),
  organization_id uuid REFERENCES organization(id),
  event_type varchar(64) NOT NULL,
  outcome varchar(16) NOT NULL,
  correlation_id varchar(64) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_org_time ON security_audit_event(organization_id,created_at DESC);
CREATE INDEX audit_actor_time ON security_audit_event(actor_id,created_at DESC);
-- Credentials, contacts, broker identities, sessions, TOTP and recovery codes belong to Keycloak.
