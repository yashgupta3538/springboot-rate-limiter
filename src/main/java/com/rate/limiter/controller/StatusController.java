package com.rate.limiter.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.rate.limiter.service.RateLimiterService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/status")
public class StatusController {

    private final RateLimiterService rateLimiterService;

    public StatusController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> getHealth() {
        return Mono.just(ResponseEntity.ok(Map.of("status", "UP", "service", "rate-limiting-gateway")));
    }

    @GetMapping("/rate-limit")
    public Mono<ResponseEntity<Map<String, Object>>> getRateLimitStatus(ServerWebExchange exchange) {

        String clientId = getClientId(exchange);

        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "rate-limiting-gateway",
                "capacity", rateLimiterService.getCapacity(clientId),
                "availableTokens", rateLimiterService.getAvailableTokens(clientId))));
    }

    private String getClientId(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

}
