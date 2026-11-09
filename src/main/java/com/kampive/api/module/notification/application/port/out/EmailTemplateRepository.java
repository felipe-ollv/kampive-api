package com.kampive.api.module.notification.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface EmailTemplateRepository {
    record Template(UUID uuid, int version, String subject, String html, String text) {}
    Optional<Template> active(String code, String locale);
}
