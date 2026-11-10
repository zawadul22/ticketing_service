package com.example.ticketing_service_backend.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.admin")
public record AdminProperties(boolean enabled, String email, String password) {
}
