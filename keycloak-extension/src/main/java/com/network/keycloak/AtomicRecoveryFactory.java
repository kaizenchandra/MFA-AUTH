package com.network.keycloak;
import org.keycloak.credential.*;
import org.keycloak.models.*;
import org.keycloak.models.credential.RecoveryAuthnCodesCredentialModel;
import org.keycloak.models.utils.RecoveryAuthnCodesUtils;
/** Native hashing and ordered codes, with a cluster-wide reservation before consumption. */
public final class AtomicRecoveryFactory extends RecoveryAuthnCodesCredentialProviderFactory {
    @Override public int order() { return 100; }
    @Override public RecoveryAuthnCodesCredentialProvider create(KeycloakSession session) {
        return new RecoveryAuthnCodesCredentialProvider(session) {
            @Override public boolean isValid(RealmModel realm,UserModel user,CredentialInput input) {
                var stored=user.credentialManager().getStoredCredentialsByTypeStream(getType()).findFirst();
                if(stored.isEmpty() || input.getChallengeResponse()==null) return false;
                var credential=RecoveryAuthnCodesCredentialModel.createFromCredentialModel(stored.get());
                var next=credential.getNextRecoveryAuthnCode();
                if(next.isEmpty() || !RecoveryAuthnCodesUtils.verifyRecoveryCodeInput(input.getChallengeResponse(),next.get().getEncodedHashedValue())) return false;
                String key="network:recovery:"+stored.get().getId()+":"+PhoneChallenge.hash(input.getChallengeResponse());
                if(!session.singleUseObjects().putIfAbsent(key,86400)) return false;
                return super.isValid(realm,user,input);
            }
        };
    }
}
