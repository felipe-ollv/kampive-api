package com.kampive.api.module.notification.configuration;

import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.kampive.api.module.notification.application.port.in.EmailSender;
import com.kampive.api.module.notification.application.port.in.EmailTemplates;
import com.kampive.api.module.notification.adapter.out.email.BrevoEmailSender;
import org.springframework.beans.factory.annotation.Value;
import java.net.http.HttpClient;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class EmailSenderConfiguration {
    @Bean @ConditionalOnMissingBean(EmailSender.class)
    EmailSender emailSender(JsonMapper json,
        @Value("${app.email.brevo.api-key:}") String key,
        @Value("${app.email.sender-email:}") String email,
        @Value("${app.email.sender-name:Kampive}") String name) {
        if(!key.isBlank()) return new BrevoEmailSender(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build(),json,key,email,name);
        return new EmailSender() {
            public boolean available() { return false; }
            public void send(UUID id,String recipient,EmailTemplates.Rendered message) {
                throw new IllegalStateException("Email provider unavailable");
            }
        };
    }
}
