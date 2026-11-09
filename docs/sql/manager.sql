-- Apply once after administrator-alerts.sql. Administrative functions verify membership.
BEGIN;
CREATE TABLE kampive.admin_audit (
 id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
 actor UUID NOT NULL REFERENCES kampive.app_user(uuid_app_user),
 kind TEXT NOT NULL CHECK(kind IN ('suggestion','claim','registration','camping','review')),
 target UUID NOT NULL,
 action TEXT NOT NULL,
 notes TEXT NOT NULL DEFAULT '' CHECK(length(notes)<=4000),
 response TEXT NOT NULL DEFAULT '' CHECK(length(response)<=4000),
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX ix_admin_audit_target ON kampive.admin_audit(kind,target,id);
REVOKE ALL ON kampive.admin_audit FROM PUBLIC,kampive_app;
GRANT SELECT ON kampive.admin_audit TO kampive_app;
ALTER TABLE kampive.campsite ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE kampive.campsite_suggestion ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE kampive.campsite_claim ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE kampive.review ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
CREATE FUNCTION kampive.bump_revision() RETURNS trigger LANGUAGE plpgsql SET search_path=pg_catalog,pg_temp AS $$
BEGIN NEW.revision:=OLD.revision+1; RETURN NEW; END $$;
REVOKE ALL ON FUNCTION kampive.bump_revision() FROM PUBLIC,kampive_app;
CREATE TRIGGER revision_campsite BEFORE UPDATE ON kampive.campsite FOR EACH ROW EXECUTE FUNCTION kampive.bump_revision();
CREATE TRIGGER revision_suggestion BEFORE UPDATE ON kampive.campsite_suggestion FOR EACH ROW EXECUTE FUNCTION kampive.bump_revision();
CREATE TRIGGER revision_claim BEFORE UPDATE ON kampive.campsite_claim FOR EACH ROW EXECUTE FUNCTION kampive.bump_revision();
CREATE TRIGGER revision_review BEFORE UPDATE ON kampive.review FOR EACH ROW EXECUTE FUNCTION kampive.bump_revision();

CREATE FUNCTION kampive.assert_administrator(p_actor UUID) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
BEGIN
 PERFORM 1 FROM kampive.administrator a JOIN kampive.app_user u USING(uuid_app_user)
 WHERE a.uuid_app_user=p_actor AND a.revoked_at IS NULL AND u.deactivated_at IS NULL AND u.email_verified_at IS NOT NULL
 FOR SHARE OF a,u;
 IF NOT FOUND THEN RAISE EXCEPTION 'Administrator required' USING ERRCODE='42501'; END IF;
END $$;
REVOKE ALL ON FUNCTION kampive.assert_administrator(UUID) FROM PUBLIC,kampive_app;

CREATE FUNCTION kampive.manager_publishable(p_id UUID) RETURNS boolean
LANGUAGE sql SET search_path=pg_catalog,pg_temp AS $$
 SELECT EXISTS(SELECT 1 FROM kampive.campsite c WHERE c.uuid_campsite=p_id AND c.uuid_city IS NOT NULL
 AND nullif(btrim(c.street_address),'') IS NOT NULL AND nullif(btrim(c.description),'') IS NOT NULL
 AND c.latitude IS NOT NULL AND c.longitude IS NOT NULL
 AND EXISTS(SELECT 1 FROM kampive.campsite_photo p WHERE p.uuid_campsite=p_id)
 AND EXISTS(SELECT 1 FROM kampive.campsite_contact p WHERE p.uuid_campsite=p_id))
$$;
REVOKE ALL ON FUNCTION kampive.manager_publishable(UUID) FROM PUBLIC,kampive_app;

CREATE FUNCTION kampive.manager_decide(p_actor UUID,p_kind TEXT,p_id UUID,p_version BIGINT,p_accept BOOLEAN,
 p_notes TEXT,p_response TEXT,p_category UUID,p_city UUID,p_existing UUID) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE s kampive.campsite_suggestion; cl kampive.campsite_claim; c kampive.campsite; target UUID; concurrent_id UUID;
BEGIN
 PERFORM kampive.assert_administrator(p_actor);
 IF p_notes IS NULL OR length(p_notes)>4000 OR p_response IS NULL OR length(p_response)>4000 OR btrim(p_response)='' THEN
 RAISE EXCEPTION 'Invalid response' USING ERRCODE='22023'; END IF;
 IF p_kind='suggestion' THEN
  -- Existing campsite first: consistent with owner/claim paths.
  IF p_accept AND p_existing IS NOT NULL THEN
   PERFORM 1 FROM kampive.campsite WHERE uuid_campsite=p_existing AND status<>'archived' FOR UPDATE;
   IF NOT FOUND THEN RAISE EXCEPTION 'Camping unavailable' USING ERRCODE='P0002'; END IF;
  END IF;
  SELECT * INTO s FROM kampive.campsite_suggestion WHERE uuid_campsite_suggestion=p_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Not found' USING ERRCODE='P0002'; END IF;
  IF s.status<>'pending_review' OR s.revision<>p_version THEN RAISE EXCEPTION 'Conflict' USING ERRCODE='40001'; END IF;
  IF p_accept THEN
   IF p_existing IS NOT NULL THEN target:=p_existing;
   ELSE
    IF p_city IS NULL OR p_category IS NULL THEN RAISE EXCEPTION 'City and category required' USING ERRCODE='22023'; END IF;
    INSERT INTO kampive.campsite(uuid_campsite_category,uuid_city,name,location_notes)
      VALUES(p_category,p_city,s.name,s.location) RETURNING uuid_campsite INTO target;
   END IF;
  END IF;
  UPDATE kampive.campsite_suggestion SET status=CASE WHEN p_accept THEN 'accepted' ELSE 'rejected' END,
   uuid_campsite=target,uuid_reviewed_by=p_actor,reviewed_at=now(),review_notes=p_notes,public_response=p_response
   WHERE uuid_campsite_suggestion=p_id;
 ELSIF p_kind='claim' THEN
  SELECT uuid_campsite INTO target FROM kampive.campsite_claim WHERE uuid_campsite_claim=p_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Not found' USING ERRCODE='P0002'; END IF;
  SELECT * INTO c FROM kampive.campsite WHERE uuid_campsite=target FOR UPDATE;
  SELECT * INTO cl FROM kampive.campsite_claim WHERE uuid_campsite_claim=p_id FOR UPDATE;
  IF cl.status<>'pending_review' OR cl.revision<>p_version THEN RAISE EXCEPTION 'Conflict' USING ERRCODE='40001'; END IF;
  IF p_accept THEN
   IF p_actor=cl.uuid_claimant OR c.status<>'published' OR (c.uuid_owner IS NOT NULL AND c.uuid_owner<>cl.uuid_claimant)
    OR EXISTS(SELECT 1 FROM kampive.campsite_claim WHERE uuid_campsite=target AND status='approved')
    OR NOT EXISTS(SELECT 1 FROM kampive.app_user WHERE uuid_app_user=cl.uuid_claimant AND deactivated_at IS NULL)
    THEN RAISE EXCEPTION 'Ownership conflict' USING ERRCODE='40001'; END IF;
   UPDATE kampive.campsite SET uuid_owner=cl.uuid_claimant WHERE uuid_campsite=target;
  END IF;
  UPDATE kampive.campsite_claim SET status=CASE WHEN p_accept THEN 'approved' ELSE 'rejected' END,
   uuid_reviewed_by=p_actor,reviewed_at=now(),review_notes=p_notes,public_response=p_response WHERE uuid_campsite_claim=p_id;
  IF p_accept THEN
   FOR concurrent_id IN SELECT uuid_campsite_claim FROM kampive.campsite_claim WHERE uuid_campsite=target AND status='pending_review' FOR UPDATE LOOP
    UPDATE kampive.campsite_claim SET status='rejected',uuid_reviewed_by=p_actor,reviewed_at=now(),
     review_notes='Another claim was approved',public_response='Outro vínculo de propriedade foi aprovado para este camping.' WHERE uuid_campsite_claim=concurrent_id;
    INSERT INTO kampive.admin_audit(actor,kind,target,action,response) VALUES(p_actor,'claim',concurrent_id,'rejected','Outro vínculo de propriedade foi aprovado para este camping.');
   END LOOP;
  END IF;
 ELSIF p_kind='registration' THEN
  target:=p_id;
  SELECT * INTO c FROM kampive.campsite WHERE uuid_campsite=p_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Not found' USING ERRCODE='P0002'; END IF;
  IF c.status<>'pending_review' OR c.revision<>p_version THEN RAISE EXCEPTION 'Conflict' USING ERRCODE='40001'; END IF;
  IF p_accept AND NOT kampive.manager_publishable(p_id) THEN RAISE EXCEPTION 'Complete publication data required' USING ERRCODE='22023'; END IF;
  UPDATE kampive.campsite SET status=CASE WHEN p_accept THEN 'published' ELSE 'rejected' END,
    verified_at=CASE WHEN p_accept THEN now() ELSE verified_at END,
    uuid_verified_by=CASE WHEN p_accept THEN p_actor ELSE uuid_verified_by END WHERE uuid_campsite=p_id;
 ELSE RAISE EXCEPTION 'Invalid type' USING ERRCODE='22023'; END IF;
 INSERT INTO kampive.admin_audit(actor,kind,target,action,notes,response)
 VALUES(p_actor,p_kind,p_id,CASE WHEN p_accept THEN 'approved' ELSE 'rejected' END,p_notes,p_response);
 RETURN target;
END $$;
REVOKE ALL ON FUNCTION kampive.manager_decide(UUID,TEXT,UUID,BIGINT,BOOLEAN,TEXT,TEXT,UUID,UUID,UUID) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION kampive.manager_decide(UUID,TEXT,UUID,BIGINT,BOOLEAN,TEXT,TEXT,UUID,UUID,UUID) TO kampive_app;

CREATE FUNCTION kampive.manager_camping_status(p_actor UUID,p_id UUID,p_version BIGINT,p_status TEXT) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE c kampive.campsite;
BEGIN
 PERFORM kampive.assert_administrator(p_actor);
 SELECT * INTO c FROM kampive.campsite WHERE uuid_campsite=p_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Not found' USING ERRCODE='P0002'; END IF;
 IF c.revision<>p_version OR c.status='pending_review' OR c.status=p_status THEN RAISE EXCEPTION 'Conflict' USING ERRCODE='40001'; END IF;
 IF p_status NOT IN ('published','archived') OR (p_status='published' AND NOT kampive.manager_publishable(p_id)) THEN RAISE EXCEPTION 'Invalid publication' USING ERRCODE='22023'; END IF;
 UPDATE kampive.campsite SET status=p_status,verified_at=CASE WHEN p_status='published' THEN now() ELSE verified_at END,
 uuid_verified_by=CASE WHEN p_status='published' THEN p_actor ELSE uuid_verified_by END WHERE uuid_campsite=p_id;
 INSERT INTO kampive.admin_audit(actor,kind,target,action) VALUES(p_actor,'camping',p_id,p_status);
END $$;
REVOKE ALL ON FUNCTION kampive.manager_camping_status(UUID,UUID,BIGINT,TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION kampive.manager_camping_status(UUID,UUID,BIGINT,TEXT) TO kampive_app;

CREATE FUNCTION kampive.manager_review(p_actor UUID,p_id UUID,p_version BIGINT,p_status TEXT) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE r kampive.review;
BEGIN
 PERFORM kampive.assert_administrator(p_actor);
 SELECT * INTO r FROM kampive.review WHERE uuid_review=p_id FOR UPDATE;
 IF NOT FOUND THEN RAISE EXCEPTION 'Not found' USING ERRCODE='P0002'; END IF;
 IF r.revision<>p_version OR r.status=p_status THEN RAISE EXCEPTION 'Conflict' USING ERRCODE='40001'; END IF;
 IF p_status NOT IN ('published','hidden') THEN RAISE EXCEPTION 'Invalid status' USING ERRCODE='22023'; END IF;
 UPDATE kampive.review SET status=p_status WHERE uuid_review=p_id;
 INSERT INTO kampive.admin_audit(actor,kind,target,action) VALUES(p_actor,'review',p_id,p_status);
END $$;
REVOKE ALL ON FUNCTION kampive.manager_review(UUID,UUID,BIGINT,TEXT) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION kampive.manager_review(UUID,UUID,BIGINT,TEXT) TO kampive_app;

CREATE FUNCTION kampive.manager_save_camping(p_actor UUID,p_id UUID,p_version BIGINT,p JSONB) RETURNS UUID
LANGUAGE plpgsql SECURITY DEFINER SET search_path=pg_catalog,pg_temp AS $$
DECLARE target UUID; old_status TEXT; old_revision BIGINT;
BEGIN
 PERFORM kampive.assert_administrator(p_actor);
 target:=p_id;
 IF target IS NULL THEN
  INSERT INTO kampive.campsite(name,uuid_campsite_category) VALUES(p->>'name',(p->>'categoryUuid')::uuid) RETURNING uuid_campsite INTO target;
 ELSE
  SELECT status,revision INTO old_status,old_revision FROM kampive.campsite WHERE uuid_campsite=target FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Not found' USING ERRCODE='P0002'; END IF;
  IF old_revision<>p_version THEN RAISE EXCEPTION 'Conflict' USING ERRCODE='40001'; END IF;
 END IF;
 UPDATE kampive.campsite SET name=p->>'name',description=p->>'description',uuid_campsite_category=(p->>'categoryUuid')::uuid,
  uuid_city=(p->>'cityUuid')::uuid,city_name=p->>'cityName',street_address=p->>'streetAddress',address_number=p->>'addressNumber',
  address_complement=p->>'addressComplement',neighborhood=p->>'neighborhood',postal_code=p->>'postalCode',location_notes=p->>'locationNotes',
  latitude=(p->>'latitude')::numeric,longitude=(p->>'longitude')::numeric,altitude_meters=(p->>'altitudeMeters')::numeric,
  starting_price_per_night=(p->>'startingPricePerNight')::numeric,uuid_currency=(p->>'currencyUuid')::uuid WHERE uuid_campsite=target;
 DELETE FROM kampive.campsite_amenity WHERE uuid_campsite=target;
 INSERT INTO kampive.campsite_amenity(uuid_campsite,uuid_amenity) SELECT target,value::uuid FROM jsonb_array_elements_text(p->'amenityUuids');
 DELETE FROM kampive.campsite_setting WHERE uuid_campsite=target;
 INSERT INTO kampive.campsite_setting(uuid_campsite,uuid_natural_setting) SELECT target,value::uuid FROM jsonb_array_elements_text(p->'settingUuids');
 DELETE FROM kampive.campsite_contact WHERE uuid_campsite=target;
 INSERT INTO kampive.campsite_contact(uuid_campsite,uuid_contact_type,value) SELECT target,(value->>'typeUuid')::uuid,value->>'value' FROM jsonb_array_elements(p->'contacts');
 DELETE FROM kampive.campsite_photo WHERE uuid_campsite=target;
 INSERT INTO kampive.campsite_photo(uuid_campsite,file_url,description,sort_order) SELECT target,value->>'url',value->>'description',ordinality FROM jsonb_array_elements(p->'photos') WITH ORDINALITY;
 IF old_status='published' AND NOT kampive.manager_publishable(target) THEN RAISE EXCEPTION 'Complete publication data required' USING ERRCODE='22023'; END IF;
 INSERT INTO kampive.admin_audit(actor,kind,target,action) VALUES(p_actor,'camping',target,CASE WHEN p_id IS NULL THEN 'created' ELSE 'updated' END);
 RETURN target;
END $$;
REVOKE ALL ON FUNCTION kampive.manager_save_camping(UUID,UUID,BIGINT,JSONB) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION kampive.manager_save_camping(UUID,UUID,BIGINT,JSONB) TO kampive_app;
COMMIT;
