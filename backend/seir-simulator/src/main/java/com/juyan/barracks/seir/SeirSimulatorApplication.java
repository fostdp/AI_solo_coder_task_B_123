package com.juyan.barracks.seir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableJpaRepositories(basePackages = "com.juyan.barracks.common.repository")
@EntityScan(basePackages = "com.juyan.barracks.common.entity")
public class SeirSimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeirSimulatorApplication.class, args);
    }
}
