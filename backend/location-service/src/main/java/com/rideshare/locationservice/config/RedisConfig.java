package com.rideshare.locationservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Lua scripts run atomically inside Redis, so "check the driver is online and free, then mark it busy"
 * cannot interleave with another matcher doing the same thing for a different ride.
 * <p>
 * Spring Boot auto-configures the {@code StringRedisTemplate} used by {@code DriverLocationRepository}.
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<Long> reserveDriverScript() {
        return RedisScript.of(new ClassPathResource("scripts/reserve-driver.lua"), Long.class);
    }

    @Bean
    public RedisScript<Long> releaseDriverScript() {
        return RedisScript.of(new ClassPathResource("scripts/release-driver.lua"), Long.class);
    }
}
