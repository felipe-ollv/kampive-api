package com.kampive.api.module.identity.application.port.in;
import java.time.Instant;
import java.util.UUID;
public interface EmailVerification {
    record Status(boolean verified, boolean contributionsAllowed, Instant requiredAt, boolean deliveryAvailable) {}
    Status status(UUID actor);
    void registered(UUID actor);
    void resend(UUID actor);
    void confirm(String token);
}
