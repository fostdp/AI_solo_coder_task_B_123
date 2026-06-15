FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /build
COPY pom.xml ./pom.xml
COPY common/ ./common/
COPY mqtt-ingest/ ./mqtt-ingest/
COPY nutrition-rf/ ./nutrition-rf/
COPY outbreak-satscan/ ./outbreak-satscan/
COPY alert-broker/ ./alert-broker/
RUN --mount=type=cache,target=/root/.m2 \
    cd /build && mvn clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=builder /build/mqtt-ingest/target/*.jar mqtt-ingest.jar
COPY --from=builder /build/nutrition-rf/target/*.jar nutrition-rf.jar
COPY --from=builder /build/outbreak-satscan/target/*.jar outbreak-satscan.jar
COPY --from=builder /build/alert-broker/target/*.jar alert-broker.jar

COPY start-all.sh ./start-all.sh
RUN chmod +x ./start-all.sh

EXPOSE 8080 8081 8082 8083

ENTRYPOINT ["./start-all.sh"]
