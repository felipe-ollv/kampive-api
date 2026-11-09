package com.kampive.api.module.identity.adapter.out.persistence;

import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kampive.api.module.identity.configuration.RecoverySettings;
import com.kampive.api.module.notification.application.port.in.EmailSender;
import com.kampive.api.module.notification.application.port.in.EmailTemplates;

/** Durable jobs contain no plaintext token. Provider must support the job UUID as idempotency key. */
@Component
public class PasswordEmailWorker {
    private record Job(UUID uuid,UUID actor,String code,UUID recovery) {}
    private record Recipient(String email,String name) {}
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final EmailSender sender;
    private final EmailTemplates templates;
    private final RecoverySettings settings;
    public PasswordEmailWorker(JdbcClient jdbc,PlatformTransactionManager manager,EmailSender sender,EmailTemplates templates,RecoverySettings settings) {
        this.jdbc=jdbc; this.transaction=new TransactionTemplate(manager); this.sender=sender; this.templates=templates; this.settings=settings;
    }
    @Scheduled(fixedDelayString="${app.recovery.poll-ms:5000}")
    public void deliver() {
        if(!sender.available() || !settings.configured()) return;
        transaction.executeWithoutResult(status -> process());
    }
    private void process() {
        var next=jdbc.sql("SELECT uuid_password_email_job,uuid_app_user,template_code,uuid_password_recovery FROM kampive.password_email_job WHERE sent_at IS NULL AND NOT failed AND available_at<=clock_timestamp() ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED")
            .query((r,n)->new Job(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getObject(4,UUID.class))).optional();
        if(next.isEmpty()) return;
        Job job=next.get();
        var recipient=jdbc.sql("SELECT u.email,u.name FROM kampive.app_user u JOIN kampive.user_credential c USING(uuid_app_user) WHERE u.uuid_app_user=:actor AND u.deactivated_at IS NULL FOR UPDATE OF u")
            .param("actor",job.actor()).query((r,n)->new Recipient(r.getString(1),r.getString(2))).optional();
        if(recipient.isEmpty()) { fail(job.uuid()); return; }
        var values=new HashMap<>(Map.of("first_name",recipient.get().name(),"logo_url",settings.logoUrl(),"support_url",settings.supportUrl()));
        if(job.code().equals("password-reset")) {
            // Never deliver an older queued recovery request after the user has changed their password.
            long stale=jdbc.sql("SELECT count(*) FROM kampive.user_credential c JOIN kampive.password_email_job j USING(uuid_app_user) WHERE j.uuid_password_email_job=:id AND c.password_changed_at>=j.created_at")
                .param("id",job.uuid()).query(Long.class).single();
            if(stale>0) { fail(job.uuid()); return; }
            String token=token(job.uuid());
            if(job.recovery()==null) {
                jdbc.sql("UPDATE kampive.password_recovery SET consumed_at=clock_timestamp() WHERE uuid_app_user=:actor AND consumed_at IS NULL")
                    .param("actor",job.actor()).update();
                UUID recovery=jdbc.sql("INSERT INTO kampive.password_recovery(uuid_app_user,token_hash,expires_at) VALUES (:actor,:hash,clock_timestamp()+interval '30 minutes') RETURNING uuid_password_recovery")
                    .param("actor",job.actor()).param("hash",JdbcPasswordRecovery.digest(token)).query(UUID.class).single();
                jdbc.sql("UPDATE kampive.password_email_job SET uuid_password_recovery=:recovery WHERE uuid_password_email_job=:id")
                    .param("recovery",recovery).param("id",job.uuid()).update();
            } else {
                long valid=jdbc.sql("SELECT count(*) FROM kampive.password_recovery WHERE uuid_password_recovery=:id AND consumed_at IS NULL AND expires_at>clock_timestamp() AND token_hash=:hash")
                    .param("id",job.recovery()).param("hash",JdbcPasswordRecovery.digest(token)).query(Long.class).single();
                if(valid==0) { fail(job.uuid()); return; }
            }
            values.put("reset_url",settings.resetUrl()+"?token="+token);
            values.put("expires_minutes","30");
        }
        try {
            sender.send(job.uuid(),recipient.get().email(),templates.render(job.code(),values));
            jdbc.sql("UPDATE kampive.password_email_job SET sent_at=clock_timestamp(),attempts=attempts+1 WHERE uuid_password_email_job=:id")
                .param("id",job.uuid()).update();
        } catch(RuntimeException failure) {
            // Do not persist provider messages: they may contain the recipient or recovery URL.
            jdbc.sql("UPDATE kampive.password_email_job SET attempts=attempts+1,failed=(attempts+1>=5),available_at=clock_timestamp()+interval '1 minute' WHERE uuid_password_email_job=:id")
                .param("id",job.uuid()).update();
        }
    }
    private void fail(UUID id) {
        jdbc.sql("UPDATE kampive.password_email_job SET failed=true WHERE uuid_password_email_job=:id").param("id",id).update();
    }
    private String token(UUID id) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(settings.tokenKey()),"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("kampive-password-reset:"+id).getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.GeneralSecurityException e) { throw new IllegalStateException("Recovery cryptography unavailable"); }
    }
}
