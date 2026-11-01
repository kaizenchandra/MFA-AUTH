package com.network.keycloak;
import org.junit.jupiter.api.*;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.models.*;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.sessions.AuthenticationSessionModel;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BoundTotpTest {
    RequiredActionContext context=mock(RequiredActionContext.class);
    AuthenticationSessionModel auth=mock(AuthenticationSessionModel.class);
    BoundTotp provider=new BoundTotp();
    @BeforeEach void setup(){when(context.getAuthenticationSession()).thenReturn(auth);}
    @Test void noPendingEnrollmentRejected(){assertFalse(provider.validateOTPCredential(context,"123456",null,null));}
    @Test void expiredEnrollmentRejected(){when(auth.getAuthNote("network_totp_started")).thenReturn(Long.toString(System.currentTimeMillis()-300001));when(auth.getAuthNote(Constants.TOTP_SECRET_KEY)).thenReturn("server-secret");assertFalse(provider.validateOTPCredential(context,"123456",null,null));}
    @Test void substitutedSecretRejected(){when(auth.getAuthNote("network_totp_started")).thenReturn(Long.toString(System.currentTimeMillis()));when(auth.getAuthNote(Constants.TOTP_SECRET_KEY)).thenReturn("server-secret");var credential=OTPCredentialModel.createTOTP("attacker-secret",6,30,"HmacSHA1");assertFalse(provider.validateOTPCredential(context,"123456",credential,null));}
}
