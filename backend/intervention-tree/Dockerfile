FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY common/ ./common/
COPY intervention-tree/ ./intervention-tree/
RUN --mount=type=cache,target=/root/.m2 \
    cd /build && mvn -pl intervention-tree -am clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=builder /build/intervention-tree/target/*.jar app.jar
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
