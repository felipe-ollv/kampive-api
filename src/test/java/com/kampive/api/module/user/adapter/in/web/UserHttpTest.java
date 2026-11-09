package com.kampive.api.module.user.adapter.in.web;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import com.kampive.api.module.identity.application.port.in.IdentityUseCases;
import com.kampive.api.module.identity.domain.IdentityFailure;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class UserHttpTest {
    @MockitoBean com.kampive.api.module.identity.application.port.in.EmailVerification verification;
    @org.junit.jupiter.api.BeforeEach void verifiedContributions() {
        org.mockito.Mockito.when(verification.status(org.mockito.ArgumentMatchers.any())).thenReturn(new com.kampive.api.module.identity.application.port.in.EmailVerification.Status(true,true,null,true));
    }
    @MockitoBean com.kampive.api.module.identity.application.port.in.SessionValidity sessionValidity;
    @org.junit.jupiter.api.BeforeEach void validTestSessions() {
        org.mockito.Mockito.when(sessionValidity.valid(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.when(sessionValidity.started()).thenReturn(java.time.Instant.now());
    }
    @Autowired MockMvc mvc;
    @MockitoBean JdbcClient jdbc;
    @MockitoBean PlatformTransactionManager transactions;
    @MockitoBean IdentityUseCases identity;
    @MockitoBean(name="userAccounts") UserAccounts users;
    final UUID actor=UUID.randomUUID();
    final UserAccounts.UserView profile=new UserAccounts.UserView(actor,"Explorer","user@example.test",null,null,null,null,false,false);
    final String input="{\"name\":\"Explorer\",\"bio\":\"Hello\",\"travelStyles\":[\"tent\",\"trailer\"]}";
    final String deletion="{\"password\":\"current password\",\"confirmation\":\"EXCLUIR\"}";
    @Test void profileUpdateRequiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(put("/api/v1/users/me").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me").with(user(actor.toString())).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isForbidden());
        verifyNoInteractions(users);
    }
    @Test void editsOnlySessionOwnerAndPreservesMultipleStyles() throws Exception {
        when(users.findActiveByUuid(actor)).thenReturn(Optional.of(profile));
        when(users.update(eq(actor),any())).thenReturn(profile);
        mvc.perform(put("/api/v1/users/me").with(user(actor.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input))
            .andExpect(status().isOk());
        verify(users).update(eq(actor),argThat(p -> p.travelStyles().equals(List.of("tent","trailer"))));
    }
    @Test void deletionRequiresCsrfPasswordAndConfirmation() throws Exception {
        mvc.perform(delete("/api/v1/users/me").with(user(actor.toString())).contentType(MediaType.APPLICATION_JSON).content(deletion)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/me").with(user(actor.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"password\",\"confirmation\":\"no\"}"))
            .andExpect(status().isBadRequest());
        when(users.findActiveByUuid(actor)).thenReturn(Optional.of(profile));
        when(identity.authenticate(anyString(),anyString())).thenThrow(new IdentityFailure(IdentityFailure.Reason.INVALID_CREDENTIALS));
        mvc.perform(delete("/api/v1/users/me").with(user(actor.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(deletion)).andExpect(status().isUnauthorized());
        verify(users,never()).delete(any());
    }
    @Test void successfulDeletionInvalidatesSession() throws Exception {
        when(users.findActiveByUuid(actor)).thenReturn(Optional.of(profile));
        when(identity.authenticate(profile.email(),"current password")).thenReturn(profile);
        var session=new MockHttpSession();
        mvc.perform(delete("/api/v1/users/me").session(session).with(user(actor.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(deletion))
            .andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie",org.hamcrest.Matchers.containsString("Max-Age=0")));
        verify(users).delete(actor);
        assertThat(session.isInvalid()).isTrue();
    }
    @Test void deletedAccountCannotUpdateUsingAnOldSession() throws Exception {
        when(users.findActiveByUuid(actor)).thenReturn(Optional.empty());
        mvc.perform(put("/api/v1/users/me").with(user(actor.toString())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isUnauthorized());
        verify(users,never()).update(any(),any());
    }
}
