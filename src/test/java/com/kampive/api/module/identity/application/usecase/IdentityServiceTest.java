package com.kampive.api.module.identity.application.usecase;

import java.util.*;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.kampive.api.module.identity.application.port.in.IdentityUseCases.*;
import com.kampive.api.module.identity.application.port.out.*;
import com.kampive.api.module.identity.domain.IdentityFailure;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class IdentityServiceTest {
    UserAccounts users=mock(UserAccounts.class);
    CredentialStore credentials=mock(CredentialStore.class);
    PasswordHasher passwords=mock(PasswordHasher.class);
    IdentityService service=new IdentityService(users,credentials,passwords);
    UUID terms=UUID.randomUUID(), privacy=UUID.randomUUID(), uuid=UUID.randomUUID();
    UserAccounts.UserView user=new UserAccounts.UserView(uuid,"Explorer","user@example.test",null,null,null,null,false,false);
    @BeforeEach void documents() {
        when(credentials.currentDocuments()).thenReturn(List.of(
            new LegalDocument(terms,"terms_of_use","1","https://example.test/terms",Instant.EPOCH),
            new LegalDocument(privacy,"privacy_policy","1","https://example.test/privacy",Instant.EPOCH)));
    }
    Registration registration(String password,List<UUID> documents) {
        return new Registration(" Explorer ","USER@example.test",password,null,null,documents);
    }
    @Test void normalizesEmailAndStoresOnlyHash() {
        when(passwords.hash("a long password phrase")).thenReturn("encoded");
        when(users.create("Explorer","user@example.test",null,null)).thenReturn(user);
        assertThat(service.register(registration("a long password phrase",List.of(terms,privacy)))).isEqualTo(user);
        verify(credentials).save(uuid,"encoded",List.of(terms,privacy));
    }
    @Test void rejectsWeakPasswordsAndStaleOrMissingConsentsBeforeWriting() {
        for (Registration c : List.of(registration("short",List.of(terms,privacy)),
                registration("a long password phrase",List.of(terms,terms)),
                registration("a long password phrase",List.of(terms,UUID.randomUUID())))) {
            assertThatThrownBy(() -> service.register(c)).isInstanceOf(IdentityFailure.class);
        }
        when(credentials.currentDocuments()).thenReturn(List.of());
        assertThatThrownBy(() -> service.register(registration("a long password phrase",List.of(terms,privacy))))
            .isInstanceOf(IdentityFailure.class);
        verifyNoInteractions(users,passwords);
    }
    @Test void unknownUsersStillPerformPasswordVerification() {
        when(users.findActiveByEmail("user@example.test")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate("USER@example.test","a long password phrase"))
            .isInstanceOf(IdentityFailure.class).hasMessage("INVALID_CREDENTIALS");
        verify(passwords).verifyDummy("a long password phrase");
    }
    @Test void wrongPasswordsAreDeniedAndValidPasswordsReturnPublicProfile() {
        when(users.findActiveByEmail("user@example.test")).thenReturn(Optional.of(user));
        when(credentials.passwordHash(uuid)).thenReturn(Optional.of("encoded"));
        assertThatThrownBy(() -> service.authenticate("user@example.test","wrong"))
            .isInstanceOf(IdentityFailure.class).hasMessage("INVALID_CREDENTIALS");
        when(passwords.matches("a long password phrase","encoded")).thenReturn(true);
        assertThat(service.authenticate("user@example.test","a long password phrase")).isEqualTo(user);
    }
}
