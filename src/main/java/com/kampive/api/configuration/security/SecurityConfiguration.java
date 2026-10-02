package com.kampive.api.configuration.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import java.util.List;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import com.kampive.api.shared.adapter.in.web.error.SecurityProblemWriter;

@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {
    @Bean SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new HttpSessionCsrfTokenRepository(); }
    @Bean SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(List.of(
            new ChangeSessionIdAuthenticationStrategy(),new CsrfAuthenticationStrategy(csrf)));
    }
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Value("${app.documentation.enabled:false}") boolean documentationEnabled,
            SecurityContextRepository contexts, CsrfTokenRepository csrf) throws Exception {
        return http
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                if (documentationEnabled) {
                    auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml",
                        "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/**").permitAll();
                }
                auth.requestMatchers(HttpMethod.GET,"/api/v1/auth/csrf","/api/v1/auth/legal-documents").permitAll();
                auth.requestMatchers(HttpMethod.POST,"/api/v1/auth/register","/api/v1/auth/login").permitAll();
                auth.requestMatchers(HttpMethod.POST,"/api/v1/auth/logout").authenticated();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/users/me").authenticated();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/continents","/api/v1/countries",
                    "/api/v1/countries/{countryUuid}").permitAll();
                auth.anyRequest().denyAll();
            })
            .csrf(config -> config.csrfTokenRepository(csrf))
            .securityContext(config -> config.securityContextRepository(contexts).requireExplicitSave(true))
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
