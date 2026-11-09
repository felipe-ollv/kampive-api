package com.kampive.api.module.user.adapter.in.web;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import com.kampive.api.module.identity.application.port.in.IdentityUseCases;
import com.kampive.api.module.user.domain.UserFailure;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name="Users")
public class UserController {
    private final UserAccounts users;
    private final IdentityUseCases identity;
    private final ConcurrentHashMap<UUID,Attempt> attempts=new ConcurrentHashMap<>();
    private record Attempt(long started,int count) {}
    public record DeleteRequest(@NotBlank @Size(max=128) String password,
                                @Pattern(regexp="EXCLUIR") @NotNull String confirmation) {
        @Override public String toString() { return "DeleteRequest[REDACTED]"; }
    }
    public UserController(UserAccounts users,IdentityUseCases identity) { this.users=users; this.identity=identity; }
    @GetMapping("/me")
    @Operation(summary="Get the authenticated user's profile")
    @SecurityRequirement(name="sessionCookie")
    public ResponseEntity<UserAccounts.UserView> me(Authentication authentication) {
        return users.findActiveByUuid(UUID.fromString(authentication.getName()))
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(401).build());
    }
    @PutMapping("/me")
    @Operation(summary="Update own profile, location and travel styles",description="Photo may be an existing URL, null to remove, or a JPEG/PNG data URI up to 2 MiB. Images are resized and metadata stripped.")
    @SecurityRequirement(name="sessionCookie")
    public UserAccounts.UserView update(Authentication auth,@RequestBody UserAccounts.ProfileInput input) {
        UUID actor=UUID.fromString(auth.getName());
        var previous=users.findActiveByUuid(actor).orElseThrow(() -> new UserFailure(UserFailure.Reason.INACTIVE));
        String photo=ProfilePhoto.normalize(input.photoUrl(),previous.photoUrl());
        return users.update(actor,new UserAccounts.ProfileInput(input.name(),input.countryUuid(),input.cityUuid(),
            input.bio(),input.travelStyles(),photo));
    }
    @DeleteMapping("/me")
    @Operation(summary="Delete own account",description="Requires current password and EXCLUIR confirmation. Removes credentials and personal profile; contributions remain under an anonymized account.")
    @SecurityRequirement(name="sessionCookie")
    public ResponseEntity<Void> delete(Authentication auth,@Valid @RequestBody DeleteRequest body,
            HttpServletRequest request,HttpServletResponse response) {
        UUID actor=UUID.fromString(auth.getName());
        var profile=users.findActiveByUuid(actor).orElseThrow(() -> new UserFailure(UserFailure.Reason.INACTIVE));
        long now=System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> now-e.getValue().started()>=60_000);
        if(attempts.size()>=10_000 && !attempts.containsKey(actor)) return ResponseEntity.status(429).build();
        Attempt attempt=attempts.compute(actor,(key,old) -> old==null?new Attempt(now,1):new Attempt(old.started(),old.count()+1));
        if(attempt.count()>5) return ResponseEntity.status(429).header("Retry-After","60").build();
        if(!identity.authenticate(profile.email(),body.password()).uuid().equals(actor))
            return ResponseEntity.status(403).build();
        users.delete(actor);
        new org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler().logout(request,response,auth);
        response.addHeader("Set-Cookie",org.springframework.http.ResponseCookie.from("JSESSIONID","")
            .path("/").httpOnly(true).secure(request.isSecure()).sameSite("Lax").maxAge(0).build().toString());
        return ResponseEntity.noContent().build();
    }
}
