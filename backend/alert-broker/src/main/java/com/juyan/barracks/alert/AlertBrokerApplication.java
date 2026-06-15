package com.juyan.barracks.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = "com.juyan.barracks.common.entity")
@EnableJpaRepositories(basePackages = "com.juyan.barracks.common.repository")
public class AlertBrokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertBrokerApplication.class, args);
    }
}
