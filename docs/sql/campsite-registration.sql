-- Apply manually after campsite-community.sql, before deploying the registration endpoint.
BEGIN;
ALTER TABLE kampive.campsite ADD COLUMN uuid_submitted_by UUID REFERENCES kampive.app_user(uuid_app_user);
CREATE INDEX ix_campsite_submitted_by ON kampive.campsite(uuid_submitted_by);
COMMENT ON COLUMN kampive.campsite.uuid_submitted_by IS 'Author of the full registration; grants no ownership or management rights';
COMMIT;
