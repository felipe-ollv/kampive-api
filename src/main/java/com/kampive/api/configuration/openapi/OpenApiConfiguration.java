package com.kampive.api.configuration.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI kampiveOpenApi() {
        return new OpenAPI()
            .info(new Info().title("Kampive API").version("0.0.1")
                .description("Kampive API: session authentication and user profiles."))
            .components(new Components().addSecuritySchemes("sessionCookie",new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID")))
            .addServersItem(new Server().url("/").description("Current server"));
    }
}
