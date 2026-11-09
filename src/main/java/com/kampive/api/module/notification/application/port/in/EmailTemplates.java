package com.kampive.api.module.notification.application.port.in;

import java.util.Map;
import java.util.UUID;

/** Internal backend contract. Does not send email or expose recovery tokens over HTTP. */
public interface EmailTemplates {
    record Rendered(UUID templateUuid, int version, String subject, String html, String text) {}
    Rendered render(String code, Map<String,String> variables);
}
