package com.kampive.api.configuration.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "app.documentation.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiConfigurationTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.jdbc.core.simple.JdbcClient jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;

    @Test
    void servesOpenApiAndSwaggerResources() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openapi").exists())
            .andExpect(jsonPath("$.info.title").value("Kampive API"))
            .andExpect(jsonPath("$.servers[0].url").value("/"))
            .andExpect(jsonPath("$.paths['/actuator/health']").doesNotExist());
        mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.url").value("/v3/api-docs"));
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        for (String file : new String[]{"index.html", "swagger-ui.css", "swagger-ui-bundle.js", "swagger-initializer.js"}) {
            mvc.perform(get("/swagger-ui/" + file)).andExpect(status().isOk());
        }
    }

    @Test
    void documentationDoesNotOpenBusinessRoutesOrDisableCsrf() throws Exception {
        mvc.perform(get("/api/campsites")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v3/api-docs")).andExpect(status().isForbidden());
    }
}
