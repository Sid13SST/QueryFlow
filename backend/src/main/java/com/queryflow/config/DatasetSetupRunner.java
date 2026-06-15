package com.queryflow.config;

import com.queryflow.service.DatasetLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DatasetSetupRunner implements CommandLineRunner {

    @Autowired
    private DatasetLoaderService datasetLoaderService;

    @Value("${queryflow.dataset.load-on-startup:true}")
    private boolean loadOnStartup;

    @Override
    public void run(String... args) {
        if (loadOnStartup) {
            log.info("Dataset loading on startup is enabled. Starting load process...");
            try {
                datasetLoaderService.loadDataset();
            } catch (Exception e) {
                log.error("Exception occurred during startup dataset loading: {}", e.getMessage(), e);
            }
        } else {
            log.info("Dataset loading on startup is disabled.");
        }
    }
}
