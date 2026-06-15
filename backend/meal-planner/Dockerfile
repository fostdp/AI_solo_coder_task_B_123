FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY common/ ./common/
COPY meal-planner/ ./meal-planner/
RUN --mount=type=cache,target=/root/.m2 \
    cd /build && mvn -pl meal-planner -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /build/meal-planner/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
