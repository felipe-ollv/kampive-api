package com.kampive.api.module.campsite.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.kampive.api.module.campsite.application.port.in.Campsites.*;
import com.kampive.api.module.campsite.application.port.out.CampsiteRepository;

@Repository
public class JdbcCampsiteRepository implements CampsiteRepository {
    private final JdbcClient jdbc;
    public JdbcCampsiteRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    private static final String FROM = """
        FROM kampive.campsite c
        JOIN kampive.campsite_category cat ON cat.uuid_campsite_category=c.uuid_campsite_category
        LEFT JOIN kampive.city ci ON ci.uuid_city=c.uuid_city
        LEFT JOIN kampive.administrative_division ad ON ad.uuid_administrative_division=ci.uuid_administrative_division
        LEFT JOIN kampive.country co ON co.uuid_country=ad.uuid_country
        LEFT JOIN kampive.currency cu ON cu.uuid_currency=c.uuid_currency
        """;
    private static final String SELECT = """
        SELECT c.*, cat.name AS category_name, COALESCE(NULLIF(c.city_name,''),ci.name) AS display_city_name, co.name AS country_name,
          co.uuid_country, cu.iso_code AS currency_code,
          (SELECT file_url FROM kampive.campsite_photo p WHERE p.uuid_campsite=c.uuid_campsite ORDER BY sort_order LIMIT 1) AS cover_url,
          (SELECT round(avg(r.rating),2) FROM kampive.review r WHERE r.uuid_campsite=c.uuid_campsite AND r.status='published') AS average_rating,
          (SELECT count(*) FROM kampive.review r WHERE r.uuid_campsite=c.uuid_campsite AND r.status='published') AS review_count
        """ + FROM;
    private static final String VISIBILITY = """
        ((CAST(:owner AS uuid) IS NULL AND c.status='published') OR (c.uuid_owner=:owner AND EXISTS (SELECT 1 FROM kampive.campsite_claim cl WHERE cl.uuid_campsite=c.uuid_campsite AND cl.uuid_claimant=:owner AND cl.status='approved')))
        """;
    private static final String FILTER = " WHERE " + VISIBILITY + """
        AND (CAST(:division AS uuid) IS NULL OR ci.uuid_administrative_division=:division)
        AND (CAST(:city AS uuid) IS NULL OR c.uuid_city=:city)
        AND (CAST(:country AS uuid) IS NULL OR co.uuid_country=:country)
        AND (CAST(:category AS uuid) IS NULL OR c.uuid_campsite_category=:category)
        AND (CAST(:amenity AS uuid) IS NULL OR EXISTS (SELECT 1 FROM kampive.campsite_amenity a WHERE a.uuid_campsite=c.uuid_campsite AND a.uuid_amenity=:amenity))
        AND (CAST(:setting AS uuid) IS NULL OR EXISTS (SELECT 1 FROM kampive.campsite_setting s WHERE s.uuid_campsite=c.uuid_campsite AND s.uuid_natural_setting=:setting))
        AND (position(translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') in translate(lower(c.name),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy'))>0 OR position(translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') in translate(lower(coalesce(ci.name,'')),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy'))>0
          OR position(translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') in translate(lower(coalesce(co.name,'')),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy'))>0)
        """;
    public Page<Summary> search(Query q, UUID owner) {
        long total=filter("SELECT count(*) " + FROM + FILTER,q,owner).query(Long.class).single();
        String order=owner!=null?"c.created_at DESC,c.uuid_campsite":
            (q.query().isEmpty()?"":"""
                CASE WHEN translate(lower(c.name),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy')=translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') THEN 0
                     WHEN translate(lower(ci.name),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy')=translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') THEN 1
                     WHEN translate(lower(co.name),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy')=translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') THEN 2
                     WHEN position(translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') in translate(lower(c.name),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy'))=1 THEN 3
                     WHEN position(translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') in translate(lower(coalesce(ci.name,'')),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy'))=1 THEN 4
                     WHEN position(translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') in translate(lower(coalesce(co.name,'')),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy'))=1 THEN 5
                     WHEN position(translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') in translate(lower(c.name),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy'))>0 THEN 6 ELSE 7 END,
                """)+"average_rating DESC NULLS LAST,review_count DESC,c.uuid_campsite";
        var items=filter(SELECT+FILTER+" ORDER BY "+order+" LIMIT :size OFFSET :offset",q,owner)
            .param("size",q.size()).param("offset",q.offset()).query(this::summary).list();
        return page(items,q,total);
    }
    private JdbcClient.StatementSpec filter(String sql,Query q,UUID owner) {
        return jdbc.sql(sql).param("owner",owner,Types.OTHER).param("country",q.countryUuid(),Types.OTHER)
            .param("division",q.divisionUuid(),Types.OTHER).param("city",q.cityUuid(),Types.OTHER).param("category",q.categoryUuid(),Types.OTHER).param("amenity",q.amenityUuid(),Types.OTHER)
            .param("setting",q.settingUuid(),Types.OTHER).param("query",q.query());
    }
    public Optional<Detail> find(UUID uuid,UUID owner) {
        return jdbc.sql(SELECT+" WHERE c.uuid_campsite=:uuid AND "+VISIBILITY)
            .param("uuid",uuid).param("owner",owner,Types.OTHER).query((rs,row) -> new Detail(summary(rs,row),content(rs))).optional();
    }
    public Optional<String> lockOwned(UUID uuid,UUID owner) {
        return jdbc.sql("SELECT status FROM kampive.campsite WHERE uuid_campsite=:uuid AND uuid_owner=:owner AND EXISTS (SELECT 1 FROM kampive.campsite_claim cl WHERE cl.uuid_campsite=:uuid AND cl.uuid_claimant=:owner AND cl.status='approved') FOR UPDATE")
            .param("uuid",uuid).param("owner",owner).query(String.class).optional();
    }
    private Summary summary(ResultSet rs,int row) throws SQLException {
        return new Summary(rs.getObject("uuid_campsite",UUID.class),rs.getString("name"),rs.getString("status"),
            rs.getString("category_name"),rs.getString("display_city_name"),rs.getString("country_name"),rs.getObject("uuid_country",UUID.class),
            rs.getBigDecimal("starting_price_per_night"),rs.getString("currency_code"),rs.getString("cover_url"),
            rs.getBigDecimal("average_rating"),rs.getLong("review_count"),rs.getBoolean("is_featured"),rs.getTimestamp("verified_at")!=null);
    }
    private Content content(ResultSet rs) throws SQLException {
        UUID uuid=rs.getObject("uuid_campsite",UUID.class);
        return new Content(rs.getString("name"),rs.getString("description"),rs.getObject("uuid_campsite_category",UUID.class),
            rs.getObject("uuid_city",UUID.class),rs.getString("street_address"),rs.getString("address_number"),
            rs.getString("address_complement"),rs.getString("neighborhood"),rs.getString("postal_code"),rs.getString("location_notes"),
            rs.getBigDecimal("latitude"),rs.getBigDecimal("longitude"),rs.getBigDecimal("altitude_meters"),
            rs.getBigDecimal("starting_price_per_night"),rs.getObject("uuid_currency",UUID.class),
            jdbc.sql("SELECT uuid_amenity FROM kampive.campsite_amenity WHERE uuid_campsite=:uuid ORDER BY uuid_amenity")
                .param("uuid",uuid).query(UUID.class).list(),
            jdbc.sql("SELECT uuid_natural_setting FROM kampive.campsite_setting WHERE uuid_campsite=:uuid ORDER BY uuid_natural_setting")
                .param("uuid",uuid).query(UUID.class).list(),
            jdbc.sql("SELECT uuid_contact_type,value FROM kampive.campsite_contact WHERE uuid_campsite=:uuid ORDER BY uuid_contact_type")
                .param("uuid",uuid).query((r,n) -> new Contact(r.getObject(1,UUID.class),r.getString(2))).list(),
            jdbc.sql("SELECT file_url,description FROM kampive.campsite_photo WHERE uuid_campsite=:uuid ORDER BY sort_order")
                .param("uuid",uuid).query((r,n) -> new Photo(r.getString(1),r.getString(2))).list(),rs.getString("city_name"));
    }
    public UUID register(UUID actor,Content c) {
        UUID uuid=parameters(jdbc.sql("""
            INSERT INTO kampive.campsite(uuid_submitted_by,uuid_campsite_category,uuid_city,city_name,uuid_currency,name,description,
              street_address,address_number,address_complement,neighborhood,postal_code,location_notes,
              latitude,longitude,altitude_meters,starting_price_per_night,status)
            VALUES (:actor,:category,:city,:cityName,:currency,:name,:description,:street,:number,:complement,:neighborhood,
              :postal,:notes,:latitude,:longitude,:altitude,:price,'pending_review') RETURNING uuid_campsite
            """),c).param("actor",actor).query(UUID.class).single();
        children(uuid,c);
        return uuid;
    }
    public Submission suggest(UUID actor,SuggestionInput input) {
        UUID uuid=jdbc.sql("""
            INSERT INTO kampive.campsite_suggestion(uuid_suggested_by,name,location,contact,notes)
            VALUES (:actor,:name,:location,:contact,:notes) RETURNING uuid_campsite_suggestion
            """).param("actor",actor).param("name",input.name()).param("location",input.googleMapsUrl())
            .param("contact",input.instagramUrl()).param("notes","").query(UUID.class).single();
        return new Submission(uuid,"pending_review");
    }
    public Optional<Submission> claim(UUID actor,UUID uuid,ClaimInput input) {
        // Serialize claims with administrative approval and other campsite mutations.
        var available=jdbc.sql("""
            SELECT uuid_campsite FROM kampive.campsite c WHERE uuid_campsite=:uuid AND status='published'
              AND NOT EXISTS (SELECT 1 FROM kampive.campsite_claim cl WHERE cl.uuid_campsite=c.uuid_campsite AND cl.status='approved')
            FOR UPDATE
            """).param("uuid",uuid).query(UUID.class).optional();
        if (available.isEmpty()) return Optional.empty();
        // A concurrent approval may have committed while this transaction waited for the row lock.
        if (jdbc.sql("SELECT count(*) FROM kampive.campsite_claim WHERE uuid_campsite=:uuid AND status='approved'")
                .param("uuid",uuid).query(Long.class).single() > 0) return Optional.empty();
        UUID claim=jdbc.sql("""
            INSERT INTO kampive.campsite_claim(uuid_campsite,uuid_claimant,contact,evidence)
            VALUES (:uuid,:actor,:contact,:evidence)
            ON CONFLICT (uuid_campsite,uuid_claimant) WHERE status='pending_review'
            DO UPDATE SET contact=kampive.campsite_claim.contact
            RETURNING uuid_campsite_claim
            """).param("uuid",uuid).param("actor",actor).param("contact",input.contact())
            .param("evidence",input.evidence()).query(UUID.class).single();
        return Optional.of(new Submission(claim,"pending_review"));
    }
    public void update(UUID uuid,Content c) {
        parameters(jdbc.sql("""
            UPDATE kampive.campsite SET uuid_campsite_category=:category,uuid_city=:city,city_name=:cityName,uuid_currency=:currency,
              name=:name,description=:description,street_address=:street,address_number=:number,
              address_complement=:complement,neighborhood=:neighborhood,postal_code=:postal,location_notes=:notes,
              latitude=:latitude,longitude=:longitude,altitude_meters=:altitude,starting_price_per_night=:price
            WHERE uuid_campsite=:uuid
            """),c).param("uuid",uuid).update();
        // Table names are a fixed allowlist, never supplied by a client.
        for (String table: List.of("campsite_photo","campsite_contact","campsite_amenity","campsite_setting"))
            jdbc.sql("DELETE FROM kampive."+table+" WHERE uuid_campsite=:uuid").param("uuid",uuid).update();
        children(uuid,c);
    }
    private JdbcClient.StatementSpec parameters(JdbcClient.StatementSpec s,Content c) {
        return s.param("category",c.categoryUuid()).param("city",c.cityUuid(),Types.OTHER).param("currency",c.currencyUuid(),Types.OTHER)
            .param("cityName",c.cityName()).param("name",c.name()).param("description",c.description()).param("street",c.streetAddress()).param("number",c.addressNumber())
            .param("complement",c.addressComplement()).param("neighborhood",c.neighborhood()).param("postal",c.postalCode())
            .param("notes",c.locationNotes()).param("latitude",c.latitude(),Types.NUMERIC).param("longitude",c.longitude(),Types.NUMERIC)
            .param("altitude",c.altitudeMeters(),Types.NUMERIC).param("price",c.startingPricePerNight(),Types.NUMERIC);
    }
    private void children(UUID uuid,Content c) {
        for (UUID amenity:c.amenityUuids()) jdbc.sql("INSERT INTO kampive.campsite_amenity(uuid_campsite,uuid_amenity) VALUES (:uuid,:value)")
            .param("uuid",uuid).param("value",amenity).update();
        for (UUID setting:c.settingUuids()) jdbc.sql("INSERT INTO kampive.campsite_setting(uuid_campsite,uuid_natural_setting) VALUES (:uuid,:value)")
            .param("uuid",uuid).param("value",setting).update();
        for (Contact contact:c.contacts()) jdbc.sql("INSERT INTO kampive.campsite_contact(uuid_campsite,uuid_contact_type,value) VALUES (:uuid,:type,:value)")
            .param("uuid",uuid).param("type",contact.typeUuid()).param("value",contact.value()).update();
        for (int i=0;i<c.photos().size();i++) jdbc.sql("INSERT INTO kampive.campsite_photo(uuid_campsite,file_url,description,sort_order) VALUES (:uuid,:url,:description,:position)")
            .param("uuid",uuid).param("url",c.photos().get(i).url()).param("description",c.photos().get(i).description()).param("position",i+1).update();
    }
    public void status(UUID uuid,String status) {
        jdbc.sql("UPDATE kampive.campsite SET status=:status WHERE uuid_campsite=:uuid").param("uuid",uuid).param("status",status).update();
    }
    private List<Option> options(String table) {
        // Only invoked with constants below.
        return jdbc.sql("SELECT uuid_"+table+",name,icon FROM kampive."+table+" ORDER BY lower(name),uuid_"+table)
            .query((r,n) -> new Option(r.getObject(1,UUID.class),r.getString(2),r.getString(3))).list();
    }
    public Catalog catalog() {
        return new Catalog(options("campsite_category"),options("amenity"),options("natural_setting"),options("contact_type"),
            jdbc.sql("SELECT uuid_currency,name,iso_code,symbol FROM kampive.currency ORDER BY iso_code")
                .query((r,n) -> new Currency(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getString(4))).list());
    }
    public Page<City> cities(Query q) {
        String from="""
            FROM kampive.city ci JOIN kampive.administrative_division ad ON ad.uuid_administrative_division=ci.uuid_administrative_division
            JOIN kampive.country co ON co.uuid_country=ad.uuid_country
            WHERE (CAST(:country AS uuid) IS NULL OR co.uuid_country=:country)
              AND position(translate(lower(:query),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy') in translate(lower(ci.name),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy'))>0
            """;
        long total=jdbc.sql("SELECT count(*) "+from).param("country",q.countryUuid(),Types.OTHER).param("query",q.query()).query(Long.class).single();
        var items=jdbc.sql("SELECT ci.uuid_city,ci.name,ad.uuid_administrative_division,ad.name,co.uuid_country,co.name "+from+" ORDER BY translate(lower(ci.name),'áàâãäåéèêëíìîïóòôõöúùûüçñýÿ','aaaaaaeeeeiiiiooooouuuucnyy'),ci.uuid_city LIMIT :size OFFSET :offset")
            .param("country",q.countryUuid(),Types.OTHER).param("query",q.query()).param("size",q.size()).param("offset",q.offset())
            .query((r,n) -> new City(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),r.getString(4),r.getObject(5,UUID.class),r.getString(6))).list();
        return page(items,q,total);
    }
    private static <T> Page<T> page(List<T> items,Query q,long total) {
        return new Page<>(items,q.page(),q.size(),total,total/q.size()+(total%q.size()==0?0:1));
    }
}
