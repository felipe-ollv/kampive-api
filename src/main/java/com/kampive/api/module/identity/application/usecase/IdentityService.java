package com.kampive.api.module.identity.application.usecase;

import java.util.List;
import java.util.Locale;
import java.util.HashSet;
import com.kampive.api.module.identity.application.port.in.IdentityUseCases;
import com.kampive.api.module.identity.application.port.out.CredentialStore;
import com.kampive.api.module.identity.application.port.out.PasswordHasher;
import com.kampive.api.module.identity.domain.IdentityFailure;
import com.kampive.api.module.user.application.port.in.UserAccounts;
import com.kampive.api.module.user.application.port.in.UserAccounts.UserView;
import static com.kampive.api.module.identity.domain.IdentityFailure.Reason.*;

public final class IdentityService implements IdentityUseCases {
    private final UserAccounts users;
    private final CredentialStore credentials;
    private final PasswordHasher passwords;
    public IdentityService(UserAccounts users, CredentialStore credentials, PasswordHasher passwords) {
        this.users=users; this.credentials=credentials; this.passwords=passwords;
    }
    public UserView register(Registration c) {
        if (c.name()==null || c.name().strip().length()<3 || c.name().strip().length()>150
                || c.password()==null || c.password().length()<15 || c.password().length()>128) {
            throw new IdentityFailure(INVALID_INPUT);
        }
        String email=normalizeEmail(c.email());
        var documents=currentDocuments();
        var required=documents.stream().map(LegalDocument::uuid).toList();
        if (documents.size()!=2 || c.acceptedDocumentUuids()==null
                || c.acceptedDocumentUuids().size()!=2
                || !new HashSet<>(required).equals(new HashSet<>(c.acceptedDocumentUuids()))) {
            throw new IdentityFailure(DOCUMENTS_REQUIRED);
        }
        // Hash before the unique insert so duplicate-account requests also perform password work.
        String hash=passwords.hash(c.password());
        UserView user=users.create(c.name().strip(),email,c.countryUuid(),c.adventurerStyleUuid());
        credentials.save(user.uuid(),hash,required);
        return user;
    }
    public UserView authenticate(String email, String password) {
        if (password==null || password.isEmpty() || password.length()>128) throw new IdentityFailure(INVALID_CREDENTIALS);
        var user=users.findActiveByEmail(normalizeEmail(email));
        var hash=user.flatMap(u -> credentials.passwordHash(u.uuid()));
        if (hash.isEmpty()) {
            passwords.verifyDummy(password);
            throw new IdentityFailure(INVALID_CREDENTIALS);
        }
        if (!passwords.matches(password,hash.get())) throw new IdentityFailure(INVALID_CREDENTIALS);
        return user.orElseThrow();
    }
    public List<LegalDocument> currentDocuments() { return credentials.currentDocuments(); }
    private String normalizeEmail(String email) {
        if (email==null || email.length()>254 || !email.strip().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new IdentityFailure(INVALID_INPUT);
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
