package com.network.keycloak;

import org.keycloak.authentication.*;
import org.keycloak.authentication.authenticators.browser.OTPFormAuthenticator;
import org.keycloak.authentication.authenticators.browser.OTPFormAuthenticatorFactory;
import org.keycloak.authentication.authenticators.broker.util.SerializedBrokeredIdentityContext;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.models.*;
import org.keycloak.representations.JsonWebToken;

/** Trust only the broker's already-validated OIDC token and operator-pinned issuer/ACR. */
public final class BrokerOtpFactory extends OTPFormAuthenticatorFactory {
    @Override public String getId(){return "network-broker-otp";}
    @Override public String getDisplayType(){return "Network broker assurance or OTP";}
    @Override public Authenticator create(KeycloakSession session){return new OTPFormAuthenticator(){
        private boolean trusted(AuthenticationFlowContext c){
            var serialized=SerializedBrokeredIdentityContext.readFromAuthenticationSession(c.getAuthenticationSession(),"PBL_BROKERED_IDENTITY_CONTEXT");
            if(serialized==null){c.getEvent().detail("external_mfa","context_missing");return false;}
            var broker=serialized.deserialize(c.getSession(),c.getAuthenticationSession());
            var config=broker.getIdpConfig().getConfig();
            String acr=config.get("network.trusted.acr"),issuer=config.get("issuer");
            if(acr==null || acr.isBlank() || issuer==null){c.getEvent().detail("external_mfa","not_configured");return false;}
            Object raw=broker.getContextData().get(OIDCIdentityProvider.VALIDATED_ID_TOKEN);
            if(!(raw instanceof JsonWebToken token) || !issuer.equals(token.getIssuer())){c.getEvent().detail("external_mfa","issuer_or_token_mismatch");return false;}
            String tenant=config.get("network.tenant.id");
            if(tenant!=null && !tenant.equals(token.getOtherClaims().get("tid"))){c.getEvent().detail("external_mfa","tenant_mismatch");return false;}
            // AMR is not trusted by name alone; a provider-specific ACR contract is mandatory.
            boolean accepted=acr.equals(token.getOtherClaims().get("acr"));
            c.getEvent().detail("external_mfa",accepted?"accepted":"acr_mismatch");
            return accepted;
        }
        @Override public void authenticate(AuthenticationFlowContext c){
            if(trusted(c)){
                c.getAuthenticationSession().setAuthNote("network_external_mfa","true");
                c.getAuthenticationSession().setUserSessionNote("platform_mfa","external");
                c.getUser().removeRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
                c.getUser().removeRequiredAction(UserModel.RequiredAction.CONFIGURE_RECOVERY_AUTHN_CODES);
                c.getAuthenticationSession().removeRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
                c.getAuthenticationSession().removeRequiredAction(UserModel.RequiredAction.CONFIGURE_RECOVERY_AUTHN_CODES);
                c.success();
            }else if(!super.configuredFor(c.getSession(),c.getRealm(),c.getUser())){
                c.getUser().addRequiredAction(UserModel.RequiredAction.CONFIGURE_TOTP);
                c.getUser().addRequiredAction(UserModel.RequiredAction.CONFIGURE_RECOVERY_AUTHN_CODES);
                c.success();
            }else{super.authenticate(c);}
        }
        @Override public boolean configuredFor(KeycloakSession s,RealmModel r,UserModel u){
            var auth=s.getContext().getAuthenticationSession();
            if(auth!=null){
                var serialized=SerializedBrokeredIdentityContext.readFromAuthenticationSession(auth,"PBL_BROKERED_IDENTITY_CONTEXT");
                if(serialized!=null){var broker=serialized.deserialize(s,auth);if(broker.getIdpConfig().getConfig().containsKey("network.trusted.acr"))return true;}
            }
            return true;
        }
    };}
}
