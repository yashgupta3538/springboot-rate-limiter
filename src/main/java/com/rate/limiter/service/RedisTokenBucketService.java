package com.rate.limiter.service;

import org.springframework.stereotype.Service;

import com.rate.limiter.config.RateLimiterProperties;

import lombok.RequiredArgsConstructor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

// store token bucket state in redis 
// manage token per client
// handle token refill based on time
// provide rate limiting logic

@Service
@RequiredArgsConstructor
public class RedisTokenBucketService {

    private final JedisPool jedisPool;
    private final RateLimiterProperties rateLimiterProperties;
    private static final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private static final String LAST_REFILL_KEY_PRIFIX = "rate_limiter:last_refill:";

    // public RedisTokenBucketService (JedisPool jedisPool, RateLimiterProperties rateLimiterProperties) {
    //     this.jedisPool = jedisPool;
    //     this.rateLimiterProperties = rateLimiterProperties;
    // } 

    // Pattern
    // rate_limiter:{type}:{clientID}

    public boolean isAllowed(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        try (Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenKey);
            long currToken = tokenStr != null ? Long.parseLong(tokenStr) : rateLimiterProperties.getCapacity();
            if (currToken <= 0) {
                return false;
            }
            long decrement = jedis.decr(tokenKey);
            return decrement >= 0;
        }
    }

    public long getCapacity(String clientId) {
        return rateLimiterProperties.getCapacity();
    }

    public long getAvailableTokens(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        try (Jedis jedis = jedisPool.getResource()) {
            refillTokens(clientId, jedis);
            String tokenStr = jedis.get(tokenKey);
            return tokenStr != null ? Long.parseLong(tokenStr) : rateLimiterProperties.getCapacity();
        }
    }

    public void refillTokens(String clientId, Jedis jedis) {
        String tokensKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PRIFIX + clientId;
        long now = System.currentTimeMillis();
        String lastRefillStr = jedis.get(lastRefillKey);
        if (lastRefillStr == null) {
            jedis.set(tokensKey, String.valueOf(rateLimiterProperties.getCapacity()));
            jedis.set(lastRefillKey, String.valueOf(now));
            return;
        }
        long lastRefillTime = Long.parseLong(lastRefillStr);
        long elapsedTime = now - lastRefillTime;
        if (elapsedTime <= 0)
            return;
        long tokenToAdd = (elapsedTime * rateLimiterProperties.getRefillRate()) / 1000;
        if (tokenToAdd <= 0)
            return;
        String tokenStr = jedis.get(tokensKey);
        long currTokens = tokenStr != null ? Long.parseLong(tokenStr) : rateLimiterProperties.getCapacity();
        long newTokens = Math.min(currTokens + tokenToAdd, rateLimiterProperties.getCapacity());
        jedis.set(tokensKey, String.valueOf(newTokens));
        jedis.set(lastRefillKey, String.valueOf(now));
    }
}
