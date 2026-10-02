package com.kampive.api.module.identity.application.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.kampive.api.module.identity.application.port.in.IdentityUseCases.LegalDocument;

public interface CredentialStore {
    Optional<String> passwordHash(UUID userUuid);
    void save(UUID userUuid, String hash, List<UUID> acceptedDocuments);
    List<LegalDocument> currentDocuments();
}
