package com.queryflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Component
@Slf4j
public class DatabaseConnectionLogger implements CommandLineRunner {

    @Autowired
    private DataSource dataSource;

    @Override
    public void run(String... args) {
        log.info("Checking database connection...");
        try (Connection connection = dataSource.getConnection()) {
            log.info("Database connection established successfully to database: {} (version: {})", 
                connection.getMetaData().getDatabaseProductName(),
                connection.getMetaData().getDatabaseProductVersion());
        } catch (SQLException e) {
            log.error("Failed to connect to the database: {}", e.getMessage());
        }
    }
}
