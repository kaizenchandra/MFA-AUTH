package com.network.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.http.HttpStatus;

@Configuration
class SecurityConfig {
    @Bean ClientRegistrationRepository registrations(@Value("${platform.issuer}") String issuer,
            @Value("${platform.idp-internal}") String internal,@Value("${platform.origin}") String origin,
            @Value("${platform.client-secret}") String secret) {
        // Split front/back channels while retaining the public issuer for ID-token validation.
        var client=org.springframework.security.oauth2.client.registration.ClientRegistration.withRegistrationId("keycloak")
            .clientId("network-bff").clientSecret(secret)
            .clientAuthenticationMethod(org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(origin+"/login/oauth2/code/keycloak").scope("openid","profile","email")
            .issuerUri(issuer).authorizationUri(issuer+"/protocol/openid-connect/auth")
            .tokenUri(internal+"/realms/network/protocol/openid-connect/token")
            .jwkSetUri(internal+"/realms/network/protocol/openid-connect/certs")
            .userInfoUri(internal+"/realms/network/protocol/openid-connect/userinfo").userNameAttributeName("sub")
            .clientName("Network identity").build();
        return new org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository(client);
    }
    @Bean OAuth2AuthorizedClientRepository clients() { return new HttpSessionOAuth2AuthorizedClientRepository(); }

    @Bean SecurityFilterChain security(HttpSecurity http, ClientRegistrationRepository registrations,
            OAuth2AuthorizedClientRepository clients, DomainService domain, @Value("${platform.origin}") String origin) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        OAuth2AuthorizationRequestResolver requests = new OAuth2AuthorizationRequestResolver() {
            public OAuth2AuthorizationRequest resolve(HttpServletRequest r) { return customize(r, resolver.resolve(r)); }
            public OAuth2AuthorizationRequest resolve(HttpServletRequest r, String id) { return customize(r, resolver.resolve(r,id)); }
            private OAuth2AuthorizationRequest customize(HttpServletRequest r, OAuth2AuthorizationRequest request) {
                if (request == null) return null;
                var builder = OAuth2AuthorizationRequest.from(request);
                builder.additionalParameters(p -> p.put("max_age","900"));
                String returnTo=r.getParameter("returnTo");
                if(Set.of("/app","/security","/organizations","/invite").contains(returnTo==null?"":returnTo))r.getSession().setAttribute("return_to",returnTo);
                String provider = r.getParameter("provider");
                String org = (String)r.getSession().getAttribute("sso_alias");
                if (org != null) { builder.additionalParameters(p -> p.put("kc_idp_hint",org)); r.getSession().removeAttribute("sso_alias"); }
                else if (Set.of("google","microsoft").contains(provider == null ? "" : provider))
                    builder.additionalParameters(p -> p.put("kc_idp_hint",provider));
                String action = r.getParameter("action");
                if (Set.of("CONFIGURE_TOTP","CONFIGURE_RECOVERY_AUTHN_CODES","UPDATE_PASSWORD","UPDATE_PROFILE","VERIFY_CONTACT").contains(action == null ? "" : action))
                    builder.additionalParameters(p -> { p.put("kc_action",action); p.put("max_age","0"); p.put("prompt","login"); });
                if(action!=null)r.getSession().setAttribute("return_to","/security");
                if ("register".equals(r.getParameter("mode"))) builder.additionalParameters(p -> p.put("prompt","create"));
                return builder.build();
            }
        };
        http.authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/auth/csrf","/api/v1/auth/sso/discovery","/api/v1/auth/providers","/api/v1/auth/invitations/prepare","/oauth2/**","/login/**","/error","/actuator/health/**").permitAll()
                .requestMatchers("/actuator/prometheus").hasRole("MONITOR")
                .anyRequest().authenticated())
            .csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .requestCache(c -> c.disable())
            .sessionManagement(s -> s.sessionFixation(f -> f.changeSessionId()))
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .oauth2Login(o -> o.authorizedClientRepository(clients)
                .authorizationEndpoint(e -> e.authorizationRequestResolver(requests))
                .successHandler((request,response,auth) -> {
                    var principal = (org.springframework.security.oauth2.core.oidc.user.OidcUser)auth.getPrincipal();
                    try {
                        domain.resolve(principal);
                        String org = (String)request.getSession().getAttribute("sso_org");
                        request.getSession().removeAttribute("sso_org");
                        if (org != null) domain.authorize(java.util.UUID.fromString(org),principal,false,true);
                        String target=(String)request.getSession().getAttribute("return_to");request.getSession().removeAttribute("return_to");
                        response.sendRedirect(origin + (Set.of("/app","/security","/organizations","/invite").contains(target==null?"":target)?target:"/app"));
                    } catch (RuntimeException ex) {
                        request.getSession().invalidate();
                        response.sendRedirect(origin + "/?error=access_denied");
                    }
                }).failureHandler((r,s,e) -> s.sendRedirect(origin + "/?error=authentication_failed")))
            .logout(l -> l.disable())
            .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }
}
