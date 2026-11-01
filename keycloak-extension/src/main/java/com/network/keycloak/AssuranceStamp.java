package com.network.keycloak;

import java.util.List;
import org.keycloak.Config;
import org.keycloak.authentication.*;
import org.keycloak.models.*;
import org.keycloak.provider.ProviderConfigProperty;

/** Run only AFTER the required MFA subflow. Required actions finish before any code is issued. */
public final class AssuranceStamp implements Authenticator,AuthenticatorFactory {
    public void authenticate(AuthenticationFlowContext c) { c.getAuthenticationSession().setUserSessionNote("platform_mfa","true".equals(c.getAuthenticationSession().getAuthNote("network_external_mfa"))?"external":"local");c.success(); }
    public void action(AuthenticationFlowContext c) { authenticate(c); }
    public boolean requiresUser() { return true; }
    public boolean configuredFor(KeycloakSession s,RealmModel r,UserModel u) { return true; }
    public void setRequiredActions(KeycloakSession s,RealmModel r,UserModel u) {}
    public Authenticator create(KeycloakSession s) { return this; }
    public String getId() { return "platform-assurance-stamp"; }
    public String getDisplayType() { return "Network verified MFA assurance"; }
    public String getReferenceCategory() { return "assurance"; }
    public boolean isConfigurable() { return false; }
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() { return new AuthenticationExecutionModel.Requirement[]{AuthenticationExecutionModel.Requirement.REQUIRED}; }
    public boolean isUserSetupAllowed() { return false; }
    public String getHelpText() { return "Must follow a REQUIRED MFA subflow; never add to a password-only flow."; }
    public List<ProviderConfigProperty> getConfigProperties() { return List.of(); }
    public void init(Config.Scope c) {}
    public void postInit(KeycloakSessionFactory f) {}
    public void close() {}
}
