package com.kampive.api.configuration.security;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import com.kampive.api.module.identity.application.port.in.SessionValidity;

/** Runs after context loading. A reset invalidates every older session, across API instances. */
public final class PasswordSessionFilter extends OncePerRequestFilter {
    public static final String STAMP="kampive.authentication.started";
    private final SessionValidity validity;
    public PasswordSessionFilter(SessionValidity validity) { this.validity=validity; }
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            var session=request.getSession(false);
            Object stamp=session==null?null:session.getAttribute(STAMP);
            boolean current=false;
            try { current=validity.valid(UUID.fromString(auth.getName()),stamp instanceof Instant i?i:null); }
            catch(IllegalArgumentException malformedIdentity) { /* reject non-UUID principals */ }
            if(!current) {
                if(session!=null) session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request,response);
    }
}
