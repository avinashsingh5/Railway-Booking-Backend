package com.railway.train.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * API docs of this service. The Swagger UI itself lives on the gateway, so the "server" listed
 * here is the gateway - "Try it out" then goes through the gateway like any real client.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI(@Value("${spring.application.name}") String serviceName,
                           @Value("${gateway.public-url:http://localhost:8080}") String gatewayUrl) {
        return new OpenAPI()
                .info(new Info().title(serviceName).version("1.0.0"))
                .servers(List.of(new Server().url(gatewayUrl)))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
