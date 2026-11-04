package com.harshith.resumematcher.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling // retention job and rate-limit cleanup
public class AppConfig {

    /** One clock for time-based rules, so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
