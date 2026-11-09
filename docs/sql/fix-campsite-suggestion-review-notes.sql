-- Optional repair for installations with the original ambiguous notes parameter.
-- Run manually as administrator. Preserves the function signature and existing permissions.
BEGIN;
CREATE OR REPLACE FUNCTION kampive.accept_campsite_suggestion(suggestion UUID, reviewer UUID, category UUID, city UUID, notes TEXT)
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
COMMIT;
