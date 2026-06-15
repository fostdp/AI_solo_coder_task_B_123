package com.juyan.barracks.ingest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.juyan.barracks.common.repository")
@EntityScan(basePackages = "com.juyan.barracks.common.entity")
public class MqttIngestApplication {

    public static void main(String[] args) {
        SpringApplication.run(MqttIngestApplication.class, args);
    }
}
