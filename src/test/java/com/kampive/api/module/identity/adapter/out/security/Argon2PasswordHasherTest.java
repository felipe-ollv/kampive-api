package com.kampive.api.module.identity.adapter.out.security;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class Argon2PasswordHasherTest {
    @Test void saltsPasswordsAndUsesArgon2id() {
        var hasher=new Argon2PasswordHasher();
        String first=hasher.hash("a long password phrase");
        assertThat(first).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(hasher.hash("a long password phrase")).isNotEqualTo(first);
        assertThat(hasher.matches("a long password phrase",first)).isTrue();
        assertThat(hasher.matches("wrong",first)).isFalse();
    }
}
