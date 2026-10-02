package com.kampive.api.module.identity.application.port.out;

public interface PasswordHasher {
    String hash(String password);
    boolean matches(String password, String hash);
    void verifyDummy(String password);
}
