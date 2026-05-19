package com.atomquest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GoalPortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(GoalPortalApplication.class, args);
    }
}
