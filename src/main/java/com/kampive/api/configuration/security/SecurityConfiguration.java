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
            SecurityContextRepository contexts, CsrfTokenRepository csrf, com.kampive.api.module.identity.application.port.in.SessionValidity validity, com.kampive.api.module.identity.application.port.in.EmailVerification verification, com.kampive.api.module.user.application.port.in.Administrators administrators) throws Exception {
        return http
            .addFilterAfter(new PasswordSessionFilter(validity), org.springframework.security.web.context.SecurityContextHolderFilter.class)
            .addFilterAfter(new EmailVerificationFilter(verification), org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll();
                var adminAccess = (org.springframework.security.authorization.AuthorizationManager<org.springframework.security.web.access.intercept.RequestAuthorizationContext>) (authentication, context) -> {
                    var actor = authentication.get(); boolean allowed = false;
                    if (actor != null && actor.isAuthenticated()) {
                        try { allowed = administrators.active(java.util.UUID.fromString(actor.getName())); }
                        catch (IllegalArgumentException invalidIdentity) { /* deny */ }
                    }
                    return new org.springframework.security.authorization.AuthorizationDecision(allowed);
                };
                auth.requestMatchers(HttpMethod.GET,"/api/v1/admin/me","/api/v1/admin/requests","/api/v1/admin/campings","/api/v1/admin/reviews","/api/v1/admin/users","/api/v1/admin/history").access(adminAccess);
                auth.requestMatchers(HttpMethod.POST,"/api/v1/admin/photos","/api/v1/admin/requests/{kind}/{id}/decision","/api/v1/admin/campings","/api/v1/admin/campings/{id}/status","/api/v1/admin/reviews/{id}/status").access(adminAccess);
                auth.requestMatchers(HttpMethod.PUT,"/api/v1/admin/campings/{id}").access(adminAccess);
                if (documentationEnabled) {
                    auth.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs.yaml",
                        "/v3/api-docs/swagger-config", "/swagger-ui.html", "/swagger-ui/**").permitAll();
                }
                auth.requestMatchers(HttpMethod.GET,"/api/v1/auth/csrf","/api/v1/auth/legal-documents").permitAll();
                auth.requestMatchers(HttpMethod.POST,"/api/v1/auth/register","/api/v1/auth/login","/api/v1/auth/password-recovery","/api/v1/auth/password-reset").permitAll();
                auth.requestMatchers(HttpMethod.POST,"/api/v1/auth/email-verification/confirm").permitAll();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/auth/email-verification").authenticated();
                auth.requestMatchers(HttpMethod.POST,"/api/v1/auth/email-verification/resend").authenticated();
                auth.requestMatchers(HttpMethod.POST,"/api/v1/auth/logout").authenticated();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/users/me").authenticated();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/users/me/reviews/{uuid}").authenticated();
                auth.requestMatchers(HttpMethod.PUT,"/api/v1/users/me/reviews/{uuid}").authenticated();
                auth.requestMatchers(HttpMethod.DELETE,"/api/v1/users/me/reviews/{uuid}").authenticated();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/users/me/requests","/api/v1/users/me/requests/{type}/{uuid}").authenticated();
                auth.requestMatchers(HttpMethod.PUT,"/api/v1/users/me").authenticated();
                auth.requestMatchers(HttpMethod.DELETE,"/api/v1/users/me").authenticated();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/continents","/api/v1/countries",
                    "/api/v1/countries/{countryUuid}").permitAll();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/campsites","/api/v1/campsites/{uuid}",
                    "/api/v1/campsite-options","/api/v1/cities","/api/v1/campsite-photos/{filename}").permitAll();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/users/me/campsites","/api/v1/users/me/campsites/{uuid}").authenticated();
                auth.requestMatchers(HttpMethod.POST,"/api/v1/campsite-registrations","/api/v1/campsite-suggestions","/api/v1/campsites/{uuid}/claims","/api/v1/campsites/{uuid}/submit",
                    "/api/v1/campsites/{uuid}/archive").authenticated();
                auth.requestMatchers(HttpMethod.PUT,"/api/v1/campsites/{uuid}").authenticated();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/review-options","/api/v1/campsites/{uuid}/reviews").permitAll();
                auth.requestMatchers(HttpMethod.GET,"/api/v1/users/me/favorites","/api/v1/users/me/notifications","/api/v1/users/me/reviews").authenticated();
                auth.requestMatchers(HttpMethod.PUT,"/api/v1/users/me/favorites/{uuid}","/api/v1/users/me/notifications/{uuid}").authenticated();
                auth.requestMatchers(HttpMethod.DELETE,"/api/v1/users/me/favorites/{uuid}").authenticated();
                auth.requestMatchers(HttpMethod.POST,"/api/v1/users/me/notifications/read-all","/api/v1/campsites/{uuid}/reviews").authenticated();
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
