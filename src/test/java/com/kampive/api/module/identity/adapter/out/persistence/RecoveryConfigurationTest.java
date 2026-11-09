package com.kampive.api.module.identity.adapter.out.persistence;

import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import com.kampive.api.module.identity.configuration.RecoverySettings;
import com.kampive.api.module.identity.application.port.out.PasswordHasher;
import com.kampive.api.module.identity.domain.RecoveryFailure;
import com.kampive.api.module.notification.application.port.in.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecoveryConfigurationTest {
    @Test void disabledDeliveryDoesNotLookUpAccountsOrCreateJobs() {
        var jdbc=mock(JdbcClient.class); var sender=mock(EmailSender.class);
        var settings=new RecoverySettings("","","","");
        var recovery=new JdbcPasswordRecovery(jdbc,mock(PasswordHasher.class),sender,settings);
        assertThrows(RecoveryFailure.class,()->recovery.request("existing@example.test"));
        assertThrows(RecoveryFailure.class,()->recovery.request("unknown@example.test"));
        verifyNoInteractions(jdbc);
        new PasswordEmailWorker(jdbc,mock(PlatformTransactionManager.class),sender,mock(EmailTemplates.class),settings).deliver();
        verifyNoInteractions(jdbc);
    }
    @Test void configurationRequiresHttpsAndStrongKeyWithoutPrintingSecrets() {
        String secret=Base64.getEncoder().encodeToString(new byte[32]);
        assertTrue(new RecoverySettings("https://example.test/reset-password","https://example.test/logo","https://example.test/support",secret).configured());
        assertFalse(new RecoverySettings("http://example.test/reset","https://example.test/logo","https://example.test/support",secret).configured());
        assertFalse(new RecoverySettings("https://example.test/reset?url=bad","https://example.test/logo","https://example.test/support",secret).configured());
        assertFalse(new RecoverySettings("https://example.test/reset","https://example.test/logo","https://example.test/support","short").configured());
        assertFalse(new RecoverySettings("a","b","c",secret).toString().contains(secret));
    }
    @Test void tokenDigestIsStableAndNeverTheTokenItself() {
        String token="a".repeat(43);
        assertEquals(64,JdbcPasswordRecovery.digest(token).length());
        assertEquals(JdbcPasswordRecovery.digest(token),JdbcPasswordRecovery.digest(token));
        assertNotEquals(token,JdbcPasswordRecovery.digest(token));
    }
}
