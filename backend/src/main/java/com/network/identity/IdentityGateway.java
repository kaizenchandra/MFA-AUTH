package com.network.identity;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** All credentials remain inside the BFF. No admin token is returned to the browser. */
@Component
class IdentityGateway {
    private final RestClient http;
    private final String adminSecret, clientSecret;
    IdentityGateway(@Value("${platform.idp-internal}") String base, @Value("${platform.admin-secret}") String adminSecret,
                    @Value("${platform.client-secret}") String clientSecret) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().baseUrl(base).requestFactory(factory).build();
        this.adminSecret=adminSecret; this.clientSecret=clientSecret;
    }
    private String adminToken;
    private long tokenExpires;
    private synchronized String adminToken() {
        if (adminToken != null && System.currentTimeMillis() < tokenExpires) return adminToken;
        var form = new LinkedMultiValueMap<String,String>();
        form.set("grant_type","client_credentials"); form.set("client_id","network-admin"); form.set("client_secret",adminSecret);
        Map<?,?> response=http.post().uri("/realms/network/protocol/openid-connect/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        adminToken=(String)Objects.requireNonNull(response).get("access_token"); tokenExpires=System.currentTimeMillis()+20000;
        return adminToken;
    }
    boolean active(String token) {
        var form = new LinkedMultiValueMap<String,String>(); form.set("token",token);
        var result=http.post().uri("/realms/network/protocol/openid-connect/token/introspect")
            .headers(h -> h.setBasicAuth("network-bff",clientSecret)).contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form).retrieve().body(Map.class);
        return result != null && Boolean.TRUE.equals(result.get("active"));
    }
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> sessions(String subject) {
        var result=http.get().uri("/admin/realms/network/users/{id}/sessions",subject).headers(h -> h.setBearerAuth(adminToken())).retrieve().body(List.class);
        return result == null ? List.of() : (List<Map<String,Object>>)result;
    }
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> credentials(String subject) {
        var list=http.get().uri("/admin/realms/network/users/{id}/credentials",subject).headers(h -> h.setBearerAuth(adminToken())).retrieve().body(List.class);
        if (list==null) return List.of();
        return ((List<Map<String,Object>>)list).stream().map(c -> Map.<String,Object>of("id",c.get("id"),"type",c.get("type"),"label",Objects.toString(c.get("userLabel"),"Authenticator"))).toList();
    }
    void revoke(String subject, String sessionId) {
        if (sessions(subject).stream().noneMatch(s -> sessionId.equals(s.get("id")))) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        http.delete().uri("/admin/realms/network/sessions/{id}",sessionId).headers(h -> h.setBearerAuth(adminToken())).retrieve().toBodilessEntity();
    }
    void logoutAll(String subject) {
        http.post().uri("/admin/realms/network/users/{id}/logout",subject).headers(h -> h.setBearerAuth(adminToken())).retrieve().toBodilessEntity();
    }
    void removeOtp(String subject, String credentialId) {
        if (credentials(subject).stream().noneMatch(c -> credentialId.equals(c.get("id")) && "otp".equals(c.get("type"))))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        http.delete().uri("/admin/realms/network/users/{id}/credentials/{credential}",subject,credentialId).headers(h -> h.setBearerAuth(adminToken())).retrieve().toBodilessEntity();
    }
}
