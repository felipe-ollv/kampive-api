package com.kampive.api.module.identity.adapter.in.web;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import com.kampive.api.module.identity.application.port.in.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class EmailVerificationHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcClient jdbc;
    @MockitoBean PlatformTransactionManager manager;
    @MockitoBean EmailVerification verification;
    @MockitoBean SessionValidity validity;
    private final String actor=UUID.randomUUID().toString();
    @BeforeEach void validSession(){when(validity.valid(any(),any())).thenReturn(true);}
    @Test void confirmationIsPublicButRequiresCsrfAndExplicitPost() throws Exception {
        String body="{\"token\":\""+"a".repeat(43)+"\"}";
        mvc.perform(post("/api/v1/auth/email-verification/confirm").contentType("application/json").content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(verification);
        mvc.perform(post("/api/v1/auth/email-verification/confirm").with(csrf()).contentType("application/json").content(body)).andExpect(status().isNoContent());
        verify(verification).confirm("a".repeat(43));
    }
    @Test void resendIsBoundToAuthenticatedAccount() throws Exception {
        mvc.perform(post("/api/v1/auth/email-verification/resend").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/email-verification/resend").with(user(actor)).with(csrf())).andExpect(status().isAccepted());
        verify(verification).resend(UUID.fromString(actor));
    }
    @Test void contributionGateCoversEveryProtectedAction() throws Exception {
        when(verification.status(any())).thenReturn(new EmailVerification.Status(false,false,null,true));
        for(String path:java.util.List.of("/campsite-suggestions","/campsite-registrations","/campsites/"+actor+"/claims","/campsites/"+actor+"/submit","/campsites/"+actor+"/reviews")) {
            mvc.perform(post("/api/v1"+path).with(user(actor)).with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
        }
    }
}
