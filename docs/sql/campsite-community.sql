-- Apply manually as database administrator after the existing kampive-infra schema.
-- No automatic DDL. Existing owner values are preserved, but management requires an approved claim.
BEGIN;
ALTER TABLE kampive.campsite ALTER COLUMN uuid_owner DROP NOT NULL;
CREATE TABLE kampive.campsite_suggestion (
    uuid_campsite_suggestion UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uuid_suggested_by UUID NOT NULL REFERENCES kampive.app_user(uuid_app_user),
    name VARCHAR(180) NOT NULL CHECK (length(btrim(name)) >= 3),
    location VARCHAR(2000) NOT NULL CHECK (btrim(location) <> ''),
    contact VARCHAR(500) NOT NULL DEFAULT '',
    notes VARCHAR(4000) NOT NULL DEFAULT '',
    status VARCHAR(30) NOT NULL DEFAULT 'pending_review' CHECK (status IN ('pending_review','accepted','rejected')),
    uuid_campsite UUID REFERENCES kampive.campsite(uuid_campsite),
    uuid_reviewed_by UUID REFERENCES kampive.app_user(uuid_app_user),
    reviewed_at TIMESTAMPTZ,
    review_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status='pending_review') = (reviewed_at IS NULL)),
    CHECK ((reviewed_at IS NULL) = (uuid_reviewed_by IS NULL)),
    CHECK ((status='accepted') = (uuid_campsite IS NOT NULL))
);
CREATE TABLE kampive.campsite_claim (
    uuid_campsite_claim UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uuid_campsite UUID NOT NULL REFERENCES kampive.campsite(uuid_campsite),
    uuid_claimant UUID NOT NULL REFERENCES kampive.app_user(uuid_app_user),
    contact VARCHAR(500) NOT NULL CHECK (btrim(contact) <> ''),
    evidence VARCHAR(4000) NOT NULL CHECK (btrim(evidence) <> ''),
    status VARCHAR(30) NOT NULL DEFAULT 'pending_review' CHECK (status IN ('pending_review','approved','rejected')),
    uuid_reviewed_by UUID REFERENCES kampive.app_user(uuid_app_user),
    reviewed_at TIMESTAMPTZ,
    review_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status='pending_review') = (reviewed_at IS NULL)),
    CHECK ((reviewed_at IS NULL) = (uuid_reviewed_by IS NULL)),
    CHECK (status <> 'approved' OR uuid_claimant IS DISTINCT FROM uuid_reviewed_by)
);
CREATE UNIQUE INDEX ix_campsite_claim_pending ON kampive.campsite_claim(uuid_campsite,uuid_claimant) WHERE status='pending_review';
CREATE UNIQUE INDEX ix_campsite_claim_approved ON kampive.campsite_claim(uuid_campsite) WHERE status='approved';
CREATE INDEX ix_campsite_suggestion_queue ON kampive.campsite_suggestion(status,created_at);
CREATE INDEX ix_campsite_claim_queue ON kampive.campsite_claim(status,created_at);

-- Administrative helpers run with the caller's database permissions (not SECURITY DEFINER).
CREATE FUNCTION kampive.accept_campsite_suggestion(suggestion UUID, reviewer UUID, category UUID, city UUID, notes TEXT)
RETURNS UUID LANGUAGE plpgsql AS $$
DECLARE item kampive.campsite_suggestion; campsite UUID;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM kampive.app_user WHERE uuid_app_user=reviewer AND deactivated_at IS NULL) THEN
        RAISE EXCEPTION 'Active reviewer required';
    END IF;
    SELECT * INTO item FROM kampive.campsite_suggestion WHERE uuid_campsite_suggestion=suggestion FOR UPDATE;
    IF NOT FOUND OR item.status <> 'pending_review' THEN RAISE EXCEPTION 'Suggestion is not pending'; END IF;
    INSERT INTO kampive.campsite(uuid_campsite_category,uuid_city,name,location_notes)
        VALUES (category,city,item.name,item.location) RETURNING uuid_campsite INTO campsite;
    UPDATE kampive.campsite_suggestion SET status='accepted',uuid_campsite=campsite,
        uuid_reviewed_by=reviewer,reviewed_at=now(),review_notes=accept_campsite_suggestion.notes WHERE uuid_campsite_suggestion=suggestion;
    RETURN campsite;
END $$;
CREATE FUNCTION kampive.approve_campsite_claim(claim UUID, reviewer UUID, notes TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
DECLARE item kampive.campsite_claim; target UUID; current_owner UUID;
BEGIN
    SELECT uuid_campsite INTO target FROM kampive.campsite_claim WHERE uuid_campsite_claim=claim;
    IF NOT FOUND THEN RAISE EXCEPTION 'Claim not found'; END IF;
    -- Same lock order as the API: campsite first, claim second.
    SELECT uuid_owner INTO current_owner FROM kampive.campsite WHERE uuid_campsite=target AND status='published' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'Published campsite required'; END IF;
    SELECT * INTO item FROM kampive.campsite_claim WHERE uuid_campsite_claim=claim FOR UPDATE;
    IF item.status <> 'pending_review' OR (current_owner IS NOT NULL AND current_owner <> item.uuid_claimant)
       OR EXISTS (SELECT 1 FROM kampive.campsite_claim WHERE uuid_campsite=target AND status='approved') THEN
        RAISE EXCEPTION 'Ownership conflict';
    END IF;
    IF reviewer=item.uuid_claimant OR NOT EXISTS (SELECT 1 FROM kampive.app_user WHERE uuid_app_user=reviewer AND deactivated_at IS NULL)
       OR NOT EXISTS (SELECT 1 FROM kampive.app_user WHERE uuid_app_user=item.uuid_claimant AND deactivated_at IS NULL) THEN
        RAISE EXCEPTION 'Independent active reviewer and active claimant required';
    END IF;
    UPDATE kampive.campsite SET uuid_owner=item.uuid_claimant WHERE uuid_campsite=target;
    UPDATE kampive.campsite_claim SET status='approved',uuid_reviewed_by=reviewer,reviewed_at=now(),review_notes=notes
        WHERE uuid_campsite_claim=claim;
    UPDATE kampive.campsite_claim SET status='rejected',uuid_reviewed_by=reviewer,reviewed_at=now(),review_notes='Another claim was approved'
        WHERE uuid_campsite=target AND status='pending_review';
END $$;
REVOKE ALL ON FUNCTION kampive.accept_campsite_suggestion(UUID,UUID,UUID,UUID,TEXT) FROM PUBLIC, kampive_app;
REVOKE ALL ON FUNCTION kampive.approve_campsite_claim(UUID,UUID,TEXT) FROM PUBLIC, kampive_app;
-- Explicit grants are needed on existing installations even without default privileges.
REVOKE ALL ON kampive.campsite_suggestion, kampive.campsite_claim FROM kampive_app;
GRANT SELECT ON kampive.campsite_suggestion, kampive.campsite_claim TO kampive_app;
GRANT INSERT (uuid_suggested_by,name,location,contact,notes) ON kampive.campsite_suggestion TO kampive_app;
GRANT INSERT (uuid_campsite,uuid_claimant,contact,evidence), UPDATE (contact) ON kampive.campsite_claim TO kampive_app;
COMMIT;
