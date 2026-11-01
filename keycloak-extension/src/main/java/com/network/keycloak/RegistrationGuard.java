package com.network.keycloak;
import java.util.*;
import org.keycloak.Config;
import org.keycloak.authentication.*;
import org.keycloak.forms.login.LoginFormsProvider;
import org.keycloak.models.*;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.ProviderConfigProperty;
public final class RegistrationGuard implements FormAction,FormActionFactory {
    public void buildPage(FormContext c,LoginFormsProvider form){}
    public void validate(ValidationContext c){
        var form=c.getHttpRequest().getDecodedFormParameters();
        String bucket="network:registration:"+PhoneChallenge.hash(c.getConnection().getRemoteAddr())+":"+System.currentTimeMillis()/3600000;
        boolean allowed=false;for(int i=0;i<20;i++)if(c.getSession().singleUseObjects().putIfAbsent(bucket+":"+i,3600)){allowed=true;break;}
        if(!allowed){c.validationError(form,List.of(new FormMessage(null,"Too many registration attempts. Try again later.")));return;}
        String username=Objects.toString(form.getFirst("username"),"").strip().toLowerCase(Locale.ROOT);
        String email=Objects.toString(form.getFirst("email"),"").strip().toLowerCase(Locale.ROOT);
        if(username.startsWith("+"))username=username.replaceAll("[ ()-]","");
        if(!username.matches("\\+[1-9][0-9]{7,14}") && !username.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")){
            c.validationError(form,List.of(new FormMessage("username","Use an email address or mobile number with +country code")));return;
        }
        if(username.contains("@")){if(!email.isEmpty()&&!email.equals(username)){c.validationError(form,List.of(new FormMessage("email","Use the same email address as your sign-in identifier")));return;}email=username;}
        form.putSingle("username",username);if(!email.isEmpty())form.putSingle("email",email);
        c.success();
    }
    public void success(FormContext c){}
    public boolean requiresUser(){return false;}
    public boolean configuredFor(KeycloakSession s,RealmModel r,UserModel u){return true;}
    public void setRequiredActions(KeycloakSession s,RealmModel r,UserModel u){}
    public FormAction create(KeycloakSession s){return this;}
    public String getId(){return "network-registration-guard";}
    public String getDisplayType(){return "Network identifier validation and registration limits";}
    public String getReferenceCategory(){return "registration";}
    public boolean isConfigurable(){return false;}
    public boolean isUserSetupAllowed(){return false;}
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices(){return new AuthenticationExecutionModel.Requirement[]{AuthenticationExecutionModel.Requirement.REQUIRED};}
    public String getHelpText(){return "Must be first in the registration form flow.";}
    public List<ProviderConfigProperty> getConfigProperties(){return List.of();}
    public void init(Config.Scope c){}
    public void postInit(KeycloakSessionFactory f){}
    public void close(){}
}
