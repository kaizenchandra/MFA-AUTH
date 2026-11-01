package com.network.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController @RequestMapping("/api/v1")
class AccountController {
    private final DomainService domain;
    private final IdentityGateway identity;
    private final JdbcTemplate db;
    private final RateLimits limits;
    private final String issuer,origin;
    AccountController(DomainService domain, IdentityGateway identity, JdbcTemplate db, RateLimits limits,
        @Value("${platform.issuer}") String issuer,@Value("${platform.origin}") String origin) {
        this.domain=domain;this.identity=identity;this.db=db;this.limits=limits;this.issuer=issuer;this.origin=origin;
    }
    @GetMapping("/auth/csrf") Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/auth/providers") Map<String,Object> providers() { return Map.of("registration","/oauth2/authorization/keycloak?mode=register","login","/oauth2/authorization/keycloak"); }
    record Discovery(@NotBlank @Size(max=254) String identifier) {}
    @PostMapping("/auth/sso/discovery") Map<String,String> discover(@Valid @RequestBody Discovery body,HttpServletRequest request) {
        limits.require("sso",request.getRemoteAddr(),20,60);
        String value=body.identifier().strip().toLowerCase(Locale.ROOT);
        List<Map<String,Object>> orgs=value.contains("@") ? db.queryForList("SELECT o.id,i.alias FROM organization o JOIN organization_domain d ON d.organization_id=o.id JOIN organization_identity_provider i ON i.organization_id=o.id WHERE d.domain=? AND o.enabled AND i.enabled AND i.approved",value.substring(value.lastIndexOf('@')+1))
            : db.queryForList("SELECT o.id,i.alias FROM organization o JOIN organization_identity_provider i ON i.organization_id=o.id WHERE o.slug=? AND o.enabled AND i.enabled AND i.approved",value);
        if(orgs.size()!=1) throw new ResponseStatusException(NOT_FOUND,"Company sign-in is unavailable. Contact your administrator.");
        request.getSession().setAttribute("sso_alias",orgs.getFirst().get("alias"));
        request.getSession().setAttribute("sso_org",orgs.getFirst().get("id").toString());
        return Map.of("authorizationUrl","/oauth2/authorization/keycloak");
    }
    @GetMapping("/me") Map<String,Object> me(@AuthenticationPrincipal OidcUser p) {
        UUID user=domain.resolve(p);
        return Map.of("id",user,"name",Objects.toString(p.getFullName(),p.getPreferredUsername()),"email",Objects.toString(p.getEmail(),""),
            "mfa",p.getClaimAsString("platform_mfa"),"organizations",db.queryForList("SELECT o.id,o.name,o.slug,m.role FROM organization_membership m JOIN organization o ON o.id=m.organization_id WHERE m.user_id=? AND m.status='ACTIVE' AND o.enabled",user));
    }
    @GetMapping("/security/mfa") Map<String,Object> mfa(@AuthenticationPrincipal OidcUser p) {
        domain.resolve(p);
        return Map.of("credentials",identity.credentials(p.getSubject()),"assurance",p.getClaimAsString("platform_mfa"),
            "enrollUrl","/oauth2/authorization/keycloak?action=CONFIGURE_TOTP","recoveryUrl","/oauth2/authorization/keycloak?action=CONFIGURE_RECOVERY_AUTHN_CODES",
            "accountUrl",origin+"/security");
    }
    @GetMapping("/security/sessions") List<Map<String,Object>> sessions(@AuthenticationPrincipal OidcUser p) {
        domain.resolve(p);
        return identity.sessions(p.getSubject()).stream().map(s -> Map.<String,Object>of("id",s.get("id"),"ipAddress",Objects.toString(s.get("ipAddress"),""),"start",s.get("start"),"lastAccess",s.get("lastAccess"),"current",Objects.equals(s.get("id"),p.getClaimAsString("sid")))).toList();
    }
    @DeleteMapping("/security/sessions/{id}") void revoke(@PathVariable @Pattern(regexp="[a-zA-Z0-9-]{1,128}") String id,@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        UUID user=domain.resolve(p); DomainService.recent(p); identity.revoke(p.getSubject(),id); domain.audit(user,null,"SESSION_REVOKED",request);
    }
    @PostMapping("/auth/logout-all") void logoutAll(@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        UUID user=domain.resolve(p); DomainService.recent(p); identity.logoutAll(p.getSubject()); domain.audit(user,null,"ALL_SESSIONS_REVOKED",request); request.getSession().invalidate();
    }
    @PostMapping({"/auth/logout-current","/auth/logout"}) void logoutCurrent(@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        UUID user=domain.resolve(p); identity.revoke(p.getSubject(),p.getClaimAsString("sid")); domain.audit(user,null,"LOGOUT",request); request.getSession().invalidate();
    }
    @DeleteMapping("/security/mfa/{credential}") void removeAuthenticator(@PathVariable String credential,@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        UUID user=domain.resolve(p); DomainService.recent(p);
        if(identity.credentials(p.getSubject()).stream().filter(c -> "otp".equals(c.get("type"))).count()<2) throw new ResponseStatusException(CONFLICT,"Enroll a replacement authenticator before removing your last device");
        identity.removeOtp(p.getSubject(),credential);domain.audit(user,null,"AUTHENTICATOR_REMOVED",request);
    }
    @GetMapping("/security/events") List<Map<String,Object>> events(@AuthenticationPrincipal OidcUser p) {
        return db.queryForList("SELECT event_type,outcome,created_at FROM security_audit_event WHERE actor_id=? ORDER BY created_at DESC LIMIT 50",domain.resolve(p));
    }
}
