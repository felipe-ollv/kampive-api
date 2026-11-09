-- Apply once, manually as administrator, after campsite-community.sql.
-- Additive, transactional. Does not backfill notifications or expose historical review_notes.
BEGIN;
ALTER TABLE kampive.campsite_suggestion ADD COLUMN public_response TEXT
    CHECK (public_response IS NULL OR length(public_response) <= 4000);
ALTER TABLE kampive.campsite_claim ADD COLUMN public_response TEXT
    CHECK (public_response IS NULL OR length(public_response) <= 4000);
COMMENT ON COLUMN kampive.campsite_suggestion.public_response IS 'Explicit user-facing response; review_notes remains private';
COMMENT ON COLUMN kampive.campsite_claim.public_response IS 'Explicit user-facing response; review_notes remains private';
CREATE INDEX ix_suggestion_author_date ON kampive.campsite_suggestion(uuid_suggested_by,created_at DESC,uuid_campsite_suggestion);
CREATE INDEX ix_claim_author_date ON kampive.campsite_claim(uuid_claimant,created_at DESC,uuid_campsite_claim);
ALTER TABLE kampive.notification ADD COLUMN request_type VARCHAR(20), ADD COLUMN request_uuid UUID,
    ADD CONSTRAINT ck_notification_request CHECK (
        (request_type IS NULL AND request_uuid IS NULL) OR
        (request_type IS NOT NULL AND request_type IN ('suggestion','claim') AND request_uuid IS NOT NULL));
CREATE UNIQUE INDEX ix_notification_request ON kampive.notification(request_type,request_uuid);

-- Runs as the administrative caller. No SECURITY DEFINER and no app moderation privileges.
CREATE FUNCTION kampive.notify_request_result() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE recipient UUID; request_id UUID; kind TEXT; subject TEXT; notification_id UUID;
BEGIN
    IF OLD.status IS NOT DISTINCT FROM NEW.status OR NEW.status='pending_review' THEN RETURN NEW; END IF;
    IF TG_TABLE_NAME='campsite_suggestion' THEN
        recipient := NEW.uuid_suggested_by; request_id := NEW.uuid_campsite_suggestion; kind := 'suggestion';
        subject := CASE WHEN NEW.status='accepted' THEN 'Indicação aceita' ELSE 'Indicação não aprovada' END;
    ELSE
        recipient := NEW.uuid_claimant; request_id := NEW.uuid_campsite_claim; kind := 'claim';
        subject := CASE WHEN NEW.status='approved' THEN 'Vínculo de proprietário aprovado' ELSE 'Vínculo de proprietário não aprovado' END;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM kampive.app_user WHERE uuid_app_user=recipient AND deactivated_at IS NULL) THEN RETURN NEW; END IF;
    INSERT INTO kampive.notification(title,message,request_type,request_uuid)
        VALUES (subject,'A equipe concluiu a análise. Consulte os detalhes em Minhas solicitações.',kind,request_id)
        ON CONFLICT (request_type,request_uuid) DO NOTHING RETURNING uuid_notification INTO notification_id;
    IF notification_id IS NOT NULL THEN
        INSERT INTO kampive.user_notification(uuid_notification,uuid_app_user) VALUES (notification_id,recipient);
    END IF;
    RETURN NEW;
END $$;
REVOKE ALL ON FUNCTION kampive.notify_request_result() FROM PUBLIC, kampive_app;
CREATE TRIGGER tr_suggestion_result AFTER UPDATE OF status ON kampive.campsite_suggestion
    FOR EACH ROW EXECUTE FUNCTION kampive.notify_request_result();
CREATE TRIGGER tr_claim_result AFTER UPDATE OF status ON kampive.campsite_claim
    FOR EACH ROW EXECUTE FUNCTION kampive.notify_request_result();
-- Existing table SELECT grants include the added columns. No new write permission for kampive_app.
COMMIT;
