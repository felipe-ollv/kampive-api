package com.kampive.api.module.campsite.adapter.in.web;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import com.kampive.api.module.campsite.application.port.in.Campsites;
import com.kampive.api.module.campsite.application.port.in.Campsites.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class CampsiteHttpTest {
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
    @MockitoBean Campsites campsites;
    @MockitoBean com.kampive.api.module.campsite.application.port.in.MyRequests requests;
    final UUID uuid=UUID.randomUUID(),actor=UUID.randomUUID();
    @Test void privateRequestsUseSessionIdentityAndRejectWrites() throws Exception {
        mvc.perform(get("/api/v1/users/me/requests")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/requests/suggestion/"+uuid)).andExpect(status().isUnauthorized());
        when(requests.list(actor,0,20)).thenReturn(new Page<>(List.of(),0,20,0,0));
        mvc.perform(get("/api/v1/users/me/requests?actor="+UUID.randomUUID()).with(user(actor.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verify(requests).list(actor,0,20);
        mvc.perform(get("/api/v1/users/me/requests/claim/"+uuid).with(user(actor.toString())))
            .andExpect(status().isOk());
        verify(requests).get(actor,"claim",uuid);
        mvc.perform(put("/api/v1/users/me/requests/claim/"+uuid).with(user(actor.toString())).with(csrf()))
            .andExpect(status().isForbidden());
    }
    @Test void publicCatalogAndBoundedPagination() throws Exception {
        when(campsites.search(any())).thenReturn(new Page<>(List.of(),0,20,0,0));
        mvc.perform(get("/api/v1/campsites")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        for (String query:List.of("size=51","size=0","page=-1","countryUuid=oops","page=10001"))
            mvc.perform(get("/api/v1/campsites?"+query)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/campsites/not-a-uuid")).andExpect(status().isBadRequest());
    }
    @Test void writesNeedSessionAndCsrfAndUnsupportedMethodsStayBlocked() throws Exception {
        mvc.perform(get("/api/v1/users/me/campsites")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/campsites/"+uuid+"/submit").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/campsites/"+uuid+"/submit").with(user(actor.toString()))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/campsites/"+uuid).with(user(actor.toString())).with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(campsites);
    }
    @Test void sessionIdentityIsPassedToOwnerOperations() throws Exception {
        mvc.perform(post("/api/v1/campsites/"+uuid+"/submit").with(user(actor.toString())).with(csrf())).andExpect(status().isOk());
        verify(campsites).submit(actor,uuid);
    }
    @Test void suggestionsAndClaimsUseSessionAndNeverAllowDirectCreation() throws Exception {
        var receipt=new Submission(uuid,"pending_review");
        when(campsites.suggest(eq(actor),any())).thenReturn(receipt);
        mvc.perform(post("/api/v1/campsite-suggestions").with(user(actor.toString())).with(csrf())
            .contentType("application/json").content("{\"name\":\"Camp\",\"instagramUrl\":\"https://instagram.com/camp/\",\"googleMapsUrl\":\"https://maps.app.goo.gl/example\",\"ownerUuid\":\"ignored\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("pending_review"));
        verify(campsites).suggest(eq(actor),any());
        mvc.perform(post("/api/v1/campsites").with(user(actor.toString())).with(csrf()))
            .andExpect(status().isForbidden());
        for (String path:List.of("/api/v1/campsite-registrations","/api/v1/campsite-suggestions","/api/v1/campsites/"+uuid+"/claims")) {
            mvc.perform(post(path).with(csrf())).andExpect(status().isUnauthorized());
            mvc.perform(post(path).with(user(actor.toString()))).andExpect(status().isForbidden());
        }
        when(campsites.claim(eq(actor),eq(uuid),any())).thenReturn(receipt);
        mvc.perform(post("/api/v1/campsites/"+uuid+"/claims").with(user(actor.toString())).with(csrf())
            .contentType("application/json").content("{\"contact\":\"phone\",\"evidence\":\"evidence\"}"))
            .andExpect(status().isCreated());
        verify(campsites).claim(eq(actor),eq(uuid),any());
    }
    @Test void registrationUsesSessionIdentityAndReturnsOnlyReceipt() throws Exception {
        when(campsites.register(eq(actor),any())).thenReturn(new Submission(uuid,"pending_review"));
        mvc.perform(post("/api/v1/campsite-registrations").with(user(actor.toString())).with(csrf())
            .contentType("application/json").content("{\"name\":\"Camp\",\"categoryUuid\":\""+UUID.randomUUID()+"\",\"ownerUuid\":\"ignored\",\"status\":\"published\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("pending_review"))
            .andExpect(jsonPath("$.ownerUuid").doesNotExist());
        verify(campsites).register(eq(actor),any());
    }
    @Test void malformedContentIsRejectedBeforeUseCase() throws Exception {
        mvc.perform(post("/api/v1/campsite-suggestions").with(user(actor.toString())).with(csrf())
            .contentType("application/json").content("{\"name\":\"camp\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.trace").doesNotExist());
        verifyNoInteractions(campsites);
    }
}
