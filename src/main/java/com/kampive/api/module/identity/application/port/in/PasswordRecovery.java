package com.kampive.api.module.identity.application.port.in;

public interface PasswordRecovery {
    void request(String email);
    void reset(String token, String password);
}
