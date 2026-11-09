package com.kampive.api.shared.adapter.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class CommunityJdbc {
    private CommunityJdbc() {}
    public static void active(JdbcClient jdbc, UUID actor) {
        if (jdbc.sql("SELECT uuid_app_user FROM kampive.app_user WHERE uuid_app_user=:actor AND deactivated_at IS NULL FOR SHARE")
                .param("actor",actor).query(UUID.class).optional().isEmpty()) fail(401);
    }
    public static void page(int page, int size) {
        if (page<0 || page>10000 || size<1 || size>50) fail(400);
    }
    public static void fail(int status) { throw new ResponseStatusException(HttpStatus.valueOf(status)); }
}
