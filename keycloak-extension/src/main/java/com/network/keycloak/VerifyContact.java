package com.network.keycloak;

import org.keycloak.Config;
import org.keycloak.authentication.*;
import org.keycloak.models.*;

/** Pending phone is user-editable; verified_phone is admin-only and never set from request data. */
public final class VerifyContact implements RequiredActionProvider,RequiredActionFactory {
    public static final String ID="VERIFY_CONTACT";
    public String getId() { return ID; }
    public String getDisplayText() { return "Verify email or mobile"; }
    public RequiredActionProvider create(KeycloakSession session) { return this; }
    public void init(Config.Scope c) {}
    public void postInit(KeycloakSessionFactory f) {}
    public void close() {}
    public InitiatedActionSupport initiatedActionSupport() { return InitiatedActionSupport.SUPPORTED; }
    public void evaluateTriggers(RequiredActionContext c) {
        UserModel u=c.getUser();
        if(u.getEmail()!=null && !u.isEmailVerified()) u.addRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
        String phone=phone(u);
        if(phone!=null && !phone.equals(u.getFirstAttribute("verified_phone"))) u.addRequiredAction(ID);
        if(phone==null && u.getEmail()==null) u.addRequiredAction(ID);
    }
    static String phone(UserModel u) {
        if(u.getUsername()!=null && u.getUsername().startsWith("+")) return u.getUsername();
        String phone=u.getFirstAttribute("phone");return phone==null || phone.isBlank()?null:phone;
    }
    public void requiredActionChallenge(RequiredActionContext c) {
        if(phone(c.getUser())==null && c.getUser().getEmail()!=null) {
            if(!c.getUser().isEmailVerified()) c.getUser().addRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
            c.success();return;
        }
        render(c,null);
    }
    private void render(RequiredActionContext c,String error) {
        String sentAt=c.getAuthenticationSession().getAuthNote("phone_sent_at");
        long remaining=sentAt==null?0:Math.max(0,60-(System.currentTimeMillis()-Long.parseLong(sentAt))/1000);
        var form=c.form().setAttribute("phone",phone(c.getUser())).setAttribute("sent",c.getAuthenticationSession().getAuthNote("phone_challenge")!=null).setAttribute("resendSeconds",remaining);
        if(error!=null) form.setError(error);
        c.challenge(form.createForm("verify-phone.ftl"));
    }
    public void processAction(RequiredActionContext c) {
        var data=c.getHttpRequest().getDecodedFormParameters();
        String phone=phone(c.getUser());
        if(phone==null) {
            String supplied=data.getFirst("phone");
            if(supplied==null || !supplied.matches("\\+[1-9][0-9]{7,14}")){render(c,"Enter a mobile number with its +country calling code");return;}
            c.getUser().setSingleAttribute("phone",supplied);phone=supplied;
        }
        var challenges=new PhoneChallenge(c.getSession().singleUseObjects());
        if("send".equals(data.getFirst("operation"))) {
            try {
                var issued=challenges.issue(phone,c.getUser().getId());
                SmsDelivery.send(phone,issued.code());
                String old=c.getAuthenticationSession().getAuthNote("phone_challenge");
                if(old!=null) c.getSession().singleUseObjects().remove(old);
                c.getAuthenticationSession().setAuthNote("phone_challenge",issued.id());
                c.getAuthenticationSession().setAuthNote("phone_sent_at",Long.toString(System.currentTimeMillis()));
                render(c,null);
            } catch(IllegalArgumentException | IllegalStateException e) { render(c,e.getMessage()); }
            return;
        }
        String id=c.getAuthenticationSession().getAuthNote("phone_challenge");
        if(!challenges.verify(id,c.getUser().getId(),phone,data.getFirst("code"))) { render(c,"Invalid or expired code. Request another after 60 seconds if needed.");return; }
        c.getUser().setSingleAttribute("verified_phone",phone);
        c.getUser().setSingleAttribute("phone_verified","true");
        c.getAuthenticationSession().removeAuthNote("phone_challenge");
        c.getEvent().detail("contact_verification","phone"); c.success();
    }
}
