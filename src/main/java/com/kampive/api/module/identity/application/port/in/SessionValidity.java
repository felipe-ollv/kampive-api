package com.kampive.api.module.identity.application.port.in;
import java.time.Instant;
import java.util.UUID;
public interface SessionValidity {
    Instant started();
    boolean valid(UUID actor, Instant started);
}
