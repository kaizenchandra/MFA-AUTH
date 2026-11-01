FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /build
COPY pom.xml pom.xml
COPY keycloak-extension keycloak-extension
RUN mvn -B -ntp -f keycloak-extension/pom.xml package -DskipTests
FROM quay.io/keycloak/keycloak:26.4.7
COPY --from=builder /build/keycloak-extension/target/keycloak-extension-1.0.0-SNAPSHOT.jar /opt/keycloak/providers/network.jar
RUN /opt/keycloak/bin/kc.sh build --db=postgres --health-enabled=true --metrics-enabled=true --features=recovery-codes
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
