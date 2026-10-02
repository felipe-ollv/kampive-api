package com.kampive.api.module.user.adapter.in.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.kampive.api.module.user.application.port.in.UserAccounts;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name="Users")
public class UserController {
    private final UserAccounts users;
    public UserController(UserAccounts users) { this.users=users; }
    @GetMapping("/me")
    @Operation(summary="Get the authenticated user's profile")
    @SecurityRequirement(name="sessionCookie")
    public ResponseEntity<UserAccounts.UserView> me(Authentication authentication) {
        return users.findActiveByUuid(UUID.fromString(authentication.getName()))
            .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.status(401).build());
    }
}
