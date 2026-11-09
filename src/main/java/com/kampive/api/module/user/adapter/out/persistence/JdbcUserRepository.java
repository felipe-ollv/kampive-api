package com.kampive.api.module.user.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.kampive.api.module.user.application.port.out.UserRepository;

@Repository
public class JdbcUserRepository implements UserRepository {
    private final JdbcClient jdbc;
    public JdbcUserRepository(JdbcClient jdbc) { this.jdbc = jdbc; }
    private static final String SELECT = """
        SELECT u.*, ci.uuid_administrative_division AS division_uuid, ci.name AS city_name, ad.name AS division_name, co.name AS country_name
        FROM kampive.app_user u
        LEFT JOIN kampive.city ci ON ci.uuid_city=u.uuid_city
        LEFT JOIN kampive.administrative_division ad ON ad.uuid_administrative_division=ci.uuid_administrative_division
        LEFT JOIN kampive.country co ON co.uuid_country=u.uuid_country
        """;
    public Optional<UserView> findActiveByEmail(String email) {
        return jdbc.sql(SELECT+" WHERE lower(u.email)=:email AND u.deactivated_at IS NULL")
            .param("email", email).query(this::map).optional();
    }
    public Optional<UserView> findActiveByUuid(UUID uuid) {
        return jdbc.sql(SELECT+" WHERE u.uuid_app_user=:uuid AND u.deactivated_at IS NULL")
            .param("uuid", uuid).query(this::map).optional();
    }
    public UserView create(String name, String email, UUID country, UUID style) {
        UUID uuid=jdbc.sql("""
            INSERT INTO kampive.app_user(name,email,uuid_country,uuid_adventurer_style)
            VALUES (:name,:email,:country,:style) RETURNING uuid_app_user
            """)
            .param("name", name).param("email", email).param("country", country)
            .param("style", style).query(UUID.class).single();
        return findActiveByUuid(uuid).orElseThrow();
    }
    public UserView update(UUID actor, ProfileInput input) {
        int changed=jdbc.sql("""
            UPDATE kampive.app_user SET name=:name,uuid_country=:country,uuid_city=:city,bio=:bio,
              travel_styles=CAST(:styles AS text[]),photo_url=:photo
            WHERE uuid_app_user=:actor AND deactivated_at IS NULL
              AND (CAST(:city AS uuid) IS NULL OR EXISTS (
                SELECT 1 FROM kampive.city ci JOIN kampive.administrative_division ad
                ON ad.uuid_administrative_division=ci.uuid_administrative_division
                WHERE ci.uuid_city=:city AND ad.uuid_country=:country))
            """).param("actor",actor).param("name",input.name()).param("country",input.countryUuid(),java.sql.Types.OTHER)
            .param("city",input.cityUuid(),java.sql.Types.OTHER).param("bio",input.bio())
            .param("styles","{"+String.join(",",input.travelStyles())+"}").param("photo",input.photoUrl()).update();
        if(changed!=1) throw new com.kampive.api.module.user.domain.UserFailure(
            findActiveByUuid(actor).isPresent()?com.kampive.api.module.user.domain.UserFailure.Reason.INVALID_INPUT:
            com.kampive.api.module.user.domain.UserFailure.Reason.INACTIVE);
        return findActiveByUuid(actor).orElseThrow();
    }
    public void delete(UUID actor) {
        int changed=jdbc.sql("""
            UPDATE kampive.app_user SET name='Conta excluída',email=uuid_app_user::text || '@deleted.invalid',
              phone_number=NULL,photo_url=NULL,bio=NULL,uuid_country=NULL,uuid_city=NULL,
              uuid_adventurer_style=NULL,travel_styles='{}',notifications_enabled=false,
              email_verified_at=NULL,phone_verified_at=NULL,deactivated_at=CURRENT_TIMESTAMP
            WHERE uuid_app_user=:actor AND deactivated_at IS NULL
            """).param("actor",actor).update();
        if(changed!=1) throw new com.kampive.api.module.user.domain.UserFailure(
            com.kampive.api.module.user.domain.UserFailure.Reason.INACTIVE);
        for(String table:java.util.List.of("user_credential","social_identity","favorite","user_notification"))
            jdbc.sql("DELETE FROM kampive."+table+" WHERE uuid_app_user=:actor").param("actor",actor).update();
        jdbc.sql("UPDATE kampive.campsite_claim SET contact='Removido',evidence='Removido' WHERE uuid_claimant=:actor")
            .param("actor",actor).update();
    }
    private UserView map(ResultSet rs, int row) throws SQLException {
        return new UserView(rs.getObject("uuid_app_user", UUID.class), rs.getString("name"),
            rs.getString("email"), rs.getObject("uuid_country", UUID.class),
            rs.getObject("uuid_adventurer_style", UUID.class), rs.getString("photo_url"),
            rs.getString("bio"), rs.getBoolean("notifications_enabled"),
            rs.getTimestamp("email_verified_at") != null,
            rs.getObject("uuid_city",UUID.class),rs.getString("city_name"),rs.getString("division_name"),
            rs.getString("country_name"),java.util.Arrays.asList((String[])rs.getArray("travel_styles").getArray()),rs.getObject("division_uuid",UUID.class));
    }
}
