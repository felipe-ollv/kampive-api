package com.kampive.api.module.identity.adapter.out.security;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;
import com.kampive.api.module.identity.application.port.out.PasswordHasher;

@Component
public class Argon2PasswordHasher implements PasswordHasher {
    private final Argon2PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
    private final String dummy=encoder.encode(java.util.UUID.randomUUID().toString());
    public String hash(String password) { return encoder.encode(password); }
    public boolean matches(String password,String hash) { return encoder.matches(password,hash); }
    public void verifyDummy(String password) { encoder.matches(password,dummy); }
}
