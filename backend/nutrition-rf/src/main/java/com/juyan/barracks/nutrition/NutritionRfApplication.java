package com.juyan.barracks.nutrition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableJpaRepositories(basePackages = "com.juyan.barracks.common.repository")
@EntityScan(basePackages = "com.juyan.barracks.common.entity")
public class NutritionRfApplication {

    public static void main(String[] args) {
        SpringApplication.run(NutritionRfApplication.class, args);
    }
}
