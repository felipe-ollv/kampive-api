package com.kampive.api.module.notification.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.kampive.api.module.notification.application.port.out.EmailTemplateRepository;

@Repository
public class JdbcEmailTemplateRepository implements EmailTemplateRepository {
    private final JdbcClient jdbc;
    public JdbcEmailTemplateRepository(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Optional<Template> active(String code,String locale) {
        return jdbc.sql("SELECT uuid_email_template,version,subject,html_body,text_body FROM kampive.email_template WHERE code=:code AND locale=:locale AND active")
            .param("code",code).param("locale",locale)
            .query((r,n)->new Template(r.getObject("uuid_email_template",UUID.class),r.getInt("version"),
                r.getString("subject"),r.getString("html_body"),r.getString("text_body"))).optional();
    }
}
