package com.vbs.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // Required for InterestScheduler @Scheduled to work
public class VbsApplication {

    public static void main(String[] args) {
        SpringApplication.run(VbsApplication.class, args);
    }
}