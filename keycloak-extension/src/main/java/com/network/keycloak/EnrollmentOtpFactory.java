package com.network.keycloak;
import org.keycloak.authentication.*;
import org.keycloak.authentication.authenticators.browser.*;
import org.keycloak.models.*;
/** First sign-in is restricted to required actions until a confirmed OTP credential exists. */
public final class EnrollmentOtpFactory extends OTPFormAuthenticatorFactory {
    @Override public String getId(){return "network-local-otp";}
    @Override public String getDisplayType(){return "Network OTP or mandatory enrollment";}
    @Override public Authenticator create(KeycloakSession session){return new OTPFormAuthenticator(){
        @Override public boolean configuredFor(KeycloakSession s,RealmModel r,UserModel u){return true;}
        @Override public void authenticate(AuthenticationFlowContext c){
            if(!super.configuredFor(c.getSession(),c.getRealm(),c.getUser())){
                c.getUser().addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
                c.getUser().addRequiredAction(UserModel.RequiredAction.CONFIGURE_RECOVERY_AUTHN_CODES);
                c.success();
            }else super.authenticate(c);
        }
    };}
}
