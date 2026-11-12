package com.example.ticketing_service_backend.configuration;

import java.util.List;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Ticketing Service API", version = "v1"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {
    @Bean
    OpenApiCustomizer optionalEventListingAuthentication() {
        return api -> {
            var path = api.getPaths().get("/api/events");
            if (path != null && path.getGet() != null) {
                // Public by default; let Swagger send the authorized token for includePast=true.
                path.getGet().setSecurity(List.of(new SecurityRequirement(),
                        new SecurityRequirement().addList("bearerAuth")));
            }
        };
    }
}
