package com.docsearch.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class TimeConfig {

    /**
     * Injected wherever timestamps are produced, so tests can fix "now" instead of
     * asserting loosely against the wall clock.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
