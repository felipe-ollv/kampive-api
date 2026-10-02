package com.kampive.api.configuration.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import com.kampive.api.shared.adapter.in.web.error.SecurityProblemWriter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${app.documentation.enabled:false}") boolean documentationEnabled) throws Exception {
        return http
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                if (documentationEnabled) {
                    auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml",
                        "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/**").permitAll();
                }
                auth.anyRequest().denyAll();
            })
            .csrf(Customizer.withDefaults())
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    SecurityProblemWriter.write(response, 401, "Unauthorized"))
                .accessDeniedHandler((request, response, exception) ->
                    SecurityProblemWriter.write(response, 403, "Forbidden")))
            .build();
    }
}
