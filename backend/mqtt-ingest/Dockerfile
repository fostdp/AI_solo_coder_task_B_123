FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY common/ ./common/
COPY mqtt-ingest/ ./mqtt-ingest/
RUN --mount=type=cache,target=/root/.m2 \
    cd /build && mvn -pl mqtt-ingest -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /build/mqtt-ingest/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
