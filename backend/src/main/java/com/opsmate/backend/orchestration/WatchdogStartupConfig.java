package com.opsmate.backend.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Triggers watchdog restart recovery on application startup.
 */
@Configuration
public class WatchdogStartupConfig {

    private static final Logger log = LoggerFactory.getLogger(WatchdogStartupConfig.class);

    @Bean
    public ApplicationRunner watchdogStartupRunner(Watchdog watchdog) {
        return args -> {
            log.info("Running watchdog restart recovery...");
            watchdog.onStartup();
            log.info("Watchdog restart recovery completed");
        };
    }
}