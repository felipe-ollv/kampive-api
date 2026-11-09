package com.kampive.api.module.campsite.application.port.in;

import java.time.Instant;
import java.util.UUID;
import com.kampive.api.module.campsite.application.port.in.Campsites.Page;

/** Private submissions, scoped to the session author; review notes are never exposed. */
public interface MyRequests {
    record Item(UUID uuid, String type, String name, String status, Instant createdAt,
                Instant reviewedAt, UUID publishedCampsiteUuid) {}
    record Detail(Item summary, String instagramUrl, String googleMapsUrl,
                  String contact, String evidence, String response) {}
    Page<Item> list(UUID actor, int page, int size);
    Detail get(UUID actor, String type, UUID uuid);
}
