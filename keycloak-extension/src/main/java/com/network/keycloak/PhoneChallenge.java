package com.network.keycloak;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.keycloak.models.SingleUseObjectProvider;

/** Delivery codes only. TOTP and password verification are exclusively Keycloak native. */
final class PhoneChallenge {
    static final int TTL=300, ATTEMPTS=5;
    private static final SecureRandom RANDOM=new SecureRandom();
    record Issued(String id,String code) {}
    private final SingleUseObjectProvider store;
    PhoneChallenge(SingleUseObjectProvider store) { this.store=store; }
    Issued issue(String phone,String subject) {
        if(!phone.matches("\\+[1-9][0-9]{7,14}")) throw new IllegalArgumentException("Use E.164 format, for example +14155550123");
        String key="network:sms:"+hash(phone);
        if(!store.putIfAbsent(key+":cooldown",60)) throw new IllegalStateException("Wait 60 seconds before sending again");
        String hour=Long.toString(System.currentTimeMillis()/3600000);
        if(!reserve(key+":"+hour,10,3600)) throw new IllegalStateException("Delivery limit reached. Try again later");
        String id=UUID.randomUUID().toString(),salt=UUID.randomUUID().toString(),code=String.format(Locale.ROOT,"%06d",RANDOM.nextInt(1000000));
        store.put(id,TTL,Map.of("hash",hash(salt+code),"salt",salt,"subject",subject,"phone",phone));
        return new Issued(id,code);
    }
    boolean verify(String id,String subject,String phone,String code) {
        if(id==null || code==null || !code.matches("[0-9]{6}")) return false;
        if(!reserve(id+":attempt",ATTEMPTS,TTL)) return false;
        Map<String,String> value=store.get(id);
        if(value==null || !subject.equals(value.get("subject")) || !phone.equals(value.get("phone"))) return false;
        if(!MessageDigest.isEqual(value.get("hash").getBytes(StandardCharsets.US_ASCII),hash(value.get("salt")+code).getBytes(StandardCharsets.US_ASCII))) return false;
        return store.remove(id)!=null; // Atomic, cluster-wide consumption.
    }
    private boolean reserve(String prefix,int max,int seconds) {
        for(int i=0;i<max;i++) if(store.putIfAbsent(prefix+":"+i,seconds)) return true;
        return false;
    }
    static String hash(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
