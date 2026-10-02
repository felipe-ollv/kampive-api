package com.kampive.api.module.identity.adapter.in.web;

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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcClient jdbc;
    @MockitoBean PlatformTransactionManager transactions;
    @MockitoBean IdentityUseCases identity;
    @MockitoBean(name="userAccounts") UserAccounts users;
    @MockitoBean AuthRateLimiter limiter;
    final UUID uuid=UUID.randomUUID();
    final UserAccounts.UserView profile=new UserAccounts.UserView(uuid,"Explorer","user@example.test",null,null,null,null,false,false);
    final String login="{\"email\":\"user@example.test\",\"password\":\"a long password phrase\"}";

    @Test void loginRotatesSessionPersistsIdentityAndLogoutInvalidatesIt() throws Exception {
        when(limiter.allow(anyString())).thenReturn(true);
        when(identity.authenticate("user@example.test","a long password phrase")).thenReturn(profile);
        when(users.findActiveByUuid(uuid)).thenReturn(Optional.of(profile));
        var session=(MockHttpSession)mvc.perform(get("/api/v1/auth/csrf"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn().getRequest().getSession(false);
        String original=session.getId();
        mvc.perform(post("/api/v1/auth/login").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isOk()).andExpect(jsonPath("$.uuid").value(uuid.toString()))
            .andExpect(jsonPath("$.password").doesNotExist());
        assertThat(session.getId()).isNotEqualTo(original);
        mvc.perform(get("/api/v1/users/me").session(session)).andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("user@example.test"));
        mvc.perform(post("/api/v1/auth/logout").session(session).with(csrf()))
            .andExpect(status().isNoContent());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
    @Test void rejectsMissingCsrfAndMalformedBodiesBeforeAuthentication() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(identity);
    }
    @Test void failedLoginIsGenericAndThrottlingStopsAuthentication() throws Exception {
        when(limiter.allow(anyString())).thenReturn(true,false);
        when(identity.authenticate(anyString(),anyString())).thenThrow(
            new IdentityFailure(IdentityFailure.Reason.INVALID_CREDENTIALS));
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.detail").value("Invalid email or password."));
        mvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
        verify(identity,times(1)).authenticate(anyString(),anyString());
    }
    @Test void userCanOnlyRequestOwnProfile() throws Exception {
        when(users.findActiveByUuid(uuid)).thenReturn(Optional.of(profile));
        mvc.perform(get("/api/v1/users/me").with(user(uuid.toString())))
            .andExpect(status().isOk());
        verify(users).findActiveByUuid(uuid);
        mvc.perform(get("/api/v1/users/"+UUID.randomUUID()).with(user(uuid.toString())))
            .andExpect(status().isForbidden());
    }
    @Test void registrationReturns201WithoutAuthenticating() throws Exception {
        when(limiter.allow(anyString())).thenReturn(true);
        when(identity.register(any())).thenReturn(profile);
        String body="""
            {"name":"Explorer","email":"user@example.test","password":"a long password phrase",
             "acceptedDocumentUuids":["%s","%s"]}
            """.formatted(UUID.randomUUID(),UUID.randomUUID());
        var session=new MockHttpSession();
        mvc.perform(post("/api/v1/auth/register").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.uuid").value(uuid.toString()));
        mvc.perform(get("/api/v1/users/me").session(session)).andExpect(status().isUnauthorized());
    }
}
