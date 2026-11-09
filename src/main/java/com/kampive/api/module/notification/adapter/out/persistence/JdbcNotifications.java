package com.kampive.api.module.notification.adapter.out.persistence;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.kampive.api.module.notification.application.port.in.Notifications;
import static com.kampive.api.shared.adapter.out.persistence.CommunityJdbc.*;
@Repository @Transactional
public class JdbcNotifications implements Notifications {
    private final JdbcClient jdbc;
    public JdbcNotifications(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Inbox list(UUID actor,int page,int size,String filter) {
        page(page,size); active(jdbc,actor);
        if(!List.of("all","read","unread").contains(filter)) fail(400);
        String where=" WHERE uuid_app_user=:actor"+(filter.equals("all")?"":filter.equals("read")?" AND read_at IS NOT NULL":" AND read_at IS NULL");
        long total=jdbc.sql("SELECT count(*) FROM kampive.user_notification"+where).param("actor",actor).query(Long.class).single();
        long unread=jdbc.sql("SELECT count(*) FROM kampive.user_notification WHERE uuid_app_user=:actor AND read_at IS NULL").param("actor",actor).query(Long.class).single();
        var items=jdbc.sql("SELECT v.*, n.request_type, n.request_uuid FROM kampive.vw_user_notification v JOIN kampive.notification n USING (uuid_notification)"+where+" ORDER BY received_at DESC,uuid_user_notification LIMIT :size OFFSET :offset")
            .param("actor",actor).param("size",size).param("offset",(long)page*size)
            .query((r,n)->new Item(r.getObject("uuid_user_notification",UUID.class),r.getString("title"),r.getString("message"),r.getTimestamp("received_at").toInstant(),r.getBoolean("is_read"),r.getString("request_type"),r.getObject("request_uuid",UUID.class))).list();
        return new Inbox(items,page,size,total,(total+size-1)/size,unread);
    }
    public void read(UUID actor,UUID uuid,boolean read) {
        active(jdbc,actor);
        int changed=jdbc.sql("UPDATE kampive.user_notification SET read_at="+(read?"GREATEST(CURRENT_TIMESTAMP,received_at)":"NULL")+" WHERE uuid_app_user=:actor AND uuid_user_notification=:id")
            .param("actor",actor).param("id",uuid).update();
        if(changed!=1) fail(404);
    }
    public void readAll(UUID actor) {
        active(jdbc,actor);
        jdbc.sql("UPDATE kampive.user_notification SET read_at=GREATEST(CURRENT_TIMESTAMP,received_at) WHERE uuid_app_user=:actor AND read_at IS NULL")
            .param("actor",actor).update();
    }
}
