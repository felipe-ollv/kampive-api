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
    public Optional<UserView> findActiveByEmail(String email) {
        return jdbc.sql("SELECT * FROM kampive.app_user WHERE lower(email)=:email AND deactivated_at IS NULL")
            .param("email", email).query(this::map).optional();
    }
    public Optional<UserView> findActiveByUuid(UUID uuid) {
        return jdbc.sql("SELECT * FROM kampive.app_user WHERE uuid_app_user=:uuid AND deactivated_at IS NULL")
            .param("uuid", uuid).query(this::map).optional();
    }
    public UserView create(String name, String email, UUID country, UUID style) {
        return jdbc.sql("""
            INSERT INTO kampive.app_user(name,email,uuid_country,uuid_adventurer_style)
            VALUES (:name,:email,:country,:style) RETURNING *
            """)
            .param("name", name).param("email", email).param("country", country)
            .param("style", style).query(this::map).single();
    }
    private UserView map(ResultSet rs, int row) throws SQLException {
        return new UserView(rs.getObject("uuid_app_user", UUID.class), rs.getString("name"),
            rs.getString("email"), rs.getObject("uuid_country", UUID.class),
            rs.getObject("uuid_adventurer_style", UUID.class), rs.getString("photo_url"),
            rs.getString("bio"), rs.getBoolean("notifications_enabled"),
            rs.getTimestamp("email_verified_at") != null);
    }
}
