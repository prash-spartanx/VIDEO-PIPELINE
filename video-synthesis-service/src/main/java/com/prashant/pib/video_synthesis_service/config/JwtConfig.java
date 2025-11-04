package com.prashant.pib.video_synthesis_service.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;

@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Bean
    public SecretKey secretKey() {
        // Validate secret length for HS512 (minimum 512 bits = 64 bytes)
        if (jwtSecret.getBytes().length < 64) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 64 bytes (512 bits) for HS512. " +
                            "Current length: " + jwtSecret.getBytes().length
            );
        }
        return Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }
}