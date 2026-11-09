package com.kampive.api.module.identity.adapter.out.persistence;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.kampive.api.module.identity.application.port.in.EmailVerification;
import com.kampive.api.module.identity.configuration.VerificationSettings;
import com.kampive.api.module.notification.application.port.in.EmailSender;
import com.kampive.api.module.identity.domain.RecoveryFailure;
@Repository
@Transactional
public class JdbcEmailVerification implements EmailVerification {
    private final JdbcClient jdbc;
    private final EmailSender sender;
    private final VerificationSettings settings;
    public JdbcEmailVerification(JdbcClient jdbc,EmailSender sender,VerificationSettings settings) {
        this.jdbc=jdbc; this.sender=sender; this.settings=settings;
    }
    public Status status(UUID actor) {
        return jdbc.sql("SELECT email_verified_at IS NOT NULL,email_verified_at IS NOT NULL OR email_verification_required_at>clock_timestamp(),email_verification_required_at FROM kampive.app_user WHERE uuid_app_user=:actor AND deactivated_at IS NULL")
            .param("actor",actor).query((r,n)->new Status(r.getBoolean(1),r.getBoolean(2),r.getTimestamp(3).toInstant(),sender.available() && settings.configured())).optional()
            .orElse(new Status(false,false,null,false));
    }
    public void registered(UUID actor) { enqueue(actor); }
    public void resend(UUID actor) {
        if(!sender.available() || !settings.configured()) throw new RecoveryFailure(RecoveryFailure.Reason.UNAVAILABLE);
        enqueue(actor);
    }
    private void enqueue(UUID actor) {
        var email=jdbc.sql("SELECT email FROM kampive.app_user WHERE uuid_app_user=:actor AND deactivated_at IS NULL AND email_verified_at IS NULL FOR UPDATE")
            .param("actor",actor).query(String.class).optional();
        if(email.isEmpty()) return;
        long recent=jdbc.sql("SELECT count(*) FROM kampive.email_verification WHERE uuid_app_user=:actor AND created_at>clock_timestamp()-interval '1 minute'")
            .param("actor",actor).query(Long.class).single();
        if(recent>0) return;
        jdbc.sql("UPDATE kampive.email_verification SET consumed_at=clock_timestamp() WHERE uuid_app_user=:actor AND consumed_at IS NULL").param("actor",actor).update();
        // Token is issued by the worker only when transport is configured.
        jdbc.sql("INSERT INTO kampive.email_verification(uuid_app_user,email_snapshot) VALUES (:actor,:email)").param("actor",actor).param("email",email.get()).update();
    }
    public void confirm(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) invalid();
        String hash=JdbcPasswordRecovery.digest(token);
        var actor=jdbc.sql("SELECT uuid_app_user FROM kampive.email_verification WHERE token_hash=:hash").param("hash",hash).query(UUID.class).optional();
        if(actor.isEmpty()) invalid();
        var email=jdbc.sql("SELECT email FROM kampive.app_user WHERE uuid_app_user=:actor AND deactivated_at IS NULL FOR UPDATE")
            .param("actor",actor.get()).query(String.class).optional();
        if(email.isEmpty()) invalid();
        int consumed=jdbc.sql("UPDATE kampive.email_verification SET consumed_at=clock_timestamp() WHERE token_hash=:hash AND uuid_app_user=:actor AND email_snapshot=:email AND consumed_at IS NULL AND expires_at>clock_timestamp()")
            .param("hash",hash).param("actor",actor.get()).param("email",email.get()).update();
        if(consumed!=1) invalid();
        jdbc.sql("UPDATE kampive.app_user SET email_verified_at=COALESCE(email_verified_at,clock_timestamp()) WHERE uuid_app_user=:actor").param("actor",actor.get()).update();
        jdbc.sql("UPDATE kampive.email_verification SET consumed_at=clock_timestamp() WHERE uuid_app_user=:actor AND consumed_at IS NULL").param("actor",actor.get()).update();
    }
    static String token(UUID id,String key) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Base64.getDecoder().decode(key),"HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(("kampive-email-verification:"+id).getBytes(StandardCharsets.UTF_8)));
        } catch(java.security.GeneralSecurityException e) { throw new IllegalStateException("Verification unavailable"); }
    }
    private static void invalid() { throw new RecoveryFailure(RecoveryFailure.Reason.INVALID_TOKEN); }
}
