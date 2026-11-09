package com.kampive.api.module.user.adapter.in.web;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.kampive.api.module.user.application.port.in.Administrators;
import com.kampive.api.module.identity.application.port.in.SessionValidity;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class AdministratorHttpTest {
    @MockitoBean org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @MockitoBean org.springframework.transaction.PlatformTransactionManager transactions;
    @MockitoBean SessionValidity validity;
    @MockitoBean Administrators administrators;
    @Autowired MockMvc mvc;
    final UUID actor=UUID.randomUUID();
    @BeforeEach void sessions() { when(validity.valid(any(),any())).thenReturn(true); }
    @Test void anonymousAndOrdinaryUsersAreDenied() throws Exception {
        mvc.perform(get("/api/v1/admin/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/me").with(user(actor.toString())))
            .andExpect(status().isForbidden());
    }
    @Test void databaseGrantIsRequiredEvenWithAnAdminAuthority() throws Exception {
        mvc.perform(get("/api/v1/admin/me").with(user(actor.toString()).roles("ADMIN")))
            .andExpect(status().isForbidden());
    }
    @Test void grantAndRevocationTakeEffectOnEveryRequest() throws Exception {
        when(administrators.active(actor)).thenReturn(true);
        mvc.perform(get("/api/v1/admin/me").with(user(actor.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"))
            .andExpect(header().string("Cache-Control","no-store"));
        when(administrators.active(actor)).thenReturn(false);
        mvc.perform(get("/api/v1/admin/me").with(user(actor.toString())))
            .andExpect(status().isForbidden());
    }
    @Test void unimplementedAdminRoutesStayClosed() throws Exception {
        when(administrators.active(actor)).thenReturn(true);
        mvc.perform(post("/api/v1/admin/grants").with(user(actor.toString())).with(csrf()))
            .andExpect(status().isForbidden());
    }
}
