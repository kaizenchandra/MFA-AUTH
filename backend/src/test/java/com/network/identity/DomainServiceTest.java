package com.network.identity;

import java.net.URL;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class DomainServiceTest {
    JdbcTemplate db=mock(JdbcTemplate.class);
    OidcUser user=mock(OidcUser.class);
    DomainService domain=new DomainService(db);
    UUID id=UUID.randomUUID(),org=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        when(user.getIssuer()).thenReturn(new URL("https://id.example/realms/network"));when(user.getSubject()).thenReturn("alice");when(user.getEmailVerified()).thenReturn(true);when(user.getClaimAsString("platform_mfa")).thenReturn("local");
        when(db.queryForList(startsWith("SELECT id,status"),anyString(),anyString())).thenReturn(List.of(Map.of("id",id,"status","ACTIVE")));
    }
    @Test void unverifiedIdentityDenied(){when(user.getEmailVerified()).thenReturn(false);assertThrows(ResponseStatusException.class,()->domain.resolve(user));verifyNoInteractions(db);}
    @Test void primaryOnlyDenied(){when(user.getClaimAsString("platform_mfa")).thenReturn(null);assertThrows(ResponseStatusException.class,()->domain.resolve(user));verifyNoInteractions(db);}
    @Test void verifiedPhonePermitted(){when(user.getEmailVerified()).thenReturn(false);when(user.getClaimAsString("phone_verified")).thenReturn("true");assertEquals(id,domain.resolve(user));}
    @Test void disabledProfileDenied(){when(db.queryForList(startsWith("SELECT id,status"),anyString(),anyString())).thenReturn(List.of(Map.of("id",id,"status","DISABLED")));assertThrows(ResponseStatusException.class,()->domain.resolve(user));}
    private void policy(boolean sso,boolean jit){when(db.queryForList(startsWith("SELECT o.id"),eq(org))).thenReturn(List.of(Map.of("name","Acme","sso_only",sso,"mfa_mode","MANDATORY","jit_enabled",jit,"alias","acme","approved",true,"idp_enabled",true)));}
    @Test void wrongProviderDenied(){policy(true,true);when(user.getClaimAsString("idp")).thenReturn("other");assertThrows(ResponseStatusException.class,()->domain.authorize(org,user,false,true));verify(db,never()).update(startsWith("INSERT INTO organization_membership"),any(),any());}
    @Test void revokedMembershipDenied(){policy(false,false);when(db.queryForList(startsWith("SELECT role"),eq(org),eq(id))).thenReturn(List.of());assertThrows(ResponseStatusException.class,()->domain.authorize(org,user,false,false));}
    @Test void memberCannotAdminister(){policy(false,false);when(db.queryForList(startsWith("SELECT role"),eq(org),eq(id))).thenReturn(List.of(Map.of("role","MEMBER")));assertThrows(ResponseStatusException.class,()->domain.authorize(org,user,true,false));}
    @Test void adminAllowed(){policy(false,false);when(db.queryForList(startsWith("SELECT role"),eq(org),eq(id))).thenReturn(List.of(Map.of("role","ADMIN")));assertEquals("ADMIN",domain.authorize(org,user,true,false).get("role"));}
    @Test void staleStepUpDenied(){when(user.getClaimAsInstant("auth_time")).thenReturn(Instant.now().minusSeconds(301));assertThrows(ResponseStatusException.class,()->DomainService.recent(user));}
    @Test void missingStepUpDenied(){assertThrows(ResponseStatusException.class,()->DomainService.recent(user));}
}
