package com.kampive.api.module.identity.adapter.in.web;

import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Schema;
import com.kampive.api.module.identity.application.port.in.IdentityUseCases;
import com.kampive.api.module.identity.application.port.in.IdentityUseCases.Registration;
import com.kampive.api.module.user.application.port.in.UserAccounts.UserView;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name="Authentication", description="Session authentication. Obtain a CSRF token before POST requests.")
public class IdentityController {
    public record RegisterRequest(
        @NotBlank @Size(min=3,max=150) String name,
        @NotBlank @Email @Size(max=254) String email,
        @NotNull @Size(min=15,max=128) @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String password,
        UUID countryUuid, UUID adventurerStyleUuid,
        @NotNull @Size(min=2,max=2) List<@NotNull UUID> acceptedDocumentUuids) {
        @Override public String toString() { return "RegisterRequest[REDACTED]"; }
    }
    public record LoginRequest(@NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(max=128) @Schema(accessMode=Schema.AccessMode.WRITE_ONLY) String password) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
    public record CsrfResponse(String headerName, String token) {}
    private final IdentityUseCases identity;
    private final AuthRateLimiter limiter;
    private final SecurityContextRepository contexts;
    private final SessionAuthenticationStrategy sessions;
    public IdentityController(IdentityUseCases identity, AuthRateLimiter limiter,
            SecurityContextRepository contexts, SessionAuthenticationStrategy sessions) {
        this.identity=identity; this.limiter=limiter; this.contexts=contexts; this.sessions=sessions;
    }
    @GetMapping("/csrf")
    @Operation(summary="Get the CSRF token", description="Keep the session cookie and send the returned header on POST requests. Refresh after login or logout.")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(),token.getToken()); }

    @GetMapping("/legal-documents")
    @Operation(summary="List the currently published terms and privacy policy")
    public List<IdentityUseCases.LegalDocument> documents() { return identity.currentDocuments(); }

    @Parameter(name="X-CSRF-TOKEN", in=ParameterIn.HEADER, required=true,
        description="Token returned by GET /api/v1/auth/csrf for the current session")
    @PostMapping("/register")
    @Operation(summary="Create an account", description="Requires both current legal document UUIDs. Returns an unverified account; registration does not log in.")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        if (!limiter.allow(request.getRemoteAddr())) return limited();
        UserView user=identity.register(new Registration(body.name(),body.email(),body.password(),
            body.countryUuid(),body.adventurerStyleUuid(),body.acceptedDocumentUuids()));
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }
    @Parameter(name="X-CSRF-TOKEN", in=ParameterIn.HEADER, required=true,
        description="Token returned by GET /api/v1/auth/csrf for the current session")
    @PostMapping("/login")
    @Operation(summary="Authenticate with email and password", description="Rotates the session identifier. Keep the new cookie and obtain a new CSRF token.")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request,HttpServletResponse response) {
        if (!limiter.allow(request.getRemoteAddr())) return limited();
        UserView user=identity.authenticate(body.email(),body.password());
        var authentication=UsernamePasswordAuthenticationToken.authenticated(user.uuid().toString(),null,
            List.of(new SimpleGrantedAuthority("ROLE_USER")));
        sessions.onAuthentication(authentication,request,response);
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context,request,response);
        return ResponseEntity.ok(user);
    }
    @Parameter(name="X-CSRF-TOKEN", in=ParameterIn.HEADER, required=true,
        description="Token returned by GET /api/v1/auth/csrf for the current session")
    @PostMapping("/logout")
    @SecurityRequirement(name="sessionCookie")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Invalidate the current session")
    public void logout(HttpServletRequest request,HttpServletResponse response) {
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler()
            .logout(request,response,SecurityContextHolder.getContext().getAuthentication());
        response.addHeader("Set-Cookie",org.springframework.http.ResponseCookie.from("JSESSIONID","")
            .path("/").httpOnly(true).secure(request.isSecure()).sameSite("Lax").maxAge(0).build().toString());
    }
    private ResponseEntity<ProblemDetail> limited() {
        return ResponseEntity.status(429).header("Retry-After","60")
            .body(ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,"Too many authentication attempts. Try again later."));
    }
}
