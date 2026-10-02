package com.kampive.api.module.identity.adapter.in.web;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class AuthRateLimiterTest {
    @Test void limitsAttemptsByAddressAndResetsAfterWindow() {
        Clock clock=mock(Clock.class);
        when(clock.millis()).thenReturn(0L);
        var limiter=new AuthRateLimiter(clock);
        for(int i=0;i<10;i++) assertThat(limiter.allow("127.0.0.1")).isTrue();
        assertThat(limiter.allow("127.0.0.1")).isFalse();
        assertThat(limiter.allow("127.0.0.2")).isTrue();
        when(clock.millis()).thenReturn(60_000L);
        assertThat(limiter.allow("127.0.0.1")).isTrue();
    }
}
