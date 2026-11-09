package com.kampive.api.module.campsite.adapter.in.web;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.kampive.api.module.campsite.application.port.in.Campsites;
import com.kampive.api.module.campsite.application.port.in.Campsites.*;

@RestController
@RequestMapping("/api/v1")
@Tag(name="Campsites",description="Published catalog, community suggestions and verified owner management. UUID identifiers only.")
public class CampsiteController {
    private final Campsites campsites;
    public CampsiteController(Campsites campsites) { this.campsites=campsites; }
    @GetMapping("/campsites")
    @Operation(summary="Search published campsites",description="Literal case-insensitive and Latin-accent-insensitive search by campsite, city or country. Filters combine with AND. Page starts at 0; size 1–50. Ordered by rating descending (unrated last), then review count and UUID. Text searches prioritize exact and prefix matches by name and destination. Optional city and administrative division UUID filters. Never includes drafts.")
    public Page<Summary> search(@RequestParam(required=false) UUID countryUuid,
            @RequestParam(required=false) UUID cityUuid,@RequestParam(required=false) UUID divisionUuid,
            @RequestParam(required=false) UUID categoryUuid,@RequestParam(required=false) UUID amenityUuid,
            @RequestParam(required=false) UUID settingUuid,@RequestParam(required=false) String query,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return campsites.search(new Query(countryUuid,categoryUuid,amenityUuid,settingUuid,query,page,size,cityUuid,divisionUuid));
    }
    @GetMapping("/campsites/{uuid}")
    @Operation(summary="Read a published campsite",description="Unpublished and unknown UUIDs both return 404. Photos are ordered, maximum 9. Ratings count only published reviews.")
    public Detail get(@PathVariable UUID uuid) { return campsites.get(uuid); }
    @GetMapping("/campsite-options")
    @Operation(summary="Read categories, amenities, natural settings, contact types and currencies",description="Catalogs are manually provisioned. No sample data is inserted automatically.")
    public Catalog options() { return campsites.catalog(); }
    @GetMapping("/cities")
    @Operation(summary="Search cities for a campsite address",description="Optional country UUID and literal name search; page starts at 0 and size is limited to 50.")
    public Page<City> cities(@RequestParam(required=false) UUID countryUuid,@RequestParam(required=false) String query,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return campsites.cities(new Query(countryUuid,null,null,null,query,page,size));
    }
    @GetMapping("/users/me/campsites")
    @SecurityRequirement(name="sessionCookie")
    @Operation(summary="List the authenticated owner's campsites, including drafts")
    public Page<Summary> mine(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return campsites.mine(actor(auth),new Query(null,null,null,null,null,page,size));
    }
    @GetMapping("/users/me/campsites/{uuid}")
    @SecurityRequirement(name="sessionCookie")
    @Operation(summary="Read an owned campsite",description="Another owner's UUID and unknown UUID both return 404.")
    public Detail owned(Authentication auth,@PathVariable UUID uuid) { return campsites.owned(actor(auth),uuid); }
    @PostMapping(value="/campsite-registrations",consumes="application/json")
    @SecurityRequirement(name="sessionCookie")
    @Operation(summary="Submit a complete campsite for staff review",description="Creates an unpublished pending campsite without an owner. The session identifies the contributor only. Requires city, street, description, contact and photo. Coordinates and location notes are optional. CSRF required.")
    public ResponseEntity<Submission> register(Authentication auth,@RequestBody Content body) {
        rejectManagedPhotos(body);
        return ResponseEntity.status(201).body(campsites.register(actor(auth),body));
    }
    @PostMapping("/campsite-suggestions")
    @SecurityRequirement(name="sessionCookie")
    @Operation(summary="Suggest a campsite",description="Stores a private suggestion for staff review. Does not create a campsite or grant ownership. CSRF required.")
    public ResponseEntity<Submission> suggest(Authentication auth,@RequestBody SuggestionInput body) {
        return ResponseEntity.status(201).body(campsites.suggest(actor(auth),body));
    }
    @PostMapping("/campsites/{uuid}/claims")
    @SecurityRequirement(name="sessionCookie")
    @Operation(summary="Request ownership review",description="Published campsites only. Contact and evidence are private. Only staff approval grants management access. Repeated pending requests return the original receipt. CSRF required.")
    public ResponseEntity<Submission> claim(Authentication auth,@PathVariable UUID uuid,@RequestBody ClaimInput body) {
        return ResponseEntity.status(201).body(campsites.claim(actor(auth),uuid,body));
    }
    @PutMapping(value="/campsites/{uuid}",consumes="application/json")
    @SecurityRequirement(name="sessionCookie")
    @Operation(summary="Replace an owned draft or rejected campsite",description="Full replacement including contacts, amenities, settings and photos. Missing lists become empty. Returns to draft. Published, pending-review and archived campsites cannot be edited (409). CSRF required.")
    public Detail update(Authentication auth,@PathVariable UUID uuid,@RequestBody Content body) { rejectManagedPhotos(body); return campsites.update(actor(auth),uuid,body); }
    @PostMapping("/campsites/{uuid}/submit")
    @SecurityRequirement(name="sessionCookie")
    @Operation(summary="Send an owned draft for review",description="Requires city, street, coordinates, description, at least one contact and one photo. Does not publish. CSRF required.")
    public Detail submit(Authentication auth,@PathVariable UUID uuid) { return campsites.submit(actor(auth),uuid); }
    @PostMapping("/campsites/{uuid}/archive")
    @SecurityRequirement(name="sessionCookie")
    @Operation(summary="Archive an owned campsite",description="Removes it from public results without deleting records. CSRF required.")
    public Detail archive(Authentication auth,@PathVariable UUID uuid) { return campsites.archive(actor(auth),uuid); }
    private void rejectManagedPhotos(Content content) {
        if(content.photos().stream().anyMatch(p -> p.url().startsWith("/")))
            throw new com.kampive.api.module.campsite.domain.CampsiteFailure(com.kampive.api.module.campsite.domain.CampsiteFailure.Reason.INVALID_INPUT);
    }
    private UUID actor(Authentication auth) { return UUID.fromString(auth.getName()); }
}
