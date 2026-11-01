FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /build
COPY backend/pom.xml pom.xml
COPY backend/src src
RUN mvn -B -ntp package -DskipTests
FROM eclipse-temurin:21.0.9_10-jre-alpine
RUN addgroup -g 10001 app && adduser -D -u 10001 -G app app
WORKDIR /app
COPY --from=builder /build/target/backend-1.0.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8080 9090
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
