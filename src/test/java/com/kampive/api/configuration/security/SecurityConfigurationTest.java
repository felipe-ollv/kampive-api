package com.kampive.api.configuration.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigurationTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;

    @Test
    void healthIsTheOnlyPublicEndpointAndRevealsNoDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(content().json("{\"status\":\"UP\"}"))
            .andExpect(jsonPath("$.components").doesNotExist())
            .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    void anonymousRequestsHaveSanitizedJsonErrors() throws Exception {
        for (String path : new String[]{"/api/campsites", "/actuator/env", "/login"}) {
            mvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
        }
    }

    @Test
    void evenAuthenticatedUsersCannotAccessUnimplementedRoutes() throws Exception {
        mvc.perform(get("/api/campsites").with(user("test")))
            .andExpect(status().isForbidden());
    }

    @Test
    void csrfRemainsEnabledIncludingOnHealth() throws Exception {
        mvc.perform(post("/actuator/health").with(user("test")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403));
        mvc.perform(post("/api/campsites").with(user("test")).with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    void noDefaultUserOrWildcardCors() throws Exception {
        assertThat(context.getBeansOfType(UserDetailsService.class)).isEmpty();
        mvc.perform(get("/actuator/health").header("Origin", "https://untrusted.example"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
