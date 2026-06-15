FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY common/ ./common/
COPY nutrition-rf/ ./nutrition-rf/
RUN --mount=type=cache,target=/root/.m2 \
    cd /build && mvn -pl nutrition-rf -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /build/nutrition-rf/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
