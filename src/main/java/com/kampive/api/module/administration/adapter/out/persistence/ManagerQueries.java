package com.kampive.api.module.administration.adapter.out.persistence;

/** Fixed SQL projections. No client-supplied identifiers or SQL fragments. */
public final class ManagerQueries {
    private ManagerQueries() {}
    public static final String HISTORY = """
      coalesce((SELECT jsonb_agg(jsonb_build_object('date',a.created_at,'text',a.action || ' · ' || u.name) ORDER BY a.id)
       FROM kampive.admin_audit a JOIN kampive.app_user u ON u.uuid_app_user=a.actor WHERE a.kind=q.kind AND a.target=q.uuid),'[]'::jsonb)
      """;
    public static final String REQUESTS = """
      SELECT jsonb_build_object('id',q.kind||':'||q.uuid,'uuid',q.uuid,'kind',q.kind,'name',q.name,'userId',q.author,
       'userName',(SELECT name FROM kampive.app_user WHERE uuid_app_user=q.author),'date',q.created_at,'status',q.status,'location',q.location,'instagram',q.instagram,'maps',q.maps,
       'contact',q.contact,'evidence',q.evidence,'campId',q.camp,'notes',coalesce(q.notes,''),'response',coalesce(q.response,''),
       'revision',q.revision,'history',
      """ + HISTORY + """
      ) AS payload, q.created_at AS ordering,q.kind||q.uuid AS tie FROM (
       SELECT 'suggestion'::text kind,s.uuid_campsite_suggestion uuid,s.name,s.uuid_suggested_by author,s.created_at,s.status,
        'Localização a conferir'::text location,s.contact instagram,s.location maps,NULL::text contact,NULL::text evidence,
        s.uuid_campsite camp,s.review_notes notes,s.public_response response,s.revision FROM kampive.campsite_suggestion s
       UNION ALL
       SELECT 'claim',cl.uuid_campsite_claim,c.name,cl.uuid_claimant,cl.created_at,cl.status,coalesce(ci.name,c.city_name,''),
        NULL,NULL,cl.contact,cl.evidence,cl.uuid_campsite,cl.review_notes,cl.public_response,cl.revision
        FROM kampive.campsite_claim cl JOIN kampive.campsite c USING(uuid_campsite) LEFT JOIN kampive.city ci USING(uuid_city)
       UNION ALL
       SELECT 'registration',c.uuid_campsite,c.name,coalesce(c.uuid_submitted_by,c.uuid_owner),c.created_at,
        CASE WHEN c.status='published' THEN 'approved' WHEN c.status='pending_review' THEN 'pending_review' ELSE c.status END,
        coalesce(ci.name,c.city_name,''),NULL,NULL,NULL,NULL,c.uuid_campsite,
        (SELECT notes FROM kampive.admin_audit a WHERE a.kind='registration' AND a.target=c.uuid_campsite ORDER BY id DESC LIMIT 1),
        (SELECT response FROM kampive.admin_audit a WHERE a.kind='registration' AND a.target=c.uuid_campsite ORDER BY id DESC LIMIT 1),c.revision
        FROM kampive.campsite c LEFT JOIN kampive.city ci USING(uuid_city)
        WHERE c.status='pending_review' OR c.uuid_submitted_by IS NOT NULL OR EXISTS(SELECT 1 FROM kampive.admin_audit a WHERE a.kind='registration' AND a.target=c.uuid_campsite)
      ) q
      """;
    public static final String CAMPS = """
      SELECT jsonb_build_object('id',c.uuid_campsite,'name',c.name,'city',coalesce(ci.name,c.city_name,''),'category',cat.name,
       'status',c.status,'description',coalesce(c.description,''),'address',coalesce(c.street_address,''),
       'contact',coalesce((SELECT value FROM kampive.campsite_contact WHERE uuid_campsite=c.uuid_campsite ORDER BY id LIMIT 1),''),
       'latitude',coalesce(c.latitude::text,''),'longitude',coalesce(c.longitude::text,''),'ownerName',(SELECT name FROM kampive.app_user WHERE uuid_app_user=c.uuid_owner),'ownerId',c.uuid_owner,'revision',c.revision,
       'amenities',coalesce((SELECT jsonb_agg(a.name) FROM kampive.campsite_amenity ca JOIN kampive.amenity a USING(uuid_amenity) WHERE ca.uuid_campsite=c.uuid_campsite),'[]'::jsonb),
       'content',jsonb_build_object('name',c.name,'description',coalesce(c.description,''),'categoryUuid',c.uuid_campsite_category,'cityUuid',c.uuid_city,'cityName',coalesce(c.city_name,''),
        'streetAddress',coalesce(c.street_address,''),'addressNumber',coalesce(c.address_number,''),'addressComplement',coalesce(c.address_complement,''),
        'neighborhood',coalesce(c.neighborhood,''),'postalCode',coalesce(c.postal_code,''),'locationNotes',coalesce(c.location_notes,''),
        'latitude',c.latitude,'longitude',c.longitude,'altitudeMeters',c.altitude_meters,'startingPricePerNight',c.starting_price_per_night,'currencyUuid',c.uuid_currency,
        'amenityUuids',coalesce((SELECT jsonb_agg(uuid_amenity) FROM kampive.campsite_amenity WHERE uuid_campsite=c.uuid_campsite),'[]'::jsonb),
        'settingUuids',coalesce((SELECT jsonb_agg(uuid_natural_setting) FROM kampive.campsite_setting WHERE uuid_campsite=c.uuid_campsite),'[]'::jsonb),
        'contacts',coalesce((SELECT jsonb_agg(jsonb_build_object('typeUuid',uuid_contact_type,'value',value) ORDER BY id) FROM kampive.campsite_contact WHERE uuid_campsite=c.uuid_campsite),'[]'::jsonb),
        'photos',coalesce((SELECT jsonb_agg(jsonb_build_object('url',file_url,'description',coalesce(description,'')) ORDER BY sort_order) FROM kampive.campsite_photo WHERE uuid_campsite=c.uuid_campsite),'[]'::jsonb)
       )) AS payload,c.created_at AS ordering,c.uuid_campsite::text AS tie
       FROM kampive.campsite c JOIN kampive.campsite_category cat USING(uuid_campsite_category) LEFT JOIN kampive.city ci USING(uuid_city)
      """;
    public static final String USERS = """
      SELECT jsonb_build_object('id',u.uuid_app_user,'name',u.name,'email',u.email,'city',coalesce(ci.name,''),'joined',u.created_at,
       'requestCount',(SELECT count(*) FROM kampive.campsite_suggestion WHERE uuid_suggested_by=u.uuid_app_user)+(SELECT count(*) FROM kampive.campsite_claim WHERE uuid_claimant=u.uuid_app_user)+(SELECT count(*) FROM kampive.campsite WHERE coalesce(uuid_submitted_by,uuid_owner)=u.uuid_app_user AND (status='pending_review' OR uuid_submitted_by IS NOT NULL OR EXISTS(SELECT 1 FROM kampive.admin_audit WHERE kind='registration' AND target=uuid_campsite))),
       'campCount',(SELECT count(*) FROM kampive.campsite WHERE uuid_owner=u.uuid_app_user),'active',u.deactivated_at IS NULL) AS payload,u.created_at AS ordering,u.uuid_app_user::text AS tie
       FROM kampive.app_user u LEFT JOIN kampive.city ci USING(uuid_city)
      """;
    public static final String HISTORIES = """
      SELECT jsonb_build_object('id',a.kind||':'||a.target,'entryId',a.id,'date',a.created_at,'text',a.action||' · '||u.name) AS payload,
       a.created_at AS ordering,lpad(a.id::text,20,'0') AS tie
       FROM kampive.admin_audit a JOIN kampive.app_user u ON u.uuid_app_user=a.actor
      """;
    public static String compactCamps() {
        int start=CAMPS.lastIndexOf(",",CAMPS.indexOf("'content'"));
        int end=CAMPS.indexOf(") AS payload",start);
        return CAMPS.substring(0,start)+CAMPS.substring(end);
    }
    public static final String REVIEWS = """
      SELECT jsonb_build_object('id',r.uuid_review,'campId',r.uuid_campsite,'userId',r.uuid_app_user,'date',r.created_at,
       'userName',(SELECT name FROM kampive.app_user WHERE uuid_app_user=r.uuid_app_user),'campName',(SELECT name FROM kampive.campsite WHERE uuid_campsite=r.uuid_campsite),'rating',r.rating,'text',r.comment,'status',r.status,'revision',r.revision,
       'photos',coalesce((SELECT jsonb_agg(file_url ORDER BY id) FROM kampive.review_photo WHERE uuid_review=r.uuid_review),'[]'::jsonb))
       AS payload,r.created_at AS ordering,r.uuid_review::text AS tie FROM kampive.review r
      """;
}
