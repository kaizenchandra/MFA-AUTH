package com.network.keycloak;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.requiredactions.UpdateTotp;
import org.keycloak.models.Constants;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.credential.OTPCredentialModel;

/** Adds a bounded, server-bound enrollment transaction to the native TOTP implementation. */
public final class BoundTotp extends UpdateTotp {
    @Override public int order() { return 100; }
    @Override public void requiredActionChallenge(RequiredActionContext c) {
        if(c.getAuthenticationSession().getAuthNote("network_totp_started")==null)
            c.getAuthenticationSession().setAuthNote("network_totp_started",Long.toString(System.currentTimeMillis()));
        super.requiredActionChallenge(c);
    }
    @Override protected boolean validateOTPCredential(RequiredActionContext c,String token,OTPCredentialModel credential,OTPPolicy policy) {
        String started=c.getAuthenticationSession().getAuthNote("network_totp_started");
        String secret=c.getAuthenticationSession().getAuthNote(Constants.TOTP_SECRET_KEY);
        if(started==null || secret==null || System.currentTimeMillis()-Long.parseLong(started)>300000) return false;
        if(!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),credential.getOTPSecretData().getValue().getBytes(StandardCharsets.UTF_8))) return false;
        String key="network:enroll:"+c.getUser().getId()+":"+PhoneChallenge.hash(secret);
        boolean reserved=false;
        for(int i=0;i<5;i++) if(c.getSession().singleUseObjects().putIfAbsent(key+":attempt:"+i,300)) { reserved=true;break; }
        if(!reserved || !super.validateOTPCredential(c,token,credential,policy)) return false;
        boolean confirmed=c.getSession().singleUseObjects().putIfAbsent(key+":confirmed",300);
        if(confirmed)c.getAuthenticationSession().setUserSessionNote("platform_mfa","local");
        return confirmed;
    }
}
