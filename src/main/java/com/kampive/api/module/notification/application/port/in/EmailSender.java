package com.kampive.api.module.notification.application.port.in;

import java.util.UUID;

/** Provider adapter must honor the idempotency key; never log recipients, bodies or tokens. */
public interface EmailSender {
    boolean available();
    void send(UUID idempotencyKey, String recipient, EmailTemplates.Rendered message);
}
