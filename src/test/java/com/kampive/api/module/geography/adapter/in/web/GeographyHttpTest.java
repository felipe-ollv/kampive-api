package com.kampive.api.module.geography.adapter.in.web;

import java.util.List;
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
import com.kampive.api.module.geography.application.port.in.GeographyCatalog;
import com.kampive.api.module.geography.application.port.in.GeographyCatalog.*;
import com.kampive.api.module.geography.domain.GeographyFailure;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GeographyHttpTest {
    @Autowired MockMvc mvc;
    @MockitoBean JdbcClient jdbc;
    @MockitoBean PlatformTransactionManager transactions;
    @MockitoBean GeographyCatalog catalog;
    UUID continent = UUID.randomUUID(), country = UUID.randomUUID();
    @Test void anonymousClientsCanReadCatalogWithOnlyPublicFields() throws Exception {
        when(catalog.continents()).thenReturn(List.of(new Continent(continent,"South America")));
        mvc.perform(get("/api/v1/continents")).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].uuid").value(continent.toString()))
            .andExpect(jsonPath("$[0].id").doesNotExist());
        var view = new Country(country,"Brazil","BR",null,continent,"South America",2);
        var query = new CountryQuery(continent,"br",0,20);
        when(catalog.countries(query)).thenReturn(new CountryPage(List.of(view),0,20,1,1));
        mvc.perform(get("/api/v1/countries").param("continentUuid",continent.toString()).param("query","br"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].campsiteCount").value(2))
            .andExpect(jsonPath("$.items[0].id").doesNotExist());
        when(catalog.country(country)).thenReturn(view);
        mvc.perform(get("/api/v1/countries/"+country)).andExpect(status().isOk())
            .andExpect(jsonPath("$.isoCode").value("BR"));
    }
    @Test void malformedIdentifiersAndPaginationReturn400() throws Exception {
        for (String query : new String[]{"page=-1","page=10001","size=0","size=101","page=abc","continentUuid=abc"}) {
            mvc.perform(get("/api/v1/countries?"+query)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.trace").doesNotExist());
        }
        mvc.perform(get("/api/v1/countries").param("query","x".repeat(121))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/countries/not-a-uuid")).andExpect(status().isBadRequest());
        verifyNoInteractions(catalog);
    }
    @Test void missingCountryReturns404AndWritesStayBlocked() throws Exception {
        when(catalog.country(country)).thenThrow(new GeographyFailure(GeographyFailure.Reason.COUNTRY_NOT_FOUND));
        mvc.perform(get("/api/v1/countries/"+country)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Country not found."));
        mvc.perform(post("/api/v1/countries").with(user("test")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/countries/"+country).with(user("test")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
}
