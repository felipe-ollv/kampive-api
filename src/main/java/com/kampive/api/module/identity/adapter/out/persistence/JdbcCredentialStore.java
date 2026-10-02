package com.kampive.api.module.identity.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.kampive.api.module.identity.application.port.out.CredentialStore;
import com.kampive.api.module.identity.application.port.in.IdentityUseCases.LegalDocument;

@Repository
public class JdbcCredentialStore implements CredentialStore {
    private final JdbcClient jdbc;
    public JdbcCredentialStore(JdbcClient jdbc) { this.jdbc=jdbc; }
    public Optional<String> passwordHash(UUID userUuid) {
        return jdbc.sql("SELECT password_hash FROM kampive.user_credential WHERE uuid_app_user=:user")
            .param("user",userUuid).query(String.class).optional();
    }
    public void save(UUID userUuid, String hash, List<UUID> documents) {
        jdbc.sql("INSERT INTO kampive.user_credential(uuid_app_user,password_hash) VALUES (:user,:hash)")
            .param("user",userUuid).param("hash",hash).update();
        for (UUID document : documents) {
            jdbc.sql("INSERT INTO kampive.document_acceptance(uuid_app_user,uuid_legal_document) VALUES (:user,:document)")
                .param("user",userUuid).param("document",document).update();
        }
    }
    public List<LegalDocument> currentDocuments() {
        return jdbc.sql("""
            SELECT DISTINCT ON (document_type) uuid_legal_document,document_type,version,content_url,published_at
            FROM kampive.legal_document WHERE published_at<=CURRENT_TIMESTAMP
            ORDER BY document_type,published_at DESC,id DESC
            """).query((rs,row) -> new LegalDocument(rs.getObject("uuid_legal_document",UUID.class),
                rs.getString("document_type"),rs.getString("version"),rs.getString("content_url"),
                rs.getTimestamp("published_at").toInstant())).list();
    }
}
