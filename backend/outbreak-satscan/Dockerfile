FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY common/ ./common/
COPY outbreak-satscan/ ./outbreak-satscan/
RUN --mount=type=cache,target=/root/.m2 \
    cd /build && mvn -pl outbreak-satscan -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /build/outbreak-satscan/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
