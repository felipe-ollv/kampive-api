package com.kampive.api.module.review.adapter.in.web;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.kampive.api.module.review.application.port.in.Reviews;
import com.kampive.api.module.campsite.application.port.in.Campsites.Page;
@RestController @RequestMapping("/api/v1")
public class ReviewController {
    private final Reviews reviews;
    public ReviewController(Reviews reviews) { this.reviews=reviews; }
    @GetMapping("/review-options") public Reviews.Options options() { return reviews.options(); }
    @GetMapping("/campsites/{uuid}/reviews") public Page<Reviews.Item> list(@PathVariable UUID uuid,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return reviews.list(uuid,page,size);
    }
    @GetMapping("/users/me/reviews") public Page<Reviews.Item> mine(Authentication auth,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return reviews.mine(UUID.fromString(auth.getName()),page,size);
    }
    @GetMapping("/users/me/reviews/{uuid}")
    public Reviews.Editable get(Authentication auth,@PathVariable UUID uuid) {
        return reviews.get(UUID.fromString(auth.getName()),uuid);
    }
    @PutMapping("/users/me/reviews/{uuid}")
    public Reviews.Receipt update(Authentication auth,@PathVariable UUID uuid,@RequestBody Reviews.Input body) {
        var photos=body.photos().stream().map(ReviewPhoto::normalize).toList();
        return reviews.update(UUID.fromString(auth.getName()),uuid,new Reviews.Input(body.rating(),body.text(),body.campingMethodUuid(),body.highlightUuids(),photos));
    }
    @DeleteMapping("/users/me/reviews/{uuid}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void delete(Authentication auth,@PathVariable UUID uuid) {
        reviews.delete(UUID.fromString(auth.getName()),uuid);
    }
    @PostMapping("/campsites/{uuid}/reviews") @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public Reviews.Receipt create(Authentication auth,@PathVariable UUID uuid,@RequestBody Reviews.Input body) {
        var photos=body.photos().stream().map(ReviewPhoto::normalize).toList();
        return reviews.create(UUID.fromString(auth.getName()),uuid,new Reviews.Input(body.rating(),body.text(),body.campingMethodUuid(),body.highlightUuids(),photos));
    }
}
