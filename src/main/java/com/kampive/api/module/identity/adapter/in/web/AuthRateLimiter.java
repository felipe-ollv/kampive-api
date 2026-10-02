package com.kampive.api.module.identity.adapter.in.web;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Local, bounded fixed-window limiter. Use a shared limiter before scaling the API. */
@Component
public class AuthRateLimiter {
    private record Window(long started, int count) {}
    private final Map<String,Window> windows=new HashMap<>();
    private final Clock clock;
    public AuthRateLimiter() { this(Clock.systemUTC()); }
    AuthRateLimiter(Clock clock) { this.clock=clock; }
    public synchronized boolean allow(String remoteAddress) {
        long now=clock.millis();
        windows.entrySet().removeIf(e -> now-e.getValue().started()>=60_000);
        Window previous=windows.get(remoteAddress);
        if (previous==null) {
            if (windows.size()>=10_000) return false;
            windows.put(remoteAddress,new Window(now,1));
            return true;
        }
        if (previous.count()>=10) return false;
        windows.put(remoteAddress,new Window(previous.started(),previous.count()+1));
        return true;
    }
}
