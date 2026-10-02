package com.kampive.api.configuration.openapi;

import io.swagger.v3.oas.models.OpenAPI;
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
                .description("Kampive backend API. Business endpoints are not implemented yet."))
            .addServersItem(new Server().url("/").description("Current server"));
    }
}
