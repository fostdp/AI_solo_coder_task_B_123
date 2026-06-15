FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY common/ ./common/
COPY supply-apriori/ ./supply-apriori/
RUN --mount=type=cache,target=/root/.m2 \
    cd /build && mvn -pl supply-apriori -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /build/supply-apriori/target/*.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]
