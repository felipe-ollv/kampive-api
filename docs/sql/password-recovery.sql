-- Apply manually after email-templates.sql. No automatic DDL.
BEGIN;
-- Reuses user_credential.password_changed_at from the base schema.
CREATE TABLE kampive.password_recovery (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 uuid_password_recovery UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
 uuid_app_user UUID NOT NULL REFERENCES kampive.app_user(uuid_app_user),
 token_hash CHAR(64) NOT NULL UNIQUE,
 expires_at TIMESTAMPTZ NOT NULL,
 consumed_at TIMESTAMPTZ,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_password_recovery_user ON kampive.password_recovery(uuid_app_user,created_at DESC);
CREATE TABLE kampive.password_email_job (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 uuid_password_email_job UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
 uuid_app_user UUID NOT NULL REFERENCES kampive.app_user(uuid_app_user),
 template_code VARCHAR(60) NOT NULL CHECK(template_code IN ('password-reset','password-changed')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 sent_at TIMESTAMPTZ,
 attempts INTEGER NOT NULL DEFAULT 0,
 failed BOOLEAN NOT NULL DEFAULT false,
 uuid_password_recovery UUID REFERENCES kampive.password_recovery(uuid_password_recovery)
);
CREATE INDEX ix_password_email_pending ON kampive.password_email_job(available_at,id) WHERE sent_at IS NULL AND NOT failed;
REVOKE ALL ON kampive.password_recovery,kampive.password_email_job FROM PUBLIC;
GRANT SELECT,INSERT,UPDATE,DELETE ON kampive.password_recovery,kampive.password_email_job TO kampive_app;
GRANT USAGE,SELECT ON SEQUENCE kampive.password_recovery_id_seq,kampive.password_email_job_id_seq TO kampive_app;
COMMIT;
