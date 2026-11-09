package com.kampive.api.module.identity.adapter.out.persistence;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.kampive.api.module.identity.configuration.VerificationSettings;
import com.kampive.api.module.notification.application.port.in.*;
@Component
public class VerificationEmailWorker {
    private record Job(UUID id,UUID actor,String email,String hash) {}
    private final JdbcClient jdbc;
    private final TransactionTemplate transaction;
    private final EmailSender sender;
    private final EmailTemplates templates;
    private final VerificationSettings settings;
    public VerificationEmailWorker(JdbcClient jdbc,PlatformTransactionManager manager,EmailSender sender,EmailTemplates templates,VerificationSettings settings) {
        this.jdbc=jdbc;this.transaction=new TransactionTemplate(manager);this.sender=sender;this.templates=templates;this.settings=settings;
    }
    @Scheduled(fixedDelayString="${app.recovery.poll-ms:5000}")
    public void deliver() {
        if(!sender.available() || !settings.configured()) return;
        transaction.executeWithoutResult(status->process());
    }
    private void process() {
        // Lock users before jobs, matching resend/confirmation and avoiding lock inversion.
        var actor=jdbc.sql("SELECT u.uuid_app_user FROM kampive.app_user u WHERE EXISTS (SELECT 1 FROM kampive.email_verification v WHERE v.uuid_app_user=u.uuid_app_user AND v.sent_at IS NULL AND v.consumed_at IS NULL AND NOT v.failed AND v.available_at<=clock_timestamp()) ORDER BY u.id LIMIT 1 FOR UPDATE OF u SKIP LOCKED")
            .query(UUID.class).optional();
        if(actor.isEmpty()) return;
        var next=jdbc.sql("SELECT uuid_email_verification,uuid_app_user,email_snapshot,token_hash FROM kampive.email_verification WHERE uuid_app_user=:actor AND sent_at IS NULL AND consumed_at IS NULL AND NOT failed AND available_at<=clock_timestamp() ORDER BY created_at LIMIT 1 FOR UPDATE")
            .param("actor",actor.get()).query((r,n)->new Job(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getString(4))).optional();
        if(next.isEmpty()) return;
        Job job=next.get();
        var name=jdbc.sql("SELECT name FROM kampive.app_user WHERE uuid_app_user=:actor AND email=:email AND deactivated_at IS NULL AND email_verified_at IS NULL")
            .param("actor",job.actor()).param("email",job.email()).query(String.class).optional();
        if(name.isEmpty()) { fail(job.id());return; }
        String token=JdbcEmailVerification.token(job.id(),settings.recovery().tokenKey());
        String hash=JdbcPasswordRecovery.digest(token);
        if(job.hash()==null) {
            jdbc.sql("UPDATE kampive.email_verification SET token_hash=:hash,expires_at=clock_timestamp()+interval '24 hours' WHERE uuid_email_verification=:id")
                .param("hash",hash).param("id",job.id()).update();
        } else {
            long valid=jdbc.sql("SELECT count(*) FROM kampive.email_verification WHERE uuid_email_verification=:id AND token_hash=:hash AND expires_at>clock_timestamp()")
                .param("id",job.id()).param("hash",hash).query(Long.class).single();
            if(valid==0) { fail(job.id());return; }
        }
        try {
            var values=Map.of("first_name",name.get(),"logo_url",settings.recovery().logoUrl(),"support_url",settings.recovery().supportUrl(),"verify_url",settings.confirmUrl()+"?token="+token);
            sender.send(job.id(),job.email(),templates.render("email-verification",values));
            jdbc.sql("UPDATE kampive.email_verification SET sent_at=clock_timestamp(),attempts=attempts+1 WHERE uuid_email_verification=:id").param("id",job.id()).update();
        } catch(RuntimeException failure) {
            jdbc.sql("UPDATE kampive.email_verification SET attempts=attempts+1,failed=(attempts+1>=5),available_at=clock_timestamp()+interval '1 minute' WHERE uuid_email_verification=:id").param("id",job.id()).update();
        }
    }
    private void fail(UUID id) { jdbc.sql("UPDATE kampive.email_verification SET failed=true WHERE uuid_email_verification=:id").param("id",id).update(); }
}
