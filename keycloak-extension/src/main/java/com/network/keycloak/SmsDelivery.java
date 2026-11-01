package com.network.keycloak;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

final class SmsDelivery {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    static void send(String phone,String code) {
        String mode=System.getenv().getOrDefault("SMS_MODE","disabled");
        HttpRequest request;
        if("test".equals(mode) && "development".equals(System.getenv("PLATFORM_ENV"))) {
            request=HttpRequest.newBuilder(URI.create(required("SMS_TEST_URL"))).header("Authorization","Bearer "+required("SMS_TEST_TOKEN"))
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("phone="+enc(phone)+"&code="+enc(code))).timeout(Duration.ofSeconds(5)).build();
        } else if("twilio".equals(mode)) {
            String sid=required("TWILIO_ACCOUNT_SID");
            if(!sid.matches("AC[a-fA-F0-9]{32}")) throw new IllegalStateException("Invalid SMS configuration");
            request=HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/"+sid+"/Messages.json"))
                .header("Authorization","Basic "+Base64.getEncoder().encodeToString((sid+":"+required("TWILIO_AUTH_TOKEN")).getBytes(StandardCharsets.UTF_8)))
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("To="+enc(phone)+"&From="+enc(required("TWILIO_FROM"))+"&Body="+enc("Your Network verification code is "+code+". Expires in 5 minutes.")))
                .timeout(Duration.ofSeconds(5)).build();
        } else throw new IllegalStateException("SMS delivery is not configured");
        try {
            int status=HTTP.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
            if(status<200 || status>=300) throw new IllegalStateException("SMS delivery unavailable");
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("SMS delivery interrupted"); }
        catch(java.io.IOException e) { throw new IllegalStateException("SMS delivery unavailable"); }
    }
    private static String enc(String s) { return URLEncoder.encode(s,StandardCharsets.UTF_8); }
    private static String required(String key) { String value=System.getenv(key); if(value==null || value.isBlank()) throw new IllegalStateException("SMS configuration is incomplete"); return value; }
}
