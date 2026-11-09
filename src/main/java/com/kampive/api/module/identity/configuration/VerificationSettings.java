package com.kampive.api.module.identity.configuration;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public record VerificationSettings(@Value("${app.verification.confirm-url:}") String confirmUrl, RecoverySettings recovery) {
    public boolean configured() {
        try {
            URI uri=URI.create(confirmUrl);
            return recovery.configured() && "https".equals(uri.getScheme()) && uri.getHost()!=null
                && uri.getUserInfo()==null && uri.getQuery()==null && uri.getFragment()==null;
        } catch(IllegalArgumentException e) { return false; }
    }
    @Override public String toString() { return "VerificationSettings[REDACTED]"; }
}
