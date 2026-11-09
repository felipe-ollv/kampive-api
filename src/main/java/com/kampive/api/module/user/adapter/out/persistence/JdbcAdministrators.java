package com.kampive.api.module.user.adapter.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.kampive.api.module.user.application.port.in.Administrators;

@Repository
public class JdbcAdministrators implements Administrators {
    private final JdbcClient jdbc;
    public JdbcAdministrators(JdbcClient jdbc) { this.jdbc = jdbc; }
    public boolean active(UUID actor) {
        return jdbc.sql("""
            SELECT EXISTS (SELECT 1 FROM kampive.administrator a
              JOIN kampive.app_user u USING(uuid_app_user)
              WHERE a.uuid_app_user=:actor AND a.revoked_at IS NULL
                AND u.deactivated_at IS NULL AND u.email_verified_at IS NOT NULL)
            """).param("actor", actor).query(Boolean.class).single();
    }
}
