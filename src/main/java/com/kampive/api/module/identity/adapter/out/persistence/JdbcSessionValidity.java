package com.kampive.api.module.identity.adapter.out.persistence;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.kampive.api.module.identity.application.port.in.SessionValidity;
@Repository
public class JdbcSessionValidity implements SessionValidity {
    private final JdbcClient jdbc;
    public JdbcSessionValidity(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Instant started() { return jdbc.sql("SELECT clock_timestamp()").query(java.sql.Timestamp.class).single().toInstant(); }
    public boolean valid(UUID actor,Instant started) {
        if(started==null) return false;
        return jdbc.sql("SELECT count(*) FROM kampive.user_credential c JOIN kampive.app_user u USING(uuid_app_user) WHERE c.uuid_app_user=:actor AND u.deactivated_at IS NULL AND (c.password_changed_at IS NULL OR c.password_changed_at <= :started)")
            .param("actor",actor).param("started",java.sql.Timestamp.from(started)).query(Long.class).single()==1;
    }
}
