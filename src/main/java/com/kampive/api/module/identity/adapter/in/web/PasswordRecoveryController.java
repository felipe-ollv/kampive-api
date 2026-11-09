package com.kampive.api.module.identity.adapter.in.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.kampive.api.module.identity.application.port.in.PasswordRecovery;
import com.kampive.api.module.identity.domain.RecoveryFailure;

@RestController
@RequestMapping("/api/v1/auth")
public class PasswordRecoveryController {
    public record Request(@NotBlank @Email @Size(max=254) String email) {
        @Override public String toString() { return "Request[REDACTED]"; }
    }
    public record Reset(@NotBlank @Size(max=128) String token,@NotNull @Size(min=8,max=128) String password) {
        @Override public String toString() { return "Reset[REDACTED]"; }
    }
    private final PasswordRecovery recovery;
    private final AuthRateLimiter limiter;
    public PasswordRecoveryController(PasswordRecovery recovery,AuthRateLimiter limiter) { this.recovery=recovery; this.limiter=limiter; }
    @PostMapping("/password-recovery")
    public ResponseEntity<?> request(@Valid @RequestBody Request body,HttpServletRequest request) {
        if(!limiter.allow("recovery:"+request.getRemoteAddr())) return ResponseEntity.status(429).header("Retry-After","60").build();
        recovery.request(body.email());
        return ResponseEntity.accepted().header("Cache-Control","no-store").body(java.util.Map.of("status","accepted"));
    }
    @PostMapping("/password-reset")
    public ResponseEntity<Void> reset(@Valid @RequestBody Reset body,HttpServletRequest request,HttpServletResponse response) {
        if(!limiter.allow("reset:"+request.getRemoteAddr())) return ResponseEntity.status(429).header("Retry-After","60").build();
        recovery.reset(body.token(),body.password());
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request,response,null);
        return ResponseEntity.noContent().header("Cache-Control","no-store").build();
    }
    @ExceptionHandler(RecoveryFailure.class)
    ResponseEntity<ProblemDetail> failure(RecoveryFailure failure) {
        HttpStatus status=switch(failure.reason()) { case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE; default -> HttpStatus.BAD_REQUEST; };
        var problem=ProblemDetail.forStatus(status);
        problem.setProperty("code",failure.reason().name());
        problem.setDetail("Password recovery could not be completed.");
        return ResponseEntity.status(status).header("Cache-Control","no-store").body(problem);
    }
}
