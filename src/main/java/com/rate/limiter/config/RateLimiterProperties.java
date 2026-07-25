package com.example.rate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {
    private int capacity = 10;
    private long refillRate = 5;

    private String apiServerUrl = "http://localhost:8080";

    private int timeOut = 5000;

}