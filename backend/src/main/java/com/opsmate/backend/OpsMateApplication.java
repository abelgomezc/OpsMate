package com.opsmate.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class OpsMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpsMateApplication.class, args);
    }
}