package com.juyan.barracks.supply;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = {"com.juyan.barracks.common.entity", "com.juyan.barracks.supply"})
@EnableJpaRepositories(basePackages = {"com.juyan.barracks.common.repository"})
@EnableScheduling
public class SupplyAprioriApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplyAprioriApplication.class, args);
    }
}
