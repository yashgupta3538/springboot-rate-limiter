package com.rate.limiter.service;

import org.springframework.stereotype.Service;
import com.rate.limiter.config.RateLimiterProperties;
import lombok.RequiredArgsConstructor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisTokenBucketService {

    private final JedisPool jedisPool;
    private final RateLimiterProperties rateLimiterProperties;

    private static final String TOKENS_KEY_PREFIX = "rate_limiter:tokens:";
    private static final String LAST_REFILL_KEY_PREFIX = "rate_limiter:last_refill:";

    // Atomic Lua Script running entirely inside the single-threaded Redis core
    private static final String LUA_SCRIPT = "local tokensKey = KEYS[1] " +
            "local refillKey = KEYS[2] " +
            "local capacity = tonumber(ARGV[1]) " +
            "local refillRate = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +

            // 1. Fetch current metrics
            "local lastRefill = tonumber(redis.call('get', refillKey)) " +
            "local currTokens = tonumber(redis.call('get', tokensKey)) " +

            // 2. Initialize bucket if brand new client
            "if not lastRefill then " +
            "    currTokens = capacity " +
            "    lastRefill = now " +
            "else " +
            // 3. Compute continuous execution time differences
            "    local elapsedTime = now - lastRefill " +
            "    if elapsedTime > 0 then " +
            "        local tokensToAdd = (elapsedTime * refillRate) / 1000 " +
            "        currTokens = math.min(capacity, currTokens + tokensToAdd) " +
            "        lastRefill = now " +
            "    end " +
            "end " +

            // 4. Enforce structural boundaries securely
            "if currTokens < 1 then " +
            "    redis.call('set', tokensKey, currTokens) " +
            "    redis.call('set', refillKey, lastRefill) " +
            "    return 0 " + // BLOCKED
            "else " +
            "    redis.call('set', tokensKey, currTokens - 1) " +
            "    redis.call('set', refillKey, lastRefill) " +
            "    return 1 " + // ALLOWED
            "end";

    public boolean isAllowed(String clientId) {
        String tokensKey = TOKENS_KEY_PREFIX + clientId;
        String lastRefillKey = LAST_REFILL_KEY_PREFIX + clientId;

        try (Jedis jedis = jedisPool.getResource()) {
            // Send keys and arguments directly to Lua engine
            Object result = jedis.eval(LUA_SCRIPT,
                    List.of(tokensKey, lastRefillKey),
                    List.of(
                            String.valueOf(rateLimiterProperties.getCapacity()),
                            String.valueOf(rateLimiterProperties.getRefillRate()),
                            String.valueOf(System.currentTimeMillis())));
            return Long.parseLong(result.toString()) == 1;
        }
    }

    public long getCapacity(String clientId) {
        return rateLimiterProperties.getCapacity();
    }

    public long getAvailableTokens(String clientId) {
        String tokenKey = TOKENS_KEY_PREFIX + clientId;
        try (Jedis jedis = jedisPool.getResource()) {
            String tokenStr = jedis.get(tokenKey);
            return tokenStr != null ? Math.round(Double.parseDouble(tokenStr)) : rateLimiterProperties.getCapacity();
        }
    }
}