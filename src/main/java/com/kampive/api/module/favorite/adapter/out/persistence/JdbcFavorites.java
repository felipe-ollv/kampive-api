package com.kampive.api.module.favorite.adapter.out.persistence;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.kampive.api.module.favorite.application.port.in.Favorites;
import static com.kampive.api.shared.adapter.out.persistence.CommunityJdbc.*;
@Repository
@Transactional
public class JdbcFavorites implements Favorites {
    private final JdbcClient jdbc;
    public JdbcFavorites(JdbcClient jdbc) { this.jdbc=jdbc; }
    public List<UUID> list(UUID actor) {
        active(jdbc,actor);
        return jdbc.sql("SELECT uuid_campsite FROM kampive.favorite WHERE uuid_app_user=:actor ORDER BY created_at DESC,uuid_campsite")
            .param("actor",actor).query(UUID.class).list();
    }
    public void save(UUID actor, UUID campsite) {
        active(jdbc,actor);
        if(jdbc.sql("SELECT uuid_campsite FROM kampive.campsite WHERE uuid_campsite=:id AND status='published' FOR SHARE")
            .param("id",campsite).query(UUID.class).optional().isEmpty()) fail(404);
        jdbc.sql("INSERT INTO kampive.favorite(uuid_app_user,uuid_campsite) VALUES (:actor,:id) ON CONFLICT (uuid_app_user,uuid_campsite) DO NOTHING")
            .param("actor",actor).param("id",campsite).update();
    }
    public void remove(UUID actor, UUID campsite) {
        active(jdbc,actor);
        jdbc.sql("DELETE FROM kampive.favorite WHERE uuid_app_user=:actor AND uuid_campsite=:id")
            .param("actor",actor).param("id",campsite).update();
    }
}
