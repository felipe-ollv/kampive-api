package com.kampive.api.module.notification.adapter.out.email;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import com.kampive.api.module.notification.application.port.in.EmailTemplates;

class BrevoEmailSenderTest {
    private final HttpClient client=mock(HttpClient.class);
    private final JsonMapper json=JsonMapper.builder().build();
    private final BrevoEmailSender sender=new BrevoEmailSender(client,json,"test-key","contato@example.com","Kampive");
    private final UUID id=UUID.randomUUID();
    private final EmailTemplates.Rendered message=new EmailTemplates.Rendered(UUID.randomUUID(),1,"Senha","<p>Olá</p>","Olá");

    @SuppressWarnings("unchecked")
    private void response(int status,String body) throws Exception {
        HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }

    @Test void sendsDatabaseContentWithStableIdempotencyAndTimeout() throws Exception {
        response(201,"{}");
        sender.send(id,"user@example.com",message);
        var capture=ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(capture.capture(),any(HttpResponse.BodyHandler.class));
        var request=capture.getValue();
        assertEquals("https://api.brevo.com/v3/smtp/email",request.uri().toString());
        assertEquals("test-key",request.headers().firstValue("api-key").orElseThrow());
        assertEquals(10,request.timeout().orElseThrow().toSeconds());
        var bytes=new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { byte[] chunk=new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk); }
            public void onError(Throwable error) { fail(error); }
            public void onComplete() {}
        });
        var body=json.readTree(bytes.toString(StandardCharsets.UTF_8));
        assertEquals(id.toString(),body.path("headers").path("idempotencyKey").asText());
        assertEquals(message.html(),body.path("htmlContent").asText());
        assertEquals(message.text(),body.path("textContent").asText());
        assertEquals("user@example.com",body.path("to").get(0).path("email").asText());
    }

    @Test void acceptsPreviouslyAcceptedJob() throws Exception {
        response(400,"{\"code\":\"duplicate_parameter\"}");
        assertDoesNotThrow(()->sender.send(id,"user@example.com",message));
    }

    @Test void quotaFailureDoesNotExposeProviderBody() throws Exception {
        response(429,"private recipient and token");
        var error=assertThrows(IllegalStateException.class,()->sender.send(id,"user@example.com",message));
        assertEquals("Email delivery failed",error.getMessage());
        assertNull(error.getCause());
    }

    @Test void malformedResponseFailsClosed() throws Exception {
        response(400,"not json");
        assertThrows(IllegalStateException.class,()->sender.send(id,"user@example.com",message));
    }

    @Test void missingConfigurationNeverSends() {
        var disabled=new BrevoEmailSender(client,json,"","","Kampive");
        assertFalse(disabled.available());
        assertThrows(IllegalStateException.class,()->disabled.send(id,"user@example.com",message));
        verifyNoInteractions(client);
    }
}
