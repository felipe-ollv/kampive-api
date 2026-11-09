package com.kampive.api.configuration.security;
import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import com.kampive.api.module.identity.application.port.in.EmailVerification;
public final class EmailVerificationFilter extends OncePerRequestFilter {
    private final EmailVerification verification;
    public EmailVerificationFilter(EmailVerification verification){this.verification=verification;}
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String path=request.getServletPath();
        if(path.isEmpty()) path=request.getRequestURI().substring(request.getContextPath().length());
        boolean contribution="POST".equals(request.getMethod()) && (path.equals("/api/v1/campsite-suggestions") || path.equals("/api/v1/campsite-registrations") || path.matches("/api/v1/campsites/[^/]+/(reviews|claims|submit)"));
        contribution = contribution || ("PUT".equals(request.getMethod()) && path.matches("/api/v1/users/me/reviews/[^/]+"));
        if(contribution && request.getUserPrincipal()!=null && !verification.status(UUID.fromString(request.getUserPrincipal().getName())).contributionsAllowed()) {
            response.setStatus(403);response.setContentType("application/problem+json");response.setHeader("Cache-Control","no-store");
            response.getWriter().write("{\"status\":403,\"code\":\"EMAIL_VERIFICATION_REQUIRED\",\"title\":\"Confirm your email before contributing\"}");return;
        }
        chain.doFilter(request,response);
    }
}
