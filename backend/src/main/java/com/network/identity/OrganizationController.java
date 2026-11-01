package com.network.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController @RequestMapping("/api/v1")
class OrganizationController {
    private final JdbcTemplate db;
    private final DomainService domain;
    private final RateLimits limits;
    OrganizationController(JdbcTemplate db,DomainService domain,RateLimits limits) { this.db=db;this.domain=domain;this.limits=limits; }
    record CreateOrganization(@NotBlank @Size(max=160) String name,@Pattern(regexp="[a-z0-9][a-z0-9-]{2,62}") @NotNull String slug) {}
    @PostMapping("/organizations") @Transactional Map<String,Object> create(@Valid @RequestBody CreateOrganization body,@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        UUID user=domain.resolve(p);DomainService.recent(p);limits.require("org-create",user.toString(),3,3600);
        UUID id=UUID.randomUUID();db.update("INSERT INTO organization(id,slug,name) VALUES (?,?,?)",id,body.slug(),body.name());
        db.update("INSERT INTO organization_security_policy(organization_id) VALUES (?)",id);
        db.update("INSERT INTO organization_membership(organization_id,user_id,role) VALUES (?,?,'OWNER')",id,user);
        domain.audit(user,id,"ORGANIZATION_CREATED",request);return Map.of("id",id,"name",body.name());
    }
    @GetMapping("/organizations/{id}") Map<String,Object> get(@PathVariable UUID id,@AuthenticationPrincipal OidcUser p) { return domain.authorize(id,p,false,false); }
    @GetMapping("/organizations/{id}/security-policy") Map<String,Object> policy(@PathVariable UUID id,@AuthenticationPrincipal OidcUser p) {
        domain.authorize(id,p,true,false);return db.queryForMap("SELECT sso_only,mfa_mode,jit_enabled,version FROM organization_security_policy WHERE organization_id=?",id);
    }
    record Policy(boolean ssoOnly,@Pattern(regexp="OPTIONAL|MANDATORY|TRUSTED_EXTERNAL") @NotNull String mfaMode,boolean jitEnabled,@Min(0) long version) {}
    @PutMapping("/organizations/{id}/security-policy") @Transactional void policy(@PathVariable UUID id,@Valid @RequestBody Policy body,@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        domain.authorize(id,p,true,false);DomainService.recent(p);
        if(body.ssoOnly() || body.jitEnabled() || "TRUSTED_EXTERNAL".equals(body.mfaMode())) {
            Integer count=db.queryForObject("SELECT count(*) FROM organization_identity_provider WHERE organization_id=? AND approved AND enabled",Integer.class,id);
            if(count==null || count==0) throw new ResponseStatusException(CONFLICT,"An approved identity provider must be enabled first");
        }
        if(db.update("UPDATE organization_security_policy SET sso_only=?,mfa_mode=?,jit_enabled=?,version=version+1 WHERE organization_id=? AND version=?",body.ssoOnly(),body.mfaMode(),body.jitEnabled(),id,body.version())!=1)
            throw new ResponseStatusException(CONFLICT,"Policy changed; reload before saving");
        domain.audit(domain.resolve(p),id,"SECURITY_POLICY_CHANGED",request);
    }
    @GetMapping("/organizations/{id}/sso") Map<String,Object> sso(@PathVariable UUID id,@AuthenticationPrincipal OidcUser p) {
        domain.authorize(id,p,true,false);var providers=db.queryForList("SELECT alias,protocol,issuer,tenant_id,enabled,approved,version FROM organization_identity_provider WHERE organization_id=?",id);
        return providers.isEmpty()?Map.of("configured",false):providers.getFirst();
    }
    record SsoEnabled(boolean enabled,@Min(0) long version) {}
    @PutMapping("/organizations/{id}/sso") @Transactional void sso(@PathVariable UUID id,@Valid @RequestBody SsoEnabled body,@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        domain.authorize(id,p,true,false);DomainService.recent(p);
        if(db.update("UPDATE organization_identity_provider SET enabled=?,version=version+1 WHERE organization_id=? AND approved AND version=?",body.enabled(),id,body.version())!=1) throw new ResponseStatusException(CONFLICT,"An operator must approve the provider configuration first");
        domain.audit(domain.resolve(p),id,"SSO_CONFIGURATION_CHANGED",request);
    }
    record Invite(@Email @NotBlank @Size(max=254) String email,@Pattern(regexp="ADMIN|MEMBER") @NotNull String role) {}
    @PostMapping("/organizations/{id}/invitations") @Transactional Map<String,String> invite(@PathVariable UUID id,@Valid @RequestBody Invite body,@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        domain.authorize(id,p,true,false);DomainService.recent(p);limits.require("invitation",p.getSubject(),10,60);
        byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        db.update("INSERT INTO invitation(id,organization_id,token_hash,email,role,expires_at) VALUES (?,?,?,?,?,now()+interval '48 hours')",UUID.randomUUID(),id,DomainService.hash(token),body.email().strip().toLowerCase(Locale.ROOT),body.role());
        domain.audit(domain.resolve(p),id,"INVITATION_CREATED",request);
        return Map.of("token",token,"acceptPath","/invite#"+token);
    }
    record Accept(@Pattern(regexp="[A-Za-z0-9_-]{43}") String token) {}
    @PostMapping("/auth/invitations/prepare") void prepare(@Valid @RequestBody Accept body,HttpServletRequest request) {
        if(body.token()==null)throw new ResponseStatusException(BAD_REQUEST);
        limits.require("invite-prepare",request.getRemoteAddr(),10,60);
        request.getSession().setAttribute("invitation_hash",DomainService.hash(body.token()));
        request.getSession().setAttribute("return_to","/invite");
    }
    @PostMapping("/invitations/accept") @Transactional void accept(@Valid @RequestBody Accept body,@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        UUID user=domain.resolve(p);limits.require("accept",user.toString(),10,60);
        if(!Boolean.TRUE.equals(p.getEmailVerified())) throw new ResponseStatusException(FORBIDDEN,"A verified email is required");
        String tokenHash=body.token()==null?(String)request.getSession().getAttribute("invitation_hash"):DomainService.hash(body.token());
        if(tokenHash==null)throw new ResponseStatusException(BAD_REQUEST,"Invitation unavailable");
        var invitations=db.queryForList("SELECT id,organization_id,role,email,accepted_by FROM invitation WHERE token_hash=? AND expires_at>now() FOR UPDATE",tokenHash);
        if(invitations.isEmpty() || !p.getEmail().equalsIgnoreCase((String)invitations.getFirst().get("email"))) throw new ResponseStatusException(FORBIDDEN,"Invitation unavailable");
        var invitation=invitations.getFirst();
        if(invitation.get("accepted_by")!=null) { if(user.equals(invitation.get("accepted_by"))) return; throw new ResponseStatusException(FORBIDDEN); }
        db.update("INSERT INTO organization_membership(organization_id,user_id,role) VALUES (?,?,?) ON CONFLICT DO NOTHING",invitation.get("organization_id"),user,invitation.get("role"));
        db.update("UPDATE invitation SET accepted_by=? WHERE id=?",user,invitation.get("id"));
        domain.audit(user,(UUID)invitation.get("organization_id"),"INVITATION_ACCEPTED",request);
    }
    @GetMapping("/organizations/{id}/members") List<Map<String,Object>> members(@PathVariable UUID id,@AuthenticationPrincipal OidcUser p) {
        domain.authorize(id,p,true,false);return db.queryForList("SELECT user_id,role,status,version FROM organization_membership WHERE organization_id=?",id);
    }
    record Member(@Pattern(regexp="ADMIN|MEMBER") @NotNull String role,@Pattern(regexp="ACTIVE|REVOKED") @NotNull String status,@Min(0) long version) {}
    @PutMapping("/organizations/{id}/members/{user}") @Transactional void member(@PathVariable UUID id,@PathVariable UUID user,@Valid @RequestBody Member body,@AuthenticationPrincipal OidcUser p,HttpServletRequest request) {
        domain.authorize(id,p,true,false);DomainService.recent(p);
        if(user.equals(domain.resolve(p))) throw new ResponseStatusException(CONFLICT,"Ask another administrator to change your membership");
        if(db.update("UPDATE organization_membership SET role=?,status=?,version=version+1 WHERE organization_id=? AND user_id=? AND role<>'OWNER' AND version=?",body.role(),body.status(),id,user,body.version())!=1) throw new ResponseStatusException(CONFLICT);
        domain.audit(domain.resolve(p),id,"MEMBERSHIP_CHANGED",request);
    }
    @GetMapping("/organizations/{id}/events") List<Map<String,Object>> events(@PathVariable UUID id,@AuthenticationPrincipal OidcUser p) {
        domain.authorize(id,p,true,false);return db.queryForList("SELECT actor_id,event_type,outcome,created_at FROM security_audit_event WHERE organization_id=? ORDER BY created_at DESC LIMIT 100",id);
    }
}
