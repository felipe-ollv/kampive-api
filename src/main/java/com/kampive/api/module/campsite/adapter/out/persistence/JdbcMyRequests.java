package com.kampive.api.module.campsite.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.kampive.api.module.campsite.application.port.in.MyRequests;
import com.kampive.api.module.campsite.application.port.in.Campsites.Page;
import static com.kampive.api.shared.adapter.out.persistence.CommunityJdbc.*;

@Repository @Transactional
public class JdbcMyRequests implements MyRequests {
    private final JdbcClient jdbc;
    public JdbcMyRequests(JdbcClient jdbc) { this.jdbc=jdbc; }
    // The actor predicate is applied inside both branches, including detail lookups.
    private static final String ROWS = """
        SELECT s.uuid_campsite_suggestion AS uuid, 'suggestion' AS type, s.name, s.status,
               s.created_at, s.reviewed_at, c.uuid_campsite AS published_campsite_uuid,
               s.contact AS instagram_url, s.location AS google_maps_url,
               NULL::text AS contact, NULL::text AS evidence, s.public_response AS response
        FROM kampive.campsite_suggestion s
        LEFT JOIN kampive.campsite c ON c.uuid_campsite=s.uuid_campsite AND c.status='published'
        WHERE s.uuid_suggested_by=:actor
        UNION ALL
        SELECT cl.uuid_campsite_claim, 'claim', c.name, cl.status, cl.created_at, cl.reviewed_at,
               CASE WHEN c.status='published' THEN c.uuid_campsite END,
               NULL::text, NULL::text, cl.contact, cl.evidence, cl.public_response
        FROM kampive.campsite_claim cl JOIN kampive.campsite c ON c.uuid_campsite=cl.uuid_campsite
        WHERE cl.uuid_claimant=:actor
        """;
    public Page<Item> list(UUID actor,int page,int size) {
        page(page,size); active(jdbc,actor);
        long total=jdbc.sql("SELECT count(*) FROM ("+ROWS+") requests")
            .param("actor",actor).query(Long.class).single();
        var items=jdbc.sql("SELECT * FROM ("+ROWS+") requests ORDER BY created_at DESC,type,uuid LIMIT :size OFFSET :offset")
            .param("actor",actor).param("size",size).param("offset",(long)page*size)
            .query((r,n)->item(r)).list();
        return new Page<>(items,page,size,total,(total+size-1)/size);
    }
    public Detail get(UUID actor,String type,UUID uuid) {
        active(jdbc,actor);
        if (!type.equals("suggestion") && !type.equals("claim")) fail(404);
        return jdbc.sql("SELECT * FROM ("+ROWS+") requests WHERE type=:type AND uuid=:uuid")
            .param("actor",actor).param("type",type).param("uuid",uuid)
            .query((r,n)->new Detail(item(r),r.getString("instagram_url"),r.getString("google_maps_url"),
                r.getString("contact"),r.getString("evidence"),r.getString("response")))
            .optional().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    private Item item(ResultSet r) throws SQLException {
        var reviewed=r.getTimestamp("reviewed_at");
        return new Item(r.getObject("uuid",UUID.class),r.getString("type"),r.getString("name"),r.getString("status"),
            r.getTimestamp("created_at").toInstant(),reviewed==null?null:reviewed.toInstant(),r.getObject("published_campsite_uuid",UUID.class));
    }
}
