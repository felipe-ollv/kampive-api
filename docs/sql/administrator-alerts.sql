-- Apply once as database administrator, after email-verification.sql and campsite-registration.sql.
-- No grants, retroactive notifications, or recipient addresses are seeded.
BEGIN;
CREATE TABLE kampive.administrator (
    uuid_app_user UUID PRIMARY KEY REFERENCES kampive.app_user(uuid_app_user),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    grant_reason TEXT NOT NULL CHECK (btrim(grant_reason) <> ''),
    revoked_at TIMESTAMPTZ,
    request_email_enabled BOOLEAN NOT NULL DEFAULT true
);
REVOKE ALL ON kampive.administrator FROM PUBLIC, kampive_app;
GRANT SELECT ON kampive.administrator TO kampive_app;

CREATE TABLE kampive.admin_request_email (
    uuid_admin_request_email UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uuid_recipient UUID NOT NULL REFERENCES kampive.administrator(uuid_app_user),
    request_type VARCHAR(20) NOT NULL CHECK (request_type IN ('suggestion','registration','claim','review','test')),
    request_uuid UUID NOT NULL,
    campsite_name VARCHAR(180) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    available_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts>=0),
    sent_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ
);
CREATE INDEX ix_admin_request_email_pending ON kampive.admin_request_email(available_at,created_at)
    WHERE sent_at IS NULL AND cancelled_at IS NULL;
REVOKE ALL ON kampive.admin_request_email FROM PUBLIC, kampive_app;
GRANT SELECT ON kampive.admin_request_email TO kampive_app;
GRANT UPDATE (available_at,attempts,sent_at,cancelled_at) ON kampive.admin_request_email TO kampive_app;

CREATE FUNCTION kampive.queue_admin_request_email() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE
    kind TEXT;
    request_id UUID;
    camping_name TEXT;
BEGIN
    -- Only new pending items and real transitions back into analysis produce a job.
    IF TG_OP = 'UPDATE' AND NEW.status IS NOT DISTINCT FROM OLD.status THEN RETURN NEW; END IF;
    IF TG_TABLE_NAME='review' THEN
        IF NEW.status <> 'pending' THEN RETURN NEW; END IF;
        kind := 'review'; request_id := NEW.uuid_review;
        SELECT name INTO camping_name FROM kampive.campsite WHERE uuid_campsite=NEW.uuid_campsite;
    ELSIF TG_TABLE_NAME='campsite' THEN
        IF NEW.status <> 'pending_review' THEN RETURN NEW; END IF;
        kind := 'registration'; request_id := NEW.uuid_campsite; camping_name := NEW.name;
    ELSIF TG_TABLE_NAME='campsite_suggestion' THEN
        IF NEW.status <> 'pending_review' THEN RETURN NEW; END IF;
        kind := 'suggestion'; request_id := NEW.uuid_campsite_suggestion; camping_name := NEW.name;
    ELSIF TG_TABLE_NAME='campsite_claim' THEN
        IF NEW.status <> 'pending_review' THEN RETURN NEW; END IF;
        kind := 'claim'; request_id := NEW.uuid_campsite_claim;
        SELECT name INTO camping_name FROM kampive.campsite WHERE uuid_campsite=NEW.uuid_campsite;
    ELSE RAISE EXCEPTION 'Unsupported notification source';
    END IF;
    INSERT INTO kampive.admin_request_email(uuid_recipient,request_type,request_uuid,campsite_name)
      SELECT a.uuid_app_user,kind,request_id,left(coalesce(camping_name,'Camping'),180)
      FROM kampive.administrator a JOIN kampive.app_user u USING(uuid_app_user)
      WHERE a.revoked_at IS NULL AND a.request_email_enabled
        AND u.deactivated_at IS NULL AND u.email_verified_at IS NOT NULL;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION kampive.queue_admin_request_email() FROM PUBLIC, kampive_app;
CREATE TRIGGER admin_email_suggestion AFTER INSERT OR UPDATE OF status ON kampive.campsite_suggestion
    FOR EACH ROW EXECUTE FUNCTION kampive.queue_admin_request_email();
CREATE TRIGGER admin_email_claim AFTER INSERT OR UPDATE OF status ON kampive.campsite_claim
    FOR EACH ROW EXECUTE FUNCTION kampive.queue_admin_request_email();
CREATE TRIGGER admin_email_registration AFTER INSERT OR UPDATE OF status ON kampive.campsite
    FOR EACH ROW EXECUTE FUNCTION kampive.queue_admin_request_email();
CREATE TRIGGER admin_email_review AFTER INSERT OR UPDATE OF status ON kampive.review
    FOR EACH ROW EXECUTE FUNCTION kampive.queue_admin_request_email();

ALTER TABLE kampive.email_template DROP CONSTRAINT email_template_code_check;
ALTER TABLE kampive.email_template ADD CONSTRAINT email_template_code_check
    CHECK(code IN ('password-reset','password-changed','email-verification','admin-request'));
INSERT INTO kampive.email_template(code,locale,version,active,subject,html_body,text_body)
VALUES ('admin-request','pt-BR',1,true,'Kampive · {{event_title}}',
$html$<!doctype html><html lang="pt-BR"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;background:#f7f8f5;font-family:Arial,sans-serif;color:#243b31"><table role="presentation" width="100%"><tr><td align="center" style="padding:32px 16px"><table role="presentation" width="100%" style="max-width:560px;background:#fff;border:1px solid #e5eae2;border-radius:12px"><tr><td style="padding:32px">
<p style="color:#64785a;font-size:14px">KAMPIVE · GESTÃO</p><h1 style="font-size:25px">{{event_title}}</h1>
<p style="line-height:1.8"><strong>Tipo:</strong> {{request_type}}<br><strong>Camping / assunto:</strong> {{campsite_name}}</p>
<p style="font-size:13px;line-height:1.8;overflow-wrap:anywhere"><strong>Protocolo:</strong><br>{{request_id}}</p>
<p style="color:#78827c;font-size:12px;line-height:1.8">Aviso automático destinado à administração do Kampive. Contatos pessoais, evidências e documentos permanecem no ambiente de gestão.</p>
</td></tr></table></td></tr></table></body></html>$html$,
$text${{event_title}}

Tipo: {{request_type}}
Camping / assunto: {{campsite_name}}
Protocolo: {{request_id}}

Aviso automático destinado à administração do Kampive.
Contatos pessoais, evidências e documentos permanecem no ambiente de gestão.
$text$);
COMMIT;
