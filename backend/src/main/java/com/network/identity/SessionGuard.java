package com.network.identity;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Introspection is deliberately uncached: disabled/revoked identity sessions fail closed. */
@Component @Order(0)
class SessionGuard extends OncePerRequestFilter {
    private final OAuth2AuthorizedClientRepository clients;
    private final IdentityGateway identity;
    SessionGuard(OAuth2AuthorizedClientRepository clients, IdentityGateway identity) { this.clients=clients; this.identity=identity; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        String correlation=UUID.randomUUID().toString(); request.setAttribute("correlationId",correlation);
        response.setHeader("X-Correlation-ID",correlation); response.setHeader("Cache-Control","no-store");
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (request.getRequestURI().startsWith("/api/v1/") && auth instanceof OAuth2AuthenticationToken token) {
            try {
                OAuth2AuthorizedClient client=clients.loadAuthorizedClient("keycloak",token,request);
                if (client==null || client.getAccessToken().getExpiresAt()==null || client.getAccessToken().getExpiresAt().isBefore(Instant.now()) || !identity.active(client.getAccessToken().getTokenValue())) {
                    if(request.getSession(false)!=null) request.getSession().invalidate();
                    response.sendError(401); return;
                }
            } catch (RuntimeException ex) { response.sendError(503,"Identity service unavailable"); return; }
        }
        chain.doFilter(request,response);
    }
}
