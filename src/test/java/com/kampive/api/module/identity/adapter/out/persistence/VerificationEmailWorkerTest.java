package com.kampive.api.module.identity.adapter.out.persistence;

import java.sql.ResultSet;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.kampive.api.module.identity.configuration.RecoverySettings;
import com.kampive.api.module.identity.configuration.VerificationSettings;
import com.kampive.api.module.notification.application.port.in.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked","rawtypes"})
class VerificationEmailWorkerTest {
    final UUID id=UUID.randomUUID(),actor=UUID.randomUUID(),recovery=UUID.randomUUID();
    final JdbcClient jdbc=mock(JdbcClient.class,RETURNS_DEEP_STUBS);
    final EmailSender sender=mock(EmailSender.class);
    final EmailTemplates templates=mock(EmailTemplates.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final RecoverySettings settings=new RecoverySettings("https://example.test/reset-password","https://example.test/logo","https://example.test/support",Base64.getEncoder().encodeToString(new byte[32]));
    void prepare(boolean retry) throws Exception {
        when(sender.available()).thenReturn(true);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ResultSet job=mock(ResultSet.class);
        when(job.getObject(1,UUID.class)).thenReturn(id);
        when(job.getObject(2,UUID.class)).thenReturn(actor);
        when(job.getString(3)).thenReturn("recipient@example.test");
        when(job.getString(4)).thenReturn(retry?"stored-hash":null);
        when(jdbc.sql(startsWith("SELECT u.uuid_app_user")).query(UUID.class).optional()).thenReturn(Optional.of(actor));
        when(jdbc.sql(startsWith("SELECT uuid_email_verification")).param("actor",actor).query(any(RowMapper.class)))
            .thenAnswer(call -> mapped(call.getArgument(0),job));
        when(jdbc.sql(startsWith("SELECT name FROM")).param("actor",actor).param("email","recipient@example.test").query(String.class).optional()).thenReturn(Optional.of("Explorer"));
        when(jdbc.sql(startsWith("SELECT count(*) FROM kampive.email_verification")).param("id",id).param(eq("hash"),any()).query(Long.class).single()).thenReturn(1L);
        when(templates.render(eq("email-verification"),anyMap())).thenReturn(new EmailTemplates.Rendered(UUID.randomUUID(),1,"Subject","html","text"));
    }
    Object mapped(RowMapper mapper,ResultSet row) throws Exception {
        var query=mock(JdbcClient.MappedQuerySpec.class);
        Object item=mapper.mapRow(row,0);
        when(query.optional()).thenReturn(Optional.of(item));
        return query;
    }
    @Test void retriesKeepSameTokenAndIdempotencyKeyWithoutStoringRawToken() throws Exception {
        prepare(false);
        var worker=new VerificationEmailWorker(jdbc,manager,sender,templates,new VerificationSettings("https://example.test/verify-email",settings));
        worker.deliver();
        var values=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(templates).render(eq("email-verification"),values.capture());
        String link=(String)values.getValue().get("verify_url");
        assertTrue(link.matches("https://example.test/verify-email\\?token=[A-Za-z0-9_-]{43}"));
        verify(sender).send(eq(id),eq("recipient@example.test"),any());
        reset(jdbc,templates,sender,manager);
        prepare(true);
        worker.deliver();
        verify(templates).render(eq("email-verification"),values.capture());
        assertEquals(link,values.getValue().get("verify_url"));
        verify(sender).send(eq(id),eq("recipient@example.test"),any());
        verify(jdbc,never()).sql(contains("token="));
    }
    @Test void expiredRetryDoesNotSend() throws Exception {
        prepare(true);
        when(jdbc.sql(startsWith("SELECT count(*) FROM kampive.email_verification")).param("id",id).param(eq("hash"),any()).query(Long.class).single()).thenReturn(0L);
        new VerificationEmailWorker(jdbc,manager,sender,templates,new VerificationSettings("https://example.test/verify-email",settings)).deliver();
        verify(sender,never()).send(any(),any(),any());
    }
    @Test void deliveryFailureIsRetriedWithoutLeakingProviderError() throws Exception {
        prepare(false);
        doThrow(new RuntimeException("private provider payload")).when(sender).send(any(),any(),any());
        new VerificationEmailWorker(jdbc,manager,sender,templates,new VerificationSettings("https://example.test/verify-email",settings)).deliver();
        verify(jdbc).sql(startsWith("UPDATE kampive.email_verification SET attempts=attempts+1,failed="));
        verify(jdbc,never()).sql(contains("private provider payload"));
    }
}
