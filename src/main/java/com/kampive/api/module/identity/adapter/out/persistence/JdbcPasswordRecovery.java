package com.kampive.api.module.identity.adapter.out.persistence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import com.kampive.api.module.identity.application.port.in.PasswordRecovery;
import com.kampive.api.module.identity.application.port.out.PasswordHasher;
import com.kampive.api.module.identity.configuration.RecoverySettings;
import com.kampive.api.module.identity.domain.RecoveryFailure;
import com.kampive.api.module.notification.application.port.in.EmailSender;
import static com.kampive.api.module.identity.domain.RecoveryFailure.Reason.*;

@Repository @Transactional
public class JdbcPasswordRecovery implements PasswordRecovery {
    private final JdbcClient jdbc;
    private final PasswordHasher passwords;
    private final EmailSender sender;
    private final RecoverySettings settings;
    public JdbcPasswordRecovery(JdbcClient jdbc,PasswordHasher passwords,EmailSender sender,RecoverySettings settings) {
        this.jdbc=jdbc; this.passwords=passwords; this.sender=sender; this.settings=settings;
    }
    public void request(String email) {
        // Same response for every address while delivery is unavailable; no account lookup.
        if(!sender.available() || !settings.configured()) throw new RecoveryFailure(UNAVAILABLE);
        var user=jdbc.sql("SELECT u.uuid_app_user FROM kampive.app_user u JOIN kampive.user_credential c USING(uuid_app_user) WHERE lower(u.email)=:email AND u.deactivated_at IS NULL FOR UPDATE OF u")
            .param("email",email.strip().toLowerCase(Locale.ROOT)).query(UUID.class).optional();
        if(user.isEmpty()) return;
        // Serialize with other requests and password changes; suppress mail floods per account.
        long recent=jdbc.sql("SELECT count(*) FROM kampive.password_email_job WHERE uuid_app_user=:user AND template_code='password-reset' AND created_at>clock_timestamp()-interval '60 seconds'")
            .param("user",user.get()).query(Long.class).single();
        if(recent==0) enqueue(user.get(),"password-reset");
    }
    public void reset(String token,String password) {
        if(password==null || password.length()<8 || password.length()>128 || !password.matches("(?s)(?=.*[A-Z])(?=.*[0-9]).*")) throw new RecoveryFailure(INVALID_PASSWORD);
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw new RecoveryFailure(INVALID_TOKEN);
        String hash=digest(token);
        var actor=jdbc.sql("SELECT uuid_app_user FROM kampive.password_recovery WHERE token_hash=:hash")
            .param("hash",hash).query(UUID.class).optional();
        if(actor.isEmpty()) throw new RecoveryFailure(INVALID_TOKEN);
        var active=jdbc.sql("SELECT uuid_app_user FROM kampive.app_user WHERE uuid_app_user=:actor AND deactivated_at IS NULL FOR UPDATE")
            .param("actor",actor.get()).query(UUID.class).optional();
        if(active.isEmpty()) throw new RecoveryFailure(INVALID_TOKEN);
        int consumed=jdbc.sql("UPDATE kampive.password_recovery SET consumed_at=clock_timestamp() WHERE token_hash=:hash AND consumed_at IS NULL AND expires_at>clock_timestamp()")
            .param("hash",hash).update();
        if(consumed!=1) throw new RecoveryFailure(INVALID_TOKEN);
        int updated=jdbc.sql("UPDATE kampive.user_credential SET password_hash=:password,password_changed_at=clock_timestamp() WHERE uuid_app_user=:actor")
            .param("password",passwords.hash(password)).param("actor",actor.get()).update();
        if(updated!=1) throw new RecoveryFailure(INVALID_TOKEN);
        jdbc.sql("UPDATE kampive.password_recovery SET consumed_at=clock_timestamp() WHERE uuid_app_user=:actor AND consumed_at IS NULL")
            .param("actor",actor.get()).update();
        // A delivery outage cannot undo a successful password change.
        enqueue(actor.get(),"password-changed");
    }
    private void enqueue(UUID actor,String code) {
        jdbc.sql("INSERT INTO kampive.password_email_job(uuid_app_user,template_code) VALUES (:actor,:code)")
            .param("actor",actor).param("code",code).update();
    }
    public static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
}
