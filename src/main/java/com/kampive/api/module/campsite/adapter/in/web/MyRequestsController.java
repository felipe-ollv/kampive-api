package com.kampive.api.module.campsite.adapter.in.web;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.kampive.api.module.campsite.application.port.in.MyRequests;
import com.kampive.api.module.campsite.application.port.in.Campsites.Page;

@RestController
@RequestMapping("/api/v1/users/me/requests")
@SecurityRequirement(name="sessionCookie")
public class MyRequestsController {
    private final MyRequests requests;
    public MyRequestsController(MyRequests requests) { this.requests=requests; }
    @GetMapping
    @Operation(summary="List own suggestions and ownership requests",description="Newest first, deterministic pagination; size 1–50. Session author only.")
    public Page<MyRequests.Item> list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return requests.list(UUID.fromString(auth.getName()),page,size);
    }
    @GetMapping("/{type}/{uuid}")
    @Operation(summary="Read own request",description="Type suggestion or claim. Another account and unknown UUID both return 404. Response is the explicitly public response, never internal review notes. Campsite link is present only while published.")
    public MyRequests.Detail get(Authentication auth,@PathVariable String type,@PathVariable UUID uuid) {
        return requests.get(UUID.fromString(auth.getName()),type,uuid);
    }
}
