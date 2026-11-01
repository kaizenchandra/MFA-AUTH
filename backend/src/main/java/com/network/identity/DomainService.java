package com.network.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
class DomainService {
    private final JdbcTemplate db;
    DomainService(JdbcTemplate db) { this.db=db; }
    @Transactional
    public UUID resolve(OidcUser p) {
        if (!Boolean.TRUE.equals(p.getEmailVerified()) && !"true".equals(p.getClaimAsString("phone_verified")))
            throw new ResponseStatusException(FORBIDDEN,"Verify a contact method first");
        if (!"local".equals(p.getClaimAsString("platform_mfa")) && !"external".equals(p.getClaimAsString("platform_mfa")))
            throw new ResponseStatusException(FORBIDDEN,"MFA assurance is missing");
        db.update("INSERT INTO app_user(id,issuer,subject) VALUES (?,?,?) ON CONFLICT (issuer,subject) DO NOTHING",UUID.randomUUID(),p.getIssuer().toString(),p.getSubject());
        var rows=db.queryForList("SELECT id,status FROM app_user WHERE issuer=? AND subject=?",p.getIssuer().toString(),p.getSubject());
        if (!"ACTIVE".equals(rows.getFirst().get("status"))) throw new ResponseStatusException(FORBIDDEN);
        return (UUID)rows.getFirst().get("id");
    }
    @Transactional
    public Map<String,Object> authorize(UUID org, OidcUser p, boolean admin, boolean allowJit) {
        UUID user=resolve(p);
        var policies=db.queryForList("""
            SELECT o.id,o.name,p.sso_only,p.mfa_mode,p.jit_enabled,i.alias,i.enabled AS idp_enabled,i.approved
            FROM organization o JOIN organization_security_policy p ON p.organization_id=o.id
            LEFT JOIN organization_identity_provider i ON i.organization_id=o.id WHERE o.id=? AND o.enabled=true
            """,org);
        if (policies.isEmpty()) throw new ResponseStatusException(FORBIDDEN);
        var policy=policies.getFirst();
        boolean trusted=Boolean.TRUE.equals(policy.get("approved")) && Boolean.TRUE.equals(policy.get("idp_enabled")) && Objects.equals(policy.get("alias"),p.getClaimAsString("idp"));
        if (Boolean.TRUE.equals(policy.get("sso_only")) && !trusted) throw new ResponseStatusException(FORBIDDEN,"Company SSO is required");
        if (!"OPTIONAL".equals(policy.get("mfa_mode")) && !"local".equals(p.getClaimAsString("platform_mfa")) && !(trusted && "TRUSTED_EXTERNAL".equals(policy.get("mfa_mode")) && "external".equals(p.getClaimAsString("platform_mfa"))))
            throw new ResponseStatusException(FORBIDDEN,"Additional authentication is required");
        if (allowJit && trusted && Boolean.TRUE.equals(policy.get("jit_enabled"))) {
            // A revoked row is never reactivated by JIT. Domain equality is never sufficient.
            db.update("INSERT INTO organization_membership(organization_id,user_id,role) VALUES (?,?,'MEMBER') ON CONFLICT DO NOTHING",org,user);
        }
        var memberships=db.queryForList("SELECT role FROM organization_membership WHERE organization_id=? AND user_id=? AND status='ACTIVE'",org,user);
        if (memberships.isEmpty()) throw new ResponseStatusException(FORBIDDEN);
        String role=(String)memberships.getFirst().get("role");
        if (admin && !Set.of("OWNER","ADMIN").contains(role)) throw new ResponseStatusException(FORBIDDEN);
        return Map.of("id",org,"name",policy.get("name"),"role",role);
    }
    void audit(UUID user, UUID org, String type, HttpServletRequest request) {
        db.update("INSERT INTO security_audit_event(id,actor_id,organization_id,event_type,outcome,correlation_id) VALUES (?,?,?,?,'SUCCESS',?)",UUID.randomUUID(),user,org,type,Objects.toString(request.getAttribute("correlationId"),UUID.randomUUID().toString()));
    }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static void recent(OidcUser p) {
        Instant authTime=p.getClaimAsInstant("auth_time");
        if (authTime==null || authTime.isBefore(Instant.now().minusSeconds(300))) throw new ResponseStatusException(FORBIDDEN,"Recent reauthentication is required");
    }
}
