package com.kampive.api.module.notification.adapter.out.persistence;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kampive.api.module.notification.application.port.in.EmailSender;
import com.kampive.api.module.notification.application.port.in.EmailTemplates;

/** Transactional outbox. Retries keep the same provider idempotency key. */
@Component
public class AdminRequestEmailWorker {
    private record Job(UUID id, String email, String type, UUID request, String campsite) {}
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final EmailSender sender;
    private final EmailTemplates templates;
    public AdminRequestEmailWorker(JdbcClient jdbc, PlatformTransactionManager manager,
            EmailSender sender, EmailTemplates templates) {
        this.jdbc=jdbc; this.transaction=new TransactionTemplate(manager);
        this.sender=sender; this.templates=templates;
    }
    @Scheduled(fixedDelayString="${app.admin-alerts.poll-ms:5000}")
    public void deliver() {
        if (!sender.available()) return;
        transaction.executeWithoutResult(status -> process());
    }
    private void process() {
        jdbc.sql("""
            UPDATE kampive.admin_request_email j SET cancelled_at=clock_timestamp()
            WHERE j.sent_at IS NULL AND j.cancelled_at IS NULL AND NOT EXISTS (
              SELECT 1 FROM kampive.administrator a JOIN kampive.app_user u USING(uuid_app_user)
              WHERE a.uuid_app_user=j.uuid_recipient AND a.revoked_at IS NULL
                AND a.request_email_enabled AND u.deactivated_at IS NULL AND u.email_verified_at IS NOT NULL)
            """).update();
        var next = jdbc.sql("""
            SELECT j.uuid_admin_request_email,u.email,j.request_type,j.request_uuid,j.campsite_name
            FROM kampive.admin_request_email j
            JOIN kampive.administrator a ON a.uuid_app_user=j.uuid_recipient
            JOIN kampive.app_user u ON u.uuid_app_user=a.uuid_app_user
            WHERE j.sent_at IS NULL AND j.cancelled_at IS NULL AND j.available_at<=clock_timestamp()
              AND a.revoked_at IS NULL AND a.request_email_enabled
              AND u.deactivated_at IS NULL AND u.email_verified_at IS NOT NULL
            ORDER BY j.created_at,j.uuid_admin_request_email LIMIT 1 FOR UPDATE OF j SKIP LOCKED
            """).query((r,n) -> new Job(r.getObject(1,UUID.class),r.getString(2),r.getString(3),
                r.getObject(4,UUID.class),r.getString(5))).optional();
        if (next.isEmpty()) return;
        var job = next.get();
        try {
            var values = Map.of("event_title", job.type().equals("test") ? "Avisos administrativos ativados" : "Nova solicitação para análise",
                "request_type", label(job.type()), "request_id", job.request().toString(),
                "campsite_name", clean(job.campsite()));
            sender.send(job.id(),job.email(),templates.render("admin-request",values));
            jdbc.sql("UPDATE kampive.admin_request_email SET sent_at=clock_timestamp(),attempts=attempts+1 WHERE uuid_admin_request_email=:id")
                .param("id",job.id()).update();
        } catch (RuntimeException failure) {
            // Do not log recipient, request contents, provider responses or secrets.
            jdbc.sql("""
                UPDATE kampive.admin_request_email SET attempts=attempts+1,
                  available_at=clock_timestamp()+make_interval(secs => LEAST(3600,60*power(2,LEAST(attempts,6)))::double precision)
                WHERE uuid_admin_request_email=:id
                """).param("id",job.id()).update();
            org.slf4j.LoggerFactory.getLogger(AdminRequestEmailWorker.class)
                .warn("Administrative email delivery deferred; retry scheduled");
        }
    }
    private static String label(String type) {
        return switch(type) {
            case "suggestion" -> "Indicação de camping";
            case "registration" -> "Cadastro ou reenvio de camping";
            case "claim" -> "Vínculo de proprietário";
            case "review" -> "Avaliação de camping";
            case "test" -> "Confirmação da configuração";
            default -> throw new IllegalArgumentException("Unknown request type");
        };
    }
    private static String clean(String value) {
        String cleaned=value.replaceAll("[\\p{Cntrl}]", " ").strip();
        return cleaned.isEmpty() ? "Camping" : cleaned;
    }
}
