package com.queryflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class QueryFlowApplication {

    public static void main(String[] args) {
        log.info("Starting QueryFlow Application...");
        SpringApplication.run(QueryFlowApplication.class, args);
        log.info("QueryFlow Application started successfully.");
    }
}
