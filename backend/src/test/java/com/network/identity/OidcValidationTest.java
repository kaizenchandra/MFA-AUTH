package com.network.identity;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.junit.jupiter.api.Assertions.*;

/** The same Spring OIDC ID-token decoder used by oauth2Login, with controlled signing keys. */
class OidcValidationTest {
    static HttpServer server;static RSAKey key;static String issuer;static JwtDecoder decoder;
    @BeforeAll static void setup() throws Exception {
        key=new RSAKeyGenerator(2048).keyID("test-key").generate();
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/realms/network/protocol/openid-connect/certs",exchange->{byte[] json=new JWKSet(key.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,json.length);exchange.getResponseBody().write(json);exchange.close();});
        server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();issuer=base+"/realms/network";
        var registration=new SecurityConfig().registrations(issuer,base,"https://app.test","test-only").findByRegistrationId("keycloak");
        decoder=new OidcIdTokenDecoderFactory().createDecoder(registration);
    }
    @AfterAll static void stop(){server.stop(0);}
    static String token(String iss,String audience,Instant expires,RSAKey signingKey) throws Exception {
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(),new JWTClaimsSet.Builder().issuer(iss).subject("test-user").audience(audience).issueTime(Date.from(Instant.now().minusSeconds(2))).expirationTime(Date.from(expires)).claim("nonce","test-nonce").build());
        jwt.sign(new RSASSASigner(signingKey));return jwt.serialize();
    }
    @Test void validToken() throws Exception{assertEquals("test-user",decoder.decode(token(issuer,"network-bff",Instant.now().plusSeconds(60),key)).getSubject());}
    @Test void wrongIssuer() throws Exception{String jwt=token("https://evil.test","network-bff",Instant.now().plusSeconds(60),key);assertThrows(JwtException.class,()->decoder.decode(jwt));}
    @Test void wrongAudience() throws Exception{String jwt=token(issuer,"another-client",Instant.now().plusSeconds(60),key);assertThrows(JwtException.class,()->decoder.decode(jwt));}
    @Test void expired() throws Exception{String jwt=token(issuer,"network-bff",Instant.now().minusSeconds(120),key);assertThrows(JwtException.class,()->decoder.decode(jwt));}
    @Test void wrongSignature() throws Exception{String jwt=token(issuer,"network-bff",Instant.now().plusSeconds(60),new RSAKeyGenerator(2048).generate());assertThrows(JwtException.class,()->decoder.decode(jwt));}
}
