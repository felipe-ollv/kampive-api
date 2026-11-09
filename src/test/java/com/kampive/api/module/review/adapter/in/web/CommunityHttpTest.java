package com.kampive.api.module.review.adapter.in.web;

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
import com.kampive.api.module.favorite.application.port.in.Favorites;
import com.kampive.api.module.notification.application.port.in.Notifications;
import com.kampive.api.module.review.application.port.in.Reviews;
import com.kampive.api.module.campsite.application.port.in.Campsites.Page;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class CommunityHttpTest {
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
    @MockitoBean Favorites favorites;
    @MockitoBean Notifications notifications;
    @MockitoBean Reviews reviews;
    final UUID actor=UUID.randomUUID(),id=UUID.randomUUID();
    @Test void privateReadsRequireSession() throws Exception {
        for(String path:List.of("favorites","notifications","reviews"))
            mvc.perform(get("/api/v1/users/me/"+path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(favorites,notifications,reviews);
    }
    @Test void mutationsRequireCsrf() throws Exception {
        mvc.perform(put("/api/v1/users/me/favorites/"+id).with(user(actor.toString()))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/users/me/favorites/"+id).with(user(actor.toString()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/users/me/notifications/read-all").with(user(actor.toString()))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/campsites/"+id+"/reviews").with(user(actor.toString()))).andExpect(status().isForbidden());
        verifyNoInteractions(favorites,notifications,reviews);
    }
    @Test void sessionActorIsUsedForFavoritesAndInbox() throws Exception {
        when(favorites.list(actor)).thenReturn(List.of(id));
        mvc.perform(get("/api/v1/users/me/favorites").with(user(actor.toString())))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0]").value(id.toString()));
        mvc.perform(put("/api/v1/users/me/favorites/"+id).with(user(actor.toString())).with(csrf()))
            .andExpect(status().isNoContent());
        verify(favorites).save(actor,id);
        mvc.perform(delete("/api/v1/users/me/favorites/"+id).with(user(actor.toString())).with(csrf()))
            .andExpect(status().isNoContent());
        verify(favorites).remove(actor,id);
        mvc.perform(put("/api/v1/users/me/notifications/"+id).with(user(actor.toString())).with(csrf())
            .contentType("application/json").content("{\"read\":true}"))
            .andExpect(status().isNoContent());
        verify(notifications).read(actor,id,true);
        mvc.perform(put("/api/v1/users/me/notifications/"+id).with(user(actor.toString())).with(csrf())
            .contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users/me/notifications/read-all").with(user(actor.toString())).with(csrf()))
            .andExpect(status().isNoContent());
        verify(notifications).readAll(actor);
    }
    @Test void reviewsArePublicButCreationUsesSessionAndPendingReceipt() throws Exception {
        when(reviews.list(id,0,20)).thenReturn(new Page<>(List.of(),0,20,0,0));
        mvc.perform(get("/api/v1/campsites/"+id+"/reviews")).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        when(reviews.create(eq(actor),eq(id),any())).thenReturn(new Reviews.Receipt(UUID.randomUUID(),"pending"));
        mvc.perform(post("/api/v1/campsites/"+id+"/reviews").with(user(actor.toString())).with(csrf())
            .contentType("application/json").content("{\"rating\":5,\"text\":\""+"A".repeat(50)+"\",\"status\":\"published\",\"actor\":\"ignored\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("pending"));
        verify(reviews).create(eq(actor),eq(id),argThat(input -> input.rating()==5 && input.photos().isEmpty()));
    }
    @Test void malformedReviewAndLocalPhotoNeverReachPersistence() throws Exception {
        for(String body:List.of("{\"rating\":0,\"text\":\"short\"}",
            "{\"rating\":5,\"text\":\""+"A".repeat(50)+"\",\"photos\":[\"file:///private/photo.jpg\"]}"))
            mvc.perform(post("/api/v1/campsites/"+id+"/reviews").with(user(actor.toString())).with(csrf())
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(reviews);
    }
    @Test void ownReviewManagementRequiresCsrfAndUsesSessionActor() throws Exception {
        String path="/api/v1/users/me/reviews/"+id;
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(user(actor.toString()))).andExpect(status().isForbidden());
        mvc.perform(delete(path).with(user(actor.toString()))).andExpect(status().isForbidden());
        when(reviews.update(eq(actor),eq(id),any())).thenReturn(new Reviews.Receipt(id,"pending"));
        mvc.perform(put(path).with(user(actor.toString())).with(csrf()).contentType("application/json")
            .content("{\"rating\":4,\"text\":\""+"A".repeat(50)+"\",\"status\":\"published\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("pending"));
        verify(reviews).update(eq(actor),eq(id),argThat(input->input.rating()==4));
        mvc.perform(delete(path).with(user(actor.toString())).with(csrf())).andExpect(status().isNoContent());
        verify(reviews).delete(actor,id);
    }
    @Test void unverifiedAccountCanRemoveButCannotEditReview() throws Exception {
        when(verification.status(any())).thenReturn(new com.kampive.api.module.identity.application.port.in.EmailVerification.Status(false,false,null,true));
        String path="/api/v1/users/me/reviews/"+id;
        mvc.perform(put(path).with(user(actor.toString())).with(csrf()).contentType("application/json")
            .content("{\"rating\":4,\"text\":\""+"A".repeat(50)+"\"}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("EMAIL_VERIFICATION_REQUIRED"));
        verify(reviews,never()).update(any(),any(),any());
        mvc.perform(delete(path).with(user(actor.toString())).with(csrf())).andExpect(status().isNoContent());
        verify(reviews).delete(actor,id);
    }

}
