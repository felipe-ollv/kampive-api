package com.kampive.api.module.review.adapter.out.persistence;
import java.util.*;
import java.sql.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.simple.JdbcClient;
import com.kampive.api.module.review.application.port.in.Reviews;
import com.kampive.api.module.campsite.application.port.in.Campsites.Page;
import com.kampive.api.module.campsite.application.port.in.Campsites.Option;
import static com.kampive.api.shared.adapter.out.persistence.CommunityJdbc.*;
@Repository @Transactional
public class JdbcReviews implements Reviews {
    private final JdbcClient jdbc;
    public JdbcReviews(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Options options() {
        return new Options(options("review_highlight"),options("camping_method"));
    }
    private List<Option> options(String table) {
        return jdbc.sql("SELECT uuid_"+table+",name,icon FROM kampive."+table+" ORDER BY name,uuid_"+table)
            .query((r,n)->new Option(r.getObject(1,UUID.class),r.getString(2),r.getString(3))).list();
    }
    public Page<Item> list(UUID campsite,int page,int size) {
        page(page,size);
        published(campsite);
        return read("r.uuid_campsite=:id AND r.status='published'",campsite,page,size);
    }
    public Page<Item> mine(UUID actor,int page,int size) {
        page(page,size); active(jdbc,actor);
        return read("r.uuid_app_user=:id",actor,page,size);
    }
    private Page<Item> read(String where,UUID id,int page,int size) {
        long total=jdbc.sql("SELECT count(*) FROM kampive.review r WHERE "+where).param("id",id).query(Long.class).single();
        var items=jdbc.sql("SELECT r.*,u.name AS author_name,c.name AS campsite_name,m.name AS method_name FROM kampive.review r JOIN kampive.app_user u USING(uuid_app_user) JOIN kampive.campsite c USING(uuid_campsite) LEFT JOIN kampive.camping_method m USING(uuid_camping_method) WHERE "+where+" ORDER BY r.created_at DESC,r.uuid_review LIMIT :size OFFSET :offset")
            .param("id",id).param("size",size).param("offset",(long)page*size).query(this::map).list();
        return new Page<>(items,page,size,total,(total+size-1)/size);
    }
    private Item map(ResultSet r,int row) throws SQLException {
        UUID uuid=r.getObject("uuid_review",UUID.class);
        var highlights=jdbc.sql("SELECT h.name FROM kampive.review_highlight_selection s JOIN kampive.review_highlight h USING(uuid_review_highlight) WHERE s.uuid_review=:id ORDER BY h.name")
            .param("id",uuid).query(String.class).list();
        var photos=jdbc.sql("SELECT file_url FROM kampive.review_photo WHERE uuid_review=:id ORDER BY sort_order")
            .param("id",uuid).query(String.class).list();
        return new Item(uuid,r.getObject("uuid_campsite",UUID.class),r.getString("campsite_name"),r.getString("author_name"),
            r.getBigDecimal("rating"),r.getString("comment"),r.getString("method_name"),r.getString("status"),r.getTimestamp("created_at").toInstant(),highlights,photos);
    }
    private void published(UUID campsite) {
        if(jdbc.sql("SELECT uuid_campsite FROM kampive.campsite WHERE uuid_campsite=:id AND status='published' FOR SHARE")
            .param("id",campsite).query(UUID.class).optional().isEmpty()) fail(404);
    }
    public Receipt create(UUID actor,UUID campsite,Input input) {
        active(jdbc,actor); published(campsite);
        validate(input);
        var uuid=jdbc.sql("INSERT INTO kampive.review(uuid_app_user,uuid_campsite,uuid_camping_method,rating,comment,status) VALUES (:actor,:campsite,:method,:rating,:comment,'pending') ON CONFLICT (uuid_app_user,uuid_campsite) DO NOTHING RETURNING uuid_review")
            .param("actor",actor).param("campsite",campsite).param("method",input.campingMethodUuid(),Types.OTHER)
            .param("rating",input.rating()).param("comment",input.text()).query(UUID.class).optional();
        if(uuid.isEmpty()) fail(409);
        children(uuid.get(),input);
        return new Receipt(uuid.get(),"pending");
    }
    private void validate(Input input) {
        if(input.campingMethodUuid()!=null && jdbc.sql("SELECT uuid_camping_method FROM kampive.camping_method WHERE uuid_camping_method=:id")
            .param("id",input.campingMethodUuid()).query(UUID.class).optional().isEmpty()) fail(400);
        for(UUID highlight:input.highlightUuids())
            if(jdbc.sql("SELECT uuid_review_highlight FROM kampive.review_highlight WHERE uuid_review_highlight=:id")
                .param("id",highlight).query(UUID.class).optional().isEmpty()) fail(400);
    }
    private void children(UUID review,Input input) {
        for(UUID highlight:input.highlightUuids())
            jdbc.sql("INSERT INTO kampive.review_highlight_selection(uuid_review,uuid_review_highlight) VALUES (:review,:highlight)")
                .param("review",review).param("highlight",highlight).update();
        for(int i=0;i<input.photos().size();i++)
            jdbc.sql("INSERT INTO kampive.review_photo(uuid_review,file_url,sort_order) VALUES (:review,:url,:position)")
                .param("review",review).param("url",input.photos().get(i)).param("position",i+1).update();
    }
    private UUID lockOwned(UUID actor,UUID review) {
        active(jdbc,actor);
        return jdbc.sql("SELECT uuid_review FROM kampive.review WHERE uuid_review=:review AND uuid_app_user=:actor FOR UPDATE")
            .param("review",review).param("actor",actor).query(UUID.class).optional().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
    }
    public Editable get(UUID actor,UUID review) {
        active(jdbc,actor);
        var row=jdbc.sql("SELECT r.*,c.name AS campsite_name FROM kampive.review r JOIN kampive.campsite c USING(uuid_campsite) WHERE r.uuid_review=:review AND r.uuid_app_user=:actor FOR SHARE OF r")
            .param("review",review).param("actor",actor).query((r,n)-> {
                var highlights=jdbc.sql("SELECT uuid_review_highlight FROM kampive.review_highlight_selection WHERE uuid_review=:review ORDER BY uuid_review_highlight").param("review",review).query(UUID.class).list();
                var photos=jdbc.sql("SELECT file_url FROM kampive.review_photo WHERE uuid_review=:review ORDER BY sort_order").param("review",review).query(String.class).list();
                return new Editable(review,r.getObject("uuid_campsite",UUID.class),r.getString("campsite_name"),new Input(r.getInt("rating"),r.getString("comment"),r.getObject("uuid_camping_method",UUID.class),highlights,photos));
            }).optional();
        if(row.isEmpty()) fail(404);
        return row.get();
    }
    public Receipt update(UUID actor,UUID review,Input input) {
        lockOwned(actor,review); validate(input);
        jdbc.sql("UPDATE kampive.review SET rating=:rating,comment=:text,uuid_camping_method=:method,status='pending' WHERE uuid_review=:review AND uuid_app_user=:actor")
            .param("rating",input.rating()).param("text",input.text()).param("method",input.campingMethodUuid(),Types.OTHER).param("review",review).param("actor",actor).update();
        clearChildren(review); children(review,input);
        return new Receipt(review,"pending");
    }
    public void delete(UUID actor,UUID review) {
        lockOwned(actor,review); clearChildren(review);
        jdbc.sql("DELETE FROM kampive.review WHERE uuid_review=:review AND uuid_app_user=:actor").param("review",review).param("actor",actor).update();
    }
    private void clearChildren(UUID review) {
        jdbc.sql("DELETE FROM kampive.review_highlight_selection WHERE uuid_review=:review").param("review",review).update();
        jdbc.sql("DELETE FROM kampive.review_photo WHERE uuid_review=:review").param("review",review).update();
    }

}
