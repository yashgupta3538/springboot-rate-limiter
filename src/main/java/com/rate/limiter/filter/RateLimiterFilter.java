package com.rate.limiter.filter;

import java.nio.charset.StandardCharsets;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;

import com.rate.limiter.service.RateLimiterService;

import reactor.core.publisher.Mono;

@Component
public class RateLimiterFilter extends AbstractGatewayFilterFactory<RateLimiterFilter.Config> {

    private final RateLimiterService rateLimiterService;

    public RateLimiterFilter(RateLimiterService rateLimiterService) {
        super(Config.class);
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public RateLimiterFilter.Config newConfig() {
        return new Config();
    }

    public static class Config {

    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            String clientId = getClientId(request);
            if (!rateLimiterService.isAllowed(clientId)) {
                response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                addRateLimitHeaders(response, clientId);
                String errorBody = String.format(
                        "{\"error\":\"Rate limit Exceeded\",\"clientId\":\"%s\"}",
                        clientId);
                return response.writeWith(
                        Mono.just(response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8))));
            }
            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                addRateLimitHeaders(response, clientId);
            }));
        };
    }

    public String getClientId(ServerHttpRequest request) {
        String xForwardFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardFor != null && !xForwardFor.isEmpty()) {
            return xForwardFor.split(",")[0].trim();
        }

        // fallback to direct connection IP
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getHostName() != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        // default fallback
        return "Unknown";
    }

    private void addRateLimitHeaders(ServerHttpResponse response, String clientId) {
        response.getHeaders().add("X-RateLimit-Limit", String.valueOf(rateLimiterService.getCapacity(clientId)));
        response.getHeaders().add("X-RateLimit-Remaining",
                String.valueOf(rateLimiterService.getAvailableTokens(clientId)));
    }

}
