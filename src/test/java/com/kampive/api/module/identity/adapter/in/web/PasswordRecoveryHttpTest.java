package com.kampive.api.module.identity.adapter.in.web;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import com.kampive.api.module.identity.application.port.in.PasswordRecovery;
import com.kampive.api.module.identity.application.port.in.SessionValidity;
import com.kampive.api.module.identity.domain.RecoveryFailure;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class PasswordRecoveryHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean com.kampive.api.module.identity.application.port.in.EmailVerification verification;
    @org.junit.jupiter.api.BeforeEach void verifiedContributions() {
        org.mockito.Mockito.when(verification.status(org.mockito.ArgumentMatchers.any())).thenReturn(new com.kampive.api.module.identity.application.port.in.EmailVerification.Status(true,true,null,true));
    }
    @MockitoBean JdbcClient jdbc;
    @MockitoBean PlatformTransactionManager transactions;
    @MockitoBean PasswordRecovery recovery;
    @MockitoBean SessionValidity validity;
    @Test void anonymousRecoveryRequiresCsrfAndReturnsNoAccountOrToken() throws Exception {
        mvc.perform(post("/api/v1/auth/password-recovery").contentType("application/json").content("{\"email\":\"a@example.test\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/password-recovery").with(csrf()).contentType("application/json").content("{\"email\":\"a@example.test\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("accepted")).andExpect(jsonPath("$.token").doesNotExist());
        verify(recovery).request("a@example.test");
    }
    @Test void unavailableProviderIsExplicitAndGeneric() throws Exception {
        doThrow(new RecoveryFailure(RecoveryFailure.Reason.UNAVAILABLE)).when(recovery).request(any());
        mvc.perform(post("/api/v1/auth/password-recovery").with(csrf()).contentType("application/json").content("{\"email\":\"a@example.test\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("UNAVAILABLE"));
    }
    @Test void resetRequiresCsrfAndNeverReturnsTheSecret() throws Exception {
        String json="{\"token\":\""+"a".repeat(43)+"\",\"password\":\"New password 7\"}";
        mvc.perform(post("/api/v1/auth/password-reset").contentType("application/json").content(json)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/password-reset").with(csrf()).contentType("application/json").content(json))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(recovery).reset("a".repeat(43),"New password 7");
    }
    @Test void invalidAndExpiredLinksHaveTheSameError() throws Exception {
        doThrow(new RecoveryFailure(RecoveryFailure.Reason.INVALID_TOKEN)).when(recovery).reset(any(),any());
        mvc.perform(post("/api/v1/auth/password-reset").with(csrf()).contentType("application/json")
            .content("{\"token\":\"expired\",\"password\":\"New password 7\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
    @Test void revokedSessionCannotAccessPrivateRoutes() throws Exception {
        when(validity.valid(any(),any())).thenReturn(false);
        mvc.perform(get("/api/v1/users/me").with(user(UUID.randomUUID().toString())))
            .andExpect(status().isUnauthorized());
    }
}
