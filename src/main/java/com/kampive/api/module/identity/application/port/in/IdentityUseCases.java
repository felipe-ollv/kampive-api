package com.kampive.api.module.identity.application.port.in;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import com.kampive.api.module.user.application.port.in.UserAccounts.UserView;

public interface IdentityUseCases {
    record Registration(String name, String email, String password, UUID countryUuid,
                        UUID adventurerStyleUuid, List<UUID> acceptedDocumentUuids) {
        @Override public String toString() { return "Registration[REDACTED]"; }
    }
    record LegalDocument(UUID uuid, String type, String version, String contentUrl, Instant publishedAt) {}
    UserView register(Registration command);
    UserView authenticate(String email, String password);
    List<LegalDocument> currentDocuments();
}
