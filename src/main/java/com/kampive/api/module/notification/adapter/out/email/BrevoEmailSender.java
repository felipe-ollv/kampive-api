package com.kampive.api.module.notification.adapter.out.email;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;
import com.kampive.api.module.notification.application.port.in.EmailSender;
import com.kampive.api.module.notification.application.port.in.EmailTemplates;

/** Sends rendered database templates. Never includes provider responses in exceptions. */
public final class BrevoEmailSender implements EmailSender {
    private static final URI ENDPOINT=URI.create("https://api.brevo.com/v3/smtp/email");
    private final HttpClient client;
    private final JsonMapper json;
    private final String key;
    private final String email;
    private final String name;

    public BrevoEmailSender(HttpClient client,JsonMapper json,String key,String email,String name) {
        this.client=client; this.json=json; this.key=key.trim(); this.email=email.trim(); this.name=name.trim();
    }

    public boolean available() {
        return !key.isBlank() && email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") && !name.isBlank();
    }

    public void send(UUID id,String recipient,EmailTemplates.Rendered message) {
        if(!available()) throw new IllegalStateException("Email provider unavailable");
        try {
            var body=Map.of("sender",Map.of("email",email,"name",name),
                "to",List.of(Map.of("email",recipient)),"subject",message.subject(),
                "htmlContent",message.html(),"textContent",message.text(),
                "headers",Map.of("idempotencyKey",id.toString()));
            var request=HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(10))
                .header("api-key",key).header("Content-Type","application/json").header("Accept","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==201) return;
            // Brevo rejects a previously accepted job UUID during its 30-minute deduplication window.
            if(response.statusCode()==400 && "duplicate_parameter".equals(json.readTree(response.body()).path("code").asText())) return;
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch(Exception ignored) {
            // Network errors and response bodies can include credentials or message content.
        }
        throw new IllegalStateException("Email delivery failed");
    }
}
