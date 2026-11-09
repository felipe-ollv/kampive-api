package com.kampive.api.module.identity.configuration;

import java.net.URI;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@org.springframework.scheduling.annotation.EnableScheduling
public record RecoverySettings(
    @Value("${app.recovery.reset-url:}") String resetUrl,
    @Value("${app.recovery.logo-url:}") String logoUrl,
    @Value("${app.recovery.support-url:}") String supportUrl,
    @Value("${app.recovery.token-key:}") String tokenKey) {
    public boolean configured() {
        try {
            for(String url: new String[]{resetUrl,logoUrl,supportUrl}) {
                URI uri=URI.create(url);
                if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null) return false;
            }
            URI reset=URI.create(resetUrl);
            return reset.getQuery()==null && reset.getFragment()==null && Base64.getDecoder().decode(tokenKey).length>=32;
        } catch(IllegalArgumentException e) { return false; }
    }
    @Override public String toString() { return "RecoverySettings[REDACTED]"; }
}
