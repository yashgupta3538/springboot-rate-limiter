package com.example.rate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import lombok.Data;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

@Component
@Data
@ConfigurationProperties(prefix = "spring.redis")
public class RedisProperties {
    private String host = "localhost";
    private int port = 6379;
    private int timeout = 2000;

    // Java client library for redis
    // let java application communicate with redis
    // Jedis pool is connection pool which keep multiple connections ready to reuse
    @Bean
    public JedisPool getJedisPool() {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(50); // max number connections in pool
        config.setMaxIdle(10); // max idle connections or ready connection 
        config.setMinIdle(5); // min connections to be idle or ready
        config.setTestOnBorrow(true); // test the connection before giving to use
        config.setTestOnReturn(true); // test the connection when returning to pool
        return new JedisPool(config, host, port, timeout);

    }
}
