package com.example.ticketing_service_backend.configuration;

import com.example.ticketing_service_backend.service.AdminSetupService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminSetupConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true")
    ApplicationRunner initializeAdmin(AdminSetupService service, AdminProperties properties) {
        return arguments -> service.initialize(properties);
    }
}
