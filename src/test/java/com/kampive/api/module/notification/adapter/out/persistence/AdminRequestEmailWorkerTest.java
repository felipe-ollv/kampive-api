package com.kampive.api.module.notification.adapter.out.persistence;

import java.sql.ResultSet;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import com.kampive.api.module.notification.application.port.in.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SuppressWarnings({"unchecked","rawtypes"})
class AdminRequestEmailWorkerTest {
    final UUID id=UUID.randomUUID(),request=UUID.randomUUID();
    final JdbcClient jdbc=mock(JdbcClient.class,RETURNS_DEEP_STUBS);
    final EmailSender sender=mock(EmailSender.class);
    final EmailTemplates templates=mock(EmailTemplates.class);
    final PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
    final AdminRequestEmailWorker worker=new AdminRequestEmailWorker(jdbc,manager,sender,templates);
    void prepare() throws Exception {
        when(sender.available()).thenReturn(true);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        ResultSet job=mock(ResultSet.class);
        when(job.getObject(1,UUID.class)).thenReturn(id);
        when(job.getString(2)).thenReturn("recipient@example.test");
        when(job.getString(3)).thenReturn("suggestion");
        when(job.getObject(4,UUID.class)).thenReturn(request);
        when(job.getString(5)).thenReturn("Camping\r\nSerra");
        when(jdbc.sql(startsWith("SELECT j.uuid_admin_request_email")).query(any(RowMapper.class)))
            .thenAnswer(call -> {
                var query=mock(JdbcClient.MappedQuerySpec.class);
                Object mapped=((RowMapper)call.getArgument(0)).mapRow(job,0);
                when(query.optional()).thenReturn(Optional.of(mapped));
                return query;
            });
        when(templates.render(eq("admin-request"),anyMap())).thenReturn(new EmailTemplates.Rendered(UUID.randomUUID(),1,"Subject","html","text"));
    }
    @Test void unavailableProviderLeavesOutboxUntouched() {
        worker.deliver(); verifyNoInteractions(jdbc,templates,manager);
    }
    @Test void successMarksSentAndSanitizesInput() throws Exception {
        prepare(); worker.deliver();
        verify(sender).send(eq(id),eq("recipient@example.test"),any());
        var values=org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(templates).render(eq("admin-request"),values.capture());
        assertEquals("Camping  Serra",values.getValue().get("campsite_name"));
        verify(jdbc).sql(startsWith("UPDATE kampive.admin_request_email SET sent_at="));
    }
    @Test void failureRetriesSameJobWithoutMarkingAsSent() throws Exception {
        prepare(); doThrow(new RuntimeException("secret provider response")).when(sender).send(any(),any(),any());
        worker.deliver(); worker.deliver();
        verify(sender,times(2)).send(eq(id),eq("recipient@example.test"),any());
        verify(jdbc,times(2)).sql(startsWith("UPDATE kampive.admin_request_email SET attempts="));
        verify(jdbc,never()).sql(startsWith("UPDATE kampive.admin_request_email SET sent_at="));
    }
}
